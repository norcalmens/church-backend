package com.norcalretreat.backend.dto;

import lombok.Data;

@Data
public class AttendeeDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private Integer age;
    private String dietaryRestrictions;
}
