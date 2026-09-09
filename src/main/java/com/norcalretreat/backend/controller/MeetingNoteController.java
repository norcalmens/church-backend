package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.MeetingNoteDTO;
import com.norcalretreat.backend.entity.MeetingNoteAttachment;
import com.norcalretreat.backend.service.MeetingNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Committee/admin-only meeting notes API. Everything here requires an
 * ADMIN, SUPERADMIN, or COMMITTEE role -- meeting notes are internal.
 * File attachments upload/download via multipart / octet-stream.
 */
@Slf4j
@RestController
@RequestMapping("/api/meeting-notes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COMMITTEE')")
public class MeetingNoteController {

    private final MeetingNoteService service;

    @GetMapping
    public ResponseEntity<List<MeetingNoteDTO>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try { return ResponseEntity.ok(service.get(id)); }
        catch (IllegalArgumentException e) { return ResponseEntity.status(404).body(Map.of("message", e.getMessage())); }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody MeetingNoteDTO req, Authentication auth) {
        try { return ResponseEntity.ok(service.create(req, auth != null ? auth.getName() : null)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody MeetingNoteDTO req) {
        try { return ResponseEntity.ok(service.update(id, req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ---- Attachments ----

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable Long id,
                                    @RequestParam("file") MultipartFile file,
                                    Authentication auth) {
        try {
            return ResponseEntity.ok(service.addAttachment(id, file, auth != null ? auth.getName() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Attachment upload failed for note {}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<?> download(@PathVariable Long attachmentId) {
        try {
            MeetingNoteAttachment a = service.loadAttachment(attachmentId);
            String ct = a.getContentType() != null ? a.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            // Content-Disposition attachment forces a download prompt with
            // the original filename, instead of the browser trying to render
            // whatever the blob is inline (safer + more predictable).
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + a.getFileName().replace("\"", "'") + "\"")
                    .contentType(MediaType.parseMediaType(ct))
                    .contentLength(a.getData() != null ? a.getData().length : 0)
                    .body(new ByteArrayResource(a.getData() != null ? a.getData() : new byte[0]));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long attachmentId) {
        try { service.deleteAttachment(attachmentId); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }
}
