package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class IpGeoInfoVO {
    private String ip;
    private String source;
    private String locationSummary;
    private String continent;
    private String country;
    private String region;
    private String city;
    private String postalCode;
    private String timezoneId;
    private String timezoneUtc;
    private String timezoneAbbr;
    private Double latitude;
    private Double longitude;
    private String isp;
    private String organization;
    private Long asn;
    private Boolean privateNetwork;
    private Boolean proxy;
    private Boolean vpn;
    private Boolean tor;
    private Boolean hosting;
    private String detailMessage;
}
