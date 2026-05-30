package com.norcalretreat.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentPlanPaymentDTO {
    private Long id;
    private Long planId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String stripePaymentId;
    private String reference;
    private String notes;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
