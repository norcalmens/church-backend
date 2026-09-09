package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.MeetingNoteAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeetingNoteAttachmentRepository extends JpaRepository<MeetingNoteAttachment, Long> {
}
