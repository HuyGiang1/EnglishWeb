import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { z } from "zod";

// 1. Login schema mirror
const loginSchema = z.object({
  email: z.string().trim().email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

// 2. Register schema mirror
const registerSchema = z
  .object({
    displayName: z
      .string()
      .trim()
      .min(2, "Display name must be at least 2 characters")
      .max(60, "Display name cannot exceed 60 characters"),
    email: z
      .string()
      .trim()
      .email("Please enter a valid email address"),
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(72, "Password cannot exceed 72 characters")
      .regex(
        /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/,
        "Must contain uppercase, lowercase, and a number"
      ),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

describe("Frontend Login Form Validation", () => {
  test("Valid login credentials succeed", () => {
    const result = loginSchema.safeParse({
      email: "learner@example.com",
      password: "SecurePassword123!",
    });
    assert.strictEqual(result.success, true);
  });

  test("Invalid email format fails validation", () => {
    const result = loginSchema.safeParse({
      email: "not-an-email",
      password: "SecurePassword123!",
    });
    assert.strictEqual(result.success, false);
    assert.match(result.error.issues[0].message, /valid email address/i);
  });

  test("Missing password fails validation", () => {
    const result = loginSchema.safeParse({
      email: "learner@example.com",
      password: "",
    });
    assert.strictEqual(result.success, false);
    assert.match(result.error.issues[0].message, /Password is required/i);
  });
});

describe("Frontend Registration Form Validation", () => {
  test("Valid registration payload succeeds", () => {
    const result = registerSchema.safeParse({
      displayName: "Minh Tran",
      email: "minh@example.com",
      password: "SecurePassword123!",
      confirmPassword: "SecurePassword123!",
    });
    assert.strictEqual(result.success, true);
  });

  test("Short display name (< 2 chars) fails validation", () => {
    const result = registerSchema.safeParse({
      displayName: "M",
      email: "minh@example.com",
      password: "SecurePassword123!",
      confirmPassword: "SecurePassword123!",
    });
    assert.strictEqual(result.success, false);
    assert.match(result.error.issues[0].message, /at least 2 characters/i);
  });

  test("Weak password lacking numbers or uppercase fails", () => {
    const result = registerSchema.safeParse({
      displayName: "Minh Tran",
      email: "minh@example.com",
      password: "alllowercaseonly",
      confirmPassword: "alllowercaseonly",
    });
    assert.strictEqual(result.success, false);
    assert.match(result.error.issues[0].message, /uppercase, lowercase, and a number/i);
  });

  test("Mismatched confirmPassword fails validation", () => {
    const result = registerSchema.safeParse({
      displayName: "Minh Tran",
      email: "minh@example.com",
      password: "SecurePassword123!",
      confirmPassword: "DifferentPassword123!",
    });
    assert.strictEqual(result.success, false);
    assert.match(result.error.issues[0].message, /Passwords do not match/i);
  });
});

describe("Authentication API Error Model", () => {
  test("RFC 7807 error envelopes map to ApiError correctly", () => {
    class ApiError extends Error {
      constructor(status, message, code, payload) {
        super(message);
        this.status = status;
        this.code = code;
        this.rawResponse = payload;
      }
    }

    const payload = {
      code: "EMAIL_ALREADY_REGISTERED",
      message: "An account with this email address already exists.",
      status: 409,
    };

    const error = new ApiError(payload.status, payload.message, payload.code, payload);
    assert.strictEqual(error.status, 409);
    assert.strictEqual(error.code, "EMAIL_ALREADY_REGISTERED");
    assert.strictEqual(error.message, "An account with this email address already exists.");
  });

  test("CSRF token regex extractor handles standard cookie header strings", () => {
    function getXsrfFromCookieString(cookieStr) {
      const match = cookieStr.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
      return match ? decodeURIComponent(match[1]) : null;
    }

    const cookieHeader = "JSESSIONID=abc12345; XSRF-TOKEN=my-csrf-secret-999; other=123";
    const token = getXsrfFromCookieString(cookieHeader);
    assert.strictEqual(token, "my-csrf-secret-999");
  });
});
