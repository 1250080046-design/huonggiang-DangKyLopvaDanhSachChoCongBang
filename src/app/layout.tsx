import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {
  title: "Đăng ký lớp học",
  description: "Hệ thống đăng ký lớp học và danh sách chờ"
};
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi"><body>{children}</body></html>;
}
