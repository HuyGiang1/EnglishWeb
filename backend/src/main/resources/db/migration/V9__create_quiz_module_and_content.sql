-- ==============================================================================
-- Flyway Migration V9: Create Quiz Module, Attempts, and Starter Assessments
-- Milestone: M2-T05 Diagnostic Quiz Vertical Slice
-- Canonical reference: docs/architecture/DATABASE.md Section 3.7 & API_CONTRACT.md Section 8
-- ==============================================================================

-- 1. Table: quiz_questions (individual prompts, options JSONB, canonical answers, explanations)
CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL,
    question_type VARCHAR(32) NOT NULL, -- MULTIPLE_CHOICE, CLOZE
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quiz_q_type CHECK (question_type IN ('MULTIPLE_CHOICE', 'CLOZE'))
);

CREATE INDEX idx_quiz_questions_content ON quiz_questions (content_id, order_index);

-- 2. Table: quiz_attempts (user quiz sessions, score percentage, completion lifecycle)
CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES learning_contents(id) ON DELETE RESTRICT,
    attempt_id UUID NOT NULL,
    total_questions INT NOT NULL DEFAULT 0,
    correct_answers INT NOT NULL DEFAULT 0,
    score_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_quiz_user_attempt UNIQUE (user_id, attempt_id),
    CONSTRAINT chk_quiz_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT chk_quiz_score CHECK (score_percentage >= 0.00 AND score_percentage <= 100.00)
);

CREATE INDEX idx_quiz_attempts_user_date ON quiz_attempts (user_id, completed_at);
CREATE INDEX idx_quiz_attempts_user_content ON quiz_attempts (user_id, content_id);

-- 3. Table: quiz_answers (user question answers, evaluations, and immutable evaluation snapshots)
CREATE TABLE quiz_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
    user_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    prompt_snapshot TEXT,
    canonical_answer_snapshot TEXT,
    explanation_snapshot TEXT,
    options_snapshot JSONB,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_quiz_attempt_question UNIQUE (quiz_attempt_id, question_id)
);

CREATE INDEX idx_quiz_answers_attempt ON quiz_answers (quiz_attempt_id);

-- 4. Update check constraint on learning_activity_events to explicitly allow QUIZ_COMPLETION alongside QUIZ_QUESTION
ALTER TABLE learning_activity_events DROP CONSTRAINT IF EXISTS chk_activity_event_type;
ALTER TABLE learning_activity_events ADD CONSTRAINT chk_activity_event_type
    CHECK (event_type IN ('FLASHCARD_REVIEW', 'DICTATION_SENTENCE', 'SHADOWING_TAKE', 'QUIZ_QUESTION', 'QUIZ_COMPLETION'));

-- 5. Seed Authoritative Starter Diagnostic Quiz Packs into learning_contents
INSERT INTO learning_contents (
    id, content_type, title, cefr_level, topic_tag, transcript_text,
    source_attribution, is_published, created_at, updated_at
) VALUES
(
    'c1000000-0000-0000-0000-000000000001',
    'QUIZ',
    'Essential Workplace English & Core Grammar',
    'B1',
    'Professional Communication',
    'Diagnostic assessment evaluating core workplace vocabulary, verb tenses, prepositions, and contextual sentence completion.',
    'EnglishFlow Curated Curriculum',
    TRUE,
    NOW(),
    NOW()
),
(
    'c2000000-0000-0000-0000-000000000002',
    'QUIZ',
    'Advanced Lexical Nuances & Academic Expressions',
    'B2',
    'Academic & Critical Discourse',
    'Diagnostic assessment measuring precision in academic vocabulary, subtle connotations, discourse markers, and formal collocations.',
    'EnglishFlow Curated Curriculum',
    TRUE,
    NOW(),
    NOW()
);

