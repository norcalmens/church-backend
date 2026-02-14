package com.norcalretreat.backend.dto;

import lombok.Data;

@Data
public class CompleteRegistrationRequest {
    private String email;
    private String currentPassword;
    private String newPassword;
    private String firstName;
    private String lastName;
    private String phone;
}
