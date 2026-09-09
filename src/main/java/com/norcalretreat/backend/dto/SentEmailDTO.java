package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SentEmailDTO {
    private Long id;
    private String recipient;
    private String subject;
    private String body;
    private String category;
    private String relatedEntityType;
    private Long relatedEntityId;
    private String status;
    private String errorMessage;
    private String triggeredBy;
    private LocalDateTime attemptedAt;
}
