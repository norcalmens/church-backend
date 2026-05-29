package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.ZoomLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoomLinkRepository extends JpaRepository<ZoomLink, Long> {

    List<ZoomLink> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    List<ZoomLink> findAllByOrderBySortOrderAscIdAsc();
}
