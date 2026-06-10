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
