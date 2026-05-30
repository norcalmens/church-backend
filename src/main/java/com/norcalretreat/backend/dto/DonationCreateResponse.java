package com.norcalretreat.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DonationCreateResponse {
    private DonationDTO donation;
    private String clientSecret;
}
