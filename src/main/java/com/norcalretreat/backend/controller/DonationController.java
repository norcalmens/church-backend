package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.DonationCreateResponse;
import com.norcalretreat.backend.dto.DonationDTO;
import com.norcalretreat.backend.service.DonationService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
public class DonationController {

    private final DonationService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody DonationDTO req) {
        try {
            return ResponseEntity.ok(service.create(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error creating donation: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.confirm(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error confirming donation {}: {}", id, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<DonationDTO>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> createManual(@RequestBody DonationDTO req) {
        try {
            return ResponseEntity.ok(service.createManual(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody DonationDTO req) {
        try {
            return ResponseEntity.ok(service.update(id, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
