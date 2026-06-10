package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.WaitlistEntry;
import com.norcalretreat.backend.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    @Transactional
    public Map<String, Object> create(WaitlistEntry entry) {
        if (entry.getFirstName() == null || entry.getFirstName().isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (entry.getLastName() == null || entry.getLastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }
        if (entry.getEmail() == null || entry.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (entry.getRequestedSeats() == null || entry.getRequestedSeats() < 1) {
            entry.setRequestedSeats(1);
        }
        entry.setId(null);
        entry.setContacted(false);
        entry.setCreatedAt(null);
        WaitlistEntry saved = waitlistRepository.save(entry);

        long position = waitlistRepository.count();
        Map<String, Object> out = new HashMap<>();
        out.put("entry", saved);
        out.put("position", position);
        out.put("totalOnWaitlist", position);
        return out;
    }

    public List<WaitlistEntry> listAll() {
        return waitlistRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public WaitlistEntry setContacted(Long id, boolean contacted) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found"));
        entry.setContacted(contacted);
        return waitlistRepository.save(entry);
    }

    @Transactional
    public void delete(Long id) {
        if (!waitlistRepository.existsById(id)) {
            throw new IllegalArgumentException("Waitlist entry not found");
        }
        waitlistRepository.deleteById(id);
    }
}
