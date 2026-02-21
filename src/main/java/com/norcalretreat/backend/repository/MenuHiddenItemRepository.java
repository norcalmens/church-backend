package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.MenuHiddenItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MenuHiddenItemRepository extends JpaRepository<MenuHiddenItem, Long> {

    Optional<MenuHiddenItem> findByItemKey(String itemKey);

    void deleteByItemKey(String itemKey);
}
