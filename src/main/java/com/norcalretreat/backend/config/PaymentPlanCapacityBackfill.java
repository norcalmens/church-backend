package com.norcalretreat.backend.config;

import com.norcalretreat.backend.repository.PaymentPlanRepository;
import com.norcalretreat.backend.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot backfill for the {@code retreat_year} and
 * {@code overnight_attendees} columns added to payment_plans in the
 * capacity-counting change. Plans created before those columns existed
 * have NULL for both, so the SUM query in RegistrationService skipped
 * them and the home hero counter didn't reflect their reserved beds.
 *
 * Every boot: any plan with NULL retreat_year or overnight_attendees is
 * stamped with the currently-active season year and 1 overnight bed
 * (safe default -- admin can bump larger group plans afterward via the
 * plan edit dialog).
 *
 * Idempotent: no rows match the WHERE clause once every plan has values.
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class PaymentPlanCapacityBackfill implements ApplicationRunner {

    private static final int FALLBACK_YEAR = 2027;

    private final PaymentPlanRepository plans;
    private final SystemSettingService settings;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int year;
        try {
            year = settings.getInt(SystemSettingService.KEY_RETREAT_ACTIVE_YEAR, FALLBACK_YEAR);
        } catch (Exception e) {
            year = FALLBACK_YEAR;
        }
        try {
            int touched = plans.backfillMissingCapacityFields(year);
            if (touched > 0) {
                log.info("Backfilled retreatYear={} + overnightAttendees=1 on {} legacy payment plan(s)",
                        year, touched);
            }
        } catch (Exception e) {
            log.warn("payment_plans capacity backfill skipped: {}", e.getMessage());
        }
    }
}
