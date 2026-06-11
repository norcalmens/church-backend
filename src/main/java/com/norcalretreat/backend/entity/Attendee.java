package com.norcalretreat.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "attendees")
@Data
public class Attendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    private Integer age;

    @Column(name = "dietary_restrictions")
    private String dietaryRestrictions;

    /** "full" or "partial". Defaults to "full" for legacy rows. */
    @Column(name = "attendance_type", length = 16)
    private String attendanceType = "full";

    /** Comma-separated retreat days, e.g. "thu,fri" — only meaningful when attendanceType = "partial". */
    @Column(name = "days", length = 32)
    private String days;

    /** "none" | "package" | "individual" — only applies to full-retreat (overnight) attendees. */
    @Column(name = "linen_option", length = 16)
    private String linenOption = "none";

    @Column(name = "linen_item_count")
    private Integer linenItemCount;

    /** "none" | "half" | "full" — only applies to single-day (partial) attendees. */
    @Column(name = "meal_option", length = 16)
    private String mealOption = "none";

    /** Server-computed price for this attendee. */
    @Column(name = "amount_paid", precision = 10, scale = 2)
    private BigDecimal amountPaid;

    /** Admin-set flag so the actual speaker is marked, even when their
     *  family was registered under someone else's contact info. */
    @Column(name = "speaker", nullable = false)
    private Boolean speaker = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    @JsonIgnore
    private RetreatRegistration registration;
}
