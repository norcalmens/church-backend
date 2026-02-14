package com.norcalretreat.backend.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long amount;
    private String currency;
    private String description;
    private String donorName;
    private String donorEmail;
}
