package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.ApiResponse;
import com.norcalretreat.backend.dto.PaymentResponse;
import com.norcalretreat.backend.dto.RegistrationDTO;
import com.norcalretreat.backend.entity.User;
import com.norcalretreat.backend.repository.UserRepository;
import com.norcalretreat.backend.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            Long userId = getCurrentUserIdOrNull();
            RegistrationDTO created = registrationService.createRegistration(dto, userId);
            return ResponseEntity.ok(ApiResponse.success("Registration created successfully", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/payment-intent")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPaymentIntent(@PathVariable Long id) {
        try {
            PaymentResponse response = registrationService.createPaymentIntent(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Payment processing error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ApiResponse<RegistrationDTO>> confirmPayment(@PathVariable Long id) {
        try {
            RegistrationDTO updated = registrationService.confirmPayment(id);
            return ResponseEntity.ok(ApiResponse.success("Payment confirmed", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("Payment verification error: " + e.getMessage()));
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

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<ApiResponse<Void>> adminDeleteRegistration(@PathVariable Long id) {
        try {
            registrationService.adminDeleteRegistration(id);
            return ResponseEntity.ok(ApiResponse.success("Registration deleted", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/admin/{id}/speaker")
    public ResponseEntity<ApiResponse<RegistrationDTO>> setSpeakerFlag(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            boolean speaker = Boolean.TRUE.equals(body.get("speaker"));
            RegistrationDTO updated = registrationService.setSpeakerFlag(id, speaker);
            return ResponseEntity.ok(ApiResponse.success("Speaker flag updated", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/admin/attendees")
    public ResponseEntity<ApiResponse<List<com.norcalretreat.backend.dto.AttendeeDTO>>> getAllAttendees() {
        return ResponseEntity.ok(ApiResponse.success(registrationService.getAllAttendees()));
    }

    @PatchMapping("/admin/attendees/{attendeeId}/speaker")
    public ResponseEntity<ApiResponse<com.norcalretreat.backend.dto.AttendeeDTO>> setAttendeeSpeakerFlag(
            @PathVariable Long attendeeId, @RequestBody Map<String, Boolean> body) {
        try {
            boolean speaker = Boolean.TRUE.equals(body.get("speaker"));
            return ResponseEntity.ok(ApiResponse.success("Speaker flag updated",
                    registrationService.setAttendeeSpeakerFlag(attendeeId, speaker)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = registrationService.getStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAvailability() {
        return ResponseEntity.ok(ApiResponse.success(registrationService.getAvailability()));
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }

    private Long getCurrentUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            String username = auth.getName();
            return userRepository.findByUsername(username).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
