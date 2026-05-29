package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ZoomLinkDTO {
    private Long id;
    private String title;
    private String description;
    private String joinUrl;
    private String meetingId;
    private String passcode;
    private String scheduleText;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
