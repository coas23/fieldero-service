package com.grash.dto;

import lombok.Data;

@Data
public class UserSchedulingLocationDTO {
    private String address;
    private String postalCode;
    private String city;
    private String country;
    private Double latitude;
    private Double longitude;
}
