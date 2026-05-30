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
