package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRsvpRepository extends JpaRepository<EventRsvp, Long> {
    List<EventRsvp> findByEventKeyOrderByCreatedAtDesc(String eventKey);
    List<EventRsvp> findAllByOrderByCreatedAtDesc();
    long countByEventKey(String eventKey);
}
