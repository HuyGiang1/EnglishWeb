import React from "react";
export const navigations = [];
const router = { push: (url) => navigations.push(["push", url]), replace: (url) => navigations.push(["replace", url]) };
export const useRouter = () => router;
let pathname = "/settings";
export const setPathname = (value) => { pathname = value; };
export const usePathname = () => pathname;
export default function Link({ href, children, ...props }) {
  return React.createElement("a", { href, ...props }, children);
}
export const cookies = async () => ({ get: (name) => {
  const value = document.cookie.split("; ").find((cookie) => cookie.startsWith(`${name}=`))?.slice(name.length + 1);
  return value === undefined ? undefined : { value };
} });
export const Inter = () => ({ variable: "test-inter" });
export const JetBrains_Mono = () => ({ variable: "test-mono" });
