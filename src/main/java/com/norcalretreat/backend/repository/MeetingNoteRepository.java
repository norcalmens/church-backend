package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.MeetingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {
    List<MeetingNote> findAllByOrderByMeetingDateDescIdDesc();
}
