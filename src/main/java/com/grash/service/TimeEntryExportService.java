package com.grash.service;

import com.grash.model.OwnUser;
import com.grash.model.TimeEntry;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TimeEntryExportService {

    private final TimeEntryService timeEntryService;
    private final MessageSource messageSource;

    public ResponseEntity<byte[]> exportEntries(OwnUser user, Date from, Date to) {
        Collection<TimeEntry> entries = timeEntryService.findEntriesForUser(user.getId(), from, to);
        byte[] csv = buildCsv(user, entries, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", buildFileName(user, from, to));
        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }

    private byte[] buildCsv(OwnUser user, Collection<TimeEntry> entries, Date from, Date to) {
        Locale locale = Helper.getLocale(user);
        String columnUser = messageSource.getMessage("time_tracking.export.user", null, locale);
        String columnStart = messageSource.getMessage("time_tracking.export.started_at", null, locale);
        String columnEnd = messageSource.getMessage("time_tracking.export.ended_at", null, locale);
        String columnDuration = messageSource.getMessage("time_tracking.export.duration", null, locale);
        String totalLabel = messageSource.getMessage("time_tracking.export.total", null, locale);
        String userLabel = messageSource.getMessage("time_tracking.export.user_label", null, locale);
        String companyLabel = messageSource.getMessage("time_tracking.export.company_label", null, locale);
        String generatedLabel = messageSource.getMessage("time_tracking.export.generated_at", null, locale);
        String rangeLabel = messageSource.getMessage("time_tracking.export.range", null, locale);

        DateTimeFormatter headerDateFormatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(locale)
                        .withZone(ZoneId.systemDefault());

        DateTimeFormatter entryFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            printer.printRecord(userLabel, user.getFullName());
            printer.printRecord(companyLabel, user.getCompany().getName());
            printer.printRecord(rangeLabel,
                    entryFormatter.format(from.toInstant()),
                    entryFormatter.format(to.toInstant()));
            printer.printRecord(generatedLabel, headerDateFormatter.format(Instant.now()));
            printer.printRecord();
            printer.printRecord(columnUser, columnStart, columnEnd, columnDuration);

            long totalSeconds = 0;

            for (TimeEntry entry : entries) {
                Date startedAt = entry.getStartedAt();
                Date endedAt = entry.getEndedAt();
                long duration = entry.getDuration();
                totalSeconds += duration;
                printer.printRecord(
                        user.getFullName(),
                        startedAt != null ? entryFormatter.format(startedAt.toInstant()) : "",
                        endedAt != null ? entryFormatter.format(endedAt.toInstant()) : "",
                        formatDuration(duration, locale)
                );
            }

            printer.printRecord();
            printer.printRecord(totalLabel, formatDuration(totalSeconds, locale));

            printer.flush();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Unable to export time entries", ex);
        }
    }

    private String formatDuration(long durationSeconds, Locale locale) {
        long hours = TimeUnit.SECONDS.toHours(durationSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(durationSeconds) % 60;
        return String.format(locale, "%d:%02d", hours, minutes);
    }

    private String buildFileName(OwnUser user, Date from, Date to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM")
                .withZone(ZoneId.systemDefault());
        String month = formatter.format(from.toInstant());
        return String.format("time-tracking-%s-%s.csv", user.getId(), month);
    }
}
