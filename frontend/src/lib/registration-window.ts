/**
 * Status visible in the UI; distinct from registrationOpen, which records an
 * administrator's saved configuration in SQL Server.
 *
 * API dates are Java LocalDateTime strings without an offset. They are read as
 * browser-local times (same local timezone as the deployed school system).
 * The Spring Boot RegistrationTermPolicy remains the authority for writes.
 */
export type RegistrationWindow = {
  registrationOpen: boolean;
  registrationStartsAt: string | null;
  registrationEndsAt: string | null;
};

export type RegistrationWindowStatus = "closed" | "upcoming" | "open" | "expired";

export function getRegistrationWindowStatus(term: RegistrationWindow, nowMs: number = Date.now()): RegistrationWindowStatus {
  if (!term.registrationOpen) return "closed";
  const starts = term.registrationStartsAt ? new Date(term.registrationStartsAt).getTime() : null;
  const ends = term.registrationEndsAt ? new Date(term.registrationEndsAt).getTime() : null;
  // Match backend's end-inclusive canRegister() logic.
  if (ends !== null && Number.isFinite(ends) && nowMs > ends) return "expired";
  if (starts !== null && Number.isFinite(starts) && nowMs < starts) return "upcoming";
  return "open";
}

export const registrationWindowLabels: Record<RegistrationWindowStatus, string> = {
  closed: "Đã đóng",
  upcoming: "Chưa đến giờ mở",
  open: "Đang mở đăng ký",
  expired: "Đã hết hạn đăng ký"
};
