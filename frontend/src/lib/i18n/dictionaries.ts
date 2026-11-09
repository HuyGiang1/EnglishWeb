import { shellEn, shellVi } from "./shell-dictionaries";
import type { Locale } from "./config";

export const en = {
  ...shellEn,
  language: "Language",
  localeSaving: "Saving language…",
  localeSaveFailed: "Could not save language. Your last confirmed language was restored. Please try again.",
  tagline: "Learn English with focus",
  privacy: "Sign in securely to continue learning.",
  loginTitle: "Sign in",
  loginDescription: "Continue your English practice.",
  registerTitle: "Create account",
  registerDescription: "Start learning English at your own pace.",
  email: "Email",
  emailPlaceholder: "you@example.com",
  password: "Password",
  passwordPlaceholder: "Enter your password",
  displayName: "Your name",
  namePlaceholder: "Your name",
  confirmPassword: "Confirm password",
  confirmPlaceholder: "Enter your password again",
  showPassword: "Show password",
  hidePassword: "Hide password",
  signingIn: "Signing in…",
  creatingAccount: "Creating account…",
  noAccount: "New to EnglishFlow?",
  hasAccount: "Already have an account?",
  passwordHint: "Use 8–72 characters with uppercase, lowercase, and a number.",
  invalidEmail: "Enter a valid email address.",
  passwordRequired: "Enter your password.",
  nameShort: "Your name must have at least 2 characters.",
  nameLong: "Your name cannot exceed 60 characters.",
  passwordShort: "Use at least 8 characters.",
  passwordLong: "Use no more than 72 characters.",
  passwordStrength: "Include uppercase, lowercase, and a number.",
  passwordMismatch: "Passwords do not match.",
  invalidCredentials: "Email or password is incorrect.",
  emailRegistered: "An account with this email already exists. Try signing in.",
  validationFailed: "Check your details and try again.",
  forbidden: "Your security check expired. Reload the page and try again.",
  networkError: "Unable to connect. Check your connection and try again.",
  authFallback: "Unable to continue. Please try again.",
} as const;

export type TranslationKey = keyof typeof en;
type Dictionary = Record<TranslationKey, string>;

export const vi: Dictionary = {
  ...shellVi,
  language: "Ngôn ngữ",
  localeSaving: "Đang lưu ngôn ngữ…",
  localeSaveFailed: "Không lưu được ngôn ngữ. Đã khôi phục lựa chọn được xác nhận gần nhất. Vui lòng thử lại.",
  tagline: "Tập trung học tiếng Anh",
  privacy: "Đăng nhập an toàn để tiếp tục học.",
  loginTitle: "Đăng nhập",
  loginDescription: "Tiếp tục luyện tiếng Anh mỗi ngày.",
  registerTitle: "Tạo tài khoản",
  registerDescription: "Bắt đầu học tiếng Anh theo nhịp độ của bạn.",
  email: "Email",
  emailPlaceholder: "ban@example.com",
  password: "Mật khẩu",
  passwordPlaceholder: "Nhập mật khẩu",
  displayName: "Tên của bạn",
  namePlaceholder: "Tên của bạn",
  confirmPassword: "Xác nhận mật khẩu",
  confirmPlaceholder: "Nhập lại mật khẩu",
  showPassword: "Hiện mật khẩu",
  hidePassword: "Ẩn mật khẩu",
  signingIn: "Đang đăng nhập…",
  creatingAccount: "Đang tạo tài khoản…",
  noAccount: "Bạn chưa có tài khoản?",
  hasAccount: "Bạn đã có tài khoản?",
  passwordHint: "Dùng 8–72 ký tự, gồm chữ hoa, chữ thường và chữ số.",
  invalidEmail: "Nhập địa chỉ email hợp lệ.",
  passwordRequired: "Nhập mật khẩu của bạn.",
  nameShort: "Tên cần có ít nhất 2 ký tự.",
  nameLong: "Tên không được vượt quá 60 ký tự.",
  passwordShort: "Dùng ít nhất 8 ký tự.",
  passwordLong: "Dùng tối đa 72 ký tự.",
  passwordStrength: "Cần có chữ hoa, chữ thường và chữ số.",
  passwordMismatch: "Mật khẩu xác nhận không khớp.",
  invalidCredentials: "Email hoặc mật khẩu không đúng.",
  emailRegistered: "Email này đã có tài khoản. Hãy thử đăng nhập.",
  validationFailed: "Kiểm tra thông tin rồi thử lại.",
  forbidden: "Phiên kiểm tra bảo mật đã hết hạn. Tải lại trang rồi thử lại.",
  networkError: "Không thể kết nối. Kiểm tra mạng rồi thử lại.",
  authFallback: "Không thể tiếp tục. Vui lòng thử lại.",
};

export const dictionaries: Record<Locale, Partial<Dictionary>> = { en, "vi-VN": vi };

// Runtime fallback also protects callers reading keys from validation libraries.
export type TranslationParams = Record<string, string | number>;

export function translate(locale: Locale, key: string, params: TranslationParams = {}): string {
  const knownKey = Object.prototype.hasOwnProperty.call(en, key) ? key as TranslationKey : "authFallback";
  const text = dictionaries[locale][knownKey] ?? en[knownKey];
  return text.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : placeholder);
}
