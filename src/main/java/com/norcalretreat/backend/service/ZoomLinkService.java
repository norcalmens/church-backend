package com.norcalretreat.backend.service;

import com.norcalretreat.backend.dto.ZoomLinkDTO;
import com.norcalretreat.backend.entity.ZoomLink;
import com.norcalretreat.backend.repository.ZoomLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoomLinkService {

    private final ZoomLinkRepository repository;

    public List<ZoomLinkDTO> listActive() {
        return repository.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream().map(this::toDto).toList();
    }

    public List<ZoomLinkDTO> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ZoomLinkDTO create(ZoomLinkDTO dto) {
        ZoomLink link = new ZoomLink();
        apply(dto, link);
        return toDto(repository.save(link));
    }

    @Transactional
    public ZoomLinkDTO update(Long id, ZoomLinkDTO dto) {
        ZoomLink link = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ZoomLink not found: " + id));
        apply(dto, link);
        return toDto(repository.save(link));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("ZoomLink not found: " + id);
        }
        repository.deleteById(id);
    }

    private void apply(ZoomLinkDTO dto, ZoomLink link) {
        link.setTitle(dto.getTitle());
        link.setDescription(dto.getDescription());
        link.setJoinUrl(dto.getJoinUrl());
        link.setMeetingId(dto.getMeetingId());
        link.setPasscode(dto.getPasscode());
        link.setScheduleText(dto.getScheduleText());
        if (dto.getSortOrder() != null) link.setSortOrder(dto.getSortOrder());
        if (dto.getIsActive() != null) link.setIsActive(dto.getIsActive());
    }

    private ZoomLinkDTO toDto(ZoomLink link) {
        ZoomLinkDTO dto = new ZoomLinkDTO();
        dto.setId(link.getId());
        dto.setTitle(link.getTitle());
        dto.setDescription(link.getDescription());
        dto.setJoinUrl(link.getJoinUrl());
        dto.setMeetingId(link.getMeetingId());
        dto.setPasscode(link.getPasscode());
        dto.setScheduleText(link.getScheduleText());
        dto.setSortOrder(link.getSortOrder());
        dto.setIsActive(link.getIsActive());
        dto.setCreatedAt(link.getCreatedAt());
        dto.setUpdatedAt(link.getUpdatedAt());
        return dto;
    }
}
