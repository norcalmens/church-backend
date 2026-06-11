package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.entity.FeedbackEntry;
import com.norcalretreat.backend.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository repository;

    // Public -- anyone who attended can submit feedback
    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody FeedbackEntry entry) {
        entry.setId(null);
        entry.setSubmittedAt(null);  // PrePersist sets it
        FeedbackEntry saved = repository.save(entry);
        return ResponseEntity.ok(Map.of("id", saved.getId(), "message", "Feedback received"));
    }

    // Admin-only -- list every submission, newest first
    @GetMapping
    public ResponseEntity<List<FeedbackEntry>> list() {
        return ResponseEntity.ok(repository.findAllByOrderBySubmittedAtDesc());
    }

    // Admin-only -- remove a submission (spam, duplicate, etc.)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Feedback not found"));
        }
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Feedback deleted"));
    }
}
