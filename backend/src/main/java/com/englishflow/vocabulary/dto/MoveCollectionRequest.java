package com.englishflow.vocabulary.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record MoveCollectionRequest(UUID folderId) {
    // A JSON null explicitly unassigns; absent, blank, arrays and other types are invalid.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static MoveCollectionRequest fromJson(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("folderId"))
            throw new IllegalArgumentException("folderId must be supplied.");
        JsonNode value = body.get("folderId");
        if (value.isNull()) return new MoveCollectionRequest(null);
        if (value.isTextual()) {
            String text = value.textValue();
            UUID id = UUID.fromString(text);
            if (id.toString().equalsIgnoreCase(text)) return new MoveCollectionRequest(id);
        }
        throw new IllegalArgumentException("folderId must be a UUID or null.");
    }
}
