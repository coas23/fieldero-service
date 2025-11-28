package com.grash.service;

import com.grash.dto.timeTracking.TimeEntryLocationDTO;
import com.grash.dto.timeTracking.TimeEntrySummaryDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.FileMapper;
import com.grash.model.OwnUser;
import com.grash.model.TimeEntry;
import com.grash.model.UserWorkingHour;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;

@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final UserService userService;
    private final FileMapper fileMapper;

    @Transactional
    public TimeEntry startTimer(OwnUser user, TimeEntryLocationDTO location) {
        Optional<TimeEntry> existing = findRunningEntry(user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setUser(user);
        timeEntry.setStartedAt(new Date());
        timeEntry.setStatus(TimeStatus.RUNNING);
        timeEntry.setDuration(0);
        if (location != null) {
            timeEntry.setStartLatitude(location.getLatitude());
            timeEntry.setStartLongitude(location.getLongitude());
            if (location.getComment() != null) {
                timeEntry.setComment(location.getComment());
            }
        }
        return timeEntryRepository.save(timeEntry);
    }

    @Transactional
    public TimeEntry stopTimer(TimeEntry entry, TimeEntryLocationDTO location) {
        entry.setStatus(TimeStatus.STOPPED);
        entry.setDuration(entry.getDuration() + Helper.getDateDiff(entry.getStartedAt(), new Date(), TimeUnit.SECONDS));
        if (location != null) {
            entry.setEndLatitude(location.getLatitude());
            entry.setEndLongitude(location.getLongitude());
            if (location.getComment() != null) {
                entry.setComment(location.getComment());
            }
        }
        return timeEntryRepository.save(entry);
    }

    public Optional<TimeEntry> findRunningEntry(Long userId) {
        return timeEntryRepository.findByUser_IdAndStatus(userId, TimeStatus.RUNNING);
    }

    public Collection<TimeEntry> findEntriesForUser(Long userId, Date start, Date end) {
        Collection<TimeEntry> entries = timeEntryRepository.findByUser_IdAndStartedAtBetweenOrderByStartedAtAsc(userId, start, end);
        Optional<OwnUser> user = userService.findById(userId);
        if (user.isEmpty()) return entries;
        return applyDailyBreak(entries, user.get());
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
            List<TimeEntry> adjustedEntries = new java.util.ArrayList<>(applyDailyBreak(userEntries, user));
            TimeEntrySummaryDTO.TimeEntrySummaryDTOBuilder builder = TimeEntrySummaryDTO.builder()
                    .userId(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .jobTitle(user.getJobTitle())
                    .totalDurationSeconds(adjustedEntries.stream().mapToLong(TimeEntry::getDuration).sum());

            if (user.getImage() != null) {
                builder.image(fileMapper.toShowDto(user.getImage()));
            }

            if (!adjustedEntries.isEmpty()) {
                TimeEntry lastEntry = adjustedEntries.get(adjustedEntries.size() - 1);
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

    private Collection<TimeEntry> applyDailyBreak(Collection<TimeEntry> entries, OwnUser user) {
        if (entries == null || entries.isEmpty()) return entries;
        // Ensure working hours are initialized to read breakMinutes.
        if (user.getWorkingHours() != null) {
            user.getWorkingHours().size();
        }
        Map<DayOfWeek, Integer> breakByDay = new ConcurrentHashMap<>();
        if (user.getWorkingHours() != null) {
            for (UserWorkingHour wh : user.getWorkingHours()) {
                breakByDay.put(wh.getDayOfWeek(), wh.getBreakMinutes() == null ? 0 : wh.getBreakMinutes());
            }
        }

        Map<LocalDate, List<TimeEntry>> byDate = entries.stream()
                .collect(Collectors.groupingBy(te -> te.getStartedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));

        for (Map.Entry<LocalDate, List<TimeEntry>> dayEntry : byDate.entrySet()) {
            LocalDate date = dayEntry.getKey();
            List<TimeEntry> dayEntries = dayEntry.getValue();
            long totalSeconds = dayEntries.stream().mapToLong(TimeEntry::getDuration).sum();
            if (totalSeconds <= 6 * 3600) continue;
            int breakMinutes = breakByDay.getOrDefault(date.getDayOfWeek(), 0);
            if (breakMinutes <= 0) continue;
            long breakSeconds = breakMinutes * 60L;
            // Apply break to the first completed entry of the day.
            for (TimeEntry entry : dayEntries) {
                if (entry.getDuration() > 0 && entry.getStatus() == TimeStatus.STOPPED) {
                    long newDuration = Math.max(0, entry.getDuration() - breakSeconds);
                    entry.setDuration(newDuration);
                    break;
                }
            }
        }
        return entries;
    }
}
