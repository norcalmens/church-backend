package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "retreat_registrations")
@Data
public class RetreatRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Primary contact
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    // Accommodation
    @Column(name = "room_preference", length = 20)
    private String roomPreference;

    // Emergency contact
    @Column(name = "emergency_name", length = 200)
    private String emergencyName;

    @Column(name = "emergency_relationship", length = 100)
    private String emergencyRelationship;

    @Column(name = "emergency_phone", length = 20)
    private String emergencyPhone;

    // Additional
    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(name = "agreed_to_terms")
    private Boolean agreedToTerms = false;

    // Payment
    @Column(name = "stripe_payment_id")
    private String stripePaymentId;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "pending";

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // Metadata
    @Column(name = "user_id", nullable = true)
    private Long userId;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attendee> attendees = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = "pending";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
