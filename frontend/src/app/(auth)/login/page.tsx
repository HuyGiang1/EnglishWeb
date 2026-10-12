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

const loginSchema = z.object({
  email: z.string().trim().email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const { login, user, isLoading: authLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: "",
      password: "",
    },
  });

  // Redirect if already authenticated
  useEffect(() => {
    if (!authLoading && user) {
      router.replace("/dashboard");
    }
  }, [user, authLoading, router]);

  const onSubmit = async (values: LoginFormValues) => {
    setServerError(null);
    try {
      await login(values);
      router.push("/dashboard");
    } catch (err) {
      if (err instanceof ApiError) {
        setServerError(err.message || "Invalid email or password.");
      } else {
        setServerError("An unexpected error occurred. Please try again.");
      }
    }
  };

  return (
    <Card className="border-border bg-card shadow-xs">
      <CardHeader className="space-y-1">
        <CardTitle className="text-xl font-bold tracking-tight text-foreground">
          Sign In
        </CardTitle>
        <CardDescription className="text-xs text-muted-foreground">
          Enter your email and password to access your learning workspace.
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
            <div className="flex items-center justify-between">
              <Label htmlFor="password" className="text-xs font-medium text-foreground">
                Password
              </Label>
            </div>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
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
            {errors.password && (
              <p id="password-error" className="text-[11px] text-destructive">
                {errors.password.message}
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
                Authenticating...
              </>
            ) : (
              "Sign In"
            )}
          </Button>
        </form>
      </CardContent>
      <CardFooter className="flex justify-center border-t border-border pt-4 text-xs text-muted-foreground">
        <span>Don&apos;t have an account? </span>
        <Link
          href="/register"
          className="ml-1 font-semibold text-foreground underline underline-offset-4 hover:text-foreground/80"
        >
          Create one
        </Link>
      </CardFooter>
    </Card>
  );
}
