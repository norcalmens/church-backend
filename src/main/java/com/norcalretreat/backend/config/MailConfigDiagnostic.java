package com.norcalretreat.backend.config;

import com.norcalretreat.backend.service.ResendMailer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Prints a single line at boot summarizing which mail transport is active
 *  (Resend HTTPS API vs. SMTP JavaMailSender), so "Email service is not
 *  configured" failures can be diagnosed from Railway logs without probes. */
@Slf4j
@Configuration
public class MailConfigDiagnostic {

    @Bean
    @Order(1)
    ApplicationRunner mailConfigReporter(ObjectProvider<JavaMailSender> senderProvider,
                                         ResendMailer resend,
                                         @Value("${spring.mail.host:}") String host,
                                         @Value("${spring.mail.port:}") String port,
                                         @Value("${spring.mail.username:}") String user,
                                         @Value("${mail.from:noreply@norcalmensretreat.com}") String from) {
        return args -> {
            String transport;
            if (resend.isReady()) {
                transport = "Resend (HTTPS API)";
            } else if (senderProvider.getIfAvailable() != null) {
                transport = "SMTP (JavaMailSender)";
            } else {
                transport = "NONE — outbound email disabled";
            }
            log.info("MAIL DIAGNOSTIC: active transport = {}, from = {}", transport, from);

            JavaMailSender sender = senderProvider.getIfAvailable();
            if (sender == null) {
                log.info("MAIL DIAGNOSTIC: SMTP fallback details — spring.mail.host={}, spring.mail.port={}, spring.mail.username={}",
                        host == null || host.isBlank() ? "<EMPTY>" : host,
                        port,
                        user == null || user.isBlank() ? "<EMPTY>" : maskUser(user));
            } else if (sender instanceof JavaMailSenderImpl impl) {
                log.info("MAIL DIAGNOSTIC: SMTP fallback ready — host={}, port={}, username={}",
                        impl.getHost() == null || impl.getHost().isBlank() ? "<EMPTY>" : impl.getHost(),
                        impl.getPort(),
                        impl.getUsername() == null || impl.getUsername().isBlank() ? "<EMPTY>" : maskUser(impl.getUsername()));
            }
        };
    }

    private static String maskUser(String u) {
        int at = u.indexOf('@');
        if (at <= 1) return "***";
        return u.charAt(0) + "***" + u.substring(at);
    }
}
