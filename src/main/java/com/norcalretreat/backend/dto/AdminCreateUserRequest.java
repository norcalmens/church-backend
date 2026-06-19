package com.norcalretreat.backend.dto;

import lombok.Data;

@Data
public class AdminCreateUserRequest {
    private String email;
    private String firstName;
    private String lastName;
    private String roleName;
    private String username;
    /** Optional. When blank, the service generates a memorable random
     *  temp password and returns it in the response so the admin can
     *  share it. Either way the user must change it on first sign-in. */
    private String password;
}
