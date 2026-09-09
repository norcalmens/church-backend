package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * RSVP submission for a lightweight event -- breakfast, meet-and-greet,
 * outreach, anything short of a full retreat registration.
 *
 * `eventKey` is a short slug ("breakfast-oct-2026", "info-night-jan",
 * etc.) rather than a foreign key to an Event table, so a new event
 * needs zero backend changes -- an admin just uses a new key on the
 * public form URL. Simple by design; if events grow into needing their
 * own fields (capacity, dates, prices) we can promote eventKey into a
 * proper foreign key later without disturbing existing rows.
 */
@Entity
@Table(name = "event_rsvps",
        indexes = { @Index(name = "idx_event_rsvp_key", columnList = "event_key") })
@Data
public class EventRsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Slug identifying which event this RSVP is for. Public form URL is
     *  /rsvp/{eventKey} and admin filters the list by this. */
    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Optional -- which church / congregation they attend. Useful for
     *  planning ("we've got 12 from Community CoC, 8 from Parkway...")
     *  and follow-up. Kept freeform rather than a lookup so RSVPers
     *  don't have to find themselves in a dropdown. */
    @Column(length = 200)
    private String congregation;

    /** How many people (including the RSVPer) they're bringing. Defaults
     *  to 1 -- most RSVPs are for one person. */
    @Column(name = "guest_count")
    private Integer guestCount = 1;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (guestCount == null || guestCount < 1) guestCount = 1;
    }
}
