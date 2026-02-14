package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private Boolean isActive;
    private Boolean isLocked;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private Boolean passwordChangeRequired;
}
