package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.Attendee;
import com.norcalretreat.backend.entity.PaymentPlan;
import com.norcalretreat.backend.entity.RetreatRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Always registered so callers don't need to check for a missing bean —
 *  {@link #isReady()} tells them whether outbound mail is configured, and
 *  every send method throws IllegalStateException when it isn't. Uses
 *  ObjectProvider for the JavaMailSender so classpath/auto-config ordering
 *  can't skip this bean the way {@code @ConditionalOnBean} used to. */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender; // null when Spring Boot didn't autoconfigure one

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    /** True when JavaMailSender was autoconfigured (spring.mail.host set + starter present). */
    public boolean isReady() {
        return mailSender != null;
    }

    @Value("${mail.from:noreply@norcalmensretreat.com}")
    private String fromEmail;

    @Value("${retreat.contact-email:}")
    private String contactEmail;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // Optional -- when present, every send is logged to sent_emails so
    // admin can review attempts (successes and failures) in /admin/emails.
    private EmailLogService emailLog;

    @Autowired(required = false)
    public void setEmailLog(EmailLogService emailLog) {
        this.emailLog = emailLog;
    }

    /** Send a message through the log wrapper so every attempt persists.
     *  Re-throws SMTP failures so callers that care (admin-triggered
     *  resends) can surface the error to the UI; fire-and-forget callers
     *  wrap this in try/catch (they already do). */
    private void sendAndLog(String category, SimpleMailMessage message,
                            String relatedType, Long relatedId) {
        if (mailSender == null) {
            throw new IllegalStateException("Email service is not configured — JavaMailSender bean was not created at startup. " +
                    "Check MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD env vars on Railway, then see boot log 'MAIL DIAGNOSTIC:'.");
        }
        String to = (message.getTo() != null && message.getTo().length > 0)
                ? message.getTo()[0] : "";
        String subj = message.getSubject();
        String body = message.getText();
        if (emailLog != null) {
            emailLog.wrap(category, to, subj, body, relatedType, relatedId, null,
                    v -> mailSender.send(message));
        } else {
            mailSender.send(message);
        }
    }

    public void sendPasswordResetEmail(String toEmail, String rawToken, String firstName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Password Reset Request - NorCal Men's Retreat");

        String resetUrl = frontendUrl + "/reset-password?token=" + rawToken;
        String greeting = (firstName != null && !firstName.isEmpty())
                ? "Hi " + firstName + ","
                : "Hi,";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("We received a request to reset your password for your NorCal Men's Retreat account.\n\n");
        body.append("Click the link below to reset your password:\n");
        body.append(resetUrl).append("\n\n");
        body.append("This link will expire in 1 hour.\n\n");
        body.append("If you did not request a password reset, you can safely ignore this email. ");
        body.append("Your password will not be changed.\n\n");
        body.append("— NorCal Men's Retreat 2027");

        message.setText(body.toString());

        try {
            sendAndLog("password_reset", message, null, null);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    public void sendWelcomeEmail(String toEmail, String firstName, String defaultPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Welcome to NorCal Men's Retreat 2027");

        String greeting = (firstName != null && !firstName.isEmpty())
                ? "Hi " + firstName + ","
                : "Hi,";

        String registerUrl = frontendUrl + "/register";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("An account has been created for you for the NorCal Men's Retreat 2027.\n\n");
        body.append("Your temporary password is: ").append(defaultPassword).append("\n\n");
        body.append("Please visit the link below to complete your registration and set a new password:\n");
        body.append(registerUrl).append("\n\n");
        body.append("If you did not expect this email, you can safely ignore it.\n\n");
        body.append("— NorCal Men's Retreat 2027");

        message.setText(body.toString());

        try {
            sendAndLog("welcome", message, "user", null);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", toEmail, e);
        }
    }

    public void sendAccountActivatedEmail(String toEmail, String firstName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Account Activated - NorCal Men's Retreat 2027");

        String greeting = (firstName != null && !firstName.isEmpty())
                ? "Hi " + firstName + ","
                : "Hi,";

        String loginUrl = frontendUrl + "/login";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("Your NorCal Men's Retreat account has been activated successfully!\n\n");
        body.append("You can now log in at:\n");
        body.append(loginUrl).append("\n\n");
        body.append("— NorCal Men's Retreat 2027");

        message.setText(body.toString());

        try {
            sendAndLog("account_activated", message, "user", null);
            log.info("Account activated email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account activated email to {}", toEmail, e);
        }
    }

    public void sendRegistrationConfirmation(RetreatRegistration reg) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(reg.getEmail());
        message.setSubject("Registration Confirmation - NorCal Men's Retreat 2027");

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(reg.getFirstName()).append(",\n\n");
        body.append("Thank you for registering for the NorCal Men's Retreat 2027!\n\n");
        body.append("Registration Details:\n");
        body.append("  Name: ").append(reg.getFirstName()).append(" ").append(reg.getLastName()).append("\n");
        body.append("  Attendees: ").append(reg.getAttendees().size()).append("\n");
        body.append("  Total Amount: $").append(reg.getTotalAmount()).append("\n");
        body.append("  Payment Status: ").append(reg.getPaymentStatus()).append("\n\n");
        appendAttendeeBreakdown(body, reg);
        body.append("Event Details:\n");
        body.append("  Dates: April 15-17, 2027\n");
        body.append("  Venue: Alliance Redwoods, 5000 Bohemian Highway, Occidental, CA 95465\n\n");
        body.append("If you have any questions, please contact us.\n\n");
        body.append("— NorCal Men's Retreat 2027");

        message.setText(body.toString());

        try {
            sendAndLog("registration_confirmation", message, "registration", reg.getId());
            log.info("Registration confirmation email sent to {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send registration confirmation to {}", reg.getEmail(), e);
        }
    }

    public void sendPaymentReceipt(RetreatRegistration reg, String paymentId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(reg.getEmail());
        message.setSubject("Payment Receipt - NorCal Men's Retreat 2027");

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(reg.getFirstName()).append(",\n\n");
        body.append("We have received your payment for the NorCal Men's Retreat 2027.\n\n");
        body.append("Payment Details:\n");
        body.append("  Amount: $").append(reg.getTotalAmount()).append("\n");
        body.append("  Payment ID: ").append(paymentId).append("\n");
        body.append("  Status: Paid\n\n");
        appendAttendeeBreakdown(body, reg);
        body.append("We look forward to seeing you April 15-17, 2027!\n\n");
        body.append("— NorCal Men's Retreat 2027");

        message.setText(body.toString());

        try {
            sendAndLog("payment_receipt", message, "registration", reg.getId());
            log.info("Payment receipt sent to {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send payment receipt to {}", reg.getEmail(), e);
        }
    }

    /**
     * Sends the payment-plan invite (with the token pay link) to the payer.
     *
     * Throws on any failure so admin-triggered resends surface a real error
     * in the UI instead of a phantom "success" toast when SMTP is broken /
     * unconfigured / rate-limited. Auto-triggered sends (on create/approve)
     * still get non-fatal treatment via caller-side try/catch.
     */
    public void sendPaymentPlanInvite(PaymentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Plan is required");
        }
        if (plan.getPayerEmail() == null || plan.getPayerEmail().isBlank()) {
            throw new IllegalStateException("Plan has no payer email address");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(plan.getPayerEmail());
        message.setSubject("Your payment plan for " + plan.getRetreatLabel());

        String payUrl = frontendUrl + "/plan/" + plan.getPayerToken();
        String greeting = (plan.getPayerName() != null && !plan.getPayerName().isBlank())
                ? "Hi " + plan.getPayerName().split(" ")[0] + ","
                : "Hi,";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("A payment plan has been set up for you for the ").append(plan.getRetreatLabel()).append(".\n\n");
        body.append("Plan: ").append(plan.getPlanName()).append("\n");
        body.append("Total: $").append(plan.getTotalAmount()).append("\n\n");
        body.append("You can pay any amount at your own pace using your secure payment link:\n");
        body.append(payUrl).append("\n\n");
        body.append("The link will always show your current balance and payment history. ");
        body.append("Save it for future payments — there's no login required.\n\n");
        if (plan.getNotes() != null && !plan.getNotes().isBlank()) {
            body.append("A note from us:\n").append(plan.getNotes()).append("\n\n");
        }
        body.append("If you have any questions, just reply to this email.\n\n");
        body.append("— NorCal Men's Retreat");

        message.setText(body.toString());

        try {
            sendAndLog("payment_plan_invite", message, "payment_plan", plan.getId());
            log.info("Payment plan invite sent to {} for plan {}", plan.getPayerEmail(), plan.getId());
        } catch (Exception e) {
            log.error("Failed to send payment plan invite to {}", plan.getPayerEmail(), e);
            // Re-throw so admin-triggered resends surface the failure to
            // the UI. Auto-sends (on create/approve) wrap this in their
            // own try/catch to keep the underlying write non-fatal.
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    /** Fires when someone submits the public "Request a Payment Plan" form.
     *  Goes to the retreat contact address so an admin sees it and can
     *  approve the plan (which auto-sends the payer their invite link).
     *  No-op when retreat.contact-email is unset -- request is still saved
     *  and visible in the admin plans table; only the ping is skipped. */
    public void sendPaymentPlanRequestAdminNotification(PaymentPlan plan) {
        if (contactEmail == null || contactEmail.isBlank()) return;
        if (plan == null) return;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(contactEmail);
        // ReplyTo the payer so the admin can just hit Reply to talk with them
        // without hunting down the email address.
        if (plan.getPayerEmail() != null && !plan.getPayerEmail().isBlank()) {
            message.setReplyTo(plan.getPayerEmail());
        }
        message.setSubject("New Payment Plan Request: " + plan.getPayerName()
                + " ($" + plan.getTotalAmount() + ")");

        StringBuilder body = new StringBuilder();
        body.append("Someone requested a payment plan through the website.\n\n");
        body.append("Payer: ").append(plan.getPayerName()).append("\n");
        body.append("Email: ").append(plan.getPayerEmail()).append("\n");
        body.append("Retreat: ").append(plan.getRetreatLabel()).append("\n");
        body.append("Total: $").append(plan.getTotalAmount()).append("\n\n");
        if (plan.getNotes() != null && !plan.getNotes().isBlank()) {
            body.append(plan.getNotes()).append("\n");
        }
        body.append("Review + approve in the admin dashboard:\n");
        body.append(frontendUrl).append("/admin/payment-plans\n\n");
        body.append("Approving flips the plan to active and emails the payer their secure pay link.");

        message.setText(body.toString());

        try {
            sendAndLog("payment_plan_request_notification", message, "payment_plan", plan.getId());
            log.info("Payment plan request notification sent to {} for plan {}", contactEmail, plan.getId());
        } catch (Exception e) {
            log.error("Failed to send payment plan request notification", e);
        }
    }

    public void sendAdminNotification(RetreatRegistration reg) {
        if (contactEmail == null || contactEmail.isBlank()) return;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(contactEmail);
        message.setSubject("New Registration: " + reg.getFirstName() + " " + reg.getLastName());

        StringBuilder body = new StringBuilder();
        body.append("New Retreat Registration Received\n\n");
        body.append("Name: ").append(reg.getFirstName()).append(" ").append(reg.getLastName()).append("\n");
        body.append("Email: ").append(reg.getEmail()).append("\n");
        body.append("Attendees: ").append(reg.getAttendees().size()).append("\n");
        body.append("Total: $").append(reg.getTotalAmount()).append("\n");
        body.append("Payment Status: ").append(reg.getPaymentStatus()).append("\n\n");
        appendAttendeeBreakdown(body, reg);

        message.setText(body.toString());

        try {
            sendAndLog("admin_notification", message, "registration", reg.getId());
            log.info("Admin notification sent for registration by {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send admin notification", e);
        }
    }

    // ----- Attendee breakdown formatting -----

    private static final Map<String, String> DAY_LABELS = Map.of(
            "thu", "Thu Jun 11",
            "fri", "Fri Jun 12",
            "sat", "Sat Jun 13"
    );

    private void appendAttendeeBreakdown(StringBuilder body, RetreatRegistration reg) {
        if (reg.getAttendees() == null || reg.getAttendees().isEmpty()) return;
        body.append("Attendee Breakdown:\n");
        for (Attendee a : reg.getAttendees()) {
            body.append("  - ").append(a.getFirstName()).append(' ').append(a.getLastName()).append('\n');
            for (String line : breakdownLines(a)) {
                body.append("      ").append(line).append('\n');
            }
            if (a.getAmountPaid() != null) {
                body.append("      Subtotal: $").append(a.getAmountPaid()).append('\n');
            }
        }
        body.append('\n');
    }

    private List<String> breakdownLines(Attendee a) {
        List<String> lines = new ArrayList<>();
        if ("partial".equalsIgnoreCase(a.getAttendanceType())) {
            String dayList = parseDays(a.getDays()).stream()
                    .map(d -> DAY_LABELS.getOrDefault(d, d))
                    .collect(Collectors.joining(", "));
            lines.add("Single day (" + dayList + ")");
            if ("half".equalsIgnoreCase(a.getMealOption())) {
                lines.add("Single-day meals: half day, 2 meals — $50");
            } else if ("full".equalsIgnoreCase(a.getMealOption())) {
                lines.add("Single-day meals: full day, 3 meals — $65");
            }
        } else {
            lines.add("Full retreat (3 days, lodging incl.) — $280");
            if ("package".equalsIgnoreCase(a.getLinenOption())) {
                lines.add("Linen & towel package — $25");
            } else if ("individual".equalsIgnoreCase(a.getLinenOption())) {
                int count = a.getLinenItemCount() != null ? a.getLinenItemCount() : 0;
                BigDecimal cost = BigDecimal.valueOf(5L * count);
                lines.add("Linens: " + count + " item" + (count == 1 ? "" : "s") + " @ $5 — $" + cost);
            }
        }
        return lines;
    }

    private List<String> parseDays(String days) {
        if (days == null || days.isBlank()) return List.of();
        return Arrays.stream(days.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }
}
