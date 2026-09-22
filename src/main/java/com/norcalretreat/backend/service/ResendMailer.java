package com.norcalretreat.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends via Resend's HTTPS API (https://api.resend.com/emails), which is
 *  reachable from Railway even though outbound SMTP (25/465/587) is blocked.
 *  {@link #isReady()} lets EmailService pick this over JavaMailSender when
 *  RESEND_API_KEY is set. Domain used in {@code from} MUST be verified in
 *  the Resend dashboard, otherwise the API returns 403 with a clear error. */
@Slf4j
@Component
public class ResendMailer {

    private static final String BASE_URL = "https://api.resend.com";

    private final String apiKey;
    private final RestClient http;

    public ResendMailer(@Value("${resend.api-key:${RESEND_API_KEY:}}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public boolean isReady() {
        return !apiKey.isBlank();
    }

    /** Fires the message through Resend. Throws on non-2xx so the log wrapper
     *  can capture the error body verbatim in sent_emails.error_message. */
    public void send(SimpleMailMessage message) {
        if (!isReady()) {
            throw new IllegalStateException("Resend API key not configured (RESEND_API_KEY)");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", message.getFrom());
        payload.put("to", toList(message.getTo()));
        if (message.getCc() != null && message.getCc().length > 0) payload.put("cc", toList(message.getCc()));
        if (message.getBcc() != null && message.getBcc().length > 0) payload.put("bcc", toList(message.getBcc()));
        if (message.getReplyTo() != null && !message.getReplyTo().isBlank()) {
            payload.put("reply_to", message.getReplyTo());
        }
        payload.put("subject", message.getSubject());
        payload.put("text", message.getText());

        try {
            http.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            throw new RuntimeException("Resend API " + e.getStatusCode() + ": " + body, e);
        }
    }

    private static List<String> toList(String[] arr) {
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}
