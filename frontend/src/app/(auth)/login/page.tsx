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

const loginSchema = z.object({
  email: z.string().trim().email("invalidEmail"),
  password: z.string().min(1, "passwordRequired"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const { locale, t } = useLocale();
  const { login, user, isLoading: authLoading } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<TranslationKey | null>(null);

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
      setServerError(authErrorKey(err));
    }
  };

  return (
    <Card className="border-border bg-card shadow-xs">
      <CardHeader className="space-y-3">
        <LanguageSwitcher />
        <CardTitle className="text-xl font-bold tracking-tight text-foreground">
          <h1>{t("loginTitle")}</h1>
        </CardTitle>
        <CardDescription className="text-xs text-muted-foreground">
          {t("loginDescription")}
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
            <div className="flex items-center justify-between">
              <Label htmlFor="password" className="text-xs font-medium text-foreground">
                {t("password")}
              </Label>
            </div>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
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
            {errors.password && (
              <p id="password-error" className="text-[11px] text-destructive">
                {translate(locale, errors.password.message ?? "authFallback")}
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
                {t("signingIn")}
              </>
            ) : (
              t("loginTitle")
            )}
          </Button>
        </form>
      </CardContent>
      <CardFooter className="flex flex-wrap justify-center gap-y-1 border-t border-border pt-4 text-xs text-muted-foreground">
        <span>{t("noAccount")} </span>
        <Link
          href="/register"
          className="ml-1 font-semibold text-foreground underline underline-offset-4 hover:text-foreground/80"
        >
          {t("registerTitle")}
        </Link>
      </CardFooter>
    </Card>
  );
}
