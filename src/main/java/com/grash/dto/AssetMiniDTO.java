package com.grash.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class AssetMiniDTO {
    private Long id;

    private String name;

    private String customId;

    private Long parentId;

    private String address;

    private String city;

    private String zip;

    private String country;

    private Double latitude;

    private Double longitude;

}
