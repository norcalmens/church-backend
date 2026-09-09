package com.norcalretreat.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Wire format for event RSVPs. Public submissions use only name/email/
 * phone/guestCount/notes -- id, eventKey, createdAt are server-set.
 */
@Data
public class EventRsvpDTO {
    private Long id;
    private String eventKey;
    private String name;
    private String email;
    private String phone;
    private String congregation;
    private Integer guestCount;
    private String notes;
    private LocalDateTime createdAt;
}
