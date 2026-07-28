package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.RetreatRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationRepository extends JpaRepository<RetreatRegistration, Long> {

    List<RetreatRegistration> findByUserId(Long userId);

    List<RetreatRegistration> findByEmail(String email);

    long countByPaymentStatus(String paymentStatus);

    List<RetreatRegistration> findByRetreatYear(Integer retreatYear);

    /** Boot-time backfill: any pre-existing registration with a NULL year
     *  belongs to the 2026 retreat (the only season that ran before we
     *  added year tagging). Idempotent — noop once every row is tagged. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RetreatRegistration r SET r.retreatYear = :year WHERE r.retreatYear IS NULL")
    int backfillNullRetreatYear(@Param("year") Integer year);
}
