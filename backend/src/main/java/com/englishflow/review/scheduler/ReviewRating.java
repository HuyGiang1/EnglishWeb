package com.englishflow.review.scheduler;

import java.util.Locale;

public enum ReviewRating {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int grade;

    ReviewRating(int grade) {
        this.grade = grade;
    }

    public int getGrade() {
        return grade;
    }

    public static ReviewRating fromGrade(int grade) {
        for (ReviewRating r : values()) {
            if (r.grade == grade) {
                return r;
            }
        }
        throw new IllegalArgumentException("Invalid grade: " + grade + ". Expected 1..4");
    }

    public static ReviewRating fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Rating cannot be null");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
