package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.PaymentPlanPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentPlanPaymentRepository extends JpaRepository<PaymentPlanPayment, Long> {

    List<PaymentPlanPayment> findByPlanIdOrderByPaidAtDesc(Long planId);
}
