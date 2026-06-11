package com.norcalretreat.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DonationDTO {
    private Long id;
    private String donorName;
    private String donorEmail;
    private BigDecimal amount;
    private String currency;
    private String message;
    private String adminNotes;
    private String stripePaymentId;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
