package com.englishflow.vocabulary.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.UUID;

// Field-scoped strict parsing: explicit JSON null is allowed, blank strings are not.
public class FolderIdDeserializer extends JsonDeserializer<UUID> {
    @Override
    public UUID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String text = parser.getText();
            try {
                UUID id = UUID.fromString(text);
                if (id.toString().equalsIgnoreCase(text)) return id;
            } catch (IllegalArgumentException ignored) { }
        }
        throw com.fasterxml.jackson.databind.JsonMappingException.from(parser, "folderId must be a UUID or null.");
    }
}
