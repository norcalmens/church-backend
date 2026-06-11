package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "donations")
@Data
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "donor_name", nullable = false, length = 200)
    private String donorName;

    @Column(name = "donor_email", nullable = false, length = 200)
    private String donorEmail;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 8)
    private String currency = "usd";

    @Column(columnDefinition = "TEXT")
    private String message;

    /** Admin-only internal notes: "sent thank-you letter", "check
     *  reconciled", "donor wants anonymity", etc. Never shown to the donor. */
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "pending";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = "pending";
        if (currency == null) currency = "usd";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
