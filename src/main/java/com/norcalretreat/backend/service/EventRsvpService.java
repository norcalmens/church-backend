package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.EventRsvpDTO;
import com.norcalretreat.backend.entity.EventRsvp;
import com.norcalretreat.backend.repository.EventRsvpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Lightweight RSVP tracking for events (breakfast, meet-and-greet, etc.).
 * Bakes in a compact allowlist of event keys to prevent a stranger from
 * probing /rsvp/anything-they-invent and filling the DB with junk rows.
 * Extend {@code KNOWN_EVENTS} as new events go up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventRsvpService {

    private final EventRsvpRepository repo;
    private RealtimeBroadcaster realtime;

    @Autowired(required = false)
    public void setRealtime(RealtimeBroadcaster realtime) {
        this.realtime = realtime;
    }

    /** Permitted event keys. Keeps the public form URL space closed --
     *  a random probe like /rsvp/foo returns 404 instead of a form. */
    private static final java.util.Set<String> KNOWN_EVENTS = java.util.Set.of(
            "breakfast-oct-2026"
    );

    // Loose email format check; treats missing email as OK (phone-only RSVP).
    private static final Pattern EMAIL_RE = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public boolean eventExists(String eventKey) {
        return eventKey != null && KNOWN_EVENTS.contains(eventKey.toLowerCase());
    }

    /** Public submission. Validates fields, saves, pings the admin toast. */
    @Transactional
    public EventRsvpDTO submit(String eventKey, EventRsvpDTO req) {
        if (!eventExists(eventKey)) {
            throw new IllegalArgumentException("Unknown event");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        String email = req.getEmail() == null ? "" : req.getEmail().trim();
        String phone = req.getPhone() == null ? "" : req.getPhone().trim();
        if (email.isEmpty() && phone.isEmpty()) {
            throw new IllegalArgumentException("Provide an email or phone number so we can confirm");
        }
        if (!email.isEmpty() && !EMAIL_RE.matcher(email).matches()) {
            throw new IllegalArgumentException("Email format looks wrong");
        }
        int guests = req.getGuestCount() == null ? 1 : Math.max(1, Math.min(20, req.getGuestCount()));

        EventRsvp e = new EventRsvp();
        e.setEventKey(eventKey.toLowerCase());
        e.setName(req.getName().trim());
        e.setEmail(email.isEmpty() ? null : email);
        e.setPhone(phone.isEmpty() ? null : phone);
        String cong = req.getCongregation() == null ? "" : req.getCongregation().trim();
        e.setCongregation(cong.isEmpty() ? null : cong);
        e.setGuestCount(guests);
        e.setNotes(req.getNotes() == null || req.getNotes().isBlank() ? null : req.getNotes().trim());
        e = repo.save(e);
        log.info("Event RSVP {} for {}: {} <{}> guests={}", e.getId(), e.getEventKey(),
                e.getName(), e.getEmail() != null ? e.getEmail() : e.getPhone(), e.getGuestCount());

        if (realtime != null) {
            String detail = e.getName()
                    + (guests > 1 ? " (+" + (guests - 1) + " guest" + (guests == 2 ? "" : "s") + ")" : "");
            realtime.broadcastAdminActivity("rsvp", "New RSVP: " + prettyEvent(e.getEventKey()), detail);
        }
        return toDto(e);
    }

    public List<EventRsvpDTO> listByEvent(String eventKey) {
        return repo.findByEventKeyOrderByCreatedAtDesc(eventKey.toLowerCase())
                .stream().map(this::toDto).toList();
    }

    public List<EventRsvpDTO> listAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("RSVP not found");
        repo.deleteById(id);
    }

    private EventRsvpDTO toDto(EventRsvp e) {
        EventRsvpDTO d = new EventRsvpDTO();
        d.setId(e.getId());
        d.setEventKey(e.getEventKey());
        d.setName(e.getName());
        d.setEmail(e.getEmail());
        d.setPhone(e.getPhone());
        d.setCongregation(e.getCongregation());
        d.setGuestCount(e.getGuestCount());
        d.setNotes(e.getNotes());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }

    /** Human label for an event key ("breakfast-oct-2026" -> "Breakfast Oct 2026").
     *  Used in the admin activity toast. */
    private static String prettyEvent(String key) {
        if (key == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("-")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
