import { NextRequest, NextResponse } from "next/server";
import { createStudentListXlsx } from "@/lib/simple-xlsx";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

type Student = {studentId?: number | string | null; fullName?: string | null; email?: string | null; faculty?: string | null; registeredAt?: string | null};
const backend = (process.env.API_PROXY_TARGET || "http://127.0.0.1:8081").replace(/\/$/, "");

export async function GET(request: NextRequest, {params}: {params: Promise<{id: string}>}) {
  const {id} = await params;
  if (!/^[1-9]\d{0,14}$/.test(id)) return NextResponse.json({message:"Mã lớp không hợp lệ."}, {status:400});

  try {
    // Forward HttpOnly login cookie to the existing ADMIN-protected Spring Boot API.
    // The browser never reads or stores the JWT in JavaScript.
    const response = await fetch(`${backend}/api/admin/classes/${id}/students`, {
      headers: {cookie:request.headers.get("cookie") || "", accept:"application/json"},
      cache:"no-store", redirect:"manual", signal:AbortSignal.timeout(20000)
    });
    if (!response.ok) {
      const message = response.status === 401 ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : response.status === 403 ? "Tài khoản không có quyền quản trị để xuất danh sách."
        : response.status === 404 ? "Không tìm thấy lớp hoặc API danh sách sinh viên trên backend."
        : `Không thể lấy danh sách sinh viên từ backend (${response.status}).`;
      return NextResponse.json({message}, {status:response.status >= 400 && response.status <= 599 ? response.status : 502});
    }
    const data: unknown = await response.json();
    if (!Array.isArray(data) || data.length > 20000 || !data.every(x => x && typeof x === "object" && "studentId" in x && "fullName" in x)) {
      return NextResponse.json({message:"Dữ liệu danh sách sinh viên không hợp lệ hoặc vượt giới hạn 20.000 dòng."}, {status:502});
    }
    const students = data as Student[];
    const rows: (string | number)[][] = [
      ["STT", "Mã sinh viên", "Họ và tên", "Email", "Khoa", "Ngày đăng ký"],
      ...students.map((student, index) => [
        index + 1, displayStudentCode(student.email, student.studentId), student.fullName ?? "", student.email ?? "",
        student.faculty ?? "", formatDate(student.registeredAt)
      ])
    ];
    const xlsx = createStudentListXlsx(rows);
    return new NextResponse(new Uint8Array(xlsx), {
      headers: {
        "Content-Type":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "Content-Disposition":`attachment; filename="danh_sach_sinh_vien_lop_${id}.xlsx"`,
        "Cache-Control":"private, no-store"
      }
    });
  } catch {
    return NextResponse.json({message:"Không thể kết nối backend để xuất Excel. Kiểm tra Spring Boot tại cổng 8081."}, {status:502});
  }
}

function formatDate(value?: string | null): string {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("vi-VN", {dateStyle:"short",timeStyle:"short",timeZone:"Asia/Ho_Chi_Minh"});
}

function displayStudentCode(email?: string | null, id?: string | number | null): string {
  const prefix=(email || "").split("@")[0]?.trim() || "";
  return /^\d{6,20}$/.test(prefix) ? prefix : String(id ?? "");
}
