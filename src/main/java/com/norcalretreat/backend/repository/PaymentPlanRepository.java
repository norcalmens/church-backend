package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.PaymentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, Long> {

    List<PaymentPlan> findAllByOrderByCreatedAtDesc();

    Optional<PaymentPlan> findByPayerToken(String payerToken);
}
