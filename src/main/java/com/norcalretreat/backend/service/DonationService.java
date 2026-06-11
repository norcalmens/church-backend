package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.DonationCreateResponse;
import com.norcalretreat.backend.dto.DonationDTO;
import com.norcalretreat.backend.entity.Donation;
import com.norcalretreat.backend.repository.DonationRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonationService {

    private final DonationRepository repository;

    @Transactional
    public DonationCreateResponse create(DonationDTO req) throws StripeException {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Donation amount must be at least $1");
        }
        if (req.getDonorName() == null || req.getDonorName().isBlank()) {
            throw new IllegalArgumentException("Donor name is required");
        }
        if (req.getDonorEmail() == null || req.getDonorEmail().isBlank()) {
            throw new IllegalArgumentException("Donor email is required");
        }

        Donation donation = new Donation();
        donation.setDonorName(req.getDonorName().trim());
        donation.setDonorEmail(req.getDonorEmail().trim());
        donation.setAmount(req.getAmount());
        donation.setCurrency(req.getCurrency() != null ? req.getCurrency().toLowerCase() : "usd");
        donation.setMessage(req.getMessage());
        donation.setPaymentStatus("pending");
        donation = repository.save(donation);

        long amountInCents = donation.getAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("donation_id", String.valueOf(donation.getId()));
        metadata.put("donor_name", donation.getDonorName());
        metadata.put("donor_email", donation.getDonorEmail());
        metadata.put("type", "donation");

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(donation.getCurrency())
                .setDescription(buildStripeDescription(donation))
                .setReceiptEmail(donation.getDonorEmail())
                .putAllMetadata(metadata)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        donation.setStripePaymentId(intent.getId());
        donation = repository.save(donation);

        log.info("Created donation {} for {} ({}) — ${}", donation.getId(), donation.getDonorName(), donation.getDonorEmail(), donation.getAmount());
        return new DonationCreateResponse(toDto(donation), intent.getClientSecret());
    }

    @Transactional
    public DonationDTO confirm(Long id) throws StripeException {
        Donation donation = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donation not found: " + id));
        if (donation.getStripePaymentId() == null) {
            throw new IllegalStateException("Donation has no Stripe payment intent");
        }
        PaymentIntent intent = PaymentIntent.retrieve(donation.getStripePaymentId());
        String status = intent.getStatus();
        if ("succeeded".equals(status)) {
            donation.setPaymentStatus("paid");
        } else if ("processing".equals(status)) {
            donation.setPaymentStatus("processing");
        } else if ("requires_payment_method".equals(status) || "canceled".equals(status)) {
            donation.setPaymentStatus("failed");
        } else {
            donation.setPaymentStatus(status);
        }
        donation = repository.save(donation);
        log.info("Confirmed donation {} — Stripe status: {}", donation.getId(), status);
        return toDto(donation);
    }

    public List<DonationDTO> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    /**
     * Admin-only: update an existing donation record (correct a typo, fix the donor email,
     * adjust the status, etc.). Does not interact with Stripe.
     */
    @Transactional
    public DonationDTO update(Long id, DonationDTO req) {
        Donation d = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Donation not found: " + id));
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (req.getDonorName() == null || req.getDonorName().isBlank()) {
            throw new IllegalArgumentException("Donor name is required");
        }
        if (req.getDonorEmail() == null || req.getDonorEmail().isBlank()) {
            throw new IllegalArgumentException("Donor email is required");
        }
        d.setDonorName(req.getDonorName().trim());
        d.setDonorEmail(req.getDonorEmail().trim());
        d.setAmount(req.getAmount());
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) {
            d.setCurrency(req.getCurrency().toLowerCase());
        }
        d.setMessage(req.getMessage());
        d.setAdminNotes(req.getAdminNotes());
        d.setStripePaymentId(req.getStripePaymentId());
        if (req.getPaymentStatus() != null && !req.getPaymentStatus().isBlank()) {
            d.setPaymentStatus(req.getPaymentStatus());
        }
        d = repository.save(d);
        log.info("Updated donation {} for {} — ${}", d.getId(), d.getDonorName(), d.getAmount());
        return toDto(d);
    }

    /**
     * Admin-only: delete a donation record. Used to clean up pending entries
     * (abandoned card flows) or duplicate manual records. Does NOT touch Stripe --
     * a paid Stripe charge stays on the books at Stripe even if removed here.
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Donation not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted donation {}", id);
    }

    /**
     * Record a donation that was already processed outside this app (e.g., a past Stripe charge,
     * a cash gift, or a check). Skips Stripe entirely.
     */
    @Transactional
    public DonationDTO createManual(DonationDTO req) {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (req.getDonorName() == null || req.getDonorName().isBlank()) {
            throw new IllegalArgumentException("Donor name is required");
        }
        if (req.getDonorEmail() == null || req.getDonorEmail().isBlank()) {
            throw new IllegalArgumentException("Donor email is required");
        }
        Donation d = new Donation();
        d.setDonorName(req.getDonorName().trim());
        d.setDonorEmail(req.getDonorEmail().trim());
        d.setAmount(req.getAmount());
        d.setCurrency(req.getCurrency() != null && !req.getCurrency().isBlank() ? req.getCurrency().toLowerCase() : "usd");
        d.setMessage(req.getMessage());
        d.setAdminNotes(req.getAdminNotes());
        d.setStripePaymentId(req.getStripePaymentId());
        d.setPaymentStatus(req.getPaymentStatus() != null && !req.getPaymentStatus().isBlank() ? req.getPaymentStatus() : "paid");
        d = repository.save(d);
        log.info("Recorded manual donation {} for {} — ${}", d.getId(), d.getDonorName(), d.getAmount());
        return toDto(d);
    }

    private String buildStripeDescription(Donation d) {
        String base = "Donation — NorCal Men's Retreat 2026";
        return d.getMessage() != null && !d.getMessage().isBlank()
                ? base + ": " + d.getMessage().trim()
                : base;
    }

    private DonationDTO toDto(Donation d) {
        DonationDTO dto = new DonationDTO();
        dto.setId(d.getId());
        dto.setDonorName(d.getDonorName());
        dto.setDonorEmail(d.getDonorEmail());
        dto.setAmount(d.getAmount());
        dto.setCurrency(d.getCurrency());
        dto.setMessage(d.getMessage());
        dto.setAdminNotes(d.getAdminNotes());
        dto.setStripePaymentId(d.getStripePaymentId());
        dto.setPaymentStatus(d.getPaymentStatus());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());
        return dto;
    }
}
