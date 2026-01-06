package com.grash.controller;

import com.grash.exception.CustomException;
import com.grash.model.Customer;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/lexware")
@Api(tags = "lexware")
@RequiredArgsConstructor
public class LexwareController {

    private final IntegrationSettingsService integrationSettingsService;
    private final UserService userService;

    @PostMapping("/contacts/sync")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    @ApiResponses(value = {//
            @ApiResponse(code = 500, message = "Something went wrong"), //
            @ApiResponse(code = 403, message = "Access denied"), //
            @ApiResponse(code = 404, message = "IntegrationSettings not found")})
    public List<Customer> syncContacts(HttpServletRequest req) {
        OwnUser user = userService.whoami(req);
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.VENDORS_AND_CUSTOMERS)) {
            throw new CustomException("Forbidden", HttpStatus.FORBIDDEN);
        }
        return integrationSettingsService.syncLexwareCustomers(user.getCompany());
    }
}
