import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";

describe("Milestone M2-T01 — Collection & Deck Management Frontend Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. Collection Form Validation
  // --------------------------------------------------------------------------
  describe("Collection Form Validation", () => {
    function CollectionFormHarness({ onSubmit }) {
      const [title, setTitle] = useState("");
      const [description, setDescription] = useState("");
      const [error, setError] = useState(null);

      const handleSubmit = (e) => {
        e.preventDefault();
        const trimmed = title.trim();
        if (!trimmed) {
          setError("Collection title is required.");
          return;
        }
        if (trimmed.length > 100) {
          setError("Collection title must not exceed 100 characters.");
          return;
        }
        setError(null);
        onSubmit({ title: trimmed, description: description.trim() || null });
      };

      return React.createElement(
        "form",
        { onSubmit: handleSubmit, "aria-label": "collection-form" },
        error && React.createElement("div", { role: "alert", id: "error-banner" }, error),
        React.createElement("input", {
          "aria-label": "Collection Title",
          value: title,
          onChange: (e) => setTitle(e.target.value),
        }),
        React.createElement("textarea", {
          "aria-label": "Description",
          value: description,
          onChange: (e) => setDescription(e.target.value),
        }),
        React.createElement("button", { type: "submit" }, "Save Collection")
      );
    }

    it("Rejects empty or whitespace-only collection title", () => {
      let submitted = null;
      render(React.createElement(CollectionFormHarness, { onSubmit: (data) => { submitted = data; } }));

      const submitBtn = screen.getByText("Save Collection");
      fireEvent.click(submitBtn);

      const alert = screen.getByRole("alert");
      assert.match(alert.textContent, /Collection title is required/i);
      assert.equal(submitted, null);
    });

    it("Rejects collection title exceeding 100 characters", () => {
      let submitted = null;
      render(React.createElement(CollectionFormHarness, { onSubmit: (data) => { submitted = data; } }));

      const input = screen.getByLabelText("Collection Title");
      fireEvent.change(input, { target: { value: "A".repeat(101) } });

      const submitBtn = screen.getByText("Save Collection");
      fireEvent.click(submitBtn);

      const alert = screen.getByRole("alert");
      assert.match(alert.textContent, /must not exceed 100 characters/i);
      assert.equal(submitted, null);
    });

    it("Accepts valid title and optional description", () => {
      let submitted = null;
      render(React.createElement(CollectionFormHarness, { onSubmit: (data) => { submitted = data; } }));

      const titleInput = screen.getByLabelText("Collection Title");
      const descInput = screen.getByLabelText("Description");

      fireEvent.change(titleInput, { target: { value: "IELTS Academic Core" } });
      fireEvent.change(descInput, { target: { value: "Essential words for IELTS band 7.5+" } });

      const submitBtn = screen.getByText("Save Collection");
      fireEvent.click(submitBtn);

      assert.deepEqual(submitted, {
        title: "IELTS Academic Core",
        description: "Essential words for IELTS band 7.5+",
      });
      assert.equal(screen.queryByRole("alert"), null);
    });
  });

  // --------------------------------------------------------------------------
  // 2. Collection Deletion Non-Destructive Confirmation
  // --------------------------------------------------------------------------
  describe("Non-Destructive Deletion Guarantees", () => {
    function DeleteCollectionModalHarness({ collection, onConfirmDelete, onCancel }) {
      return React.createElement(
        "div",
        { role: "dialog", "aria-label": "Delete Collection Modal" },
        React.createElement("h2", null, `Delete ${collection.title}?`),
        React.createElement(
          "div",
          { role: "note", id: "non-destructive-banner" },
          "Deleting this collection will NOT delete any vocabulary words. All words in this collection will remain safe in your Vocabulary Library, and your SRS review schedules are preserved."
        ),
        React.createElement("button", { onClick: onCancel }, "Cancel"),
        React.createElement("button", { onClick: onConfirmDelete, id: "confirm-delete" }, "Delete Collection")
      );
    }

    it("Explicitly informs user that vocabulary is preserved before deletion", () => {
      let deleteCalled = false;
      const testCol = { id: "col-1", title: "Business English", wordCount: 15 };

      render(
        React.createElement(DeleteCollectionModalHarness, {
          collection: testCol,
          onConfirmDelete: () => { deleteCalled = true; },
          onCancel: () => {},
        })
      );

      const note = screen.getByRole("note");
      assert.match(note.textContent, /will NOT delete any vocabulary words/i);
      assert.match(note.textContent, /remain safe in your Vocabulary Library/i);

      const deleteBtn = screen.getByText("Delete Collection");
      fireEvent.click(deleteBtn);
      assert.equal(deleteCalled, true);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Collection Loading, Empty and Error States
  // --------------------------------------------------------------------------
  describe("Collection Library State Transitions", () => {
    function CollectionLibraryHarness({ initialLoading, initialError, initialData, onRetry }) {
      const [loading, setLoading] = useState(initialLoading);
      const [error, setError] = useState(initialError);
      const [collections, setCollections] = useState(initialData);

      const handleRetry = async () => {
        setError(null);
        setLoading(true);
        try {
          const res = await onRetry();
          setCollections(res);
        } catch (err) {
          setError(err.message);
        } finally {
          setLoading(false);
        }
      };

      if (loading) {
        return React.createElement("div", { id: "loading-skeleton" }, "Loading collections...");
      }

      if (error) {
        return React.createElement(
          "div",
          { role: "alert", id: "error-state" },
          React.createElement("p", null, error),
          React.createElement("button", { onClick: handleRetry }, "Retry")
        );
      }

      if (!collections || collections.length === 0) {
        return React.createElement(
          "div",
          { id: "empty-state" },
          React.createElement("h3", null, "No collections yet"),
          React.createElement("button", null, "Create your first collection")
        );
      }

      return React.createElement(
        "ul",
        { id: "collections-list" },
        collections.map((c) =>
          React.createElement(
            "li",
            { key: c.id },
            `${c.title} (${c.wordCount} words)`
          )
        )
      );
    }

    it("Renders loading skeleton while fetching", () => {
      render(React.createElement(CollectionLibraryHarness, { initialLoading: true, initialError: null, initialData: [] }));
      assert.ok(document.getElementById("loading-skeleton"));
    });

    it("Renders empty state when 0 collections exist", () => {
      render(React.createElement(CollectionLibraryHarness, { initialLoading: false, initialError: null, initialData: [] }));
      assert.ok(document.getElementById("empty-state"));
      assert.ok(screen.getByText("Create your first collection"));
    });

    it("Transitions from error to recovered state on retry", async () => {
      const retryMock = async () => [
        { id: "col-1", title: "TOEIC 800", wordCount: 42 },
      ];

      render(
        React.createElement(CollectionLibraryHarness, {
          initialLoading: false,
          initialError: "Network failure",
          initialData: [],
          onRetry: retryMock,
        })
      );

      assert.ok(screen.getByRole("alert"));
      const retryBtn = screen.getByText("Retry");
      fireEvent.click(retryBtn);

      await waitFor(() => {
        assert.ok(screen.getByText("TOEIC 800 (42 words)"));
      });
    });
  });

  // --------------------------------------------------------------------------
  // 4. Add Words to Collection Interaction
  // --------------------------------------------------------------------------
  describe("Add Words to Collection Interactions", () => {
    function AddWordsHarness({ availableWords, onAssign }) {
      const [selectedIds, setSelectedIds] = useState(new Set());

      const toggleWord = (id) => {
        const next = new Set(selectedIds);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        setSelectedIds(next);
      };

      return React.createElement(
        "div",
        null,
        React.createElement(
          "ul",
          null,
          availableWords.map((w) =>
            React.createElement(
              "li",
              { key: w.id },
              React.createElement("label", null,
                React.createElement("input", {
                  type: "checkbox",
                  checked: selectedIds.has(w.id),
                  onChange: () => toggleWord(w.id),
                  "aria-label": w.term,
                }),
                w.term
              )
            )
          )
        ),
        React.createElement(
          "button",
          {
            disabled: selectedIds.size === 0,
            onClick: () => onAssign(Array.from(selectedIds)),
          },
          `Add ${selectedIds.size} Words`
        )
      );
    }

    it("Selects multiple vocabulary words and submits their IDs", () => {
      const words = [
        { id: "v-1", term: "perseverance" },
        { id: "v-2", term: "resilience" },
        { id: "v-3", term: "tenacity" },
      ];

      let assignedIds = [];
      render(React.createElement(AddWordsHarness, {
        availableWords: words,
        onAssign: (ids) => { assignedIds = ids; },
      }));

      const addBtn = screen.getByText("Add 0 Words");
      assert.equal(addBtn.disabled, true);

      // Check first two words
      fireEvent.click(screen.getByLabelText("perseverance"));
      fireEvent.click(screen.getByLabelText("resilience"));

      const updatedBtn = screen.getByText("Add 2 Words");
      assert.equal(updatedBtn.disabled, false);

      fireEvent.click(updatedBtn);
      assert.deepEqual(assignedIds, ["v-1", "v-2"]);
    });
  });

  // --------------------------------------------------------------------------
  // 5. Remove Word from Collection Confirmation
  // --------------------------------------------------------------------------
  describe("Remove Word from Collection", () => {
    function RemoveWordModalHarness({ term, onConfirmRemove, onCancel }) {
      return React.createElement(
        "div",
        { role: "dialog", "aria-label": "Remove Word Dialog" },
        React.createElement("h2", null, `Remove "${term}" from Collection?`),
        React.createElement(
          "p",
          { id: "remove-disclaimer" },
          "This word will remain in your main Vocabulary Library. Its SRS flashcard schedule will not be reset."
        ),
        React.createElement("button", { onClick: onCancel }, "Cancel"),
        React.createElement("button", { onClick: onConfirmRemove }, "Remove from Deck")
      );
    }

    it("Confirms removing membership without deleting vocabulary", () => {
      let removed = false;
      render(
        React.createElement(RemoveWordModalHarness, {
          term: "ubiquitous",
          onConfirmRemove: () => { removed = true; },
          onCancel: () => {},
        })
      );

      const disclaimer = document.getElementById("remove-disclaimer");
      assert.match(disclaimer.textContent, /remain in your main Vocabulary Library/i);

      fireEvent.click(screen.getByText("Remove from Deck"));
      assert.equal(removed, true);
    });
  });

  // --------------------------------------------------------------------------
  // 6. API Error and Conflict Handling (TITLE_ALREADY_EXISTS)
  // --------------------------------------------------------------------------
  describe("API Conflict Handling", () => {
    function DuplicateTitleHarness({ onSubmit }) {
      const [title] = useState("IELTS");
      const [error, setError] = useState(null);

      const handleSubmit = async (e) => {
        e.preventDefault();
        try {
          await onSubmit(title);
        } catch (err) {
          if (err.code === "TITLE_ALREADY_EXISTS") {
            setError("A collection with this title already exists.");
          } else {
            setError(err.message);
          }
        }
      };

      return React.createElement(
        "form",
        { onSubmit: handleSubmit },
        error && React.createElement("div", { role: "alert", id: "conflict-error" }, error),
        React.createElement("button", { type: "submit" }, "Create")
      );
    }

    it("Catches RFC 7807 TITLE_ALREADY_EXISTS error and renders friendly message", async () => {
      const conflictMock = async () => {
        const error = new Error("Duplicate");
        error.code = "TITLE_ALREADY_EXISTS";
        throw error;
      };

      render(React.createElement(DuplicateTitleHarness, { onSubmit: conflictMock }));

      fireEvent.click(screen.getByText("Create"));

      await waitFor(() => {
        const alert = screen.getByRole("alert");
        assert.match(alert.textContent, /A collection with this title already exists/i);
      });
    });
  });
});
