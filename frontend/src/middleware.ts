import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Next.js Edge Session Guard.
 * Provides pre-render routing protection by inspecting the presence
 * of the authoritative Spring Security JSESSIONID cookie.
 *
 * Security Boundary:
 * 1. Edge Guard: Fast non-cryptographic cookie existence check to redirect unauthenticated navigation.
 * 2. Origin Authority: Spring Boot backend validates session authenticity against PostgreSQL on every API call.
 */
export function middleware(request: NextRequest) {
  const sessionCookie = request.cookies.get("JSESSIONID");
  const pathname = request.nextUrl.pathname;

  if (pathname.startsWith("/media/")) {
    return new NextResponse(null, { status: 404 });
  }

  const isProtected =
    pathname.startsWith("/dashboard") ||
    pathname.startsWith("/vocabulary") ||
    pathname.startsWith("/collections") ||
    pathname.startsWith("/review") ||
    pathname.startsWith("/flashcards") ||
    pathname.startsWith("/dictation") ||
    pathname.startsWith("/shadowing") ||
    pathname.startsWith("/quiz") ||
    pathname.startsWith("/settings");

  if (isProtected && !sessionCookie) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("from", pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/vocabulary/:path*",
    "/collections/:path*",
    "/review/:path*",
    "/flashcards/:path*",
    "/dictation/:path*",
    "/shadowing/:path*",
    "/quiz/:path*",
    "/settings/:path*",
    "/media/:path*",
  ],
};
