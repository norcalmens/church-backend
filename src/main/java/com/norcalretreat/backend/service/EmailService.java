package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.RetreatRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnBean(JavaMailSender.class)
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from:noreply@norcalmensretreat.com}")
    private String fromEmail;

    @Value("${retreat.contact-email:}")
    private String contactEmail;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

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
        body.append("— NorCal Men's Retreat 2026");

        message.setText(body.toString());

        try {
            mailSender.send(message);
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
        message.setSubject("Welcome to NorCal Men's Retreat 2026");

        String greeting = (firstName != null && !firstName.isEmpty())
                ? "Hi " + firstName + ","
                : "Hi,";

        String registerUrl = frontendUrl + "/register";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("An account has been created for you for the NorCal Men's Retreat 2026.\n\n");
        body.append("Your temporary password is: ").append(defaultPassword).append("\n\n");
        body.append("Please visit the link below to complete your registration and set a new password:\n");
        body.append(registerUrl).append("\n\n");
        body.append("If you did not expect this email, you can safely ignore it.\n\n");
        body.append("— NorCal Men's Retreat 2026");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", toEmail, e);
        }
    }

    public void sendAccountActivatedEmail(String toEmail, String firstName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Account Activated - NorCal Men's Retreat 2026");

        String greeting = (firstName != null && !firstName.isEmpty())
                ? "Hi " + firstName + ","
                : "Hi,";

        String loginUrl = frontendUrl + "/login";

        StringBuilder body = new StringBuilder();
        body.append(greeting).append("\n\n");
        body.append("Your NorCal Men's Retreat account has been activated successfully!\n\n");
        body.append("You can now log in at:\n");
        body.append(loginUrl).append("\n\n");
        body.append("— NorCal Men's Retreat 2026");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Account activated email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account activated email to {}", toEmail, e);
        }
    }

    public void sendRegistrationConfirmation(RetreatRegistration reg) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(reg.getEmail());
        message.setSubject("Registration Confirmation - NorCal Men's Retreat 2026");

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(reg.getFirstName()).append(",\n\n");
        body.append("Thank you for registering for the NorCal Men's Retreat 2026!\n\n");
        body.append("Registration Details:\n");
        body.append("  Name: ").append(reg.getFirstName()).append(" ").append(reg.getLastName()).append("\n");
        body.append("  Attendees: ").append(reg.getAttendees().size()).append("\n");
        body.append("  Total Amount: $").append(reg.getTotalAmount()).append("\n");
        body.append("  Payment Status: ").append(reg.getPaymentStatus()).append("\n\n");
        body.append("Event Details:\n");
        body.append("  Dates: June 11-13, 2026\n");
        body.append("  Venue: Alliance Redwoods, 5000 Bohemian Highway, Occidental, CA 95465\n\n");
        body.append("If you have any questions, please contact us.\n\n");
        body.append("— NorCal Men's Retreat 2026");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Registration confirmation email sent to {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send registration confirmation to {}", reg.getEmail(), e);
        }
    }

    public void sendPaymentReceipt(RetreatRegistration reg, String paymentId) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(reg.getEmail());
        message.setSubject("Payment Receipt - NorCal Men's Retreat 2026");

        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(reg.getFirstName()).append(",\n\n");
        body.append("We have received your payment for the NorCal Men's Retreat 2026.\n\n");
        body.append("Payment Details:\n");
        body.append("  Amount: $").append(reg.getTotalAmount()).append("\n");
        body.append("  Payment ID: ").append(paymentId).append("\n");
        body.append("  Status: Paid\n\n");
        body.append("We look forward to seeing you June 11-13, 2026!\n\n");
        body.append("— NorCal Men's Retreat 2026");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Payment receipt sent to {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send payment receipt to {}", reg.getEmail(), e);
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
        body.append("Payment Status: ").append(reg.getPaymentStatus()).append("\n");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Admin notification sent for registration by {}", reg.getEmail());
        } catch (Exception e) {
            log.error("Failed to send admin notification", e);
        }
    }
}
