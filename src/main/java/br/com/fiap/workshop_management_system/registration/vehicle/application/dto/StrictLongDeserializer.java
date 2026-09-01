package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public class StrictLongDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        JsonNode value = context.readTree(parser);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw InvalidFormatException.from(
                    parser,
                    "A quilometragem deve ser um número inteiro válido",
                    value,
                    Long.class
            );
        }
        return value.longValue();
    }

    @Override
    public Long getNullValue(DeserializationContext context) {
        return null;
    }
}
