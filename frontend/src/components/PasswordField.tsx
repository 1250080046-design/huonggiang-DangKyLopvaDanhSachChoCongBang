"use client";

import { useId, useState, type InputHTMLAttributes } from "react";

type PasswordFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & {
  label: string;
};

/** Reusable accessible password field; values stay controlled by the parent form. */
export default function PasswordField({ label, id, disabled, ...inputProps }: PasswordFieldProps) {
  const generatedId = useId();
  const inputId = id || generatedId;
  const [visible, setVisible] = useState(false);

  return <div className="password-input-group">
    <label htmlFor={inputId}>{label}</label>
    <div className="password-field">
      <input {...inputProps} id={inputId} disabled={disabled} type={visible ? "text" : "password"} />
      <button
        className="password-visibility-toggle"
        type="button"
        disabled={disabled}
        aria-label={`${visible ? "Ẩn" : "Hiện"} ${label.toLocaleLowerCase("vi-VN")}`}
        aria-pressed={visible}
        onClick={() => setVisible(current => !current)}
      >
        {visible ? "Ẩn" : "Hiện"}
      </button>
    </div>
  </div>;
}
