package com.grash.mapper;

import com.grash.dto.*;
import com.grash.model.OwnUser;
import com.grash.model.UiConfiguration;
import com.grash.model.UserWorkingHour;
import com.grash.service.UiConfigurationService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {SuperAccountRelationMapper.class, FileMapper.class})
public abstract class UserMapper {
    @Mappings({
            @Mapping(target = "workingHours", ignore = true),
            @Mapping(target = "supervisor", ignore = true),
            @Mapping(target = "schedulingAddress", ignore = true),
            @Mapping(target = "schedulingPostalCode", ignore = true),
            @Mapping(target = "schedulingCity", ignore = true),
            @Mapping(target = "schedulingCountry", ignore = true),
            @Mapping(target = "schedulingLatitude", ignore = true),
            @Mapping(target = "schedulingLongitude", ignore = true)
    })
    public abstract OwnUser updateUser(@MappingTarget OwnUser entity, UserPatchDTO dto);

    @Lazy
    @Autowired
    private UiConfigurationService uiConfigurationService;

    @Mappings({@Mapping(source = "company.id", target = "companyId"),
            @Mapping(source = "company.companySettings.id", target = "companySettingsId"),
            @Mapping(source = "userSettings.id", target = "userSettingsId"),
            @Mapping(target = "workingHours", expression = "java(toWorkingHourDtoList(model.getWorkingHours()))"),
            @Mapping(target = "supervisor", expression = "java(model.getSupervisor() != null ? toMiniDto(model.getSupervisor()) : null)"),
            @Mapping(target = "schedulingLocation", expression = "java(toSchedulingLocation(model))")})
    @Mapping(source = "company.companySettings.uiConfiguration", target = "uiConfiguration")
    public abstract UserResponseDTO toResponseDto(OwnUser model);

    @AfterMapping
    protected UserResponseDTO toResponseDto(OwnUser model, @MappingTarget UserResponseDTO target) {
        if (target.getUiConfiguration() == null) {
            UiConfiguration uiConfiguration = new UiConfiguration();
            uiConfiguration.setCompanySettings(model.getCompany().getCompanySettings());
            target.setUiConfiguration(uiConfigurationService.create(uiConfiguration));
        }
        return target;
    }

    protected List<UserWorkingHourDTO> toWorkingHourDtoList(List<UserWorkingHour> workingHours) {
        if (workingHours == null) {
            return new ArrayList<>();
        }
        return workingHours.stream()
                .filter(Objects::nonNull)
                .map(this::toWorkingHourDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    protected UserWorkingHourDTO toWorkingHourDto(UserWorkingHour workingHour) {
        if (workingHour == null) {
            return null;
        }
        UserWorkingHourDTO dto = new UserWorkingHourDTO();
        dto.setDayOfWeek(workingHour.getDayOfWeek());
        dto.setStartTime(workingHour.getStartTime());
        dto.setEndTime(workingHour.getEndTime());
        dto.setBreakMinutes(workingHour.getBreakMinutes());
        return dto;
    }

    protected UserSchedulingLocationDTO toSchedulingLocation(OwnUser user) {
        if (user == null) {
            return null;
        }
        if (user.getSchedulingAddress() == null && user.getSchedulingPostalCode() == null &&
                user.getSchedulingCity() == null && user.getSchedulingCountry() == null &&
                user.getSchedulingLatitude() == null && user.getSchedulingLongitude() == null) {
            return null;
        }
        UserSchedulingLocationDTO dto = new UserSchedulingLocationDTO();
        dto.setAddress(user.getSchedulingAddress());
        dto.setPostalCode(user.getSchedulingPostalCode());
        dto.setCity(user.getSchedulingCity());
        dto.setCountry(user.getSchedulingCountry());
        dto.setLatitude(user.getSchedulingLatitude());
        dto.setLongitude(user.getSchedulingLongitude());
        return dto;
    }

    @Mappings({})
    public abstract OwnUser toModel(UserSignupRequest dto);

    public abstract UserMiniDTO toMiniDto(OwnUser user);
}
