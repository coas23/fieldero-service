package com.grash.controller;

import com.grash.dto.timeTracking.TimeEntryPatchDTO;
import com.grash.dto.timeTracking.TimeEntrySummaryDTO;
import com.grash.exception.CustomException;
import com.grash.model.OwnUser;
import com.grash.model.TimeEntry;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.PlanFeatures;
import com.grash.service.TimeEntryExportService;
import com.grash.service.TimeEntryService;
import com.grash.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Date;

@RestController
@RequestMapping("/time-entries")
@Api(tags = "timeEntries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryExportService exportService;
    private final UserService userService;

    @GetMapping("/summary")
    public Collection<TimeEntrySummaryDTO> getSummary(
            HttpServletRequest req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end) {
        OwnUser user = userService.whoami(req);
        ensureCanAccess(user);
        return timeEntryService.getSummary(user.getCompany().getId(), resolveStart(start), resolveEnd(end));
    }

    @GetMapping("/user/{id}")
    public Collection<TimeEntry> getEntriesForUser(
            @ApiParam("id") @PathVariable("id") Long userId,
            HttpServletRequest req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end) {
        OwnUser requester = userService.whoami(req);
        ensureCanAccess(requester);
        OwnUser target = timeEntryService.validateUserInCompany(userId, requester);
        return timeEntryService.findEntriesForUser(target.getId(), resolveStart(start), resolveEnd(end));
    }

    @GetMapping("/me")
    public Collection<TimeEntry> getMyEntries(
            HttpServletRequest req,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end) {
        OwnUser user = userService.whoami(req);
        ensureFeatureEnabled(user);
        return timeEntryService.findEntriesForUser(user.getId(), resolveStart(start), resolveEnd(end));
    }

    @GetMapping("/me/current")
    public TimeEntry getMyCurrentEntry(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        ensureFeatureEnabled(user);
        return timeEntryService.findRunningEntry(user.getId()).orElse(null);
    }

    @PostMapping("/control")
    public TimeEntry controlTimer(
            HttpServletRequest req,
            @RequestParam(defaultValue = "true") boolean start) {
        OwnUser user = userService.whoami(req);
        ensureFeatureEnabled(user);
        if (start) {
            return timeEntryService.startTimer(user);
        } else {
            TimeEntry runningEntry = timeEntryService.findRunningEntry(user.getId())
                    .orElseThrow(() -> new CustomException("No timer to stop", HttpStatus.NOT_FOUND));
            return timeEntryService.stopTimer(runningEntry);
        }
    }

    @PatchMapping("/{id}")
    public TimeEntry updateTimeEntry(
            @ApiParam("id") @PathVariable("id") Long id,
            @Valid @RequestBody TimeEntryPatchDTO dto,
            HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        ensureCanAccess(user);
        return timeEntryService.editEntry(id, dto.getStartedAt(), dto.getEndedAt(), user);
    }

    @GetMapping("/user/{id}/export")
    public ResponseEntity<byte[]> exportTimeEntries(
            @ApiParam("id") @PathVariable("id") Long userId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date to,
            HttpServletRequest req) {
        OwnUser requester = userService.whoami(req);
        ensureCanAccess(requester);
        OwnUser target = timeEntryService.validateUserInCompany(userId, requester);
        return exportService.exportEntries(target, from, to);
    }

    private Date resolveStart(Date start) {
        if (start != null) {
            return start;
        }
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime startOfDay = startOfWeek.atStartOfDay();
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Date resolveEnd(Date end) {
        if (end != null) {
            return end;
        }
        LocalDate today = LocalDate.now();
        LocalDate endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDateTime endOfDay = endOfWeek.atTime(LocalTime.MAX);
        return Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    private void ensureFeatureEnabled(OwnUser user) {
        if (user.getCompany().getSubscription() == null ||
                user.getCompany().getSubscription().getSubscriptionPlan() == null ||
                !user.getCompany().getSubscription().getSubscriptionPlan().getFeatures().contains(PlanFeatures.ADDITIONAL_TIME)) {
            throw new CustomException("Upgrade required for time tracking", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureCanAccess(OwnUser user) {
        ensureFeatureEnabled(user);
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.TIME_TRACKING)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
