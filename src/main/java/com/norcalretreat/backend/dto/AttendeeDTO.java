package com.norcalretreat.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AttendeeDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private Integer age;
    private String dietaryRestrictions;

    /** "full" (default) or "partial". */
    private String attendanceType;

    /** When attendanceType = "partial", which days: any of "thu","fri","sat". */
    private List<String> days;

    /** Linens (full-retreat only): "none" (default) | "package" | "individual". */
    private String linenOption;

    /** Required when linenOption = "individual". */
    private Integer linenItemCount;

    /** Meals (single-day only): "none" (default) | "half" | "full". */
    private String mealOption;

    /** Server-computed; ignored on input. */
    private BigDecimal amountPaid;

    /** Admin-set: true if this individual is a retreat speaker. */
    private Boolean speaker;

    // Hoisted from parent registration so an attendee-flat admin view can
    // show contact info and church without re-joining server-side.
    private Long registrationId;
    private String congregation;
    private String primaryEmail;
    private String primaryPhone;
}
