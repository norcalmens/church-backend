package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.FeedbackEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntry, Long> {
    List<FeedbackEntry> findAllByOrderBySubmittedAtDesc();
}
