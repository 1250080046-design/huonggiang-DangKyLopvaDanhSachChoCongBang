export const API_URL = "/backend";

// The JWT remains exclusively in an HttpOnly cookie. Only the non-secret CSRF token is held in memory.
let csrfToken: string | null = null;
let csrfRequest: Promise<string> | null = null;

async function acquireCsrf(force = false): Promise<string> {
  if (force) { csrfToken = null; csrfRequest = null; }
  if (csrfToken) return csrfToken;
  if (!csrfRequest) {
    csrfRequest = (async () => {
      const response = await fetch(`${API_URL}/api/auth/csrf`, { credentials: "include", cache: "no-store" });
      if (!response.ok) throw new Error(`Không lấy được CSRF token (${response.status}).`);
      const data: { token?: string } = await response.json();
      if (!data.token) throw new Error("Backend không trả về CSRF token. Hãy cập nhật backend cùng bản vá.");
      // In the browser the CSRF token must also be present as a same-origin cookie.
      // A stale/cross-host cookie can otherwise cause unexplained 403 responses.
      if (typeof document !== "undefined") {
        const cookie = document.cookie.split("; ").find(item => item.startsWith("XSRF-TOKEN="));
        if (!cookie) throw new Error("Thiếu cookie XSRF-TOKEN. Vui lòng sử dụng http://localhost:3000 và đăng nhập lại.");
        const cookieToken = decodeURIComponent(cookie.slice("XSRF-TOKEN=".length));
        if (cookieToken !== data.token) {
          // The server validates the header against the CSRF cookie; a mismatch cannot succeed.
          throw new Error("CSRF token và cookie không khớp. Hãy xóa cookie của localhost rồi tải lại trang.");
        }
      }
      csrfToken = data.token;
      return data.token;
    })().finally(() => { csrfRequest = null; });
  }
  return csrfRequest;
}

export async function api<T>(path: string, _unused?: string | null, init: RequestInit = {}): Promise<T> {
  const method = (init.method || "GET").toUpperCase();
  const unsafe = !["GET", "HEAD", "OPTIONS"].includes(method);
  const perform = async (freshToken: boolean): Promise<Response> => {
    const headers = new Headers(init.headers || {});
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (unsafe) headers.set("X-XSRF-TOKEN", await acquireCsrf(freshToken));
    return fetch(`${API_URL}${path}`, { ...init, headers, cache: "no-store", credentials: "include" });
  };
  // Logout can happen after hours of inactivity. Always sync CSRF with its cookie first.
  let response = await perform(path === "/api/auth/logout");
  // If the cookie expired/was regenerated, re-fetch it once. Never disable CSRF protection.
  if (unsafe && response.status === 403) response = await perform(true);
  const body = await response.text();
  let data: unknown = null;
  try { data = body ? JSON.parse(body) : null; } catch { data = body; }
  if (!response.ok) {
    const message = typeof data === "object" && data && "message" in data
      ? String((data as { message: unknown }).message)
      : response.status === 403 ? "Truy cập bị từ chối (403). Kiểm tra phiên đăng nhập, quyền tài khoản và CSRF. Hãy tải lại trang và đăng nhập lại nếu cần."
      : response.status === 401 ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
      : response.status === 404 && path.startsWith("/api/admin/import/")
        ? "API nhập Excel/CSV chưa tồn tại trên backend đang chạy (404). Hãy dừng backend cũ tại cổng 8081, chạy lại backend từ mã nguồn V20.39 trở lên bằng mvn clean spring-boot:run, sau đó tải lại trang."
      : `Yêu cầu thất bại (${response.status}).`;
    throw new Error(message);
  }
  // Public CSRF endpoint can be invoked explicitly; keep the in-memory state aligned.
  if (path === "/api/auth/csrf" && data && typeof data === "object" && "token" in data)
    csrfToken = String((data as { token: string }).token);
  if (path === "/api/auth/login" || path === "/api/auth/logout") csrfToken = null;
  return data as T;
}

export type ClassView = {
  id: number; name: string; courseCode: string; courseName: string; term: string; teacherName: string; teacherId: number;
  room: string | null; schedule: string | null; capacity: number; enrolled: number; remaining: number; status: "OPEN" | "CLOSED" | "CANCELLED";
};
export type StudyScheduleItem = { registrationId: number; classId: number; className: string; courseCode: string; courseName: string; credits: number; term: string; teacherName: string; room: string | null; schedule: string | null };
export type StudySchedule = { currentTerm: string; classes: StudyScheduleItem[] };
export type RegistrationView = { id: number; classId: number; className: string; courseCode: string; courseName: string; registeredAt: string; status: "ACTIVE" | "CANCELLED" };
export type WaitlistView = { id: number; classId: number; className: string; courseCode: string; courseName: string; joinedAt: string; status: "WAITING" | "PROMOTED" | "WITHDRAWN" | "INELIGIBLE"; position: number; ineligibleReason?: string | null; updatedAt?: string | null };
export type LoginResponse = { token: null; email: string; role: "ADMIN" | "STUDENT" | "TEACHER"; displayName: string; mustChangePassword: boolean };
export type ProfileView = { id: number; username: string | null; email: string; fullName: string; role: "ADMIN" | "STUDENT" | "TEACHER"; faculty: string | null; major: string | null; cohort: string | null; accountStatus: string; learningStatus: string | null; dateOfBirth: string | null };
export type ConversationView = { id: number; userEmail: string; userName: string; status: string; updatedAt: string };
export type MessageView = { id: number; conversationId: number; senderEmail: string; senderRole: string; content: string; sentAt: string };
