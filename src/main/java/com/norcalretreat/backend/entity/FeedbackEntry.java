package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/** A submission from the public /feedback form. */
@Entity
@Table(name = "feedback_entries")
@Data
public class FeedbackEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String name;

    @Column(length = 200)
    private String email;

    /** 1-5 stars, nullable if the submitter skipped the rating. */
    @Column
    private Integer rating;

    /** "What worked well?" -- free text. */
    @Column(columnDefinition = "TEXT")
    private String worked;

    /** "What could be better?" -- free text. */
    @Column(columnDefinition = "TEXT")
    private String improve;

    /** "Anything else?" -- free text. */
    @Column(columnDefinition = "TEXT")
    private String other;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = LocalDateTime.now();
    }
}
