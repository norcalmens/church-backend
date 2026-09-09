package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.EventRsvpDTO;
import com.norcalretreat.backend.service.EventRsvpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rsvp")
@RequiredArgsConstructor
public class EventRsvpController {

    private final EventRsvpService service;

    // ---- Public ----

    /** Fetch metadata about the event -- lets the public form render
     *  "You're RSVPing for {name}" before the visitor submits. Returns
     *  404 for unknown event keys so probes can't enumerate. */
    @GetMapping("/public/{eventKey}")
    public ResponseEntity<?> exists(@PathVariable String eventKey) {
        if (!service.eventExists(eventKey)) {
            return ResponseEntity.status(404).body(Map.of("message", "Event not found"));
        }
        return ResponseEntity.ok(Map.of("eventKey", eventKey));
    }

    /** Public RSVP submission. Same event-key allowlist prevents junk rows. */
    @PostMapping("/public/{eventKey}")
    public ResponseEntity<?> submit(@PathVariable String eventKey, @RequestBody EventRsvpDTO req) {
        try {
            return ResponseEntity.ok(service.submit(eventKey, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ---- Admin ----

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COMMITTEE')")
    public ResponseEntity<List<EventRsvpDTO>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{eventKey}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COMMITTEE')")
    public ResponseEntity<List<EventRsvpDTO>> listForEvent(@PathVariable String eventKey) {
        return ResponseEntity.ok(service.listByEvent(eventKey));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
