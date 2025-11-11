package com.grash.service;

import com.grash.dto.timeTracking.TimeEntrySummaryDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.FileMapper;
import com.grash.model.OwnUser;
import com.grash.model.TimeEntry;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.TimeStatus;
import com.grash.repository.TimeEntryRepository;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final UserService userService;
    private final FileMapper fileMapper;

    @Transactional
    public TimeEntry startTimer(OwnUser user) {
        Optional<TimeEntry> existing = findRunningEntry(user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setUser(user);
        timeEntry.setStartedAt(new Date());
        timeEntry.setStatus(TimeStatus.RUNNING);
        timeEntry.setDuration(0);
        return timeEntryRepository.save(timeEntry);
    }

    @Transactional
    public TimeEntry stopTimer(TimeEntry entry) {
        entry.setStatus(TimeStatus.STOPPED);
        entry.setDuration(entry.getDuration() + Helper.getDateDiff(entry.getStartedAt(), new Date(), TimeUnit.SECONDS));
        return timeEntryRepository.save(entry);
    }

    public Optional<TimeEntry> findRunningEntry(Long userId) {
        return timeEntryRepository.findByUser_IdAndStatus(userId, TimeStatus.RUNNING);
    }

    public Collection<TimeEntry> findEntriesForUser(Long userId, Date start, Date end) {
        return timeEntryRepository.findByUser_IdAndStartedAtBetweenOrderByStartedAtAsc(userId, start, end);
    }

    public Collection<TimeEntrySummaryDTO> getSummary(Long companyId, Date start, Date end) {
        Collection<OwnUser> users = userService.findByCompany(companyId);
        Map<Long, List<TimeEntry>> entriesByUser = timeEntryRepository
                .findByCompany_IdAndStartedAtBetween(companyId, start, end)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getUser().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            list.sort(Comparator.comparing(TimeEntry::getStartedAt));
                            return list;
                        })));

        Map<Long, TimeEntry> runningEntries = timeEntryRepository
                .findByCompany_IdAndStatus(companyId, TimeStatus.RUNNING)
                .stream()
                .collect(Collectors.toMap(entry -> entry.getUser().getId(), Function.identity(), (entry, duplicate) -> entry));

        return users.stream().map(user -> {
            List<TimeEntry> userEntries = entriesByUser.getOrDefault(user.getId(), Collections.emptyList());
            TimeEntrySummaryDTO.TimeEntrySummaryDTOBuilder builder = TimeEntrySummaryDTO.builder()
                    .userId(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .jobTitle(user.getJobTitle())
                    .totalDurationSeconds(userEntries.stream().mapToLong(TimeEntry::getDuration).sum());

            if (user.getImage() != null) {
                builder.image(fileMapper.toShowDto(user.getImage()));
            }

            if (!userEntries.isEmpty()) {
                TimeEntry lastEntry = userEntries.get(userEntries.size() - 1);
                builder.lastEntryStart(lastEntry.getStartedAt());
                builder.lastEntryEnd(lastEntry.getEndedAt());
            }

            TimeEntry runningEntry = runningEntries.get(user.getId());
            if (runningEntry != null) {
                builder.running(true);
                builder.runningSince(runningEntry.getStartedAt());
            } else {
                builder.running(false);
            }

            return builder.build();
        }).collect(Collectors.toList());
    }

    public OwnUser validateUserInCompany(Long userId, OwnUser requester) {
        OwnUser targetUser = userService.findById(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        if (!targetUser.getCompany().getId().equals(requester.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return targetUser;
    }

    public TimeEntry editEntry(Long id, Date startedAt, Date endedAt, OwnUser requester) {
        TimeEntry entry = timeEntryRepository.findById(id)
                .orElseThrow(() -> new CustomException("Time entry not found", HttpStatus.NOT_FOUND));
        if (!entry.getCompany().getId().equals(requester.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (endedAt.before(startedAt)) {
            throw new CustomException("End date must be after start date", HttpStatus.BAD_REQUEST);
        }
        entry.setStartedAt(startedAt);
        entry.setDuration(Helper.getDateDiff(startedAt, endedAt, TimeUnit.SECONDS));
        entry.setStatus(TimeStatus.STOPPED);
        return timeEntryRepository.save(entry);
    }

    public boolean hasTimeTrackingPermission(OwnUser user) {
        return user.getRole().getViewPermissions().contains(PermissionEntity.TIME_TRACKING);
    }
}
