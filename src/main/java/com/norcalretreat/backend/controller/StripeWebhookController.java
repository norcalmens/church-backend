package com.norcalretreat.backend.controller;

import com.norcalretreat.backend.service.PaymentPlanService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentPlanService paymentPlanService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Received Stripe webhook but stripe.webhook-secret is not configured — ignoring");
            return ResponseEntity.ok("webhook secret not configured");
        }
        if (sigHeader == null) {
            log.warn("Webhook missing Stripe-Signature header");
            return ResponseEntity.badRequest().body("missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.status(400).body("invalid signature");
        } catch (Exception e) {
            log.warn("Failed to parse Stripe webhook: {}", e.getMessage());
            return ResponseEntity.status(400).body("invalid payload");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> obj = deserializer.getObject();
        if (obj.isEmpty()) {
            // Likely an API version mismatch. Best we can do is log; Stripe will not retry for parsing issues.
            log.warn("Stripe event {} ({}) could not be deserialized (API version mismatch?) — ignoring",
                    event.getId(), event.getType());
            return ResponseEntity.ok("event ignored");
        }

        try {
            switch (event.getType()) {
                case "checkout.session.completed": {
                    Session session = (Session) obj.get();
                    if ("subscription".equals(session.getMode())) {
                        paymentPlanService.handleSubscriptionCheckoutCompleted(session);
                    }
                    break;
                }
                case "invoice.payment_succeeded":
                case "invoice.paid": {
                    Invoice invoice = (Invoice) obj.get();
                    paymentPlanService.handleInvoicePaid(invoice);
                    break;
                }
                case "customer.subscription.updated":
                case "customer.subscription.deleted": {
                    Subscription sub = (Subscription) obj.get();
                    paymentPlanService.handleSubscriptionStatusChanged(sub);
                    break;
                }
                default:
                    log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            // Log and swallow — return 200 so Stripe doesn't retry on app bugs we can't fix in the moment.
            log.error("Error handling Stripe event {} ({})", event.getId(), event.getType(), e);
        }
        return ResponseEntity.ok("ok");
    }
}
