package com.norcalretreat.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RegistrationDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String congregation;
    private String roomPreference;
    private String emergencyName;
    private String emergencyRelationship;
    private String emergencyPhone;
    private String specialRequests;
    private Boolean agreedToTerms;
    private Boolean speaker;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private String stripePaymentId;
    private Long userId;
    private LocalDateTime registeredAt;
    private List<AttendeeDTO> attendees;
    private int attendeeCount;

    // Populated only on create() response so the success screen can
    // show "You are #N of CAPACITY" without a follow-up round trip.
    private Integer positionFirst;
    private Integer positionLast;
    private Integer totalAttendees;
    private Integer capacity;
}
