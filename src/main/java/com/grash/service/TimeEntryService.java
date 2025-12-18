package com.grash.service;

import com.grash.dto.timeTracking.TimeEntryLocationDTO;
import com.grash.dto.timeTracking.TimeEntrySummaryDTO;
import com.grash.dto.timeTracking.TimeEntrySummaryPageDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        // Beim Start nie in die Zukunft runden, sonst läuft der Timer negativ.
        Date startedAt = roundToFiveMinutes(new Date(), true);
        TimeEntry timeEntry = new TimeEntry();
        timeEntry.setUser(user);
        timeEntry.setStartedAt(startedAt);
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
        Date roundedStart = roundToFiveMinutes(entry.getStartedAt(), true);
        Date roundedEnd = roundToFiveMinutes(new Date(), false);
        if (roundedEnd.before(roundedStart)) {
            // Fallback: Endzeit nicht vor Startzeit
            roundedEnd = roundedStart;
        }
        entry.setStartedAt(roundedStart);
        long roundedSeconds = Math.max(0, Helper.getDateDiff(roundedStart, roundedEnd, TimeUnit.SECONDS));
        entry.setDuration(roundedSeconds);
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
        return timeEntryRepository.findByUser_IdAndStartedAtBetweenOrderByStartedAtAsc(userId, start, end);
    }

    public TimeEntrySummaryPageDTO getSummary(Long companyId, Date start, Date end, int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("firstName").ascending().and(Sort.by("lastName").ascending()));
        Page<OwnUser> usersPage = userService.findByCompanyPaged(companyId, pageable, search);
        List<Long> userIds = usersPage.getContent().stream().map(OwnUser::getId).collect(Collectors.toList());

        Map<Long, List<TimeEntry>> entriesByUser = timeEntryRepository
                .findByCompany_IdAndStartedAtBetweenAndUser_IdIn(companyId, start, end, userIds)
                .stream()
                .collect(Collectors.groupingBy(entry -> entry.getUser().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            list.sort(Comparator.comparing(TimeEntry::getStartedAt));
                            return list;
                        })));

        Map<Long, TimeEntry> runningEntries = timeEntryRepository
                .findByCompany_IdAndUser_IdInAndStatus(companyId, userIds, TimeStatus.RUNNING)
                .stream()
                .collect(Collectors.toMap(entry -> entry.getUser().getId(), Function.identity(), (entry, duplicate) -> entry));

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        List<TimeEntrySummaryDTO> summaries = usersPage.stream().map(user -> {
            List<TimeEntry> userEntries = entriesByUser.getOrDefault(user.getId(), Collections.emptyList());
            long grossDurationSeconds = userEntries.stream().mapToLong(TimeEntry::getDuration).sum();
            long breakDurationSeconds = computeBreakSeconds(userEntries, user);
            long netDurationSeconds = Math.max(0, grossDurationSeconds - breakDurationSeconds);
            long grossToday = userEntries.stream()
                    .filter(te -> te.getStartedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().equals(today))
                    .mapToLong(TimeEntry::getDuration)
                    .sum();
            long breakToday = computeBreakSecondsForDate(userEntries, user, today);
            long netToday = Math.max(0, grossToday - breakToday);
            TimeEntrySummaryDTO.TimeEntrySummaryDTOBuilder builder = TimeEntrySummaryDTO.builder()
                    .userId(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .jobTitle(user.getJobTitle())
                    .totalDurationSeconds(netDurationSeconds)
                    .grossDurationSeconds(grossDurationSeconds)
                    .breakDurationSeconds(breakDurationSeconds)
                    .todayDurationSeconds(netToday);

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

        return TimeEntrySummaryPageDTO.builder()
                .items(summaries)
                .totalElements(usersPage.getTotalElements())
                .build();
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

    /**
     * Rundet auf 5 Minuten. Wenn floorOnly=true, wird immer abgerundet (keine Zukunftszeit).
     */
    private Date roundToFiveMinutes(Date date, boolean floorOnly) {
        if (date == null) return null;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(date.getTime());
        long remainder = seconds % 300;
        long adjusted;
        if (floorOnly) {
            adjusted = seconds - remainder;
        } else {
            adjusted = remainder >= 150 ? seconds + (300 - remainder) : seconds - remainder;
        }
        return new Date(TimeUnit.SECONDS.toMillis(adjusted));
    }

    private long computeBreakSeconds(Collection<TimeEntry> entries, OwnUser user) {
        if (entries == null || entries.isEmpty()) return 0L;
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

        long totalBreakSeconds = 0L;
        for (Map.Entry<LocalDate, List<TimeEntry>> dayEntry : byDate.entrySet()) {
            LocalDate date = dayEntry.getKey();
            List<TimeEntry> dayEntries = dayEntry.getValue();
            long totalSeconds = dayEntries.stream().mapToLong(TimeEntry::getDuration).sum();
            if (totalSeconds <= 6 * 3600) continue;
            int breakMinutes = breakByDay.getOrDefault(date.getDayOfWeek(), 0);
            long breakSeconds = Math.max(0, breakMinutes) * 60L;
            if (totalSeconds > 9 * 3600) {
                breakSeconds += 15 * 60L;
            }
            totalBreakSeconds += Math.min(breakSeconds, totalSeconds);
        }
        return totalBreakSeconds;
    }

    private long computeBreakSecondsForDate(Collection<TimeEntry> entries, OwnUser user, LocalDate targetDate) {
        if (entries == null || entries.isEmpty()) return 0L;
        if (user.getWorkingHours() != null) {
            user.getWorkingHours().size();
        }
        Map<DayOfWeek, Integer> breakByDay = new ConcurrentHashMap<>();
        if (user.getWorkingHours() != null) {
            for (UserWorkingHour wh : user.getWorkingHours()) {
                breakByDay.put(wh.getDayOfWeek(), wh.getBreakMinutes() == null ? 0 : wh.getBreakMinutes());
            }
        }
        List<TimeEntry> dayEntries = entries.stream()
                .filter(te -> te.getStartedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().equals(targetDate))
                .collect(Collectors.toList());
        long totalSeconds = dayEntries.stream().mapToLong(TimeEntry::getDuration).sum();
        if (totalSeconds <= 6 * 3600) return 0L;
        int breakMinutes = breakByDay.getOrDefault(targetDate.getDayOfWeek(), 0);
        long breakSeconds = Math.max(0, breakMinutes) * 60L;
        if (totalSeconds > 9 * 3600) {
            breakSeconds += 15 * 60L;
        }
        return Math.min(breakSeconds, totalSeconds);
    }
}
