package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.SentEmailDTO;
import com.norcalretreat.backend.entity.SentEmail;
import com.norcalretreat.backend.repository.SentEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin log of every outbound email attempt. All endpoints are gated to
 * ADMIN/SUPERADMIN -- an email log can contain sensitive info (password
 * reset tokens, payer email addresses, etc.) so committee-tier users
 * shouldn't have read access.
 */
@Slf4j
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
public class SentEmailController {

    private final SentEmailRepository repo;

    /** Paged + filtered list. Query params:
     *   ?status=sent|failed|pending  (optional)
     *   ?category=payment_plan_invite|... (optional)
     *   ?q=text                     (searches recipient + subject; optional)
     *   ?page=0&size=25             (pagination) */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)));
        // Normalize empties to null so the query treats them as "no filter"
        String s  = (status  == null || status.isBlank())  ? null : status;
        String c  = (category == null || category.isBlank()) ? null : category;
        String qs = (q       == null || q.isBlank())       ? null : q.trim();
        Page<SentEmail> results = repo.search(s, c, qs, pageable);
        List<SentEmailDTO> dtos = results.getContent().stream().map(this::toDto).toList();
        Map<String, Object> body = new HashMap<>();
        body.put("content", dtos);
        body.put("totalElements", results.getTotalElements());
        body.put("totalPages", results.getTotalPages());
        body.put("page", results.getNumber());
        body.put("size", results.getSize());
        return ResponseEntity.ok(body);
    }

    /** Simple counts for the admin dashboard header. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> body = new HashMap<>();
        body.put("total",   repo.count());
        body.put("sent",    repo.countByStatus("sent"));
        body.put("failed",  repo.countByStatus("failed"));
        body.put("pending", repo.countByStatus("pending"));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return repo.findById(id)
                .map(e -> ResponseEntity.ok((Object) toDto(e)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("message", "Email log entry not found")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("message", "Not found"));
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private SentEmailDTO toDto(SentEmail e) {
        SentEmailDTO d = new SentEmailDTO();
        d.setId(e.getId());
        d.setRecipient(e.getRecipient());
        d.setSubject(e.getSubject());
        d.setBody(e.getBody());
        d.setCategory(e.getCategory());
        d.setRelatedEntityType(e.getRelatedEntityType());
        d.setRelatedEntityId(e.getRelatedEntityId());
        d.setStatus(e.getStatus());
        d.setErrorMessage(e.getErrorMessage());
        d.setTriggeredBy(e.getTriggeredBy());
        d.setAttemptedAt(e.getAttemptedAt());
        return d;
    }
}
