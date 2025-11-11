package com.grash.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grash.model.abstracts.Time;
import com.grash.model.enums.TimeStatus;
import com.grash.utils.Helper;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Data
@NoArgsConstructor
public class TimeEntry extends Time {

    @ManyToOne
    @NotNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private OwnUser user;

    @Temporal(TemporalType.TIMESTAMP)
    @NotNull
    private Date startedAt;

    @Enumerated(EnumType.STRING)
    @NotNull
    private TimeStatus status = TimeStatus.STOPPED;

    public Date getEndedAt() {
        return getDuration() == 0 ? null : Helper.addSeconds(startedAt, Math.toIntExact(this.getDuration()));
    }

    public boolean isRunning() {
        return TimeStatus.RUNNING.equals(status);
    }
}
