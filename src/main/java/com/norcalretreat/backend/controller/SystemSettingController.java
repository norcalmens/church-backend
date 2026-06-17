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
    // resolve to the live values without admin auth.
    //   enabled         -- master switch (default off). When false the
    //                      frontend hides every icon regardless of per-icon flags.
    //   show<Platform>  -- per-icon switches (default true). Let an admin
    //                      hide one specific icon while leaving the URL saved.
    // An icon is rendered iff (enabled AND showPlatform AND URL is non-empty).
    @GetMapping("/public/social")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSocial() {
        return ResponseEntity.ok(ApiResponse.success(readSocialState()));
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
        // Master + per-icon boolean flags
        for (String[] pair : new String[][] {
                {"enabled",       SystemSettingService.KEY_SOCIAL_ENABLED},
                {"showFacebook",  SystemSettingService.KEY_SOCIAL_SHOW_FACEBOOK},
                {"showInstagram", SystemSettingService.KEY_SOCIAL_SHOW_INSTAGRAM},
                {"showYoutube",   SystemSettingService.KEY_SOCIAL_SHOW_YOUTUBE}}) {
            String field = pair[0], key = pair[1];
            if (body.containsKey(field)) {
                boolean value = Boolean.TRUE.equals(body.get(field))
                        || "true".equalsIgnoreCase(String.valueOf(body.get(field)));
                settingService.set(key, value ? "true" : "false");
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Social settings updated", readSocialState()));
    }

    /** Snapshot every social-related setting at once. Defaults to enabled=false
     *  + showX=true so a never-configured install hides everything via the
     *  master switch but pre-grants per-icon visibility once unlocked. */
    private Map<String, Object> readSocialState() {
        Map<String, Object> out = new HashMap<>();
        out.put("enabled",       readBool(SystemSettingService.KEY_SOCIAL_ENABLED,       false));
        out.put("showFacebook",  readBool(SystemSettingService.KEY_SOCIAL_SHOW_FACEBOOK,  true));
        out.put("showInstagram", readBool(SystemSettingService.KEY_SOCIAL_SHOW_INSTAGRAM, true));
        out.put("showYoutube",   readBool(SystemSettingService.KEY_SOCIAL_SHOW_YOUTUBE,   true));
        out.put("facebook",      settingService.get(SystemSettingService.KEY_SOCIAL_FACEBOOK).orElse(""));
        out.put("instagram",     settingService.get(SystemSettingService.KEY_SOCIAL_INSTAGRAM).orElse(""));
        out.put("youtube",       settingService.get(SystemSettingService.KEY_SOCIAL_YOUTUBE).orElse(""));
        return out;
    }

    private boolean readBool(String key, boolean defaultValue) {
        return settingService.get(key)
                .map(v -> "true".equalsIgnoreCase(v.trim()))
                .orElse(defaultValue);
    }

    // === Theme ============================================================
    // Allowlist matches the IDs defined in src/styles.scss. Anything not in
    // this set is rejected, so a typo or hand-crafted PUT can't break the
    // running UI (the frontend would just not match a .theme-<id> class).
    private static final java.util.Set<String> VALID_THEMES = java.util.Set.of(
            "sunrise", "forest", "pacific", "vintage", "slate", "sage");
    private static final String DEFAULT_THEME = "sunrise";

    @GetMapping("/public/theme")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTheme() {
        Map<String, Object> body = new HashMap<>();
        body.put("theme", settingService.get(SystemSettingService.KEY_THEME).orElse(DEFAULT_THEME));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/theme")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setTheme(@RequestBody Map<String, Object> body) {
        Object raw = body.get("theme");
        if (raw == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("theme is required"));
        }
        String id = raw.toString().trim().toLowerCase();
        if (!VALID_THEMES.contains(id)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Unknown theme '" + id + "'. Valid options: " + VALID_THEMES));
        }
        settingService.set(SystemSettingService.KEY_THEME, id);
        Map<String, Object> out = new HashMap<>();
        out.put("theme", id);
        return ResponseEntity.ok(ApiResponse.success("Theme updated", out));
    }
}
