package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.ApiResponse;
import com.norcalretreat.backend.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SystemSettingService settingService;

    // Public — exposes only safe-to-read keys so the registration page
    // can show capacity without admin auth.
    @GetMapping("/public/retreat-capacity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCapacity() {
        int capacity = settingService.getInt(SystemSettingService.KEY_RETREAT_CAPACITY, 35);
        Map<String, Object> body = new HashMap<>();
        body.put("capacity", capacity);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/retreat-capacity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setCapacity(@RequestBody Map<String, Object> body) {
        Object raw = body.get("capacity");
        if (raw == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("capacity is required"));
        }
        int capacity;
        try {
            capacity = Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("capacity must be a positive integer"));
        }
        if (capacity < 1) {
            return ResponseEntity.badRequest().body(ApiResponse.error("capacity must be at least 1"));
        }
        settingService.set(SystemSettingService.KEY_RETREAT_CAPACITY, String.valueOf(capacity));
        Map<String, Object> out = new HashMap<>();
        out.put("capacity", capacity);
        return ResponseEntity.ok(ApiResponse.success("Capacity updated", out));
    }
}
