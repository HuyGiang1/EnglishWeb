// Deterministic API fixtures shared by component tests and isolated browser smoke.
export const word = { id: "fixture-id", term: "resilience", partOfSpeech: "noun", phoneticIpa: "/rɪˈzɪliəns/", vietnameseMeaning: "sự kiên cường", englishDefinition: "The ability to recover.", exampleSentence: "Her resilience is remarkable.", exampleTranslation: "Sự kiên cường của cô ấy thật đáng nể.", notes: "My private note", difficultyLevel: "B2", createdAt: "2026-11-07T08:00:00Z", updatedAt: "2026-11-07T08:00:00Z" };
export const collection = { id: "fixture-id", title: "My English words", description: "User-authored description", wordCount: 1, createdAt: word.createdAt, updatedAt: word.updatedAt };
export const lesson = { contentId: "lesson-id", title: "A morning walk", cefrLevel: "A1", topicTag: "Daily life", audioUrl: "/fixture-audio.wav", audioDurationSeconds: 5, isAudioAvailable: true, isCompleted: false, lastAccuracyScore: null, takeCount: 0, lastSelfRating: null, transcriptText: "I go for a walk every morning.", audioReadiness: "LOCAL_SPOKEN", isSpokenSpeech: true };
export const quiz = { id: "quiz-id", title: "Everyday English", cefrLevel: "A1", topicTag: "Daily life", description: "English learning material", questionCount: 2, attemptCount: 0, bestScore: null, latestScore: null };
export const questions = [{ questionId: "q1", prompt: "Choose the correct word.", questionType: "MULTIPLE_CHOICE", options: ["resilience", "fragility"], orderIndex: 0 }, { questionId: "q2", prompt: "Complete: I go for a ____.", questionType: "CLOZE", options: [], orderIndex: 1 }];
export function page(content) { return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }; }
export function fixtureApi() {
  const requests = [];
  let account = { id: "learner", email: "learner@example.com", displayName: "Learner", timezone: "UTC", preferredLocale: null, preferredAudioSpeed: 1, dailyGoalItems: 20 };
  const failures = new Map();
  const overrides = new Map();
  let vocabulary = [structuredClone(word)], sets = [structuredClone(collection)];
  function respond(url, options = {}) {
    const path = new URL(url, "http://localhost").pathname;
    const method = options.method ?? "GET";
    const payload = typeof options.body === "string" ? JSON.parse(options.body) : options.body;
    requests.push({ path, method, payload, headers: options.headers });
    if (failures.has(path)) return { status: 500, body: { code: failures.get(path), message: "RAW_PRIVATE_SERVER_EXCEPTION" } };
    if (overrides.has(path)) return { status: 200, body: overrides.get(path) };
    let body;
    if (path.endsWith("/folders") && method === "GET") return { status: 200, body: page([]) };
    if (path.endsWith("/auth/csrf")) return { status: 204, body: null };
    if (path.endsWith("/auth/me") || path.endsWith("/settings")) body = account;
    else if (path.endsWith("/settings/preferences")) { account = { ...account, ...payload }; body = account; }
    else if (path.endsWith("/vocabulary/bulk-import/preview")) body = { totalRows: 3, validRows: 1, duplicateRows: 1, errorRows: 1, errors: [{ rowNumber: 4, field: "term", reason: "RAW_PRIVATE_SERVER_EXCEPTION" }], previewRows: [{ ...word, rowNumber: 2, status: "VALID" }, { ...word, term: "existing", rowNumber: 3, status: "DUPLICATE_LIBRARY", validationMessage: "RAW_PRIVATE_SERVER_EXCEPTION" }, { ...word, term: "invalid", rowNumber: 4, status: "INVALID", validationMessage: "RAW_PRIVATE_SERVER_EXCEPTION" }], previewSample: [] };
    else if (path.endsWith("/vocabulary/bulk-import/confirm")) body = { importedCount: 1, skippedCount: 1, rejectedCount: 0 };
    else if (path.endsWith("/duplicates/check")) body = { exists: false, existingEntries: [] };
    else if (/\/vocabulary\/[^/]+\/collections$/.test(path)) body = [];
    else if (/\/collections\/[^/]+\/vocabularies$/.test(path)) body = method === "POST" ? { addedCount: payload.vocabularyIds.length } : page(vocabulary);
    else if (path.endsWith("/collections")) { if (method === "POST") { sets.push({ ...collection, ...payload }); body = sets.at(-1); } else body = page(sets); }
    else if (/\/collections\/[^/]+$/.test(path)) body = { ...collection, ...payload };
    else if (path.endsWith("/vocabulary")) { if (method === "POST") { vocabulary.push({ ...word, ...payload }); body = vocabulary.at(-1); } else body = page(vocabulary); }
    else if (/\/vocabulary\/[^/]+$/.test(path)) body = { ...word, ...payload };
    else if (path.endsWith("/review/queue")) body = { totalDue: 1, items: [{ ...word, vocabularyId: word.id, srsItemId: "srs-id", intervalDays: 3, easeFactor: 2.5 }] };
    else if (path.endsWith("/review/grade")) body = { ...payload, newIntervalDays: 6, newEaseFactor: 2.5, dueDate: "2026-11-15", nextReviewLabel: "in 6 days" };
    else if (path.endsWith("/dictation")) body = [lesson];
    else if (/\/dictation\/[^/]+\/submit$/.test(path)) body = { ...payload, canonicalTranscript: lesson.transcriptText, contentId: lesson.contentId, accuracyScore: 80, wordCount: 8, errorCount: 1, isPassed: true, diffTokens: [{ type: "EQUAL", text: "I", expectedWord: null }, { type: "MISSING", text: "", expectedWord: "morning" }] };
    else if (/\/dictation\/[^/]+$/.test(path)) body = lesson;
    else if (path.endsWith("/shadowing")) body = [lesson];
    else if (/\/shadowing\/[^/]+\/complete$/.test(path)) body = { ...payload, contentId: lesson.contentId, hasRecordingSaved: false, verifiedActivityCreated: true, completedAt: word.createdAt };
    else if (/\/shadowing\/[^/]+$/.test(path)) body = lesson;
    else if (path.endsWith("/quizzes/history")) body = [];
    else if (path.endsWith("/quizzes")) body = [quiz];
    else if (/\/quizzes\/[^/]+\/start$/.test(path)) body = { quizAttemptId: "quiz-attempt", attemptId: payload.attemptId, contentId: quiz.id, title: quiz.title, totalQuestions: 2, questions, savedAnswers: [] };
    else if (path.endsWith("/answers")) body = { questionId: payload.questionId, saved: true };
    else if (path.endsWith("/complete")) body = { quizAttemptId: "quiz-attempt", attemptId: payload.attemptId, contentId: quiz.id, title: quiz.title, scorePercentage: 50, correctCount: 1, incorrectCount: 0, unansweredCount: 1, totalQuestions: 2, verifiedActivityCreated: true, completedAt: word.createdAt, questions: questions.map((q, index) => ({ ...q, selectedAnswer: index ? "" : "resilience", canonicalAnswer: index ? "walk" : "resilience", isCorrect: !index, explanation: "This is the English explanation." })) };
    else throw new Error(`Unmocked API ${method} ${path}`);
    return { status: 200, body };
  }
  return { requests, failures, overrides, respond };
}
