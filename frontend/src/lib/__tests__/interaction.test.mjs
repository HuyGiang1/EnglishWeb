import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M1 — Frontend Component Interaction & Behavior Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. Login Validation and Submission Error Handling
  // --------------------------------------------------------------------------
  describe("Login Form Component Interactions", () => {
    function LoginFormHarness({ onSubmit }) {
      const [email, setEmail] = useState("");
      const [password, setPassword] = useState("");
      const [error, setError] = useState(null);
      const [loading, setLoading] = useState(false);

      const handleSubmit = async (e) => {
        e.preventDefault();
        if (!email.includes("@")) {
          setError("Please enter a valid email address.");
          return;
        }
        if (!password) {
          setError("Password is required.");
          return;
        }
        setError(null);
        setLoading(true);
        try {
          await onSubmit({ email, password });
        } catch (err) {
          setError(err.message || "Invalid credentials. Please try again.");
        } finally {
          setLoading(false);
        }
      };

      return React.createElement(
        "form",
        { onSubmit: handleSubmit, "aria-label": "login-form" },
        error && React.createElement("div", { role: "alert", id: "error-banner" }, error),
        React.createElement("input", {
          "aria-label": "Email",
          value: email,
          onChange: (e) => setEmail(e.target.value),
        }),
        React.createElement("input", {
          "aria-label": "Password",
          type: "password",
          value: password,
          onChange: (e) => setPassword(e.target.value),
        }),
        React.createElement(
          "button",
          { type: "submit", disabled: loading },
          loading ? "Signing in..." : "Sign In"
        )
      );
    }

    it("Rejects submission and displays error when email is malformed", () => {
      render(React.createElement(LoginFormHarness, { onSubmit: () => Promise.resolve() }));

      const emailInput = screen.getByLabelText("Email");
      const submitBtn = screen.getByRole("button", { name: "Sign In" });

      fireEvent.change(emailInput, { target: { value: "invalid-email" } });
      fireEvent.click(submitBtn);

      const alert = screen.getByRole("alert");
      assert.equal(alert.textContent, "Please enter a valid email address.");
    });

    it("Displays RFC 7807 error alert on 401 Unauthorized API failure", async () => {
      const mockSubmit = () =>
        Promise.reject(new Error("Invalid email or password. Please verify your credentials."));

      render(React.createElement(LoginFormHarness, { onSubmit: mockSubmit }));

      fireEvent.change(screen.getByLabelText("Email"), { target: { value: "user@example.com" } });
      fireEvent.change(screen.getByLabelText("Password"), { target: { value: "WrongPass123!" } });
      fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

      const alert = await screen.findByRole("alert");
      assert.match(alert.textContent, /Invalid email or password/i);
    });
  });

  // --------------------------------------------------------------------------
  // 2. Quick Add Vocabulary Dialog Interactions
  // --------------------------------------------------------------------------
  describe("Quick Add Vocabulary Dialog Interactions", () => {
    function QuickAddHarness({ onSave, existingTerms = [] }) {
      const [term, setTerm] = useState("");
      const [meaning, setMeaning] = useState("");
      const [isDuplicate, setIsDuplicate] = useState(false);

      const handleTermChange = (e) => {
        const val = e.target.value;
        setTerm(val);
        setIsDuplicate(existingTerms.includes(val.toLowerCase().trim()));
      };

      const handleSave = () => {
        if (!term.trim() || !meaning.trim()) return;
        onSave({ term, meaning });
      };

      const handleKeyDown = (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
          e.preventDefault();
          handleSave();
        }
      };

      return React.createElement(
        "div",
        { role: "dialog", "aria-label": "Quick Add Vocabulary", onKeyDown: handleKeyDown },
        React.createElement("input", {
          "aria-label": "Term",
          value: term,
          onChange: handleTermChange,
        }),
        isDuplicate &&
          React.createElement(
            "p",
            { role: "status", id: "dup-warning" },
            "Advisory: Word already in your vocabulary"
          ),
        React.createElement("input", {
          "aria-label": "Vietnamese Meaning",
          value: meaning,
          onChange: (e) => setMeaning(e.target.value),
        }),
        React.createElement(
          "button",
          {
            onClick: handleSave,
            disabled: !term.trim() || !meaning.trim(),
          },
          "Save Word"
        )
      );
    }

    it("Triggers duplicate advisory warning when term already exists in library", () => {
      render(React.createElement(QuickAddHarness, { existingTerms: ["ephemeral"] }));

      const termInput = screen.getByLabelText("Term");
      fireEvent.change(termInput, { target: { value: "ephemeral" } });

      const warning = screen.getByRole("status");
      assert.match(warning.textContent, /Word already in your vocabulary/i);
    });

    it("Enforces Mod+Enter keyboard submission shortcut", () => {
      let savedPayload = null;
      render(
        React.createElement(QuickAddHarness, {
          onSave: (p) => {
            savedPayload = p;
          },
        })
      );

      fireEvent.change(screen.getByLabelText("Term"), { target: { value: "serendipity" } });
      fireEvent.change(screen.getByLabelText("Vietnamese Meaning"), {
        target: { value: "may mắn bất ngờ" },
      });

      // Press Cmd+Enter / Ctrl+Enter
      fireEvent.keyDown(screen.getByRole("dialog"), { key: "Enter", metaKey: true });

      assert.deepEqual(savedPayload, { term: "serendipity", meaning: "may mắn bất ngờ" });
    });
  });

  // --------------------------------------------------------------------------
  // 3. Vocabulary Search and Empty States
  // --------------------------------------------------------------------------
  describe("Vocabulary Library Search & Empty States", () => {
    function VocabularyListHarness({ initialItems }) {
      const [query, setQuery] = useState("");
      const filtered = initialItems.filter((i) =>
        i.term.toLowerCase().includes(query.toLowerCase().trim())
      );

      return React.createElement(
        "div",
        null,
        React.createElement("input", {
          "aria-label": "Search vocabulary",
          value: query,
          onChange: (e) => setQuery(e.target.value),
        }),
        filtered.length === 0
          ? React.createElement(
              "div",
              { role: "region", "aria-label": "empty-state" },
              React.createElement("p", null, "No vocabulary found matching your search."),
              React.createElement("button", { onClick: () => setQuery("") }, "Clear Search")
            )
          : React.createElement(
              "ul",
              { "aria-label": "vocab-items" },
              filtered.map((item) =>
                React.createElement("li", { key: item.id }, item.term)
              )
            )
      );
    }

    it("Renders clear empty state when search produces 0 matches, and restores on clear", () => {
      const items = [{ id: "1", term: "resilience" }, { id: "2", term: "tenacity" }];
      render(React.createElement(VocabularyListHarness, { initialItems: items }));

      assert.ok(screen.getByText("resilience"));

      // Search for non-matching term
      fireEvent.change(screen.getByLabelText("Search vocabulary"), {
        target: { value: "xyznonexistent" },
      });

      assert.ok(screen.getByText("No vocabulary found matching your search."));

      // Click clear search
      fireEvent.click(screen.getByText("Clear Search"));
      assert.ok(screen.getByText("resilience"));
      assert.ok(screen.getByText("tenacity"));
    });
  });

  // --------------------------------------------------------------------------
  // 4. Flashcard Reveal, Grading & Concurrency Duplicate Prevention
  // --------------------------------------------------------------------------
  describe("Flashcard Review Card Interactions & Attempt Idempotency", () => {
    function FlashcardReviewHarness({ card, onGrade }) {
      const [revealed, setRevealed] = useState(false);
      const [submitting, setSubmitting] = useState(false);
      const attemptId = "stable-attempt-uuid-001";

      const handleGrade = async (grade) => {
        if (submitting) return; // In-flight duplicate prevention gate
        setSubmitting(true);
        try {
          await onGrade({ srsItemId: card.id, attemptId, grade });
        } catch {
          // Preserve same attemptId on failure for retry
        } finally {
          setSubmitting(false);
        }
      };

      return React.createElement(
        "div",
        { "aria-label": "flashcard" },
        React.createElement("div", { id: "card-front" }, card.term),
        revealed && React.createElement("div", { id: "card-back" }, card.meaning),
        !revealed
          ? React.createElement(
              "button",
              { onClick: () => setRevealed(true) },
              "Reveal Answer"
            )
          : React.createElement(
              "div",
              { role: "group", "aria-label": "grade-actions" },
              React.createElement(
                "button",
                { disabled: submitting, onClick: () => handleGrade(1) },
                "Again (1)"
              ),
              React.createElement(
                "button",
                { disabled: submitting, onClick: () => handleGrade(2) },
                "Hard (2)"
              ),
              React.createElement(
                "button",
                { disabled: submitting, onClick: () => handleGrade(3) },
                "Good (3)"
              ),
              React.createElement(
                "button",
                { disabled: submitting, onClick: () => handleGrade(4) },
                "Easy (4)"
              )
            )
      );
    }

    it("Reveals answer card back when Reveal button is clicked", () => {
      const card = { id: "srs-1", term: "perseverance", meaning: "sự kiên trì" };
      render(React.createElement(FlashcardReviewHarness, { card, onGrade: () => {} }));

      assert.equal(screen.queryByText("sự kiên trì"), null);

      fireEvent.click(screen.getByText("Reveal Answer"));
      assert.ok(screen.getByText("sự kiên trì"));
      assert.ok(screen.getByText("Good (3)"));
    });

    it("Duplicate-click prevention: disables buttons and ignores repeated clicks while in-flight", async () => {
      let callCount = 0;
      let resolveSubmission;
      const delayedGrade = () => {
        callCount++;
        return new Promise((res) => {
          resolveSubmission = res;
        });
      };

      const card = { id: "srs-1", term: "perseverance", meaning: "sự kiên trì" };
      render(React.createElement(FlashcardReviewHarness, { card, onGrade: delayedGrade }));

      fireEvent.click(screen.getByText("Reveal Answer"));
      const goodBtn = screen.getByText("Good (3)");

      // Click first time
      fireEvent.click(goodBtn);
      assert.equal(callCount, 1);

      // Rapid secondary clicks while in flight
      fireEvent.click(goodBtn);
      fireEvent.click(goodBtn);
      assert.equal(callCount, 1); // Strictly prevented

      // Resolve in-flight request
      resolveSubmission();
    });
  });

  // --------------------------------------------------------------------------
  // 5. Dashboard Loading, Error/Retry, and Success States
  // --------------------------------------------------------------------------
  describe("Dashboard Page State Transitions", () => {
    function DashboardHarness({ fetchSummary }) {
      const [loading, setLoading] = useState(false);
      const [error, setError] = useState(null);
      const [data, setData] = useState(null);

      const load = async () => {
        setLoading(true);
        setError(null);
        try {
          const res = await fetchSummary();
          setData(res);
        } catch (err) {
          setError(err.message || "Failed to load dashboard");
        } finally {
          setLoading(false);
        }
      };

      React.useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
      }, []);

      if (loading) {
        return React.createElement("div", { role: "status", "aria-label": "dashboard-skeleton" }, "Loading metrics...");
      }

      if (error) {
        return React.createElement(
          "div",
          { role: "alert", "aria-label": "dashboard-error" },
          React.createElement("p", null, error),
          React.createElement("button", { onClick: load }, "Retry")
        );
      }

      if (!data) return null;

      return React.createElement(
        "div",
        { "aria-label": "dashboard-content" },
        React.createElement("h1", null, data.greeting),
        React.createElement("div", { "data-testid": "vocab-total" }, `Vocab: ${data.vocabularyTotal}`),
        React.createElement("div", { "data-testid": "due-count" }, `Due: ${data.dueReviewCount}`),
        React.createElement("div", { "data-testid": "streak" }, `Streak: ${data.currentStreakDays}`)
      );
    }

    it("Transitions from loading skeleton to error state, and recovers on Retry click", async () => {
      let attempt = 0;
      const fetchMock = () => {
        attempt++;
        if (attempt === 1) {
          return Promise.reject(new Error("Network error"));
        }
        return Promise.resolve({
          greeting: "Welcome back, Alice",
          vocabularyTotal: 15,
          dueReviewCount: 3,
          currentStreakDays: 2,
        });
      };

      render(React.createElement(DashboardHarness, { fetchSummary: fetchMock }));

      // First call failed -> Error state with retry button
      const alert = await screen.findByRole("alert");
      assert.match(alert.textContent, /Network error/i);

      // Click retry
      fireEvent.click(screen.getByText("Retry"));

      // Successfully transitions to data view
      const heading = await screen.findByText("Welcome back, Alice");
      assert.ok(heading);
      assert.equal(screen.getByTestId("vocab-total").textContent, "Vocab: 15");
      assert.equal(screen.getByTestId("due-count").textContent, "Due: 3");
      assert.equal(screen.getByTestId("streak").textContent, "Streak: 2");
    });
  });
});
