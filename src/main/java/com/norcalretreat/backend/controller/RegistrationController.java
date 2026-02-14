package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.ApiResponse;
import com.norcalretreat.backend.dto.RegistrationDTO;
import com.norcalretreat.backend.entity.User;
import com.norcalretreat.backend.repository.UserRepository;
import com.norcalretreat.backend.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RegistrationDTO>>> getMyRegistrations() {
        Long userId = getCurrentUserId();
        List<RegistrationDTO> registrations = registrationService.getUserRegistrations(userId);
        return ResponseEntity.ok(ApiResponse.success(registrations));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationDTO>> createRegistration(@RequestBody RegistrationDTO dto) {
        try {
            Long userId = getCurrentUserId();
            RegistrationDTO created = registrationService.createRegistration(dto, userId);
            return ResponseEntity.ok(ApiResponse.success("Registration created successfully", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RegistrationDTO>> getRegistration(@PathVariable Long id) {
        try {
            RegistrationDTO reg = registrationService.getRegistration(id);
            return ResponseEntity.ok(ApiResponse.success(reg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RegistrationDTO>> updateRegistration(
            @PathVariable Long id, @RequestBody RegistrationDTO dto) {
        try {
            Long userId = getCurrentUserId();
            RegistrationDTO updated = registrationService.updateRegistration(id, dto, userId);
            return ResponseEntity.ok(ApiResponse.success("Registration updated successfully", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRegistration(@PathVariable Long id) {
        try {
            Long userId = getCurrentUserId();
            registrationService.deleteRegistration(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Registration cancelled", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // Admin endpoints
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<RegistrationDTO>>> getAllRegistrations() {
        List<RegistrationDTO> registrations = registrationService.getAllRegistrations();
        return ResponseEntity.ok(ApiResponse.success(registrations));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = registrationService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }
}
