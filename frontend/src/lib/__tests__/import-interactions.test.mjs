import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M2-T02 — Bulk Import Frontend Interaction Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. File Selection & Validation
  // --------------------------------------------------------------------------
  describe("File Selection & Validation", () => {
    function FileUploadHarness({ onFileAccepted, onError }) {
      const [error, setError] = useState(null);
      const [selectedFile, setSelectedFile] = useState(null);

      const handleFile = (file) => {
        setError(null);
        if (!file) return;

        const ext = file.name.split(".").pop()?.toLowerCase();
        if (ext !== "csv" && ext !== "tsv" && ext !== "txt") {
          const err = "Please select a .csv or .tsv file.";
          setError(err);
          onError?.(err);
          return;
        }

        if (file.size > 2 * 1024 * 1024) {
          const err = "File exceeds maximum allowed size of 2 MB.";
          setError(err);
          onError?.(err);
          return;
        }

        setSelectedFile(file);
        onFileAccepted?.(file);
      };

      return React.createElement(
        "div",
        { "aria-label": "upload-container" },
        error && React.createElement("div", { role: "alert", id: "upload-error" }, error),
        React.createElement("input", {
          type: "file",
          "aria-label": "Select CSV or TSV File",
          onChange: (e) => handleFile(e.target.files?.[0]),
        }),
        selectedFile && React.createElement("span", { "data-testid": "filename" }, selectedFile.name)
      );
    }

    it("Accepts valid .csv file under 2 MB", () => {
      let accepted = null;
      render(React.createElement(FileUploadHarness, { onFileAccepted: (f) => { accepted = f; } }));

      const input = screen.getByLabelText("Select CSV or TSV File");
      const testFile = new File(["term,vietnameseMeaning\ncat,mèo"], "vocab.csv", { type: "text/csv" });

      fireEvent.change(input, { target: { files: [testFile] } });

      assert.equal(accepted?.name, "vocab.csv");
      assert.equal(screen.queryByRole("alert"), null);
      assert.equal(screen.getByTestId("filename").textContent, "vocab.csv");
    });

    it("Accepts valid .tsv file", () => {
      let accepted = null;
      render(React.createElement(FileUploadHarness, { onFileAccepted: (f) => { accepted = f; } }));

      const input = screen.getByLabelText("Select CSV or TSV File");
      const testFile = new File(["term\tvietnameseMeaning\ndog\tchó"], "vocab.tsv", { type: "text/tab-separated-values" });

      fireEvent.change(input, { target: { files: [testFile] } });

      assert.equal(accepted?.name, "vocab.tsv");
      assert.equal(screen.queryByRole("alert"), null);
    });

    it("Rejects unsupported file format (.pdf / .xlsx / .exe)", () => {
      let errorReported = null;
      render(React.createElement(FileUploadHarness, { onError: (err) => { errorReported = err; } }));

      const input = screen.getByLabelText("Select CSV or TSV File");
      const testFile = new File(["dummy binary data"], "vocab.xlsx", { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });

      fireEvent.change(input, { target: { files: [testFile] } });

      const alert = screen.getByRole("alert");
      assert.match(alert.textContent, /Please select a \.csv or \.tsv file/i);
      assert.equal(errorReported, "Please select a .csv or .tsv file.");
    });

    it("Rejects file exceeding 2 MB limit", () => {
      let errorReported = null;
      render(React.createElement(FileUploadHarness, { onError: (err) => { errorReported = err; } }));

      const input = screen.getByLabelText("Select CSV or TSV File");
      // Create a mock large file with size property
      const largeFile = new File(["dummy"], "huge_vocab.csv", { type: "text/csv" });
      Object.defineProperty(largeFile, "size", { value: 3 * 1024 * 1024 });

      fireEvent.change(input, { target: { files: [largeFile] } });

      const alert = screen.getByRole("alert");
      assert.match(alert.textContent, /File exceeds maximum allowed size of 2 MB/i);
      assert.equal(errorReported, "File exceeds maximum allowed size of 2 MB.");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Import Preview Rendering & Row-Level Validation Messages
  // --------------------------------------------------------------------------
  describe("Import Preview & Row Validation", () => {
    function PreviewTableHarness({ previewData, filter = "ALL" }) {
      const filtered = previewData.previewRows.filter((r) => {
        if (filter === "VALID") return r.status === "VALID";
        if (filter === "DUPLICATES") return r.status.startsWith("DUPLICATE_");
        if (filter === "ERRORS") return r.status === "INVALID";
        return true;
      });

      return React.createElement(
        "div",
        { "aria-label": "preview-summary" },
        React.createElement("div", { "data-testid": "total-count" }, `Total: ${previewData.totalRows}`),
        React.createElement("div", { "data-testid": "valid-count" }, `Ready: ${previewData.validRows}`),
        React.createElement("div", { "data-testid": "duplicate-count" }, `Duplicates: ${previewData.duplicateRows}`),
        React.createElement("div", { "data-testid": "error-count" }, `Errors: ${previewData.errorRows}`),
        React.createElement(
          "table",
          { "aria-label": "preview-table" },
          React.createElement(
            "tbody",
            null,
            filtered.map((row) =>
              React.createElement(
                "tr",
                { key: row.rowNumber, "data-testid": `row-${row.rowNumber}` },
                React.createElement("td", { "data-testid": `row-num-${row.rowNumber}` }, String(row.rowNumber)),
                React.createElement("td", { "data-testid": `term-${row.rowNumber}` }, row.term || "(empty)"),
                React.createElement("td", { "data-testid": `status-${row.rowNumber}` }, row.status),
                row.validationMessage &&
                  React.createElement(
                    "td",
                    { role: "alert", "data-testid": `err-${row.rowNumber}` },
                    row.validationMessage
                  )
              )
            )
          )
        )
      );
    }

    const samplePreview = {
      totalRows: 4,
      validRows: 2,
      duplicateRows: 1,
      errorRows: 1,
      errors: [{ rowNumber: 4, message: "Missing required 'vietnameseMeaning'", field: null }],
      previewRows: [
        { rowNumber: 2, term: "serendipity", partOfSpeech: "noun", vietnameseMeaning: "sự tình cờ may mắn", status: "VALID", validationMessage: null },
        { rowNumber: 3, term: "ephemeral", partOfSpeech: "adjective", vietnameseMeaning: "phù du", status: "DUPLICATE_LIBRARY", validationMessage: "Already exists in your Vocabulary Library." },
        { rowNumber: 4, term: "ubiquitous", partOfSpeech: "adjective", vietnameseMeaning: "", status: "INVALID", validationMessage: "Missing required 'vietnameseMeaning'" },
        { rowNumber: 5, term: "lucid", partOfSpeech: "adjective", vietnameseMeaning: "rõ ràng, minh bạch", status: "VALID", validationMessage: null },
      ],
    };

    it("Renders summary counts correctly", () => {
      render(React.createElement(PreviewTableHarness, { previewData: samplePreview }));

      assert.equal(screen.getByTestId("total-count").textContent, "Total: 4");
      assert.equal(screen.getByTestId("valid-count").textContent, "Ready: 2");
      assert.equal(screen.getByTestId("duplicate-count").textContent, "Duplicates: 1");
      assert.equal(screen.getByTestId("error-count").textContent, "Errors: 1");
    });

    it("Renders row-level validation message for invalid row", () => {
      render(React.createElement(PreviewTableHarness, { previewData: samplePreview }));

      const errCell = screen.getByTestId("err-4");
      assert.match(errCell.textContent, /Missing required 'vietnameseMeaning'/i);
      assert.equal(screen.getByTestId("status-4").textContent, "INVALID");
    });

    it("Filters rows by status category", () => {
      const { rerender } = render(React.createElement(PreviewTableHarness, { previewData: samplePreview, filter: "ERRORS" }));

      // Only row 4 should appear in ERRORS filter
      assert.ok(screen.queryByTestId("row-4"));
      assert.equal(screen.queryByTestId("row-2"), null);
      assert.equal(screen.queryByTestId("row-3"), null);

      rerender(React.createElement(PreviewTableHarness, { previewData: samplePreview, filter: "DUPLICATES" }));
      // Only row 3 should appear in DUPLICATES filter
      assert.ok(screen.queryByTestId("row-3"));
      assert.equal(screen.queryByTestId("row-2"), null);
      assert.equal(screen.queryByTestId("row-4"), null);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Duplicate Handling Policy Selection
  // --------------------------------------------------------------------------
  describe("Duplicate Handling Policy Selection", () => {
    function DuplicatePolicySelectorHarness({ onPolicyChange, initialPolicy = "SKIP" }) {
      const [policy, setPolicy] = useState(initialPolicy);

      const handleChange = (newPolicy) => {
        setPolicy(newPolicy);
        onPolicyChange?.(newPolicy);
      };

      return React.createElement(
        "div",
        { role: "radiogroup", "aria-label": "Duplicate Policy" },
        React.createElement(
          "label",
          null,
          React.createElement("input", {
            type: "radio",
            name: "duplicatePolicy",
            value: "SKIP",
            checked: policy === "SKIP",
            onChange: () => handleChange("SKIP"),
            "aria-label": "Skip Duplicates",
          }),
          "Skip duplicates (Recommended)"
        ),
        React.createElement(
          "label",
          null,
          React.createElement("input", {
            type: "radio",
            name: "duplicatePolicy",
            value: "REJECT",
            checked: policy === "REJECT",
            onChange: () => handleChange("REJECT"),
            "aria-label": "Reject on Duplicates",
          }),
          "Reject entire batch if duplicates exist"
        ),
        React.createElement("span", { "data-testid": "active-policy" }, policy)
      );
    }

    it("Defaults to SKIP duplicate policy", () => {
      render(React.createElement(DuplicatePolicySelectorHarness, {}));

      const skipRadio = screen.getByLabelText("Skip Duplicates");
      assert.equal(skipRadio.checked, true);
      assert.equal(screen.getByTestId("active-policy").textContent, "SKIP");
    });

    it("Switches to REJECT duplicate policy on user selection", () => {
      let selectedPolicy = null;
      render(React.createElement(DuplicatePolicySelectorHarness, { onPolicyChange: (p) => { selectedPolicy = p; } }));

      const rejectRadio = screen.getByLabelText("Reject on Duplicates");
      fireEvent.click(rejectRadio);

      assert.equal(selectedPolicy, "REJECT");
      assert.equal(screen.getByTestId("active-policy").textContent, "REJECT");
    });
  });

  // --------------------------------------------------------------------------
  // 4. Submit Blocking While Pending & Loading States
  // --------------------------------------------------------------------------
  describe("Submission State & Button Guard", () => {
    function ConfirmButtonHarness({ onConfirm, isConfirming, disabled }) {
      return React.createElement(
        "button",
        {
          onClick: onConfirm,
          disabled: disabled || isConfirming,
          "aria-label": "Confirm Import",
          "aria-busy": isConfirming ? "true" : "false",
        },
        isConfirming ? "Importing vocabulary..." : "Confirm & Import"
      );
    }

    it("Disables button and indicates busy state while confirmation is pending", () => {
      let clicks = 0;
      render(React.createElement(ConfirmButtonHarness, { onConfirm: () => { clicks++; }, isConfirming: true, disabled: false }));

      const btn = screen.getByLabelText("Confirm Import");
      assert.equal(btn.disabled, true);
      assert.equal(btn.getAttribute("aria-busy"), "true");
      assert.match(btn.textContent, /Importing vocabulary\.\.\./i);

      fireEvent.click(btn);
      assert.equal(clicks, 0, "No submit events allowed while confirming");
    });

    it("Enables button when idle with valid entries", () => {
      let clicks = 0;
      render(React.createElement(ConfirmButtonHarness, { onConfirm: () => { clicks++; }, isConfirming: false, disabled: false }));

      const btn = screen.getByLabelText("Confirm Import");
      assert.equal(btn.disabled, false);
      assert.equal(btn.getAttribute("aria-busy"), "false");

      fireEvent.click(btn);
      assert.equal(clicks, 1);
    });
  });

  // --------------------------------------------------------------------------
  // 5. Target Collection Selection
  // --------------------------------------------------------------------------
  describe("Target Collection Selection", () => {
    function CollectionSelectorHarness({ collections, onSelect }) {
      const [selectedId, setSelectedId] = useState("");

      const handleChange = (e) => {
        setSelectedId(e.target.value);
        onSelect?.(e.target.value);
      };

      return React.createElement(
        "div",
        null,
        React.createElement(
          "select",
          {
            "aria-label": "Target Collection",
            value: selectedId,
            onChange: handleChange,
          },
          React.createElement("option", { value: "" }, "No collection (Library only)"),
          collections.map((c) =>
            React.createElement("option", { key: c.id, value: c.id }, c.title)
          )
        ),
        React.createElement("span", { "data-testid": "selected-id" }, selectedId || "none")
      );
    }

    const mockCollections = [
      { id: "col-1", title: "TOEFL Core 500" },
      { id: "col-2", title: "Business English" },
    ];

    it("Allows optional collection assignment", () => {
      let chosen = null;
      render(React.createElement(CollectionSelectorHarness, { collections: mockCollections, onSelect: (id) => { chosen = id; } }));

      const select = screen.getByLabelText("Target Collection");
      assert.equal(screen.getByTestId("selected-id").textContent, "none");

      fireEvent.change(select, { target: { value: "col-2" } });
      assert.equal(chosen, "col-2");
      assert.equal(screen.getByTestId("selected-id").textContent, "col-2");
    });
  });

  // --------------------------------------------------------------------------
  // 6. Import Results Screen & Feedback
  // --------------------------------------------------------------------------
  describe("Import Results Screen", () => {
    function ImportResultHarness({ result, onReset }) {
      return React.createElement(
        "div",
        { "aria-label": "import-results" },
        React.createElement("h2", null, "Import Completed"),
        React.createElement("div", { "data-testid": "imported-stat" }, `${result.importedCount} words imported`),
        React.createElement("div", { "data-testid": "skipped-stat" }, `${result.skippedCount} duplicates skipped`),
        React.createElement("div", { "data-testid": "rejected-stat" }, `${result.rejectedCount} items rejected`),
        React.createElement("button", { onClick: onReset }, "Import Another File"),
        React.createElement("a", { href: "/vocabulary" }, "Go to Vocabulary Library"),
        React.createElement("a", { href: "/review" }, "Start Reviewing")
      );
    }

    it("Displays accurate counts and navigation links upon successful import", () => {
      let resetClicked = false;
      const res = { importedCount: 8, skippedCount: 2, rejectedCount: 0 };

      render(React.createElement(ImportResultHarness, { result: res, onReset: () => { resetClicked = true; } }));

      assert.equal(screen.getByTestId("imported-stat").textContent, "8 words imported");
      assert.equal(screen.getByTestId("skipped-stat").textContent, "2 duplicates skipped");
      assert.equal(screen.getByTestId("rejected-stat").textContent, "0 items rejected");

      const resetBtn = screen.getByText("Import Another File");
      fireEvent.click(resetBtn);
      assert.equal(resetClicked, true);

      assert.ok(screen.getByText("Go to Vocabulary Library"));
      assert.ok(screen.getByText("Start Reviewing"));
    });
  });

  // --------------------------------------------------------------------------
  // 7. Keyboard Accessibility
  // --------------------------------------------------------------------------
  describe("Keyboard Accessibility", () => {
    function AccessibleTabsHarness({ onTabChange }) {
      const [active, setActive] = useState("ALL");
      const tabs = ["ALL", "VALID", "DUPLICATES", "ERRORS"];

      return React.createElement(
        "div",
        { role: "tablist", "aria-label": "Filter preview rows" },
        tabs.map((tab) =>
          React.createElement(
            "button",
            {
              key: tab,
              role: "tab",
              "aria-selected": active === tab,
              tabIndex: active === tab ? 0 : -1,
              onClick: () => {
                setActive(tab);
                onTabChange?.(tab);
              },
              onKeyDown: (e) => {
                if (e.key === "Enter" || e.key === " ") {
                  setActive(tab);
                  onTabChange?.(tab);
                }
              },
            },
            tab
          )
        )
      );
    }

    it("Supports keyboard interaction and aria-selected state for tab navigation", () => {
      let changedTab = null;
      render(React.createElement(AccessibleTabsHarness, { onTabChange: (t) => { changedTab = t; } }));

      const validTab = screen.getByRole("tab", { name: "VALID" });
      assert.equal(validTab.getAttribute("aria-selected"), "false");

      fireEvent.keyDown(validTab, { key: "Enter" });
      assert.equal(changedTab, "VALID");
      assert.equal(validTab.getAttribute("aria-selected"), "true");
    });
  });
});
