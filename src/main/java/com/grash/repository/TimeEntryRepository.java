package com.grash.repository;

import com.grash.model.TimeEntry;
import com.grash.model.enums.TimeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Date;
import java.util.Optional;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    Collection<TimeEntry> findByUser_IdAndStartedAtBetweenOrderByStartedAtAsc(Long userId, Date start, Date end);

    Collection<TimeEntry> findByCompany_IdAndStartedAtBetween(Long companyId, Date start, Date end);

    Optional<TimeEntry> findTopByUser_IdAndCompany_IdOrderByStartedAtDesc(Long userId, Long companyId);

    Optional<TimeEntry> findByUser_IdAndStatus(Long userId, TimeStatus status);

    Collection<TimeEntry> findByCompany_IdAndStatus(Long companyId, TimeStatus status);
}
