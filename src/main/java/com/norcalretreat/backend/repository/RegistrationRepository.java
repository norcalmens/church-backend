package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.RetreatRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistrationRepository extends JpaRepository<RetreatRegistration, Long> {

    List<RetreatRegistration> findByUserId(Long userId);

    List<RetreatRegistration> findByEmail(String email);

    long countByPaymentStatus(String paymentStatus);
}
