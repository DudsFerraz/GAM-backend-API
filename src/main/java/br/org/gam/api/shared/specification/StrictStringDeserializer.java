package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

final class StrictStringDeserializer extends StdDeserializer<String> {

    StrictStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getText();
        }
        return (String) context.handleUnexpectedToken(
                String.class,
                parser.currentToken(),
                parser,
                "Expected a JSON string."
        );
    }

    @Override
    public String getNullValue(DeserializationContext context) {
        return null;
    }
}
