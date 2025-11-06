package io.github.jav.exposerversdk.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Replacement enum that gracefully handles newer Expo error codes.
 */
public enum TicketError {
    DEVICENOTREGISTERED("DeviceNotRegistered"),
    INVALIDCREDENTIALS("InvalidCredentials"),
    MESSAGERATEEXCEEDED("MessageRateExceeded"),
    @JsonEnumDefaultValue
    UNKNOWN("Unknown");

    private final String error;

    TicketError(String error) {
        this.error = error;
    }

    @JsonValue
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return error;
    }

    @JsonCreator
    public static TicketError fromValue(String value) {
        if (value != null) {
            for (TicketError ticketError : values()) {
                if (ticketError.error.equalsIgnoreCase(value)) {
                    return ticketError;
                }
            }
        }
        return UNKNOWN;
    }
}
