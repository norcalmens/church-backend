package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.service.MenuConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/menu-config")
@RequiredArgsConstructor
public class MenuConfigController {

    private final MenuConfigService menuConfigService;

    @GetMapping("/hidden")
    public ResponseEntity<List<String>> getHiddenKeys() {
        return ResponseEntity.ok(new ArrayList<>(menuConfigService.getHiddenKeys()));
    }

    @PutMapping("/hidden")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERUSER')")
    public ResponseEntity<List<String>> setHiddenKeys(@RequestBody List<String> keys) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Admin '{}' updating hidden menu keys: {} items", username, keys.size());
        menuConfigService.setHiddenKeys(new HashSet<>(keys), username);
        return ResponseEntity.ok(new ArrayList<>(menuConfigService.getHiddenKeys()));
    }
}
