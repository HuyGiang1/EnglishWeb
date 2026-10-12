"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Eye, EyeOff, Loader2, AlertCircle } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";

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

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const { register: registerUser, user, isLoading: authLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      displayName: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  // Redirect if already authenticated
  useEffect(() => {
    if (!authLoading && user) {
      router.replace("/dashboard");
    }
  }, [user, authLoading, router]);

  const onSubmit = async (values: RegisterFormValues) => {
    setServerError(null);
    try {
      const detectedTz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
      await registerUser({
        email: values.email,
        password: values.password,
        displayName: values.displayName,
        timezone: detectedTz,
      });
      router.push("/dashboard");
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          setServerError("An account with this email address already exists.");
        } else {
          setServerError(err.message || "Registration failed. Please check your inputs.");
        }
      } else {
        setServerError("An unexpected error occurred. Please try again.");
      }
    }
  };

  return (
    <Card className="border-border bg-card shadow-xs">
      <CardHeader className="space-y-1">
        <CardTitle className="text-xl font-bold tracking-tight text-foreground">
          Create Account
        </CardTitle>
        <CardDescription className="text-xs text-muted-foreground">
          Sign up to begin your spaced repetition language acquisition journey.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {serverError && (
          <div
            role="alert"
            className="mb-4 flex items-start gap-2.5 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive"
          >
            <AlertCircle className="size-4 shrink-0 mt-0.5" />
            <span>{serverError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="displayName" className="text-xs font-medium text-foreground">
              Display Name
            </Label>
            <Input
              id="displayName"
              type="text"
              autoComplete="name"
              placeholder="Minh Tran"
              disabled={isSubmitting}
              className={errors.displayName ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.displayName}
              aria-describedby={errors.displayName ? "displayName-error" : undefined}
              {...register("displayName")}
            />
            {errors.displayName && (
              <p id="displayName-error" className="text-[11px] text-destructive">
                {errors.displayName.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="email" className="text-xs font-medium text-foreground">
              Email Address
            </Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              disabled={isSubmitting}
              className={errors.email ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? "email-error" : undefined}
              {...register("email")}
            />
            {errors.email && (
              <p id="email-error" className="text-[11px] text-destructive">
                {errors.email.message}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password" className="text-xs font-medium text-foreground">
              Password
            </Label>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                placeholder="••••••••"
                disabled={isSubmitting}
                className={`pr-9 ${errors.password ? "border-destructive focus-visible:ring-destructive/20" : ""}`}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? "password-error" : undefined}
                {...register("password")}
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                aria-label={showPassword ? "Hide password" : "Show password"}
                tabIndex={-1}
              >
                {showPassword ? (
                  <EyeOff className="size-4" />
                ) : (
                  <Eye className="size-4" />
                )}
              </button>
            </div>
            {errors.password ? (
              <p id="password-error" className="text-[11px] text-destructive">
                {errors.password.message}
              </p>
            ) : (
              <p className="text-[11px] text-muted-foreground">
                Minimum 8 characters with uppercase, lowercase, and number.
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword" className="text-xs font-medium text-foreground">
              Confirm Password
            </Label>
            <Input
              id="confirmPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder="••••••••"
              disabled={isSubmitting}
              className={errors.confirmPassword ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.confirmPassword}
              aria-describedby={errors.confirmPassword ? "confirmPassword-error" : undefined}
              {...register("confirmPassword")}
            />
            {errors.confirmPassword && (
              <p id="confirmPassword-error" className="text-[11px] text-destructive">
                {errors.confirmPassword.message}
              </p>
            )}
          </div>

          <Button
            type="submit"
            className="w-full text-xs font-semibold h-9"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 size-3.5 animate-spin" />
                Creating Account...
              </>
            ) : (
              "Create Account"
            )}
          </Button>
        </form>
      </CardContent>
      <CardFooter className="flex justify-center border-t border-border pt-4 text-xs text-muted-foreground">
        <span>Already have an account? </span>
        <Link
          href="/login"
          className="ml-1 font-semibold text-foreground underline underline-offset-4 hover:text-foreground/80"
        >
          Sign in
        </Link>
      </CardFooter>
    </Card>
  );
}
