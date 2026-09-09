package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Committee-internal meeting notes -- planning meetings, decisions,
 * action items. Simple plain-text body; document uploads live in the
 * {@link MeetingNoteAttachment} child table (agendas, handouts, etc.).
 */
@Entity
@Table(name = "meeting_notes",
        indexes = { @Index(name = "idx_meeting_note_date", columnList = "meeting_date") })
@Data
public class MeetingNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    /** Free-form notes. Plain text on purpose -- the committee wanted a
     *  simple CRUD, not a rich-text editor. If they need bold/lists later
     *  we can upgrade to Markdown rendering without a schema change. */
    @Column(columnDefinition = "TEXT")
    private String body;

    /** Comma-separated attendee names (kept lightweight, no separate table).
     *  If attendee analytics ever matter this can normalize into a join
     *  table without breaking existing rows. */
    @Column(length = 1000)
    private String attendees;

    /** Username of the committee member who created the note. Displayed
     *  in the list as "by <who>". Not enforced as a foreign key so an
     *  admin-deleted user doesn't cascade-delete their old notes. */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "meetingNote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MeetingNoteAttachment> attachments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
