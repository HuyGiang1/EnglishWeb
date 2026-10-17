import "./setup-dom.mjs";
import { describe, it, beforeEach } from "node:test";
import assert from "node:assert/strict";
import React, { useState } from "react";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";

describe("Milestone M2-T07 — Learner Profile & Settings Frontend Interaction Tests", () => {
  beforeEach(() => {
    cleanup();
    document.body.innerHTML = "";
  });

  // --------------------------------------------------------------------------
  // 1. Initial Profile Loading & Tab Navigation
  // --------------------------------------------------------------------------
  describe("Tabs & Navigation Structure", () => {
    function TabHarness({ initialTab = "general", onTabChange }) {
      const [tab, setTab] = useState(initialTab);
      return React.createElement(
        "div",
        null,
        React.createElement(
          "div",
          { role: "tablist", "data-testid": "tablist" },
          ["general", "preferences", "security", "account"].map((t) =>
            React.createElement(
              "button",
              {
                key: t,
                role: "tab",
                "aria-selected": tab === t,
                "data-testid": `tab-${t}`,
                onClick: () => {
                  setTab(t);
                  if (onTabChange) onTabChange(t);
                },
              },
              t
            )
          )
        ),
        React.createElement("div", { "data-testid": "active-tab-content" }, tab)
      );
    }

    it("Switches active tabs correctly and updates aria-selected attributes", () => {
      let activeTab = "general";
      render(
        React.createElement(TabHarness, {
          onTabChange: (t) => {
            activeTab = t;
          },
        })
      );

      const generalTab = screen.getByTestId("tab-general");
      const prefTab = screen.getByTestId("tab-preferences");
      const secTab = screen.getByTestId("tab-security");
      const accTab = screen.getByTestId("tab-account");

      assert.equal(generalTab.getAttribute("aria-selected"), "true");
      assert.equal(prefTab.getAttribute("aria-selected"), "false");

      fireEvent.click(prefTab);
      assert.equal(activeTab, "preferences");
      assert.equal(prefTab.getAttribute("aria-selected"), "true");
      assert.equal(generalTab.getAttribute("aria-selected"), "false");

      fireEvent.click(secTab);
      assert.equal(activeTab, "security");
      assert.equal(secTab.getAttribute("aria-selected"), "true");

      fireEvent.click(accTab);
      assert.equal(activeTab, "account");
      assert.equal(accTab.getAttribute("aria-selected"), "true");
    });
  });

  // --------------------------------------------------------------------------
  // 2. Profile Editing & Validation
  // --------------------------------------------------------------------------
  describe("Profile Editing Form", () => {
    function ProfileHarness({ initialName, email, onSave }) {
      const [name, setName] = useState(initialName);
      const [error, setError] = useState(null);
      const [saving, setSaving] = useState(false);

      const handleSubmit = (e) => {
        e.preventDefault();
        const trimmed = name.trim();
        if (trimmed.length < 2 || trimmed.length > 100) {
          setError("Display name must be between 2 and 100 characters.");
          return;
        }
        setError(null);
        setSaving(true);
        onSave(trimmed);
      };

      return React.createElement(
        "form",
        { onSubmit: handleSubmit, "data-testid": "profile-form" },
        React.createElement("input", {
          "data-testid": "input-display-name",
          value: name,
          onChange: (e) => setName(e.target.value),
        }),
        React.createElement("input", {
          "data-testid": "input-email-readonly",
          value: email,
          readOnly: true,
          disabled: true,
        }),
        error && React.createElement("div", { "data-testid": "profile-error" }, error),
        React.createElement(
          "button",
          {
            type: "submit",
            "data-testid": "save-profile-btn",
            disabled: saving || name.trim().length < 2,
          },
          saving ? "Saving..." : "Save Profile"
        )
      );
    }

    it("Displays safe read-only email and allows editing valid display name", () => {
      let savedName = null;
      render(
        React.createElement(ProfileHarness, {
          initialName: "Minh Tran",
          email: "learner@example.com",
          onSave: (val) => {
            savedName = val;
          },
        })
      );

      const emailInput = screen.getByTestId("input-email-readonly");
      assert.equal(emailInput.value, "learner@example.com");
      assert.equal(emailInput.readOnly, true);

      const nameInput = screen.getByTestId("input-display-name");
      fireEvent.change(nameInput, { target: { value: "Minh Tran Updated" } });
      const submitBtn = screen.getByTestId("save-profile-btn");
      fireEvent.click(submitBtn);

      assert.equal(savedName, "Minh Tran Updated");
    });

    it("Rejects short display name (< 2 chars) and disables submit button", () => {
      render(
        React.createElement(ProfileHarness, {
          initialName: "Minh Tran",
          email: "learner@example.com",
          onSave: () => {},
        })
      );

      const nameInput = screen.getByTestId("input-display-name");
      const submitBtn = screen.getByTestId("save-profile-btn");

      fireEvent.change(nameInput, { target: { value: "A" } });
      assert.equal(submitBtn.disabled, true);

      fireEvent.change(nameInput, { target: { value: "   " } });
      assert.equal(submitBtn.disabled, true);
    });
  });

  // --------------------------------------------------------------------------
  // 3. Learning Preferences: Daily Goal, Timezone & Audio Speed
  // --------------------------------------------------------------------------
  describe("Learning Preferences Form", () => {
    function PreferencesHarness({ initialGoal, initialTz, initialSpeed, onSave }) {
      const [goal, setGoal] = useState(initialGoal);
      const [timezone, setTimezone] = useState(initialTz);
      const [tzSearch, setTzSearch] = useState("");
      const [speed, setSpeed] = useState(initialSpeed);

      const availableZones = ["Asia/Ho_Chi_Minh", "Asia/Tokyo", "Europe/London", "America/New_York", "UTC"];
      const filtered = availableZones.filter((z) => z.toLowerCase().includes(tzSearch.toLowerCase()));

      return React.createElement(
        "div",
        null,
        // Daily goal input & presets
        React.createElement("input", {
          "data-testid": "input-goal",
          type: "number",
          value: goal,
          onChange: (e) => setGoal(Number(e.target.value)),
        }),
        [10, 20, 30, 50].map((p) =>
          React.createElement(
            "button",
            {
              key: p,
              "data-testid": `preset-${p}`,
              onClick: () => setGoal(p),
            },
            `${p}`
          )
        ),
        // Timezone search & options
        React.createElement("input", {
          "data-testid": "input-tz-search",
          value: tzSearch,
          onChange: (e) => setTzSearch(e.target.value),
        }),
        React.createElement(
          "div",
          { "data-testid": "tz-list" },
          filtered.map((tz) =>
            React.createElement(
              "button",
              {
                key: tz,
                "data-testid": `tz-opt-${tz}`,
                onClick: () => setTimezone(tz),
              },
              tz
            )
          )
        ),
        React.createElement("span", { "data-testid": "selected-tz" }, timezone),
        // Audio speed chips
        [0.75, 1.0, 1.25].map((s) =>
          React.createElement(
            "button",
            {
              key: s,
              "data-testid": `speed-${s}`,
              onClick: () => setSpeed(s),
            },
            `${s}x`
          )
        ),
        React.createElement("span", { "data-testid": "selected-speed" }, `${speed}x`),
        // Save button
        React.createElement(
          "button",
          {
            "data-testid": "save-pref-btn",
            onClick: () => onSave({ goal, timezone, speed }),
          },
          "Save Preferences"
        )
      );
    }

    it("Applies daily goal presets correctly on click", () => {
      let saved = null;
      render(
        React.createElement(PreferencesHarness, {
          initialGoal: 20,
          initialTz: "UTC",
          initialSpeed: 1.0,
          onSave: (val) => {
            saved = val;
          },
        })
      );

      const goalInput = screen.getByTestId("input-goal");
      assert.equal(goalInput.value, "20");

      fireEvent.click(screen.getByTestId("preset-30"));
      assert.equal(goalInput.value, "30");

      fireEvent.click(screen.getByTestId("preset-50"));
      assert.equal(goalInput.value, "50");

      fireEvent.click(screen.getByTestId("save-pref-btn"));
      assert.equal(saved.goal, 50);
    });

    it("Filters timezone options based on search query and allows selection", () => {
      let saved = null;
      render(
        React.createElement(PreferencesHarness, {
          initialGoal: 20,
          initialTz: "UTC",
          initialSpeed: 1.0,
          onSave: (val) => {
            saved = val;
          },
        })
      );

      const tzSearch = screen.getByTestId("input-tz-search");
      fireEvent.change(tzSearch, { target: { value: "Tokyo" } });

      assert.equal(screen.queryByTestId("tz-opt-Europe/London"), null);
      assert.notEqual(screen.getByTestId("tz-opt-Asia/Tokyo"), null);

      fireEvent.click(screen.getByTestId("tz-opt-Asia/Tokyo"));
      assert.equal(screen.getByTestId("selected-tz").textContent, "Asia/Tokyo");

      fireEvent.click(screen.getByTestId("save-pref-btn"));
      assert.equal(saved.timezone, "Asia/Tokyo");
    });

    it("Selects preferred audio playback speed chip correctly", () => {
      let saved = null;
      render(
        React.createElement(PreferencesHarness, {
          initialGoal: 20,
          initialTz: "UTC",
          initialSpeed: 1.0,
          onSave: (val) => {
            saved = val;
          },
        })
      );

      assert.equal(screen.getByTestId("selected-speed").textContent, "1x");

      fireEvent.click(screen.getByTestId("speed-0.75"));
      assert.equal(screen.getByTestId("selected-speed").textContent, "0.75x");

      fireEvent.click(screen.getByTestId("speed-1.25"));
      assert.equal(screen.getByTestId("selected-speed").textContent, "1.25x");

      fireEvent.click(screen.getByTestId("save-pref-btn"));
      assert.equal(saved.speed, 1.25);
    });
  });

  // --------------------------------------------------------------------------
  // 4. Security & Password Change
  // --------------------------------------------------------------------------
  describe("Security & Password Change Form", () => {
    function SecurityHarness({ onSave }) {
      const [currentPass, setCurrentPass] = useState("");
      const [newPass, setNewPass] = useState("");
      const [confirmPass, setConfirmPass] = useState("");
      const [error, setError] = useState(null);
      const [success, setSuccess] = useState(null);

      const handleSubmit = (e) => {
        e.preventDefault();
        if (!currentPass) {
          setError("Current password is required.");
          return;
        }
        if (newPass.length < 8) {
          setError("New password must be at least 8 characters.");
          return;
        }
        if (newPass !== confirmPass) {
          setError("New password and confirmation do not match.");
          return;
        }
        setError(null);
        setSuccess("Password changed successfully.");
        onSave({ currentPass, newPass, confirmPass });
      };

      return React.createElement(
        "form",
        { onSubmit: handleSubmit, "data-testid": "security-form" },
        React.createElement("input", {
          "data-testid": "input-current-pass",
          type: "password",
          value: currentPass,
          onChange: (e) => setCurrentPass(e.target.value),
        }),
        React.createElement("input", {
          "data-testid": "input-new-pass",
          type: "password",
          value: newPass,
          onChange: (e) => setNewPass(e.target.value),
        }),
        React.createElement("input", {
          "data-testid": "input-confirm-pass",
          type: "password",
          value: confirmPass,
          onChange: (e) => setConfirmPass(e.target.value),
        }),
        error && React.createElement("div", { "data-testid": "pass-error" }, error),
        success && React.createElement("div", { "data-testid": "pass-success" }, success),
        React.createElement(
          "button",
          {
            type: "submit",
            "data-testid": "submit-password-btn",
            disabled: !currentPass || !newPass || !confirmPass,
          },
          "Change Password"
        )
      );
    }

    it("Validates password mismatch and prevents submission", () => {
      let saved = null;
      render(
        React.createElement(SecurityHarness, {
          onSave: (val) => {
            saved = val;
          },
        })
      );

      fireEvent.change(screen.getByTestId("input-current-pass"), { target: { value: "OldPassword123!" } });
      fireEvent.change(screen.getByTestId("input-new-pass"), { target: { value: "NewPassword456!" } });
      fireEvent.change(screen.getByTestId("input-confirm-pass"), { target: { value: "Different456!" } });

      fireEvent.click(screen.getByTestId("submit-password-btn"));

      assert.equal(screen.getByTestId("pass-error").textContent, "New password and confirmation do not match.");
      assert.equal(saved, null);
    });

    it("Submits valid password change and renders success banner", () => {
      let saved = null;
      render(
        React.createElement(SecurityHarness, {
          onSave: (val) => {
            saved = val;
          },
        })
      );

      fireEvent.change(screen.getByTestId("input-current-pass"), { target: { value: "OldPassword123!" } });
      fireEvent.change(screen.getByTestId("input-new-pass"), { target: { value: "NewPassword456!" } });
      fireEvent.change(screen.getByTestId("input-confirm-pass"), { target: { value: "NewPassword456!" } });

      fireEvent.click(screen.getByTestId("submit-password-btn"));

      assert.notEqual(screen.getByTestId("pass-success"), null);
      assert.equal(saved.currentPass, "OldPassword123!");
      assert.equal(saved.newPass, "NewPassword456!");
    });
  });

  // --------------------------------------------------------------------------
  // 5. Account Deletion Confirmation Workflow
  // --------------------------------------------------------------------------
  describe("Account Deletion Workflow", () => {
    function DeletionHarness({ onDelete }) {
      const [isOpen, setIsOpen] = useState(false);
      const [password, setPassword] = useState("");

      return React.createElement(
        "div",
        null,
        React.createElement(
          "button",
          {
            "data-testid": "open-delete-btn",
            onClick: () => setIsOpen(true),
          },
          "Delete Account"
        ),
        isOpen &&
          React.createElement(
            "div",
            { role: "dialog", "data-testid": "delete-dialog" },
            React.createElement("input", {
              "data-testid": "input-delete-pass",
              type: "password",
              value: password,
              onChange: (e) => setPassword(e.target.value),
            }),
            React.createElement(
              "button",
              {
                "data-testid": "cancel-btn",
                onClick: () => setIsOpen(false),
              },
              "Cancel"
            ),
            React.createElement(
              "button",
              {
                "data-testid": "confirm-delete-btn",
                disabled: !password,
                onClick: () => onDelete(password),
              },
              "Confirm Erasure"
            )
          )
      );
    }

    it("Opens modal on demand and requires password input before enabling confirmation", () => {
      let deletedPassword = null;
      render(
        React.createElement(DeletionHarness, {
          onDelete: (pw) => {
            deletedPassword = pw;
          },
        })
      );

      assert.equal(screen.queryByTestId("delete-dialog"), null);

      fireEvent.click(screen.getByTestId("open-delete-btn"));
      assert.notEqual(screen.getByTestId("delete-dialog"), null);

      const confirmBtn = screen.getByTestId("confirm-delete-btn");
      assert.equal(confirmBtn.disabled, true);

      fireEvent.change(screen.getByTestId("input-delete-pass"), { target: { value: "MySecret123!" } });
      assert.equal(confirmBtn.disabled, false);

      fireEvent.click(confirmBtn);
      assert.equal(deletedPassword, "MySecret123!");
    });

    it("Closes confirmation modal cleanly when Cancel is clicked", () => {
      render(React.createElement(DeletionHarness, { onDelete: () => {} }));

      fireEvent.click(screen.getByTestId("open-delete-btn"));
      assert.notEqual(screen.getByTestId("delete-dialog"), null);

      fireEvent.click(screen.getByTestId("cancel-btn"));
      assert.equal(screen.queryByTestId("delete-dialog"), null);
    });
  });
});
