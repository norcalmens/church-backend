package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Key/value settings that admins can edit live (no redeploy).
 * Currently used for `retreat.capacity`; designed to absorb future
 * editable knobs without schema changes.
 */
@Entity
@Table(name = "system_settings")
@Data
public class SystemSetting {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", length = 500)
    private String settingValue;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    protected void stamp() {
        updatedAt = LocalDateTime.now();
    }
}
