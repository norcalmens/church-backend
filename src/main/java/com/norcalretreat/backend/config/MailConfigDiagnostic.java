package com.norcalretreat.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Prints a single line at boot summarizing the mail configuration Spring
 *  actually picked up, so "Email service is not configured" failures can be
 *  diagnosed from the Railway logs without adding a temporary probe.
 *  ObjectProvider lets the runner start even when JavaMailSender is absent. */
@Slf4j
@Configuration
public class MailConfigDiagnostic {

    @Bean
    @Order(1)
    ApplicationRunner mailConfigReporter(ObjectProvider<JavaMailSender> senderProvider,
                                         @Value("${spring.mail.host:}") String host,
                                         @Value("${spring.mail.port:}") String port,
                                         @Value("${spring.mail.username:}") String user) {
        return args -> {
            JavaMailSender sender = senderProvider.getIfAvailable();
            String hostShown = host == null || host.isBlank() ? "<EMPTY>" : host;
            String userShown = user == null || user.isBlank() ? "<EMPTY>" : maskUser(user);
            if (sender == null) {
                log.warn("MAIL DIAGNOSTIC: JavaMailSender bean NOT created. spring.mail.host={}, spring.mail.port={}, spring.mail.username={}. " +
                        "EmailService will be skipped; every send-triggering action will fail with 'Email service is not configured'.",
                        hostShown, port, userShown);
            } else if (sender instanceof JavaMailSenderImpl impl) {
                log.info("MAIL DIAGNOSTIC: JavaMailSender ready. host={}, port={}, username={}",
                        impl.getHost() == null || impl.getHost().isBlank() ? "<EMPTY>" : impl.getHost(),
                        impl.getPort(),
                        impl.getUsername() == null || impl.getUsername().isBlank() ? "<EMPTY>" : maskUser(impl.getUsername()));
            } else {
                log.info("MAIL DIAGNOSTIC: JavaMailSender ready ({})", sender.getClass().getSimpleName());
            }
        };
    }

    private static String maskUser(String u) {
        int at = u.indexOf('@');
        if (at <= 1) return "***";
        return u.charAt(0) + "***" + u.substring(at);
    }
}
