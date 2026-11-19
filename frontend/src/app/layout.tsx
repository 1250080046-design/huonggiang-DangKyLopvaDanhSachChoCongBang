import type { Metadata } from "next";
import "./globals.css";
import "./school-brand.css";
export const metadata: Metadata = {
  title: "Đăng ký lớp học",
  icons: { icon: "/school-building-icon-clean.png" },
  description: "Hệ thống đăng ký lớp học và danh sách chờ"
};
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="vi"><body>{children}</body></html>;
}
