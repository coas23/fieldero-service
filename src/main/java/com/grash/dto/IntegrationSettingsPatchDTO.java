package com.grash.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationSettingsPatchDTO {
    @NotBlank
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String lexwareSecret;
}
