package com.grash.service;

import com.grash.model.OwnUser;
import com.grash.model.TimeEntry;
import com.grash.model.UserWorkingHour;
import com.grash.model.enums.TimeStatus;
import com.grash.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeEntryExportService {

    private final TimeEntryService timeEntryService;
    private final MessageSource messageSource;

    public ResponseEntity<byte[]> exportEntries(OwnUser user, Date from, Date to) {
        Collection<TimeEntry> entries = timeEntryService.findEntriesForUser(user.getId(), from, to);
        // Nur abgeschlossene Einträge exportieren
        entries = entries.stream()
                .filter(te -> te.getStatus() == TimeStatus.STOPPED)
                .collect(Collectors.toList());
        byte[] fileBytes = buildWorkbook(user, entries, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", buildFileName(user, from, to));
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    private byte[] buildWorkbook(OwnUser user, Collection<TimeEntry> entries, Date from, Date to) {
        Locale locale = Helper.getLocale(user);
        // Feste deutsche Labels gemäß gewünschtem Layout
        String userLabel = "Mitarbeiter";
        String companyLabel = "Firma";
        String rangeLabel = "Zeitraum";
        String generatedLabel = "Export erstellt am";
        String headerUser = "Benutzer";
        String headerStart = "Beginn";
        String headerEnd = "Ende";
        String headerDuration = "Dauer";
        String headerWorkHours = "Arbeitsstunden";
        String headerExpected = "Soll Std";
        String headerPlus = "Plus Std";
        String totalLabel = "Gesamtzeit";

        DateTimeFormatter headerDateFormatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(locale)
                        .withZone(ZoneId.systemDefault());

        DateTimeFormatter entryFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

        Map<TimeEntry, Long> adjustedDurations = adjustDurationsWithBreaks(entries, user);
        Map<DayOfWeek, Long> expectedByDay = expectedSecondsByDay(user);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Zeiterfassung");
            int rowIdx = 0;

            rowIdx = writeRow(sheet, rowIdx, userLabel, user.getFullName());
            rowIdx = writeRow(sheet, rowIdx, companyLabel, user.getCompany().getName());
            rowIdx = writeRow(sheet, rowIdx, rangeLabel,
                    entryFormatter.format(from.toInstant()),
                    entryFormatter.format(to.toInstant()));
            rowIdx = writeRow(sheet, rowIdx, generatedLabel, headerDateFormatter.format(Instant.now()));
            rowIdx++; // Leerzeile

            writeRow(sheet, rowIdx++, "Benutzer", "Beginn", "Ende", "Dauer (Std:Min)",
                    "Datum", "Beginn", "Datum", "Ende", headerDuration, headerWorkHours, headerExpected, headerPlus);

            long totalSeconds = 0;

            List<TimeEntry> sortedEntries = entries.stream()
                    .sorted((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                    .collect(Collectors.toList());
            for (TimeEntry entry : sortedEntries) {
                Date start = entry.getStartedAt();
                Date end = entry.getEndedAt();
                long rawDuration = entry.getDuration();
                long netDuration = adjustedDurations.getOrDefault(entry, rawDuration);
                totalSeconds += netDuration;
                String startDate = start != null ? DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(start.toInstant()) : "";
                String startTime = start != null ? DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(start.toInstant()) : "";
                String endDate = end != null ? DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(end.toInstant()) : startDate;
                String endTime = end != null ? DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(end.toInstant()) : "";
                String durationStr = formatDuration(rawDuration, locale);
                String netDurationStr = formatDuration(netDuration, locale);
                DayOfWeek dow = start != null
                        ? start.toInstant().atZone(ZoneId.systemDefault()).getDayOfWeek()
                        : null;
                long expectedSeconds = dow != null
                        ? expectedByDay.getOrDefault(dow, 8 * 3600L)
                        : 8 * 3600L;
                String workHours = netDurationStr;
                String expectedStr = formatDuration(expectedSeconds, locale);
                String plus = formatSignedDuration(netDuration - expectedSeconds, locale);

                writeRow(sheet, rowIdx++,
                        user.getFullName(),
                        start != null ? entryFormatter.format(start.toInstant()) : "",
                        end != null ? entryFormatter.format(end.toInstant()) : "",
                        durationStr,
                        startDate,
                        "T " + startTime,
                        endDate,
                        "T " + endTime,
                        durationStr,
                        workHours,
                        expectedStr,
                        plus
                );
            }

            rowIdx++;
            writeRow(sheet, rowIdx, totalLabel, formatDuration(totalSeconds, locale));

            for (int i = 0; i < 12; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy")
                .withZone(ZoneId.systemDefault());
        String monthYear = formatter.format(from.toInstant());
        return String.format("%s_%s_%s_Zeiterfassung.xlsx", user.getFirstName(), user.getLastName(), monthYear);
    }

    private String formatSignedDuration(long seconds, Locale locale) {
        String sign = seconds >= 0 ? "" : "-";
        long abs = Math.abs(seconds);
        long hours = TimeUnit.SECONDS.toHours(abs);
        long minutes = TimeUnit.SECONDS.toMinutes(abs) % 60;
        return String.format(locale, "%s%d:%02d", sign, hours, minutes);
    }

    private int writeRow(Sheet sheet, int rowIdx, Object... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object val = values[i];
            if (val instanceof Number) {
                cell.setCellValue(((Number) val).doubleValue());
            } else {
                cell.setCellValue(val == null ? "" : val.toString());
            }
        }
        return rowIdx + 1;
    }

    /**
     * Wendet tägliche Pausen (ab 6h, plus 15min ab 9h) auf die erste abgeschlossene Buchung des Tages an,
     * analog zur Anzeige. Gibt die angepassten Dauern je Eintrag zurück.
     */
    private Map<TimeEntry, Long> adjustDurationsWithBreaks(Collection<TimeEntry> entries, OwnUser user) {
        if (entries == null || entries.isEmpty()) return Collections.emptyMap();
        if (user.getWorkingHours() != null) {
            user.getWorkingHours().size();
        }
        Map<DayOfWeek, Integer> breakByDay = new ConcurrentHashMap<>();
        if (user.getWorkingHours() != null) {
            for (UserWorkingHour wh : user.getWorkingHours()) {
                breakByDay.put(wh.getDayOfWeek(), wh.getBreakMinutes() == null ? 0 : wh.getBreakMinutes());
            }
        }
        Map<TimeEntry, Long> result = new ConcurrentHashMap<>();
        Map<java.time.LocalDate, List<TimeEntry>> byDate = entries.stream()
                .collect(Collectors.groupingBy(te -> te.getStartedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()));
        for (Map.Entry<java.time.LocalDate, List<TimeEntry>> dayEntry : byDate.entrySet()) {
            java.time.LocalDate date = dayEntry.getKey();
            List<TimeEntry> dayEntries = dayEntry.getValue();
            long totalSeconds = dayEntries.stream().mapToLong(TimeEntry::getDuration).sum();
            int breakMinutes = breakByDay.getOrDefault(date.getDayOfWeek(), 0);
            long breakSeconds = Math.max(0, breakMinutes) * 60L;
            if (totalSeconds > 9 * 3600) {
                breakSeconds += 15 * 60L;
            }
            if (totalSeconds <= 6 * 3600 || breakSeconds <= 0) {
                dayEntries.forEach(te -> result.put(te, te.getDuration()));
                continue;
            }
            long remainingBreak = Math.min(breakSeconds, totalSeconds);
            List<TimeEntry> sorted = dayEntries.stream()
                    .sorted((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                    .collect(Collectors.toList());
            for (TimeEntry te : sorted) {
                if (te.getDuration() > 0 && te.getStatus() == TimeStatus.STOPPED && remainingBreak > 0) {
                    long applied = Math.min(remainingBreak, te.getDuration());
                    result.put(te, te.getDuration() - applied);
                    remainingBreak -= applied;
                } else {
                    result.put(te, te.getDuration());
                }
            }
        }
        return result;
    }

    private Map<DayOfWeek, Long> expectedSecondsByDay(OwnUser user) {
        Map<DayOfWeek, Long> expected = new ConcurrentHashMap<>();
        if (user.getWorkingHours() != null) {
            user.getWorkingHours().size();
            for (UserWorkingHour wh : user.getWorkingHours()) {
                if (wh.getStartTime() != null && wh.getEndTime() != null) {
                    LocalTime start = wh.getStartTime();
                    LocalTime end = wh.getEndTime();
                    long seconds = java.time.Duration.between(start, end).getSeconds();
                    long breakSeconds = (wh.getBreakMinutes() == null ? 0 : wh.getBreakMinutes()) * 60L;
                    expected.put(wh.getDayOfWeek(), Math.max(0, seconds - breakSeconds));
                }
            }
        }
        return expected;
    }
}
