package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.PaymentPlanDTO;
import com.norcalretreat.backend.dto.PaymentPlanPayResponse;
import com.norcalretreat.backend.dto.PaymentPlanPaymentDTO;
import com.norcalretreat.backend.entity.PaymentPlan;
import com.norcalretreat.backend.entity.PaymentPlanPayment;
import com.norcalretreat.backend.repository.PaymentPlanPaymentRepository;
import com.norcalretreat.backend.repository.PaymentPlanRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPlanService {

    private final PaymentPlanRepository plans;
    private final PaymentPlanPaymentRepository payments;

    private EmailService emailService;

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ===== Admin: plans =====

    public List<PaymentPlanDTO> listAll() {
        return plans.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> toDto(p, payments.findByPlanIdOrderByPaidAtDesc(p.getId())))
                .toList();
    }

    public PaymentPlanDTO get(Long id) {
        PaymentPlan p = plans.findById(id).orElseThrow(() -> new IllegalArgumentException("PaymentPlan not found: " + id));
        return toDto(p, payments.findByPlanIdOrderByPaidAtDesc(id));
    }

    @Transactional
    public PaymentPlanDTO create(PaymentPlanDTO req) {
        PaymentPlan p = new PaymentPlan();
        applyAdminFields(req, p);
        p = plans.save(p);
        log.info("Created PaymentPlan {} ({}) for {} <{}> — total ${}", p.getId(), p.getPlanName(), p.getPayerName(), p.getPayerEmail(), p.getTotalAmount());
        // Email the payer their personal payment link. Non-fatal if mail is unavailable.
        if (emailService != null) {
            try { emailService.sendPaymentPlanInvite(p); }
            catch (Exception e) { log.warn("Could not send payment plan invite for {}: {}", p.getId(), e.getMessage()); }
        }
        return toDto(p, List.of());
    }

    /** Admin-triggered resend of the invite email (e.g., payer lost the link). */
    public void resendInvite(Long planId) {
        PaymentPlan p = plans.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("PaymentPlan not found: " + planId));
        if (emailService == null) {
            throw new IllegalStateException("Email service is not configured in this environment.");
        }
        emailService.sendPaymentPlanInvite(p);
        log.info("Resent payment plan invite for plan {} to {}", p.getId(), p.getPayerEmail());
    }

    @Transactional
    public PaymentPlanDTO update(Long id, PaymentPlanDTO req) {
        PaymentPlan p = plans.findById(id).orElseThrow(() -> new IllegalArgumentException("PaymentPlan not found: " + id));
        applyAdminFields(req, p);
        p = plans.save(p);
        log.info("Updated PaymentPlan {}", p.getId());
        return toDto(p, payments.findByPlanIdOrderByPaidAtDesc(p.getId()));
    }

    @Transactional
    public void delete(Long id) {
        if (!plans.existsById(id)) throw new IllegalArgumentException("PaymentPlan not found: " + id);
        payments.deleteAll(payments.findByPlanIdOrderByPaidAtDesc(id));
        plans.deleteById(id);
        log.info("Deleted PaymentPlan {}", id);
    }

    // ===== Admin: manual payments (cash/check) =====

    @Transactional
    public PaymentPlanPaymentDTO recordManualPayment(Long planId, PaymentPlanPaymentDTO req) {
        PaymentPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("PaymentPlan not found: " + planId));
        validateAmount(req.getAmount());
        PaymentPlanPayment payment = new PaymentPlanPayment();
        payment.setPlanId(plan.getId());
        payment.setAmount(req.getAmount());
        String method = req.getMethod() != null ? req.getMethod().toLowerCase() : "cash";
        if (!List.of("cash", "check", "stripe").contains(method)) method = "cash";
        payment.setMethod(method);
        payment.setStatus(req.getStatus() != null ? req.getStatus() : "paid");
        payment.setReference(req.getReference());
        payment.setStripePaymentId(req.getStripePaymentId());
        payment.setNotes(req.getNotes());
        payment.setPaidAt(req.getPaidAt());
        payment = payments.save(payment);
        maybeMarkCompleted(plan);
        return paymentDto(payment);
    }

    @Transactional
    public PaymentPlanPaymentDTO updateManualPayment(Long paymentId, PaymentPlanPaymentDTO req) {
        PaymentPlanPayment p = payments.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        validateAmount(req.getAmount());
        p.setAmount(req.getAmount());
        if (req.getMethod() != null) p.setMethod(req.getMethod().toLowerCase());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        p.setReference(req.getReference());
        p.setStripePaymentId(req.getStripePaymentId());
        p.setNotes(req.getNotes());
        if (req.getPaidAt() != null) p.setPaidAt(req.getPaidAt());
        p = payments.save(p);
        plans.findById(p.getPlanId()).ifPresent(this::maybeMarkCompleted);
        return paymentDto(p);
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        PaymentPlanPayment p = payments.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        Long planId = p.getPlanId();
        payments.deleteById(paymentId);
        plans.findById(planId).ifPresent(this::maybeMarkCompleted);
    }

    // ===== Public: by token =====

    public PaymentPlanDTO getByToken(String token) {
        PaymentPlan p = plans.findByPayerToken(token).orElseThrow(() -> new IllegalArgumentException("Payment plan not found"));
        return toDto(p, payments.findByPlanIdOrderByPaidAtDesc(p.getId()));
    }

    /**
     * Public Stripe payment for a payment plan token. Creates a pending Payment record + Stripe PaymentIntent.
     */
    @Transactional
    public PaymentPlanPayResponse startStripePayment(String token, BigDecimal amount) throws StripeException {
        validateAmount(amount);
        PaymentPlan plan = plans.findByPayerToken(token).orElseThrow(() -> new IllegalArgumentException("Payment plan not found"));
        if (!"active".equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalStateException("This payment plan is not accepting payments.");
        }

        PaymentPlanPayment payment = new PaymentPlanPayment();
        payment.setPlanId(plan.getId());
        payment.setAmount(amount);
        payment.setMethod("stripe");
        payment.setStatus("pending");
        payment = payments.save(payment);

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("type", "payment_plan");
        metadata.put("plan_id", String.valueOf(plan.getId()));
        metadata.put("payment_id", String.valueOf(payment.getId()));
        metadata.put("payer_name", plan.getPayerName());
        metadata.put("payer_email", plan.getPayerEmail());

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setDescription("Payment Plan: " + plan.getPlanName())
                .setReceiptEmail(plan.getPayerEmail())
                .putAllMetadata(metadata)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        payment.setStripePaymentId(intent.getId());
        payment = payments.save(payment);

        log.info("Started Stripe payment {} on plan {} for ${}", payment.getId(), plan.getId(), amount);
        return new PaymentPlanPayResponse(payment.getId(), intent.getClientSecret());
    }

    // ===== Public: recurring (Stripe Subscriptions via Checkout) =====

    /**
     * Create a Stripe Checkout Session in subscription mode for this plan.
     * Returns the URL to redirect the payer to. Stripe collects/saves the card,
     * creates the Customer + Subscription, and then redirects back to the portal.
     * Webhook events finish the wiring (storing IDs on the plan, recording each payment).
     */
    @Transactional
    public String createSubscriptionCheckout(String token, BigDecimal amount) throws StripeException {
        validateAmount(amount);
        PaymentPlan plan = plans.findByPayerToken(token).orElseThrow(() -> new IllegalArgumentException("Payment plan not found"));
        if (!"active".equalsIgnoreCase(plan.getStatus())) {
            throw new IllegalStateException("This payment plan is not accepting payments.");
        }
        if (plan.getStripeSubscriptionId() != null && "active".equalsIgnoreCase(plan.getRecurringStatus())) {
            throw new IllegalStateException("A monthly schedule is already active for this plan. Cancel it first to set up a new one.");
        }
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontendUrl + "/plan/" + token + "?recurring=success")
                .setCancelUrl(frontendUrl + "/plan/" + token + "?recurring=canceled")
                .setCustomerEmail(plan.getPayerEmail())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amountInCents)
                                .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                        .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                        .build())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Payment Plan: " + plan.getPlanName())
                                        .build())
                                .build())
                        .build())
                .putMetadata("type", "payment_plan_subscription")
                .putMetadata("plan_id", String.valueOf(plan.getId()))
                .putMetadata("payer_token", plan.getPayerToken())
                .build();

        Session session = Session.create(params);
        log.info("Created subscription Checkout Session {} for plan {} (${}/mo)", session.getId(), plan.getId(), amount);
        return session.getUrl();
    }

    /** Admin: cancel the recurring subscription on a plan. */
    @Transactional
    public PaymentPlanDTO cancelRecurring(Long planId) throws StripeException {
        PaymentPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("PaymentPlan not found: " + planId));
        if (plan.getStripeSubscriptionId() == null) {
            throw new IllegalStateException("This plan has no recurring subscription.");
        }
        Subscription sub = Subscription.retrieve(plan.getStripeSubscriptionId());
        sub.cancel(SubscriptionCancelParams.builder().build());
        plan.setRecurringStatus("canceled");
        plans.save(plan);
        log.info("Canceled subscription {} on plan {}", plan.getStripeSubscriptionId(), plan.getId());
        return toDto(plan, payments.findByPlanIdOrderByPaidAtDesc(plan.getId()));
    }

    // ===== Webhook event handlers =====

    /** Called by webhook on checkout.session.completed for subscription mode. */
    @Transactional
    public void handleSubscriptionCheckoutCompleted(Session session) {
        Map<String, String> md = session.getMetadata();
        if (md == null || !"payment_plan_subscription".equals(md.get("type"))) return;
        String planIdStr = md.get("plan_id");
        if (planIdStr == null) return;
        Long planId = Long.parseLong(planIdStr);
        PaymentPlan plan = plans.findById(planId).orElse(null);
        if (plan == null) { log.warn("Subscription checkout for missing plan {}", planId); return; }

        plan.setStripeCustomerId(session.getCustomer());
        plan.setStripeSubscriptionId(session.getSubscription());
        plan.setRecurringStatus("active");
        plan.setRecurringStartedAt(java.time.LocalDateTime.now());
        // Fetch the subscription to get the actual amount on the price
        try {
            Subscription sub = Subscription.retrieve(session.getSubscription());
            if (sub.getItems() != null && sub.getItems().getData() != null && !sub.getItems().getData().isEmpty()) {
                var price = sub.getItems().getData().get(0).getPrice();
                if (price != null && price.getUnitAmount() != null) {
                    plan.setRecurringAmount(BigDecimal.valueOf(price.getUnitAmount()).divide(BigDecimal.valueOf(100)));
                }
            }
        } catch (Exception e) {
            log.warn("Could not load subscription detail for {}: {}", session.getSubscription(), e.getMessage());
        }
        plans.save(plan);
        log.info("Activated recurring subscription {} on plan {} (${}/mo)", plan.getStripeSubscriptionId(), plan.getId(), plan.getRecurringAmount());
    }

    /** Called by webhook on invoice.payment_succeeded. Records the recurring charge as a paid Payment. */
    @Transactional
    public void handleInvoicePaid(Invoice invoice) {
        String subId = invoice.getSubscription();
        if (subId == null) return; // not a subscription invoice
        PaymentPlan plan = plans.findAll().stream()
                .filter(p -> subId.equals(p.getStripeSubscriptionId()))
                .findFirst().orElse(null);
        if (plan == null) { log.warn("Invoice {} for unknown subscription {}", invoice.getId(), subId); return; }

        // Idempotency: skip if we already have a payment for this invoice charge
        String chargeId = invoice.getCharge();
        String paymentIntentId = invoice.getPaymentIntent();
        String ref = chargeId != null ? chargeId : paymentIntentId;
        if (ref != null) {
            boolean exists = payments.findByPlanIdOrderByPaidAtDesc(plan.getId()).stream()
                    .anyMatch(p -> ref.equals(p.getStripePaymentId()));
            if (exists) { log.info("Invoice {} already recorded for plan {}", invoice.getId(), plan.getId()); return; }
        }

        BigDecimal amount = invoice.getAmountPaid() != null
                ? BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        PaymentPlanPayment p = new PaymentPlanPayment();
        p.setPlanId(plan.getId());
        p.setAmount(amount);
        p.setMethod("stripe");
        p.setStatus("paid");
        p.setStripePaymentId(ref);
        p.setReference("Recurring invoice " + invoice.getNumber());
        p.setPaidAt(java.time.LocalDateTime.now());
        payments.save(p);
        log.info("Recorded recurring payment ${} on plan {} (invoice {})", amount, plan.getId(), invoice.getId());

        // Auto-cancel subscription when the plan is fully paid
        maybeMarkCompleted(plan);
        if ("completed".equalsIgnoreCase(plan.getStatus()) && plan.getStripeSubscriptionId() != null
                && "active".equalsIgnoreCase(plan.getRecurringStatus())) {
            try {
                Subscription sub = Subscription.retrieve(plan.getStripeSubscriptionId());
                sub.cancel(SubscriptionCancelParams.builder().build());
                plan.setRecurringStatus("canceled");
                plans.save(plan);
                log.info("Plan {} fully paid — canceled subscription {}", plan.getId(), plan.getStripeSubscriptionId());
            } catch (Exception e) {
                log.warn("Could not auto-cancel subscription on completed plan {}: {}", plan.getId(), e.getMessage());
            }
        }
    }

    /** Called by webhook on customer.subscription.updated / deleted. Keeps our recurring_status in sync. */
    @Transactional
    public void handleSubscriptionStatusChanged(Subscription sub) {
        if (sub == null || sub.getId() == null) return;
        PaymentPlan plan = plans.findAll().stream()
                .filter(p -> sub.getId().equals(p.getStripeSubscriptionId()))
                .findFirst().orElse(null);
        if (plan == null) return;
        plan.setRecurringStatus(sub.getStatus()); // active | past_due | canceled | unpaid | trialing
        plans.save(plan);
        log.info("Synced subscription {} status -> {} on plan {}", sub.getId(), sub.getStatus(), plan.getId());
    }

    @Transactional
    public PaymentPlanPaymentDTO confirmStripePayment(String token, Long paymentId) throws StripeException {
        PaymentPlan plan = plans.findByPayerToken(token).orElseThrow(() -> new IllegalArgumentException("Payment plan not found"));
        PaymentPlanPayment payment = payments.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
        if (!plan.getId().equals(payment.getPlanId())) {
            throw new IllegalArgumentException("Payment does not belong to this plan");
        }
        if (payment.getStripePaymentId() == null) {
            throw new IllegalStateException("Payment has no Stripe intent");
        }
        PaymentIntent intent = PaymentIntent.retrieve(payment.getStripePaymentId());
        String s = intent.getStatus();
        if ("succeeded".equals(s)) payment.setStatus("paid");
        else if ("processing".equals(s)) payment.setStatus("processing");
        else if ("requires_payment_method".equals(s) || "canceled".equals(s)) payment.setStatus("failed");
        else payment.setStatus(s);
        if (payment.getPaidAt() == null && "paid".equals(payment.getStatus())) {
            payment.setPaidAt(java.time.LocalDateTime.now());
        }
        payment = payments.save(payment);
        maybeMarkCompleted(plan);
        return paymentDto(payment);
    }

    // ===== helpers =====

    private void applyAdminFields(PaymentPlanDTO req, PaymentPlan p) {
        require(req.getPlanName(), "Plan name");
        require(req.getRetreatLabel(), "Retreat");
        require(req.getPayerName(), "Payer name");
        require(req.getPayerEmail(), "Payer email");
        validateAmount(req.getTotalAmount());
        p.setPlanName(req.getPlanName().trim());
        p.setRetreatLabel(req.getRetreatLabel().trim());
        p.setPayerName(req.getPayerName().trim());
        p.setPayerEmail(req.getPayerEmail().trim());
        p.setTotalAmount(req.getTotalAmount());
        p.setNotes(req.getNotes());
        if (req.getStatus() != null && !req.getStatus().isBlank()) p.setStatus(req.getStatus());
    }

    private void require(String v, String label) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(label + " is required");
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
    }

    private void maybeMarkCompleted(PaymentPlan plan) {
        BigDecimal paid = sumPaid(plan.getId());
        if (paid.compareTo(plan.getTotalAmount()) >= 0 && "active".equalsIgnoreCase(plan.getStatus())) {
            plan.setStatus("completed");
            plans.save(plan);
        } else if (paid.compareTo(plan.getTotalAmount()) < 0 && "completed".equalsIgnoreCase(plan.getStatus())) {
            // A payment was deleted/edited downward — reopen
            plan.setStatus("active");
            plans.save(plan);
        }
    }

    private BigDecimal sumPaid(Long planId) {
        return payments.findByPlanIdOrderByPaidAtDesc(planId).stream()
                .filter(p -> "paid".equalsIgnoreCase(p.getStatus()))
                .map(PaymentPlanPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PaymentPlanDTO toDto(PaymentPlan p, List<PaymentPlanPayment> pays) {
        PaymentPlanDTO dto = new PaymentPlanDTO();
        dto.setId(p.getId());
        dto.setPlanName(p.getPlanName());
        dto.setRetreatLabel(p.getRetreatLabel());
        dto.setPayerName(p.getPayerName());
        dto.setPayerEmail(p.getPayerEmail());
        dto.setTotalAmount(p.getTotalAmount());
        dto.setPayerToken(p.getPayerToken());
        dto.setStatus(p.getStatus());
        dto.setNotes(p.getNotes());
        dto.setStripeCustomerId(p.getStripeCustomerId());
        dto.setStripeSubscriptionId(p.getStripeSubscriptionId());
        dto.setRecurringAmount(p.getRecurringAmount());
        dto.setRecurringStatus(p.getRecurringStatus());
        dto.setRecurringStartedAt(p.getRecurringStartedAt());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        BigDecimal paid = pays.stream()
                .filter(pp -> "paid".equalsIgnoreCase(pp.getStatus()))
                .map(PaymentPlanPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setPaidAmount(paid);
        dto.setBalance(p.getTotalAmount().subtract(paid));
        dto.setPayments(pays.stream().map(this::paymentDto).toList());
        return dto;
    }

    private PaymentPlanPaymentDTO paymentDto(PaymentPlanPayment p) {
        PaymentPlanPaymentDTO d = new PaymentPlanPaymentDTO();
        d.setId(p.getId());
        d.setPlanId(p.getPlanId());
        d.setAmount(p.getAmount());
        d.setMethod(p.getMethod());
        d.setStatus(p.getStatus());
        d.setStripePaymentId(p.getStripePaymentId());
        d.setReference(p.getReference());
        d.setNotes(p.getNotes());
        d.setPaidAt(p.getPaidAt());
        d.setCreatedAt(p.getCreatedAt());
        return d;
    }
}
