package com.oneutility.cba.bankingapi.onus.dto;

/** Where the customer was, if the client has it. */
public record Location(Double latitude, Double longitude, Double locationAccuracy, String locationSource) {
}