-- 6. Seed Questions for Quiz Pack 1 (B1 Essential Workplace)
INSERT INTO quiz_questions (
    id, content_id, prompt, question_type, options, correct_answer, explanation, order_index, created_at
) VALUES
(
    'c1000000-0000-0000-0001-000000000001',
    'c1000000-0000-0000-0000-000000000001',
    'Select the most appropriate synonym for ''resilient'' in a business context:',
    'MULTIPLE_CHOICE',
    '["fragile", "adaptable", "rigid", "temporary"]'::jsonb,
    'adaptable',
    '''Resilient'' denotes the capability to withstand or recover quickly from difficult conditions; ''adaptable'' is the closest synonym in this context.',
    0,
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000002',
    'c1000000-0000-0000-0000-000000000001',
    'The project team decided to ___ the software deployment until next Tuesday.',
    'MULTIPLE_CHOICE',
    '["postpone", "cancel", "accelerate", "eliminate"]'::jsonb,
    'postpone',
    '''Postpone'' means to arrange for an event to take place at a later time than originally scheduled.',
    1,
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000003',
    'c1000000-0000-0000-0000-000000000001',
    'Identify the correct preposition: ''She has been working at the agency ___ five years.''',
    'MULTIPLE_CHOICE',
    '["since", "for", "during", "while"]'::jsonb,
    'for',
    '''For'' is used to denote a continuous duration or period of time (''five years''), whereas ''since'' marks a specific beginning point.',
    2,
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000004',
    'c1000000-0000-0000-0000-000000000001',
    'Complete the conditional: ''If we ___ (receive) the quarterly reports yesterday, we would have revised our budget proposal.''',
    'CLOZE',
    '[]'::jsonb,
    'had received',
    'Third conditional sentences require ''had + past participle'' in the condition clause to refer to an unreal past event.',
    3,
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000005',
    'c1000000-0000-0000-0000-000000000001',
    'Which phrase best expresses polite disagreement in a formal team discussion?',
    'MULTIPLE_CHOICE',
    '["You are completely mistaken", "I see your point, but I have a different perspective", "That idea has no merit whatsoever", "Never make that suggestion again"]'::jsonb,
    'I see your point, but I have a different perspective',
    'This expression professionally validates the speaker''s contribution before articulating a divergent viewpoint.',
    4,
    NOW()
),
(
    'c1000000-0000-0000-0001-000000000006',
    'c1000000-0000-0000-0000-000000000001',
    'Fill in the blank with the appropriate preposition: ''The department head requested everyone to submit their feedback ___ Friday at 5 PM at the latest.''',
    'CLOZE',
    '[]'::jsonb,
    'by',
    '''By'' establishes a strict deadline meaning ''not later than the specified time''.',
    5,
    NOW()
);

-- 7. Seed Questions for Quiz Pack 2 (B2 Advanced Lexical & Academic)
INSERT INTO quiz_questions (
    id, content_id, prompt, question_type, options, correct_answer, explanation, order_index, created_at
) VALUES
(
    'c2000000-0000-0000-0002-000000000001',
    'c2000000-0000-0000-0000-000000000002',
    'Choose the word that best completes the sentence: ''The empirical evidence appears to ___ the primary hypothesis proposed by the research group.''',
    'MULTIPLE_CHOICE',
    '["corroborate", "contradictory", "corroboration", "corroborating"]'::jsonb,
    'corroborate',
    '''Corroborate'' is the grammatically correct transitive verb meaning to confirm or substantiate through evidence.',
    0,
    NOW()
),
(
    'c2000000-0000-0000-0002-000000000002',
    'c2000000-0000-0000-0000-000000000002',
    'Select the definitive antonym for ''ambiguous'':',
    'MULTIPLE_CHOICE',
    '["equivocal", "lucid", "obscure", "vague"]'::jsonb,
    'lucid',
    '''Lucid'' signifies clear, intelligible, and easy to comprehend, standing as a direct antonym to ''ambiguous'' (open to multiple conflicting interpretations).',
    1,
    NOW()
),
(
    'c2000000-0000-0000-0002-000000000003',
    'c2000000-0000-0000-0000-000000000002',
    'Complete the formal collocation: ''The leadership council took immediate precautions to ___ the looming financial deficit.''',
    'MULTIPLE_CHOICE',
    '["avert", "deviate", "divert", "disperse"]'::jsonb,
    'avert',
    '''Avert'' is the standard academic and formal collocation with threats, crises, or deficits, meaning to prevent an undesirable outcome.',
    2,
    NOW()
),
(
    'c2000000-0000-0000-0002-000000000004',
    'c2000000-0000-0000-0000-000000000002',
    'Complete the sentence with an appropriate conjunctive adverb of concession: ''The experimental funding was severely reduced; ___, the researchers successfully concluded the clinical trial.''',
    'CLOZE',
    '[]'::jsonb,
    'nevertheless|nonetheless|however',
    '''Nevertheless'', ''nonetheless'', or ''however'' correctly marks a formal concession or contrast against a prior constraint.',
    3,
    NOW()
),
(
    'c2000000-0000-0000-0002-000000000005',
    'c2000000-0000-0000-0000-000000000002',
    'Which reporting verb conveys the most objective and academically neutral tone?',
    'MULTIPLE_CHOICE',
    '["argues", "rants", "claims without justification", "proclaims wildly"]'::jsonb,
    'argues',
    '''Argues'' is a recognized objective reporting verb in scholarly writing to introduce evidence-backed assertions.',
    4,
    NOW()
),
(
    'c2000000-0000-0000-0002-000000000006',
    'c2000000-0000-0000-0000-000000000002',
    'Fill in the blank with the appropriate preposition: ''The newly ratified international trade standards will come ___ effect on the first of January.''',
    'CLOZE',
    '[]'::jsonb,
    'into',
    'The fixed idiom is ''come into effect'', meaning to become operative, enforceable, or valid.',
    5,
    NOW()
);
