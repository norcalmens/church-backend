package com.norcalretreat.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "menu_hidden_item")
@Data
public class MenuHiddenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_key", nullable = false, unique = true)
    private String itemKey;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @Column(name = "hidden_by")
    private String hiddenBy;

    @PrePersist
    protected void onCreate() {
        hiddenAt = LocalDateTime.now();
    }
}
