package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDate;

/** Fineract writes dates as {@code [2026, 11, 23]}, or as text in some answers; both read as a {@link LocalDate}. */
public final class FineractDates extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.START_ARRAY) {
            int[] parts = parser.readValueAs(int[].class);
            return parts.length >= 3 ? LocalDate.of(parts[0], parts[1], parts[2]) : null;
        }
        String text = parser.getValueAsString();
        return text == null || text.isBlank() ? null : LocalDate.parse(text.substring(0, 10));
    }
}
