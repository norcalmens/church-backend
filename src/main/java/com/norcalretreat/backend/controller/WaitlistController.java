package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.ApiResponse;
import com.norcalretreat.backend.entity.WaitlistEntry;
import com.norcalretreat.backend.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    // Public — anyone can submit themselves to the waitlist
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody WaitlistEntry entry) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Added to waitlist", waitlistService.create(entry)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Admin endpoints
    @GetMapping
    public ResponseEntity<ApiResponse<List<WaitlistEntry>>> list() {
        return ResponseEntity.ok(ApiResponse.success(waitlistService.listAll()));
    }

    @PatchMapping("/{id}/contacted")
    public ResponseEntity<ApiResponse<WaitlistEntry>> setContacted(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            boolean contacted = Boolean.TRUE.equals(body.get("contacted"));
            return ResponseEntity.ok(ApiResponse.success("Waitlist entry updated", waitlistService.setContacted(id, contacted)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        try {
            waitlistService.delete(id);
            return ResponseEntity.ok(ApiResponse.success("Waitlist entry removed", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
