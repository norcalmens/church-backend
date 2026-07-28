package com.norcalretreat.backend.config;

import com.norcalretreat.backend.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-shot backfill for the {@code retreat_year} column added in the
 *  season-tagging change. Every pre-existing registration was part of
 *  the 2026 retreat (the only season that ran before this column existed),
 *  so we stamp them all to 2026. New rows are stamped by RegistrationService
 *  from the retreat.active.year setting. Runs on every boot; the UPDATE is
 *  no-op once every row is tagged. */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class RetreatYearBackfill implements ApplicationRunner {

    private static final int LEGACY_YEAR = 2026;

    private final RegistrationRepository registrationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int touched = registrationRepository.backfillNullRetreatYear(LEGACY_YEAR);
            if (touched > 0) {
                log.info("Backfilled retreat_year={} on {} legacy registration(s)", LEGACY_YEAR, touched);
            }
        } catch (Exception e) {
            log.warn("retreat_year backfill skipped: {}", e.getMessage());
        }
    }
}
