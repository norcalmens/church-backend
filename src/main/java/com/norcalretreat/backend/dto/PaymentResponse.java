package com.norcalretreat.backend.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private String clientSecret;
    private String paymentIntentId;
}
