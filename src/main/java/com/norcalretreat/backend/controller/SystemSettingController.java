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

    // Public read of social-media settings so the footer / topbar icons
    // resolve to the live values without admin auth. The `enabled` flag is
    // a master switch -- when false the frontend hides every icon even if
    // the URLs are filled in (lets an admin pre-load URLs and reveal the
    // icons at launch time).
    @GetMapping("/public/social")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSocial() {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled",   settingService.get(SystemSettingService.KEY_SOCIAL_ENABLED)
                                            .map(v -> "true".equalsIgnoreCase(v.trim()))
                                            .orElse(false));   // default: hidden until admin enables
        body.put("facebook",  settingService.get(SystemSettingService.KEY_SOCIAL_FACEBOOK).orElse(""));
        body.put("instagram", settingService.get(SystemSettingService.KEY_SOCIAL_INSTAGRAM).orElse(""));
        body.put("youtube",   settingService.get(SystemSettingService.KEY_SOCIAL_YOUTUBE).orElse(""));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/social")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setSocial(@RequestBody Map<String, Object> body) {
        // URL fields
        for (String[] pair : new String[][] {
                {"facebook",  SystemSettingService.KEY_SOCIAL_FACEBOOK},
                {"instagram", SystemSettingService.KEY_SOCIAL_INSTAGRAM},
                {"youtube",   SystemSettingService.KEY_SOCIAL_YOUTUBE}}) {
            String field = pair[0], key = pair[1];
            if (body.containsKey(field)) {
                Object raw = body.get(field);
                String value = raw == null ? "" : raw.toString().trim();
                // Basic sanity: only accept http/https URLs or empty (clear).
                if (!value.isEmpty() && !value.matches("(?i)^https?://.+")) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(
                            field + " must be a http:// or https:// URL (or empty to clear)"));
                }
                settingService.set(key, value);
            }
        }
        // Master enabled flag
        if (body.containsKey("enabled")) {
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                    || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
            settingService.set(SystemSettingService.KEY_SOCIAL_ENABLED, enabled ? "true" : "false");
        }
        // Return the current state so the client can sync without a second fetch.
        Map<String, Object> out = new HashMap<>();
        out.put("enabled",   settingService.get(SystemSettingService.KEY_SOCIAL_ENABLED)
                                            .map(v -> "true".equalsIgnoreCase(v.trim()))
                                            .orElse(false));
        out.put("facebook",  settingService.get(SystemSettingService.KEY_SOCIAL_FACEBOOK).orElse(""));
        out.put("instagram", settingService.get(SystemSettingService.KEY_SOCIAL_INSTAGRAM).orElse(""));
        out.put("youtube",   settingService.get(SystemSettingService.KEY_SOCIAL_YOUTUBE).orElse(""));
        return ResponseEntity.ok(ApiResponse.success("Social settings updated", out));
    }
}
