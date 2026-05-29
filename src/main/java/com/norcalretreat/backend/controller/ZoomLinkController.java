package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.ZoomLinkDTO;
import com.norcalretreat.backend.service.ZoomLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/zoom-links")
@RequiredArgsConstructor
public class ZoomLinkController {

    private final ZoomLinkService service;

    @GetMapping
    public ResponseEntity<List<ZoomLinkDTO>> listActive() {
        return ResponseEntity.ok(service.listActive());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<ZoomLinkDTO>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ZoomLinkDTO> create(@RequestBody ZoomLinkDTO dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Admin '{}' creating zoom link: {}", username, dto.getTitle());
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ZoomLinkDTO> update(@PathVariable Long id, @RequestBody ZoomLinkDTO dto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Admin '{}' updating zoom link {}", username, id);
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Admin '{}' deleting zoom link {}", username, id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
