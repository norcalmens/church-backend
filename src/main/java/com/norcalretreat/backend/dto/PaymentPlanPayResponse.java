package com.norcalretreat.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentPlanPayResponse {
    private Long paymentId;
    private String clientSecret;
}
