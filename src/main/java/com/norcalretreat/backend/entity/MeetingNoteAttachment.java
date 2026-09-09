package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * File attached to a meeting note (agenda PDF, handout DOCX, image, etc.).
 * Stored as a BLOB in the DB so we don't need external object storage --
 * committee documents are small (usually under a few MB) and Railway
 * ephemeral filesystem can't hold them across deploys.
 *
 * If total blob size ever becomes a problem we can move data out to S3/R2
 * later; other columns already carry the metadata the UI needs.
 */
@Entity
@Table(name = "meeting_note_attachments",
        indexes = { @Index(name = "idx_meeting_note_att_note", columnList = "meeting_note_id") })
@Data
public class MeetingNoteAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_note_id", nullable = false)
    private MeetingNote meetingNote;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** Raw file bytes. LAZY so the list query doesn't drag every attachment
     *  blob into memory just to show file names. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
