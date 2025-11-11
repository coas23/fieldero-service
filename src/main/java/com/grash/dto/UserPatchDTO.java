package com.grash.dto;

import com.grash.model.File;
import com.grash.model.Location;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UserPatchDTO {

    private String firstName;

    private String lastName;

    private long rate;

    private String phone;

    private String jobTitle;

    private Location location;

    private File image;

    private String newPassword;

    private List<UserWorkingHourDTO> workingHours;

    private Long supervisorId;

    private Boolean supervisorIdSpecified;

    private UserSchedulingLocationDTO schedulingLocation;
}
