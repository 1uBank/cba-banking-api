package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;

/** Sends an amount as plain text ({@code "250000"}, never {@code 2.5E+5}), read with the request's locale. */
public final class PlainDecimal extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeString(value.toPlainString());
    }
}
