package com.grash.dto.timeTracking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeEntrySummaryPageDTO {
    private List<TimeEntrySummaryDTO> items;
    private long totalElements;
}
