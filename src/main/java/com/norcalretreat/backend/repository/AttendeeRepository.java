package com.norcalretreat.backend.repository;

import com.norcalretreat.backend.entity.Attendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendeeRepository extends JpaRepository<Attendee, Long> {

    List<Attendee> findByRegistrationId(Long registrationId);
}
