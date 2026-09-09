package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Log of every outbound email the app attempts to send. Persists BEFORE
 * the SMTP call so we have a record even if the JVM crashes mid-send;
 * status is then updated to "sent" or "failed" after the send returns.
 *
 * Useful for:
 *   - troubleshooting delivery ("did the invite go out?")
 *   - showing an admin why a resend failed (SMTP error captured verbatim)
 *   - retrying failed sends from the UI
 *   - audit trail of automated notifications
 */
@Entity
@Table(name = "sent_emails",
        indexes = {
                @Index(name = "idx_sent_email_at",         columnList = "attempted_at"),
                @Index(name = "idx_sent_email_status",     columnList = "status"),
                @Index(name = "idx_sent_email_category",   columnList = "category"),
                @Index(name = "idx_sent_email_recipient",  columnList = "recipient")
        })
@Data
public class SentEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Primary recipient (To:). We don't currently CC/BCC; if that
     *  changes, add a separate column rather than smashing them here. */
    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    /** Short kind label so admin can filter the log by category. Values:
     *  registration_confirmation, admin_notification, payment_plan_invite,
     *  payment_plan_request_notification, password_reset, welcome,
     *  account_activated, payment_receipt, generic. */
    @Column(length = 60)
    private String category;

    /** Loose link back to the row this email was about (registration id,
     *  plan id, etc.) plus the entity name. Nullable -- generic sends
     *  (password reset, welcome) don't belong to a domain row. */
    @Column(name = "related_entity_type", length = 40)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    /** "sent" or "failed". "pending" is possible if the JVM dies between
     *  the pre-send insert and the post-send update, so the UI treats it
     *  the same as failed (needs attention). */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    /** Populated on failure with the underlying SMTP exception message. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Username of the admin who triggered a manual resend, if any.
     *  Auto-triggered sends leave this null. */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) attemptedAt = LocalDateTime.now();
        if (status == null) status = "pending";
    }
}
