package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.entity.ProspectiveSpeaker;
import com.norcalretreat.backend.repository.ProspectiveSpeakerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** CRUD for the admin-only prospective-speakers list. Admin + Superadmin
 *  only — mirrored on both class-level @PreAuthorize so any new mapping
 *  added later inherits the gate without opt-in. */
@Slf4j
@RestController
@RequestMapping("/api/speakers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class ProspectiveSpeakerController {

    // Allowed status transitions. Kept here as a server guard so a malformed
    // PUT body can't poison the list with a status the UI won't know how to
    // render — e.g. "WHATEVER" ending up next to the real values.
    private static final Set<String> VALID_STATUSES = Set.of("pending", "invited", "confirmed", "declined");

    private final ProspectiveSpeakerRepository repo;

    @GetMapping
    public ResponseEntity<List<ProspectiveSpeaker>> list() {
        return ResponseEntity.ok(repo.findAllByOrderByStatusAscCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProspectiveSpeaker body) {
        String err = validate(body);
        if (err != null) return ResponseEntity.badRequest().body(Map.of("message", err));
        body.setId(null); // ignore any client-supplied id — DB assigns.
        if (body.getStatus() == null || body.getStatus().isBlank()) {
            body.setStatus("pending");
        }
        ProspectiveSpeaker saved = repo.save(body);
        log.info("Prospective speaker created: id={} name={}", saved.getId(), saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ProspectiveSpeaker body) {
        ProspectiveSpeaker existing = repo.findById(id).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();
        String err = validate(body);
        if (err != null) return ResponseEntity.badRequest().body(Map.of("message", err));
        existing.setName(body.getName());
        existing.setEmail(body.getEmail());
        existing.setPhone(body.getPhone());
        existing.setChurch(body.getChurch());
        existing.setTopic(body.getTopic());
        existing.setStatus(body.getStatus() == null || body.getStatus().isBlank() ? "pending" : body.getStatus());
        existing.setNotes(body.getNotes());
        ProspectiveSpeaker saved = repo.save(existing);
        log.info("Prospective speaker updated: id={} status={}", saved.getId(), saved.getStatus());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        log.info("Prospective speaker deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }

    /** Returns an error message or null if the payload is acceptable. */
    private String validate(ProspectiveSpeaker body) {
        if (body == null) return "Request body is required";
        if (body.getName() == null || body.getName().isBlank()) return "Name is required";
        if (body.getName().length() > 120) return "Name must be 120 characters or fewer";
        if (body.getEmail() != null && body.getEmail().length() > 180) return "Email must be 180 characters or fewer";
        if (body.getPhone() != null && body.getPhone().length() > 40) return "Phone must be 40 characters or fewer";
        if (body.getChurch() != null && body.getChurch().length() > 160) return "Church must be 160 characters or fewer";
        if (body.getTopic() != null && body.getTopic().length() > 240) return "Topic must be 240 characters or fewer";
        if (body.getStatus() != null && !body.getStatus().isBlank()
                && !VALID_STATUSES.contains(body.getStatus().toLowerCase())) {
            return "Status must be one of: " + VALID_STATUSES;
        }
        return null;
    }
}
