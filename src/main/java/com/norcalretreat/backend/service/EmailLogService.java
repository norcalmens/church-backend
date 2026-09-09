package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.SentEmail;
import com.norcalretreat.backend.repository.SentEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Persists a record of every outbound email attempt. Wraps SMTP sends
 * with try/catch/log so the log stays truthful even when SMTP throws.
 *
 * Rows are inserted BEFORE the actual send (in a REQUIRES_NEW transaction
 * so a mid-send crash still leaves a "pending" audit row); status is then
 * flipped to "sent" or "failed" after the send returns. Callers that want
 * the SMTP failure to propagate (e.g. admin-triggered resends) get it
 * re-thrown; the log entry is updated first so it's persisted regardless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailLogService {

    private final SentEmailRepository repo;

    /** Insert a "pending" row FIRST (own transaction so it survives a
     *  rollback in the caller), then run the sender lambda, then update
     *  the row to sent/failed. Re-throws any exception so the caller can
     *  surface it to the UI. */
    public SentEmail wrap(String category,
                          String recipient,
                          String subject,
                          String body,
                          String relatedType,
                          Long relatedId,
                          String triggeredBy,
                          Consumer<Void> sender) {
        SentEmail log = persistPending(category, recipient, subject, body, relatedType, relatedId, triggeredBy);
        try {
            sender.accept(null);
            markSent(log.getId());
            return log;
        } catch (RuntimeException e) {
            markFailed(log.getId(), safeMessage(e));
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected SentEmail persistPending(String category, String recipient, String subject, String body,
                                       String relatedType, Long relatedId, String triggeredBy) {
        SentEmail row = new SentEmail();
        row.setCategory(category);
        row.setRecipient(recipient);
        row.setSubject(subject);
        row.setBody(body);
        row.setRelatedEntityType(relatedType);
        row.setRelatedEntityId(relatedId);
        row.setTriggeredBy(triggeredBy);
        row.setStatus("pending");
        return repo.save(row);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markSent(Long id) {
        repo.findById(id).ifPresent(r -> {
            r.setStatus("sent");
            r.setErrorMessage(null);
            repo.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markFailed(Long id, String errorMessage) {
        repo.findById(id).ifPresent(r -> {
            r.setStatus("failed");
            r.setErrorMessage(errorMessage);
            repo.save(r);
        });
    }

    private static String safeMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String m = t.getMessage();
        if (m == null || m.isBlank()) m = t.getClass().getSimpleName();
        // Cap length so a monster stacktrace doesn't blow up the row
        return m.length() > 2000 ? m.substring(0, 2000) + "…" : m;
    }
}
