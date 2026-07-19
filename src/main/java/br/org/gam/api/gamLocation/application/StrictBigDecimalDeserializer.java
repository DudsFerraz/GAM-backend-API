package br.org.gam.api.gamLocation.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.IOException;
import java.math.BigDecimal;

public final class StrictBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {
    @Override
    public BigDecimal deserialize(JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context)
            throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT
                && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT) {
            throw JsonMappingException.from(parser, "Coordinate values must be JSON numbers, not strings.");
        }
        return parser.getDecimalValue();
    }
}
