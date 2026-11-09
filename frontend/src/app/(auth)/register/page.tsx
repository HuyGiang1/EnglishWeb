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
import { authErrorKey } from "@/lib/auth/auth-errors";
import { useLocale } from "@/lib/i18n/locale-provider";
import { translate, type TranslationKey } from "@/lib/i18n/dictionaries";
import { LanguageSwitcher } from "@/components/auth/language-switcher";

const registerSchema = z
  .object({
    displayName: z
      .string()
      .trim()
      .min(2, "nameShort")
      .max(60, "nameLong"),
    email: z
      .string()
      .trim()
      .email("invalidEmail"),
    password: z
      .string()
      .min(8, "passwordShort")
      .max(72, "passwordLong")
      .regex(
        /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/,
        "passwordStrength"
      ),
    confirmPassword: z.string(),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "passwordMismatch",
    path: ["confirmPassword"],
  });

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const { locale, t } = useLocale();
  const { register: registerUser, user, isLoading: authLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<TranslationKey | null>(null);

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
      setServerError(authErrorKey(err));
    }
  };

  return (
    <Card className="border-border bg-card shadow-xs">
      <CardHeader className="space-y-3">
        <LanguageSwitcher />
        <CardTitle className="text-xl font-bold tracking-tight text-foreground">
          <h1>{t("registerTitle")}</h1>
        </CardTitle>
        <CardDescription className="text-xs text-muted-foreground">
          {t("registerDescription")}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {serverError && (
          <div
            role="alert"
            className="mb-4 flex items-start gap-2.5 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive"
          >
            <AlertCircle className="size-4 shrink-0 mt-0.5" />
            <span>{t(serverError)}</span>
          </div>
        )}

        <form noValidate aria-busy={isSubmitting} onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="displayName" className="text-xs font-medium text-foreground">
              {t("displayName")}
            </Label>
            <Input
              id="displayName"
              type="text"
              autoComplete="name"
              placeholder={t("namePlaceholder")}
              disabled={isSubmitting}
              className={errors.displayName ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.displayName}
              aria-describedby={errors.displayName ? "displayName-error" : undefined}
              {...register("displayName")}
            />
            {errors.displayName && (
              <p id="displayName-error" className="text-[11px] text-destructive">
                {translate(locale, errors.displayName.message ?? "authFallback")}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="email" className="text-xs font-medium text-foreground">
              {t("email")}
            </Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder={t("emailPlaceholder")}
              disabled={isSubmitting}
              className={errors.email ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.email}
              aria-describedby={errors.email ? "email-error" : undefined}
              {...register("email")}
            />
            {errors.email && (
              <p id="email-error" className="text-[11px] text-destructive">
                {translate(locale, errors.email.message ?? "authFallback")}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password" className="text-xs font-medium text-foreground">
              {t("password")}
            </Label>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                placeholder={t("passwordPlaceholder")}
                disabled={isSubmitting}
                className={`pr-11 ${errors.password ? "border-destructive focus-visible:ring-destructive/20" : ""}`}
                aria-invalid={!!errors.password}
                aria-describedby={errors.password ? "password-error" : undefined}
                {...register("password")}
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                className="absolute right-0.5 top-1/2 -translate-y-1/2 flex size-9 items-center justify-center text-muted-foreground hover:text-foreground transition-colors rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2"
                aria-label={showPassword ? t("hidePassword") : t("showPassword")}
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
                {translate(locale, errors.password.message ?? "authFallback")}
              </p>
            ) : (
              <p className="text-[11px] text-muted-foreground">
                {t("passwordHint")}
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword" className="text-xs font-medium text-foreground">
              {t("confirmPassword")}
            </Label>
            <Input
              id="confirmPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder={t("confirmPlaceholder")}
              disabled={isSubmitting}
              className={errors.confirmPassword ? "border-destructive focus-visible:ring-destructive/20" : ""}
              aria-invalid={!!errors.confirmPassword}
              aria-describedby={errors.confirmPassword ? "confirmPassword-error" : undefined}
              {...register("confirmPassword")}
            />
            {errors.confirmPassword && (
              <p id="confirmPassword-error" className="text-[11px] text-destructive">
                {translate(locale, errors.confirmPassword.message ?? "authFallback")}
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
                {t("creatingAccount")}
              </>
            ) : (
              t("registerTitle")
            )}
          </Button>
        </form>
      </CardContent>
      <CardFooter className="flex flex-wrap justify-center gap-y-1 border-t border-border pt-4 text-xs text-muted-foreground">
        <span>{t("hasAccount")} </span>
        <Link
          href="/login"
          className="ml-1 font-semibold text-foreground underline underline-offset-4 hover:text-foreground/80"
        >
          {t("loginTitle")}
        </Link>
      </CardFooter>
    </Card>
  );
}
