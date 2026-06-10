package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findAllByOrderByCreatedAtAsc();
}
