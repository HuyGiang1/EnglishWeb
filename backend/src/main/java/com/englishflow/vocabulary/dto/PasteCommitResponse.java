package com.englishflow.vocabulary.dto;

import java.util.UUID;

public record PasteCommitResponse(int createdCount, int linkedCount, int alreadyPresentCount,
                                  int duplicateCount, int excludedCount, int rejectedCount, UUID collectionId) {}
