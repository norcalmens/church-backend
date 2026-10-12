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
    /** Which retreat year the registration form + counter operates on.
     *  Bump when opening a new season -- old rows stay tagged by their year
     *  so historical data isn't lost. Default: 2027. */
    public static final String KEY_RETREAT_ACTIVE_YEAR = "retreat.active.year";
    public static final String KEY_SOCIAL_ENABLED   = "social.enabled";
    public static final String KEY_SOCIAL_FACEBOOK       = "social.facebook.url";
    public static final String KEY_SOCIAL_INSTAGRAM      = "social.instagram.url";
    public static final String KEY_SOCIAL_YOUTUBE        = "social.youtube.url";
    public static final String KEY_SOCIAL_SHOW_FACEBOOK  = "social.facebook.show";
    public static final String KEY_SOCIAL_SHOW_INSTAGRAM = "social.instagram.show";
    public static final String KEY_SOCIAL_SHOW_YOUTUBE   = "social.youtube.show";
    public static final String KEY_THEME                 = "theme.name";
    /** The retreat's event theme — the tagline/motif displayed under the
     *  retreat name on the home hero and itinerary subtitle. Updated once
     *  per year as the theme changes; every surface reads from this so a
     *  single change propagates site-wide. Default: "Forged By Fire". */
    public static final String KEY_RETREAT_THEME_NAME    = "retreat.theme.name";
    /** Scripture reference paired with the retreat theme (e.g. "1 Peter 1:7"
     *  for Forged By Fire). Shown in parentheses after the theme name. */
    public static final String KEY_RETREAT_THEME_SCRIPTURE = "retreat.theme.scripture";

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
