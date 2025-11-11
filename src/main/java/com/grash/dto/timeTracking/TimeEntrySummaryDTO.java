package com.grash.dto.timeTracking;

import com.grash.dto.FileShowDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeEntrySummaryDTO {
    private Long userId;
    private String firstName;
    private String lastName;
    private String jobTitle;
    private FileShowDTO image;
    private boolean running;
    private Date runningSince;
    private Date lastEntryStart;
    private Date lastEntryEnd;
    private long totalDurationSeconds;
}
