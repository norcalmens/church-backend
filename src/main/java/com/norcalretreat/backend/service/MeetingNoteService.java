package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.MeetingNoteAttachmentDTO;
import com.norcalretreat.backend.dto.MeetingNoteDTO;
import com.norcalretreat.backend.entity.MeetingNote;
import com.norcalretreat.backend.entity.MeetingNoteAttachment;
import com.norcalretreat.backend.repository.MeetingNoteAttachmentRepository;
import com.norcalretreat.backend.repository.MeetingNoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingNoteService {

    private final MeetingNoteRepository notes;
    private final MeetingNoteAttachmentRepository attachments;

    /** Rejects uploads bigger than the multipart limit in application.yaml
     *  (10MB) with a clear error, rather than letting Spring's generic
     *  "MaxUploadSizeExceeded" bubble to the client as a 500. */
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    public List<MeetingNoteDTO> listAll() {
        return notes.findAllByOrderByMeetingDateDescIdDesc().stream()
                .map(this::toDto).toList();
    }

    public MeetingNoteDTO get(Long id) {
        MeetingNote n = notes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting note not found: " + id));
        return toDto(n);
    }

    @Transactional
    public MeetingNoteDTO create(MeetingNoteDTO req, String currentUser) {
        validate(req);
        MeetingNote n = new MeetingNote();
        applyFields(req, n);
        n.setCreatedBy(currentUser);
        n = notes.save(n);
        log.info("Created meeting note {} '{}'", n.getId(), n.getTitle());
        return toDto(n);
    }

    @Transactional
    public MeetingNoteDTO update(Long id, MeetingNoteDTO req) {
        validate(req);
        MeetingNote n = notes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting note not found: " + id));
        applyFields(req, n);
        n = notes.save(n);
        return toDto(n);
    }

    @Transactional
    public void delete(Long id) {
        if (!notes.existsById(id)) throw new IllegalArgumentException("Meeting note not found: " + id);
        notes.deleteById(id);
    }

    // ==== Attachments ====

    @Transactional
    public MeetingNoteAttachmentDTO addAttachment(Long noteId, MultipartFile file, String currentUser) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("File too large -- max 10 MB");
        }
        MeetingNote n = notes.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting note not found: " + noteId));

        MeetingNoteAttachment a = new MeetingNoteAttachment();
        a.setMeetingNote(n);
        a.setFileName(safeFileName(file.getOriginalFilename()));
        a.setContentType(file.getContentType());
        a.setFileSize(file.getSize());
        a.setData(file.getBytes());
        a.setUploadedBy(currentUser);
        a = attachments.save(a);
        log.info("Attached file '{}' ({} bytes) to meeting note {}",
                a.getFileName(), a.getFileSize(), noteId);
        return toAttachmentDto(a);
    }

    /** Load an attachment including its blob, for streaming to the client. */
    public MeetingNoteAttachment loadAttachment(Long attachmentId) {
        return attachments.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        if (!attachments.existsById(attachmentId)) {
            throw new IllegalArgumentException("Attachment not found: " + attachmentId);
        }
        attachments.deleteById(attachmentId);
    }

    // ==== helpers ====

    private void validate(MeetingNoteDTO req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (req.getMeetingDate() == null) {
            throw new IllegalArgumentException("Meeting date is required");
        }
    }

    private void applyFields(MeetingNoteDTO req, MeetingNote n) {
        n.setTitle(req.getTitle().trim());
        n.setMeetingDate(req.getMeetingDate());
        n.setBody(req.getBody() == null || req.getBody().isBlank() ? null : req.getBody().trim());
        n.setAttendees(req.getAttendees() == null || req.getAttendees().isBlank() ? null : req.getAttendees().trim());
    }

    /** Strip path components and dodgy chars so an uploaded filename can't
     *  smuggle a directory traversal or weird control sequence into the DB. */
    private static String safeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[\\x00-\\x1F<>:\"/\\\\|?*]", "_");
        if (base.length() > 200) base = base.substring(0, 200);
        return base.isBlank() ? "file" : base;
    }

    private MeetingNoteDTO toDto(MeetingNote n) {
        MeetingNoteDTO d = new MeetingNoteDTO();
        d.setId(n.getId());
        d.setTitle(n.getTitle());
        d.setMeetingDate(n.getMeetingDate());
        d.setBody(n.getBody());
        d.setAttendees(n.getAttendees());
        d.setCreatedBy(n.getCreatedBy());
        d.setCreatedAt(n.getCreatedAt());
        d.setUpdatedAt(n.getUpdatedAt());
        d.setAttachments(n.getAttachments() == null ? List.of()
                : n.getAttachments().stream().map(this::toAttachmentDto).toList());
        return d;
    }

    private MeetingNoteAttachmentDTO toAttachmentDto(MeetingNoteAttachment a) {
        MeetingNoteAttachmentDTO d = new MeetingNoteAttachmentDTO();
        d.setId(a.getId());
        d.setFileName(a.getFileName());
        d.setContentType(a.getContentType());
        d.setFileSize(a.getFileSize());
        d.setUploadedBy(a.getUploadedBy());
        d.setUploadedAt(a.getUploadedAt());
        return d;
    }
}
