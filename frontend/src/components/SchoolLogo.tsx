import Image from "next/image";

/** Shared school emblem for the sign-in page and application navigation. */
export default function SchoolLogo() {
  return <Image className="school-logo" src="/school-building-icon-clean.png" alt="Biểu trưng ngôi trường" width={64} height={64} priority />;
}
