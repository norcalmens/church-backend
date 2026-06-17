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

    // Public read of social-media URLs so the footer / topbar icons resolve
    // to the live values without admin auth. Empty string for any key the
    // admin hasn't filled in yet -- the frontend hides icons with no URL.
    @GetMapping("/public/social")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSocial() {
        Map<String, String> body = new HashMap<>();
        body.put("facebook",  settingService.get(SystemSettingService.KEY_SOCIAL_FACEBOOK).orElse(""));
        body.put("instagram", settingService.get(SystemSettingService.KEY_SOCIAL_INSTAGRAM).orElse(""));
        body.put("youtube",   settingService.get(SystemSettingService.KEY_SOCIAL_YOUTUBE).orElse(""));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/social")
    public ResponseEntity<ApiResponse<Map<String, String>>> setSocial(@RequestBody Map<String, String> body) {
        Map<String, String> out = new HashMap<>();
        for (String[] pair : new String[][] {
                {"facebook",  SystemSettingService.KEY_SOCIAL_FACEBOOK},
                {"instagram", SystemSettingService.KEY_SOCIAL_INSTAGRAM},
                {"youtube",   SystemSettingService.KEY_SOCIAL_YOUTUBE}}) {
            String field = pair[0], key = pair[1];
            if (body.containsKey(field)) {
                String value = body.get(field) == null ? "" : body.get(field).trim();
                // Basic sanity: only accept http/https URLs or empty (clear).
                if (!value.isEmpty() && !value.matches("(?i)^https?://.+")) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(
                            field + " must be a http:// or https:// URL (or empty to clear)"));
                }
                settingService.set(key, value);
                out.put(field, value);
            } else {
                out.put(field, settingService.get(key).orElse(""));
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Social links updated", out));
    }
}
