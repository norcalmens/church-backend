package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.AuditLog;
import com.norcalretreat.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logEvent(String eventType, String username, Long userId,
                         String ipAddress, String userAgent, String details, boolean success) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setUsername(username);
        log.setUserId(userId);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setDetails(details);
        log.setIsSuccess(success);
        auditLogRepository.save(log);
    }

    public List<AuditLog> getRecentLogs() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}
