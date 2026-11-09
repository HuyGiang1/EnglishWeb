package com.englishflow.user;

import com.englishflow.user.exception.InvalidSettingsException;

public enum SupportedLocale {
    VIETNAMESE("vi-VN"), ENGLISH("en");

    private final String value;

    SupportedLocale(String value) {
        this.value = value;
    }

    public static String validate(String value) {
        for (SupportedLocale locale : values()) {
            if (locale.value.equals(value)) return value;
        }
        throw new InvalidSettingsException("Preferred locale must be vi-VN or en.");
    }
}
