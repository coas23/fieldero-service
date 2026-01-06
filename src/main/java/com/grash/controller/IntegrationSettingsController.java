package com.grash.controller;

import com.grash.dto.IntegrationSettingsPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.IntegrationSettings;
import com.grash.model.OwnUser;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.IntegrationSettingsService;
import com.grash.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("/integration-settings")
@Api(tags = "integrationSettings")
@RequiredArgsConstructor
public class IntegrationSettingsController {

    private final IntegrationSettingsService integrationSettingsService;
    private final UserService userService;

    @GetMapping("")
    @PreAuthorize("permitAll()")
    @ApiResponses(value = {//
            @ApiResponse(code = 500, message = "Something went wrong"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "IntegrationSettings not found")})
    public IntegrationSettings getForCompany(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<IntegrationSettings> integrationSettings = integrationSettingsService.findByCompanySettings(user.getCompany().getCompanySettings().getId());
        if (integrationSettings.isPresent()) {
            return integrationSettings.get();
        } else throw new CustomException("IntegrationSettings not found", HttpStatus.NOT_FOUND);
    }

    @PatchMapping("/lexware-secret")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @ApiResponses(value = {//
            @ApiResponse(code = 500, message = "Something went wrong"), //
            @ApiResponse(code = 403, message = "Access denied"), //
            @ApiResponse(code = 404, message = "IntegrationSettings not found")})
    public IntegrationSettings updateLexwareSecret(@ApiParam("IntegrationSettingsPatchDTO") @Valid @RequestBody IntegrationSettingsPatchDTO integrationSettingsPatchDTO,
                                                   HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        Optional<IntegrationSettings> integrationSettings = integrationSettingsService.findByCompanySettings(user.getCompany().getCompanySettings().getId());
        if (integrationSettings.isPresent()) {
            IntegrationSettings savedIntegrationSettings = integrationSettings.get();
            if (user.getRole().getViewPermissions().contains(PermissionEntity.SETTINGS)) {
                return integrationSettingsService.updateLexwareSecret(savedIntegrationSettings.getId(), integrationSettingsPatchDTO);
            } else throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        } else throw new CustomException("IntegrationSettings not found", HttpStatus.NOT_FOUND);
    }

}
