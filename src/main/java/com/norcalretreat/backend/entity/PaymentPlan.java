package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_plans",
        indexes = { @Index(name = "idx_payment_plan_token", columnList = "payer_token", unique = true) })
@Data
public class PaymentPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false, length = 200)
    private String planName;

    @Column(name = "retreat_label", nullable = false, length = 200)
    private String retreatLabel;

    @Column(name = "payer_name", nullable = false, length = 200)
    private String payerName;

    @Column(name = "payer_email", nullable = false, length = 200)
    private String payerEmail;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payer_token", nullable = false, length = 64, unique = true)
    private String payerToken;

    /** active | completed | canceled */
    @Column(length = 20)
    private String status = "active";

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ---- Capacity impact -------------------------------------------------
    // People on payment plans are still attending; they count against the
    // overnight bed cap the same as anyone in retreat_registrations.
    // These fields let the capacity calculator include them:
    //   retreatYear         -- which season this plan is for; matched
    //                          against retreat.active.year for the counter.
    //   overnightAttendees  -- how many beds this plan reserves. Defaults
    //                          to 1 (the payer themselves); admin can bump
    //                          it for family/group plans.
    // Only "active" and "completed" plans count -- "requested" is still
    // provisional (admin may reject), "canceled" no longer attending.

    @Column(name = "retreat_year")
    private Integer retreatYear;

    @Column(name = "overnight_attendees")
    private Integer overnightAttendees = 1;

    // ===== Recurring (Stripe Subscription) =====
    @Column(name = "stripe_customer_id", length = 64)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 64)
    private String stripeSubscriptionId;

    @Column(name = "recurring_amount", precision = 10, scale = 2)
    private BigDecimal recurringAmount;

    /** active | past_due | canceled | unpaid | trialing | null */
    @Column(name = "recurring_status", length = 20)
    private String recurringStatus;

    @Column(name = "recurring_started_at")
    private LocalDateTime recurringStartedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "active";
        if (payerToken == null || payerToken.isBlank()) {
            payerToken = UUID.randomUUID().toString().replace("-", "");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
