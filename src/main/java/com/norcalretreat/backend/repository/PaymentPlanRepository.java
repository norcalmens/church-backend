package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {

    List<PaymentPlan> findAllByOrderByCreatedAtDesc();

    Optional<PaymentPlan> findByPayerToken(String payerToken);

    /** Total overnight beds reserved by ACTIVE or COMPLETED plans in a
     *  given season. Requested plans are excluded (still provisional);
     *  canceled plans are excluded (no longer attending). Returns 0 if
     *  no matching plans exist. */
    @Query("SELECT COALESCE(SUM(p.overnightAttendees), 0) FROM PaymentPlan p " +
           "WHERE p.retreatYear = :year " +
           "AND LOWER(p.status) IN ('active', 'completed')")
    long sumOvernightAttendeesForYear(@Param("year") int year);

    /** Boot-time backfill: plans created BEFORE the retreatYear/
     *  overnightAttendees columns existed have NULL for both. Stamp them
     *  as active-year + 1 overnight so they count toward capacity like
     *  any new plan would. Idempotent -- noop once every row is tagged. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PaymentPlan p SET p.retreatYear = :year, p.overnightAttendees = 1 " +
           "WHERE p.retreatYear IS NULL OR p.overnightAttendees IS NULL")
    int backfillMissingCapacityFields(@Param("year") int year);
}
