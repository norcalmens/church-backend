package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.ProspectiveSpeaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProspectiveSpeakerRepository extends JpaRepository<ProspectiveSpeaker, Long> {
    /** Active first (pending, invited, confirmed), declined last; within each
     *  group, newest-created first so recent outreach surfaces at the top. */
    List<ProspectiveSpeaker> findAllByOrderByStatusAscCreatedAtDesc();
}
