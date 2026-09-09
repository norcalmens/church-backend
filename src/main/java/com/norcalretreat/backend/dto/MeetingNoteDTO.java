package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingNoteDTO {
    private Long id;
    private String title;
    private LocalDate meetingDate;
    private String body;
    private String attendees;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MeetingNoteAttachmentDTO> attachments;
}
