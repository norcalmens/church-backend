package com.norcalretreat.backend.dto;

import lombok.Data;

@Data
public class AdminCreateUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String roleName;
    private String username;
}
