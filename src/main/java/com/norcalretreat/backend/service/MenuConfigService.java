package com.norcalretreat.backend.service;

import com.norcalretreat.backend.entity.MenuHiddenItem;
import com.norcalretreat.backend.repository.MenuHiddenItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuConfigService {

    private final MenuHiddenItemRepository repository;

    public Set<String> getHiddenKeys() {
        return repository.findAll()
                .stream()
                .map(MenuHiddenItem::getItemKey)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void setHiddenKeys(Set<String> keys, String username) {
        log.info("Setting {} hidden menu keys by user: {}", keys.size(), username);
        repository.deleteAll();
        for (String key : keys) {
            MenuHiddenItem item = new MenuHiddenItem();
            item.setItemKey(key);
            item.setHiddenBy(username);
            repository.save(item);
        }
    }

    @Transactional
    public void hideItem(String key, String username) {
        if (repository.findByItemKey(key).isEmpty()) {
            MenuHiddenItem item = new MenuHiddenItem();
            item.setItemKey(key);
            item.setHiddenBy(username);
            repository.save(item);
            log.info("Hidden menu item '{}' by user: {}", key, username);
        }
    }

    @Transactional
    public void showItem(String key) {
        repository.deleteByItemKey(key);
        log.info("Showed menu item '{}'", key);
    }
}
