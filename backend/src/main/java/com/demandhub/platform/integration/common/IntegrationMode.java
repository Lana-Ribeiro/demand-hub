package com.demandhub.platform.integration.common;

import java.util.Locale;

/** REAL: API externa verdadeira. MOCK: simulação local explícita (nunca apresentada como real). DISABLED: não integrar. */
public enum IntegrationMode {
    REAL, MOCK, DISABLED;

    public static IntegrationMode from(String value) {
        if (value == null || value.isBlank()) {
            return DISABLED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "real" -> REAL;
            case "mock" -> MOCK;
            default -> DISABLED;
        };
    }
}
