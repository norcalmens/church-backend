package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.SentEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    /** Filtered + paged list for the admin log page. Any filter left null
     *  matches all values. Search matches recipient or subject prefix. */
    @Query("SELECT e FROM SentEmail e " +
           "WHERE (:status   IS NULL OR LOWER(e.status)   = LOWER(:status)) " +
           "AND   (:category IS NULL OR LOWER(e.category) = LOWER(:category)) " +
           "AND   (:q IS NULL OR LOWER(e.recipient) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "                  OR LOWER(e.subject)   LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY e.attemptedAt DESC")
    Page<SentEmail> search(@Param("status") String status,
                           @Param("category") String category,
                           @Param("q") String q,
                           Pageable pageable);

    long countByStatus(String status);
}
