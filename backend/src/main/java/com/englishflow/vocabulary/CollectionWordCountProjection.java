package com.englishflow.vocabulary;

import java.util.UUID;

public interface CollectionWordCountProjection {
    UUID getCollectionId();
    Long getWordCount();
}
