package com.norcalretreat.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes live events to STOMP topics. Two audiences:
 *
 *   /topic/public/capacity   -- open, everyone with the home page sees it
 *   /topic/admin/activity    -- admin/committee only, gated in WebSocketConfig
 *
 * Services call the {@code notify*} methods; this class owns the topic
 * naming and payload shape so callers don't need to know the wire format.
 *
 * All broadcasts are best-effort and non-fatal -- a broker failure never
 * blocks the underlying transaction. If SimpMessagingTemplate isn't in the
 * context (e.g. WebSocket starter absent in a slim test slice) the notify
 * calls are no-ops so callers stay unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBroadcaster {

    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ==== Public topics ====

    /** Push a fresh capacity snapshot to every viewer of the home page.
     *  Called after any registration/mutation that changes overnight totals. */
    public void broadcastCapacity(int capacity, int overnight, int retreatYear) {
        if (messagingTemplate == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("capacity", capacity);
        body.put("totalAttendees", overnight);
        body.put("overnightAttendees", overnight);
        body.put("spacesLeft", Math.max(0, capacity - overnight));
        body.put("isFull", capacity - overnight <= 0);
        body.put("retreatYear", retreatYear);
        // Same field name the initial-load HTTP endpoint uses, so the
        // frontend can just overwrite its snapshot with each event.
        safeSend("/topic/public/capacity", body);
    }

    // ==== Admin topics ====

    /** Push a compact activity event to the admin feed -- something the
     *  admin should notice ("new registration by Foo", "new plan request").
     *  Keep the payload small; the admin UI shows a toast + updates its
     *  own count/badge. Deeper detail loads via the existing REST endpoints. */
    public void broadcastAdminActivity(String type, String title, String detail) {
        if (messagingTemplate == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);          // "registration" | "donation" | "payment_plan_request" | "payment"
        body.put("title", title);        // "New registration"
        body.put("detail", detail);      // "Foo Bar -- 2 attendees -- $496"
        body.put("at", LocalDateTime.now().toString());
        safeSend("/topic/admin/activity", body);
    }

    private void safeSend(String destination, Object body) {
        try {
            messagingTemplate.convertAndSend(destination, body);
        } catch (Exception e) {
            log.warn("STOMP broadcast to {} failed: {}", destination, e.getMessage());
        }
    }
}
