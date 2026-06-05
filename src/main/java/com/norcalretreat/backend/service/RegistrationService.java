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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationRepository registrationRepository;

    @Value("${retreat.cost-per-person:248.00}")
    private BigDecimal fullRetreatPrice;

    @Value("${retreat.cost-per-day:85.00}")
    private BigDecimal perDayPrice;

    @Value("${retreat.linen-package-price:25.00}")
    private BigDecimal linenPackagePrice;

    @Value("${retreat.linen-item-price:5.00}")
    private BigDecimal linenItemPrice;

    @Value("${retreat.meals-half-day-price:50.00}")
    private BigDecimal halfDayMealPrice;

    @Value("${retreat.meals-full-day-price:65.00}")
    private BigDecimal fullDayMealPrice;

    private static final Set<String> VALID_DAYS = new HashSet<>(Arrays.asList("thu", "fri", "sat"));

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

        if (dto.getAttendees() != null) {
            for (AttendeeDTO aDto : dto.getAttendees()) {
                reg.getAttendees().add(buildAttendee(aDto, reg));
            }
        }

        reg.setTotalAmount(computeTotal(reg));

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

        if ("paid".equals(reg.getPaymentStatus())) {
            return convertToDTO(reg);
        }

        if (reg.getStripePaymentId() == null) {
            throw new IllegalArgumentException("No payment intent found for this registration");
        }

        PaymentIntent paymentIntent = PaymentIntent.retrieve(reg.getStripePaymentId());
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            throw new IllegalArgumentException("Payment has not succeeded. Status: " + paymentIntent.getStatus());
        }

        reg.setPaymentStatus("paid");
        reg = registrationRepository.save(reg);

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

        reg.getAttendees().clear();
        if (dto.getAttendees() != null) {
            for (AttendeeDTO aDto : dto.getAttendees()) {
                reg.getAttendees().add(buildAttendee(aDto, reg));
            }
        }

        reg.setTotalAmount(computeTotal(reg));

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

    @Transactional
    public RegistrationDTO setSpeakerFlag(Long id, boolean speaker) {
        RetreatRegistration reg = registrationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));
        reg.setSpeaker(speaker);
        reg = registrationRepository.save(reg);
        return convertToDTO(reg);
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

        // Day-attendance counts (for catering / lodging planning)
        Map<String, Long> dayCounts = new HashMap<>();
        dayCounts.put("thu", 0L);
        dayCounts.put("fri", 0L);
        dayCounts.put("sat", 0L);
        for (RetreatRegistration reg : all) {
            for (Attendee a : reg.getAttendees()) {
                if ("partial".equalsIgnoreCase(a.getAttendanceType())) {
                    for (String d : parseDays(a.getDays())) {
                        dayCounts.merge(d, 1L, Long::sum);
                    }
                } else {
                    dayCounts.merge("thu", 1L, Long::sum);
                    dayCounts.merge("fri", 1L, Long::sum);
                    dayCounts.merge("sat", 1L, Long::sum);
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRegistrations", totalRegistrations);
        stats.put("totalAttendees", totalAttendees);
        stats.put("paidRegistrations", paidCount);
        stats.put("totalRevenue", totalRevenue);
        stats.put("dayAttendance", dayCounts);
        return stats;
    }

    // ----- Helpers -----

    private void mapDtoToEntity(RegistrationDTO dto, RetreatRegistration reg) {
        reg.setFirstName(dto.getFirstName());
        reg.setLastName(dto.getLastName());
        reg.setEmail(dto.getEmail());
        reg.setPhone(dto.getPhone());
        reg.setAddress(dto.getAddress());
        reg.setCity(dto.getCity());
        reg.setState(dto.getState());
        reg.setZipCode(dto.getZipCode());
        reg.setCongregation(dto.getCongregation());
        reg.setRoomPreference(dto.getRoomPreference());
        reg.setEmergencyName(dto.getEmergencyName());
        reg.setEmergencyRelationship(dto.getEmergencyRelationship());
        reg.setEmergencyPhone(dto.getEmergencyPhone());
        reg.setSpecialRequests(dto.getSpecialRequests());
        reg.setAgreedToTerms(dto.getAgreedToTerms());
        if (dto.getSpeaker() != null) reg.setSpeaker(dto.getSpeaker());
    }

    private Attendee buildAttendee(AttendeeDTO aDto, RetreatRegistration reg) {
        Attendee attendee = new Attendee();
        attendee.setFirstName(aDto.getFirstName());
        attendee.setLastName(aDto.getLastName());
        attendee.setAge(aDto.getAge());
        attendee.setDietaryRestrictions(aDto.getDietaryRestrictions());

        boolean isPartial = "partial".equalsIgnoreCase(aDto.getAttendanceType());
        attendee.setAttendanceType(isPartial ? "partial" : "full");

        if (isPartial) {
            List<String> normalized = (aDto.getDays() == null ? List.<String>of() : aDto.getDays()).stream()
                    .filter(d -> d != null && VALID_DAYS.contains(d.toLowerCase()))
                    .map(String::toLowerCase)
                    .distinct()
                    .collect(Collectors.toList());
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Single-day attendee must select at least one day");
            }
            attendee.setDays(String.join(",", normalized));
            attendee.setLinenOption("none");
            attendee.setLinenItemCount(null);
            attendee.setMealOption(normalizeMealOption(aDto.getMealOption()));
        } else {
            attendee.setDays(null);
            attendee.setMealOption("none");
            String linen = normalizeLinenOption(aDto.getLinenOption());
            attendee.setLinenOption(linen);
            if ("individual".equals(linen)) {
                int count = aDto.getLinenItemCount() != null ? aDto.getLinenItemCount() : 0;
                if (count < 1) {
                    throw new IllegalArgumentException("Individual linen items requires linenItemCount >= 1");
                }
                attendee.setLinenItemCount(count);
            } else {
                attendee.setLinenItemCount(null);
            }
        }

        attendee.setAmountPaid(computeAttendeeCost(attendee));
        attendee.setRegistration(reg);
        return attendee;
    }

    private BigDecimal computeAttendeeCost(Attendee a) {
        BigDecimal base;
        if ("partial".equalsIgnoreCase(a.getAttendanceType())) {
            int dayCount = parseDays(a.getDays()).size();
            BigDecimal perDay = perDayPrice.multiply(BigDecimal.valueOf(dayCount));
            base = perDay.min(fullRetreatPrice);
            BigDecimal meal = BigDecimal.ZERO;
            if ("half".equalsIgnoreCase(a.getMealOption())) meal = halfDayMealPrice;
            else if ("full".equalsIgnoreCase(a.getMealOption())) meal = fullDayMealPrice;
            return base.add(meal);
        }
        base = fullRetreatPrice;
        BigDecimal linen = BigDecimal.ZERO;
        if ("package".equalsIgnoreCase(a.getLinenOption())) {
            linen = linenPackagePrice;
        } else if ("individual".equalsIgnoreCase(a.getLinenOption())) {
            int count = a.getLinenItemCount() != null ? a.getLinenItemCount() : 0;
            linen = linenItemPrice.multiply(BigDecimal.valueOf(count));
        }
        return base.add(linen);
    }

    private BigDecimal computeTotal(RetreatRegistration reg) {
        return reg.getAttendees().stream()
                .map(a -> a.getAmountPaid() != null ? a.getAmountPaid() : computeAttendeeCost(a))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalizeLinenOption(String value) {
        if (value == null) return "none";
        String v = value.toLowerCase();
        return ("package".equals(v) || "individual".equals(v)) ? v : "none";
    }

    private String normalizeMealOption(String value) {
        if (value == null) return "none";
        String v = value.toLowerCase();
        return ("half".equals(v) || "full".equals(v)) ? v : "none";
    }

    private List<String> parseDays(String days) {
        if (days == null || days.isBlank()) return List.of();
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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
        dto.setCongregation(reg.getCongregation());
        dto.setRoomPreference(reg.getRoomPreference());
        dto.setEmergencyName(reg.getEmergencyName());
        dto.setEmergencyRelationship(reg.getEmergencyRelationship());
        dto.setEmergencyPhone(reg.getEmergencyPhone());
        dto.setSpecialRequests(reg.getSpecialRequests());
        dto.setAgreedToTerms(reg.getAgreedToTerms());
        dto.setSpeaker(reg.getSpeaker() != null && reg.getSpeaker());
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
        dto.setAttendanceType(attendee.getAttendanceType());
        dto.setDays(parseDays(attendee.getDays()));
        dto.setLinenOption(attendee.getLinenOption());
        dto.setLinenItemCount(attendee.getLinenItemCount());
        dto.setMealOption(attendee.getMealOption());
        dto.setAmountPaid(attendee.getAmountPaid());
        return dto;
    }
}
