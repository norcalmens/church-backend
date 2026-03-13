package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.AttendeeDTO;
import com.norcalretreat.backend.dto.PaymentResponse;
import com.norcalretreat.backend.dto.RegistrationDTO;
import com.norcalretreat.backend.entity.Attendee;
import com.norcalretreat.backend.entity.RetreatRegistration;
import com.norcalretreat.backend.repository.RegistrationRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationRepository registrationRepository;

    @Value("${retreat.cost-per-person:248.00}")
    private BigDecimal costPerPerson;

    private EmailService emailService;

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Transactional
    public RegistrationDTO createRegistration(RegistrationDTO dto, Long userId) {
        RetreatRegistration reg = new RetreatRegistration();
        reg.setUserId(userId);
        mapDtoToEntity(dto, reg);

        // Compute total amount based on attendees
        int attendeeCount = dto.getAttendees() != null ? dto.getAttendees().size() : 0;
        reg.setTotalAmount(costPerPerson.multiply(BigDecimal.valueOf(attendeeCount)));

        // Map attendees
        if (dto.getAttendees() != null) {
            for (AttendeeDTO aDto : dto.getAttendees()) {
                Attendee attendee = new Attendee();
                attendee.setFirstName(aDto.getFirstName());
                attendee.setLastName(aDto.getLastName());
                attendee.setAge(aDto.getAge());
                attendee.setDietaryRestrictions(aDto.getDietaryRestrictions());
                attendee.setRegistration(reg);
                reg.getAttendees().add(attendee);
            }
        }

        reg = registrationRepository.save(reg);
        return convertToDTO(reg);
    }

    @Transactional
    public PaymentResponse createPaymentIntent(Long registrationId) throws StripeException {
        RetreatRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (!"pending".equals(reg.getPaymentStatus())) {
            throw new IllegalArgumentException("Registration is not in pending status");
        }

        long amountInCents = reg.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("NorCal Men's Retreat 2026 - " + reg.getFirstName() + " " + reg.getLastName())
                .putMetadata("registration_id", registrationId.toString())
                .putMetadata("registrant_email", reg.getEmail())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        reg.setStripePaymentId(paymentIntent.getId());
        registrationRepository.save(reg);

        return new PaymentResponse(paymentIntent.getClientSecret(), paymentIntent.getId());
    }

    @Transactional
    public RegistrationDTO confirmPayment(Long registrationId) throws StripeException {
        RetreatRegistration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        // Idempotent: if already paid, return success
        if ("paid".equals(reg.getPaymentStatus())) {
            return convertToDTO(reg);
        }

        if (reg.getStripePaymentId() == null) {
            throw new IllegalArgumentException("No payment intent found for this registration");
        }

        // Verify payment status with Stripe directly
        PaymentIntent paymentIntent = PaymentIntent.retrieve(reg.getStripePaymentId());
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new IllegalArgumentException("Payment has not succeeded. Status: " + paymentIntent.getStatus());
        }

        reg.setPaymentStatus("paid");
        reg = registrationRepository.save(reg);

        // Send confirmation emails after successful payment
        if (emailService != null) {
            try {
                emailService.sendRegistrationConfirmation(reg);
                emailService.sendAdminNotification(reg);
            } catch (Exception e) {
                log.error("Failed to send registration emails", e);
            }
        }

        return convertToDTO(reg);
    }

    public List<RegistrationDTO> getUserRegistrations(Long userId) {
        return registrationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public RegistrationDTO getRegistration(Long id) {
        RetreatRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));
        return convertToDTO(reg);
    }

    @Transactional
    public RegistrationDTO updateRegistration(Long id, RegistrationDTO dto, Long userId) {
        RetreatRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (reg.getUserId() == null || !reg.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to update this registration");
        }

        mapDtoToEntity(dto, reg);

        // Clear and re-add attendees
        reg.getAttendees().clear();
        if (dto.getAttendees() != null) {
            for (AttendeeDTO aDto : dto.getAttendees()) {
                Attendee attendee = new Attendee();
                attendee.setFirstName(aDto.getFirstName());
                attendee.setLastName(aDto.getLastName());
                attendee.setAge(aDto.getAge());
                attendee.setDietaryRestrictions(aDto.getDietaryRestrictions());
                attendee.setRegistration(reg);
                reg.getAttendees().add(attendee);
            }
        }

        int attendeeCount = reg.getAttendees().size();
        reg.setTotalAmount(costPerPerson.multiply(BigDecimal.valueOf(attendeeCount)));

        reg = registrationRepository.save(reg);
        return convertToDTO(reg);
    }

    @Transactional
    public void deleteRegistration(Long id, Long userId) {
        RetreatRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (reg.getUserId() == null || !reg.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this registration");
        }

        registrationRepository.delete(reg);
    }

    // Admin methods

    @Transactional
    public void adminDeleteRegistration(Long id) {
        RetreatRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));
        registrationRepository.delete(reg);
    }

    public List<RegistrationDTO> getAllRegistrations() {
        return registrationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        List<RetreatRegistration> all = registrationRepository.findAll();
        long totalRegistrations = all.size();
        long totalAttendees = all.stream()
                .mapToLong(r -> r.getAttendees().size())
                .sum();
        long paidCount = registrationRepository.countByPaymentStatus("paid");
        BigDecimal totalRevenue = all.stream()
                .filter(r -> "paid".equals(r.getPaymentStatus()))
                .map(RetreatRegistration::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegistrations", totalRegistrations);
        stats.put("totalAttendees", totalAttendees);
        stats.put("paidRegistrations", paidCount);
        stats.put("totalRevenue", totalRevenue);
        return stats;
    }

    // Helper methods

    private void mapDtoToEntity(RegistrationDTO dto, RetreatRegistration reg) {
        reg.setFirstName(dto.getFirstName());
        reg.setLastName(dto.getLastName());
        reg.setEmail(dto.getEmail());
        reg.setPhone(dto.getPhone());
        reg.setAddress(dto.getAddress());
        reg.setCity(dto.getCity());
        reg.setState(dto.getState());
        reg.setZipCode(dto.getZipCode());
        reg.setRoomPreference(dto.getRoomPreference());
        reg.setEmergencyName(dto.getEmergencyName());
        reg.setEmergencyRelationship(dto.getEmergencyRelationship());
        reg.setEmergencyPhone(dto.getEmergencyPhone());
        reg.setSpecialRequests(dto.getSpecialRequests());
        reg.setAgreedToTerms(dto.getAgreedToTerms());
    }

    private RegistrationDTO convertToDTO(RetreatRegistration reg) {
        RegistrationDTO dto = new RegistrationDTO();
        dto.setId(reg.getId());
        dto.setFirstName(reg.getFirstName());
        dto.setLastName(reg.getLastName());
        dto.setEmail(reg.getEmail());
        dto.setPhone(reg.getPhone());
        dto.setAddress(reg.getAddress());
        dto.setCity(reg.getCity());
        dto.setState(reg.getState());
        dto.setZipCode(reg.getZipCode());
        dto.setRoomPreference(reg.getRoomPreference());
        dto.setEmergencyName(reg.getEmergencyName());
        dto.setEmergencyRelationship(reg.getEmergencyRelationship());
        dto.setEmergencyPhone(reg.getEmergencyPhone());
        dto.setSpecialRequests(reg.getSpecialRequests());
        dto.setAgreedToTerms(reg.getAgreedToTerms());
        dto.setPaymentStatus(reg.getPaymentStatus());
        dto.setTotalAmount(reg.getTotalAmount());
        dto.setStripePaymentId(reg.getStripePaymentId());
        dto.setUserId(reg.getUserId());
        dto.setRegisteredAt(reg.getRegisteredAt());
        dto.setAttendeeCount(reg.getAttendees().size());
        dto.setAttendees(reg.getAttendees().stream()
                .map(this::convertAttendeeToDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private AttendeeDTO convertAttendeeToDTO(Attendee attendee) {
        AttendeeDTO dto = new AttendeeDTO();
        dto.setId(attendee.getId());
        dto.setFirstName(attendee.getFirstName());
        dto.setLastName(attendee.getLastName());
        dto.setAge(attendee.getAge());
        dto.setDietaryRestrictions(attendee.getDietaryRestrictions());
        return dto;
    }
}
