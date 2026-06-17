package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.SystemSetting;
import com.norcalretreat.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    public static final String KEY_RETREAT_CAPACITY = "retreat.capacity";
    public static final String KEY_SOCIAL_ENABLED   = "social.enabled";
    public static final String KEY_SOCIAL_FACEBOOK       = "social.facebook.url";
    public static final String KEY_SOCIAL_INSTAGRAM      = "social.instagram.url";
    public static final String KEY_SOCIAL_YOUTUBE        = "social.youtube.url";
    public static final String KEY_SOCIAL_SHOW_FACEBOOK  = "social.facebook.show";
    public static final String KEY_SOCIAL_SHOW_INSTAGRAM = "social.instagram.show";
    public static final String KEY_SOCIAL_SHOW_YOUTUBE   = "social.youtube.show";
    public static final String KEY_THEME                 = "theme.name";

    private final SystemSettingRepository repo;

    public Optional<String> get(String key) {
        return repo.findById(key).map(SystemSetting::getSettingValue);
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(v -> {
            try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }

    @Transactional
    public SystemSetting set(String key, String value) {
        SystemSetting s = repo.findById(key).orElseGet(SystemSetting::new);
        s.setSettingKey(key);
        s.setSettingValue(value);
        return repo.save(s);
    }
}
