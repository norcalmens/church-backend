package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_plan_payments",
        indexes = { @Index(name = "idx_ppp_plan", columnList = "plan_id") })
@Data
public class PaymentPlanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** cash | check | stripe */
    @Column(nullable = false, length = 20)
    private String method;

    /** pending | paid | failed */
    @Column(length = 20)
    private String status = "paid";

    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    /** Free-text reference (check number, memo, transaction ID, etc.) */
    @Column(length = 200)
    private String reference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** When the money actually arrived. For Stripe = confirmation time. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (paidAt == null) paidAt = LocalDateTime.now();
        if (status == null) status = "paid";
    }
}
