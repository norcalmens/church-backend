package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** Attachment metadata for the meeting-notes list/detail views. Never
 *  carries the actual blob -- that streams via the /download endpoint. */
@Data
public class MeetingNoteAttachmentDTO {
    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
