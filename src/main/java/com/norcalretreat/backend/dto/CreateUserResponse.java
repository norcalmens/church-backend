package com.norcalretreat.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateUserResponse {
    private UserDTO user;
    private boolean welcomeEmailSent;
    private String defaultPassword;
}
