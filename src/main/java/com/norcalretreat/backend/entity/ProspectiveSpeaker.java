package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Admin-only list of people being considered as retreat speakers. Tracks
 *  contact info, the proposed topic, and the admin's outreach status so
 *  the planning team can see at a glance who still needs a reply, who's
 *  confirmed, and who turned down the invite. */
@Entity
@Table(name = "prospective_speakers",
        indexes = {
            @Index(name = "idx_prospective_speaker_status", columnList = "status"),
            @Index(name = "idx_prospective_speaker_created", columnList = "created_at")
        })
@Getter
@Setter
public class ProspectiveSpeaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String church;

    /** Session title or subject matter the speaker is being asked about. */
    @Column(length = 240)
    private String topic;

    /** Outreach state. Values: pending, invited, confirmed, declined.
     *  Stored as a plain string (no DB enum) so admins can add new states
     *  without a migration if the workflow evolves. Default: pending. */
    @Column(nullable = false, length = 24)
    private String status = "pending";

    /** Freeform admin notes — background on the speaker, scheduling
     *  constraints, follow-up reminders, who referred them. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
