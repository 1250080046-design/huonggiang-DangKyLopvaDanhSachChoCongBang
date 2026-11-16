"use client";
import { FormEvent, useEffect, useState } from "react";
import { api } from "@/lib/api";
import Link from "next/link";
import PasswordField from "@/components/PasswordField";
export default function ActivatePage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState(""); const [confirm, setConfirm] = useState(""); const [message, setMessage] = useState(""); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  useEffect(() => { setToken(new URLSearchParams(window.location.search).get("token") || ""); }, []);
  async function submit(e: FormEvent) { e.preventDefault(); setError(""); setMessage(""); if (!token) return setError("Liên kết kích hoạt thiếu token."); if (password !== confirm) return setError("Mật khẩu xác nhận không trùng khớp."); setBusy(true); try { await api("/api/auth/csrf"); const r = await api<{ message: string }>("/api/auth/activate", null, { method: "POST", body: JSON.stringify({ token, newPassword: password }) }); setMessage(r.message); } catch (err) { setError(err instanceof Error ? err.message : "Có lỗi xảy ra."); } finally { setBusy(false); } }
  return <main className="activate-wrap"><form className="login-card" onSubmit={submit}><p className="eyebrow">KÍCH HOẠT TÀI KHOẢN</p><h1>Tạo mật khẩu mới</h1><PasswordField label="Mật khẩu mới" minLength={8} required value={password} onChange={e => setPassword(e.target.value)} autoComplete="new-password" /><PasswordField label="Xác nhận mật khẩu" minLength={8} required value={confirm} onChange={e => setConfirm(e.target.value)} autoComplete="new-password" />{error && <p className="notice error">{error}</p>}{message && <p className="notice success">{message}</p>}<button className="primary full" disabled={busy}>{busy ? "Đang kích hoạt..." : "Kích hoạt tài khoản"}</button><Link className="text-link" href="/">Quay về đăng nhập</Link></form></main>;
}
