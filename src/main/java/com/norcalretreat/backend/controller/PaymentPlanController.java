package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.dto.PaymentPlanDTO;
import com.norcalretreat.backend.dto.PaymentPlanPayResponse;
import com.norcalretreat.backend.dto.PaymentPlanPaymentDTO;
import com.norcalretreat.backend.service.PaymentPlanService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment-plans")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService service;

    // ===== Admin =====

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<PaymentPlanDTO>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try { return ResponseEntity.ok(service.get(id)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> create(@RequestBody PaymentPlanDTO req) {
        try { return ResponseEntity.ok(service.create(req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PaymentPlanDTO req) {
        try { return ResponseEntity.ok(service.update(id, req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try { service.delete(id); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> resendInvite(@PathVariable Long id) {
        try { service.resendInvite(id); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
        catch (IllegalStateException e)   { return ResponseEntity.status(503).body(Map.of("message", e.getMessage())); }
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> recordPayment(@PathVariable Long id, @RequestBody PaymentPlanPaymentDTO req) {
        try { return ResponseEntity.ok(service.recordManualPayment(id, req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @PutMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> updatePayment(@PathVariable Long paymentId, @RequestBody PaymentPlanPaymentDTO req) {
        try { return ResponseEntity.ok(service.updateManualPayment(paymentId, req)); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    @DeleteMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> deletePayment(@PathVariable Long paymentId) {
        try { service.deletePayment(paymentId); return ResponseEntity.noContent().build(); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    }

    // ===== Public, by tokenized URL =====

    @GetMapping("/by-token/{token}")
    public ResponseEntity<?> getByToken(@PathVariable String token) {
        try { return ResponseEntity.ok(service.getByToken(token)); }
        catch (IllegalArgumentException e) { return ResponseEntity.status(404).body(Map.of("message", e.getMessage())); }
    }

    public static class PayRequest { public BigDecimal amount; }

    @PostMapping("/by-token/{token}/pay")
    public ResponseEntity<?> startPay(@PathVariable String token, @RequestBody PayRequest req) {
        try {
            PaymentPlanPayResponse resp = service.startStripePayment(token, req != null ? req.amount : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error on payment plan pay: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }

    @PostMapping("/by-token/{token}/recurring/checkout")
    public ResponseEntity<?> startRecurringCheckout(@PathVariable String token, @RequestBody PayRequest req) {
        try {
            String url = service.createSubscriptionCheckout(token, req != null ? req.amount : null);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error creating subscription checkout: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/recurring/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> cancelRecurring(@PathVariable Long id) {
        try { return ResponseEntity.ok(service.cancelRecurring(id)); }
        catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error canceling subscription: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }

    @PostMapping("/by-token/{token}/payments/{paymentId}/confirm")
    public ResponseEntity<?> confirmPay(@PathVariable String token, @PathVariable Long paymentId) {
        try { return ResponseEntity.ok(service.confirmStripePayment(token, paymentId)); }
        catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe error confirming plan payment: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("message", "Payment processor error: " + e.getMessage()));
        }
    }
}
