package com.norcalretreat.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentPlanDTO {
    private Long id;
    private String planName;
    private String retreatLabel;
    private String payerName;
    private String payerEmail;
    private BigDecimal totalAmount;
    private String payerToken;
    private String status;
    private String notes;

    // Recurring (Stripe Subscription)
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private BigDecimal recurringAmount;
    private String recurringStatus;
    private LocalDateTime recurringStartedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed when listing/getting
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private List<PaymentPlanPaymentDTO> payments;
}
