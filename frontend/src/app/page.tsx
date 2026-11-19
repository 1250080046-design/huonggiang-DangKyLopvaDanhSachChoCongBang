"use client";
import { FormEvent, ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { api, API_URL, ClassView, RegistrationView, WaitlistView, LoginResponse, ConversationView, MessageView, ProfileView, StudySchedule } from "@/lib/api";
import PasswordField from "@/components/PasswordField";
import SchoolLogo from "@/components/SchoolLogo";
import { getRegistrationWindowStatus, registrationWindowLabels } from "@/lib/registration-window";
import { classStatusDescription, classStatusLabel } from "@/lib/class-status";
type StudyProgressView = { status:"ON_TRACK"|"DELAYED"|"SUSPENDED"|"RETURNED"; effectiveYear:number|null; inferredYear:number; note:string|null; retakeCourseCodes:string[] };
const studyStatusLabels:Record<StudyProgressView["status"],string> = {
  ON_TRACK:"Đúng tiến độ", DELAYED:"Học chậm tiến độ", SUSPENDED:"Bảo lưu", RETURNED:"Quay lại học"
};
type CourseView = { id:number; code:string; name:string; credits:number; year:number | null; semester:number | null; major:string | null };
type Summary = { accounts: number; classes: number; activeRegistrations: number; waitingStudents: number };
type Options = { courses: { id: number; label: string }[]; terms: { id: number; label: string }[]; teachers: { id: number; label: string }[] };
type StudentRow = { studentId: number; fullName: string; email: string; faculty: string; registeredAt: string };
// Mã số sinh viên hiển thị là phần số trong email trường, không phải ID nội bộ SQL Server.
const studentCode = (email: string | null | undefined, id: number | string) => {
  const local = (email || "").split("@")[0]?.trim() || "";
  return /^\d{6,20}$/.test(local) ? local : String(id);
};
type PeopleImportSummary = { total:number; created:number; skipped:number; ignoredRole:number; failed:number; errors:{row:number;reason:string}[];message:string };
type AdminWaitlistItem = { id: number; classId: number; className: string; studentId: number; studentName: string; studentEmail: string; joinedAt: string; status: string; position: number };
type DecisionItem = { id: number; classId: number; studentId: number | null; waitlistId: number | null; registrationId: number | null; occurredAt: string; result: string; reason: string | null; className: string | null; courseCode: string | null; courseName: string | null; studentName: string | null; studentEmail: string | null };
const decisionResultLabels: Record<string, string> = { REGISTERED: "Đăng ký thành công", WAITLISTED: "Đã vào danh sách chờ", CANCELLED: "Đã hủy đăng ký", WAITLIST_WITHDRAWN: "Đã rút khỏi danh sách chờ", SKIPPED_INELIGIBLE: "Bỏ qua – không đủ điều kiện", PROMOTED_ROUND_ROBIN: "Được cấp chỗ (Round Robin)" };
const decisionResultLabel = (value: string) => decisionResultLabels[value] || value.replaceAll("_", " ");
type Tab = "overview" | "schedule" | "profile" | "program" | "catalog" | "password" | "students" | "teachers" | "classes" | "registrations" | "waitlist" | "admin" | "create-class" | "accounts" | "admin-waitlist" | "decisions" | "teacher" | "teacher-students" | "chat" | "admin-registrations" | "terms";
type AdminEditor = "person" | "course" | "term" | "class" | null;
type UserRole = "ADMIN" | "STUDENT" | "TEACHER";
const PAGE_ROUTES: Record<UserRole, Partial<Record<Tab,string>>> = {
  ADMIN: {admin:"/admin/overview",students:"/admin/students",teachers:"/admin/teachers",catalog:"/admin/courses",program:"/admin/curriculum","create-class":"/admin/classes","admin-registrations":"/admin/registrations",terms:"/admin/terms","admin-waitlist":"/admin/waitlist",decisions:"/admin/decisions",chat:"/admin/support",password:"/admin/change-password"},
  STUDENT: {overview:"/student/overview",classes:"/student/courses",program:"/student/curriculum",registrations:"/student/registrations",waitlist:"/student/waitlist",schedule:"/student/schedule",chat:"/student/support",profile:"/student/profile",password:"/student/change-password"},
  TEACHER: {overview:"/teacher/overview",program:"/teacher/curriculum",teacher:"/teacher/classes","teacher-students":"/teacher/students",schedule:"/teacher/schedule",profile:"/teacher/profile",password:"/teacher/change-password"}
};
const initialTab = (role:UserRole):Tab => role === "ADMIN" ? "admin" : "overview";
function tabFromAddress(role:UserRole):Tab {
  if (typeof window === "undefined") return initialTab(role);
  const result = Object.entries(PAGE_ROUTES[role]).find(([,route]) => route === window.location.pathname);
  return (result?.[0] as Tab | undefined) || initialTab(role);
}
/** Select only values currently present in SQL Server; allow a new legitimate value when needed. */
function DatabaseChoice({value, choices, onChange, label, allowOther = true}: {
  value:string; choices:string[]; onChange:(value:string)=>void; label:string; allowOther?:boolean;
}) {
  const [custom, setCustom] = useState(false);
  const isOther = custom || (value !== "" && !choices.includes(value));
  return <div className="database-choice">
    <select aria-label={label} value={isOther ? "__custom__" : value} onChange={e=>{
      if (e.target.value === "__custom__") { setCustom(true); onChange(""); }
      else { setCustom(false); onChange(e.target.value); }
    }}>
      <option value="">Tất cả / Chọn {label.toLowerCase()}</option>
      {choices.map(c=><option value={c} key={c}>{c}</option>)}
      {allowOther && <option value="__custom__">Khác (nhập mới)...</option>}
    </select>
    {isOther && allowOther && <input maxLength={150} aria-label={`Nhập ${label.toLowerCase()} khác`} placeholder={`Nhập ${label.toLowerCase()}`} value={value} onChange={e=>onChange(e.target.value)}/>}
  </div>;
}

const fmt = (value: string) => new Date(value).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
const MAJOR_DISPLAY_ORDER = ["Công nghệ thông tin", "Hệ thống thông tin", "Khoa học máy tính", "Kỹ thuật phần mềm", "An toàn thông tin", "Kỹ thuật dữ liệu"] as const;
const COURSE_MAJOR_BY_CODE: Record<string, string> = {
  AI301: "Kỹ thuật dữ liệu",
  ML302: "Kỹ thuật dữ liệu",
  BIG302: "Kỹ thuật dữ liệu",
  NLP302: "Kỹ thuật dữ liệu",
  IMG401: "Kỹ thuật dữ liệu",
  DATA401: "Kỹ thuật dữ liệu",
  SEC301: "An toàn thông tin",
  SEC402: "An toàn thông tin",
  // CO3010 (Công nghệ phần mềm) là học phần nền tảng năm 2 trong danh mục mẫu:
  // để dùng chung cho các ngành CNTT thay vì gắn riêng Kỹ thuật phần mềm.
  SE301: "Kỹ thuật phần mềm",
  WEB302: "Kỹ thuật phần mềm",
  APP302: "Kỹ thuật phần mềm",
  QA302: "Kỹ thuật phần mềm",
  OS301: "Khoa học máy tính",
  VLSI401: "Khoa học máy tính",
  OSRT401: "Khoa học máy tính",
  ROBOT402: "Khoa học máy tính",
  GIS301: "Hệ thống thông tin",
  DB301: "Hệ thống thông tin",
  BI402: "Hệ thống thông tin",
  NET301: "Công nghệ thông tin",
  NET402: "Công nghệ thông tin",
  CLOUD302: "Công nghệ thông tin",
  IOT401: "Công nghệ thông tin",
  INTER402: "Dùng chung",
  PROJ401: "Dùng chung",
  THESIS402: "Dùng chung"
};
function normalizeLabel(value: string | null | undefined) {
  return (value || "").trim().replace(/\s+/g, " ");
}
function courseMajorValue(course: Pick<CourseView, "code" | "major">) {
  const explicitMajor = normalizeLabel(course.major);
  if (explicitMajor) return explicitMajor;
  return COURSE_MAJOR_BY_CODE[course.code.toUpperCase()] || "Dùng chung";
}
function editableCourseMajor(course: Pick<CourseView, "code" | "major">) {
  const explicitMajor = normalizeLabel(course.major);
  const derivedMajor = courseMajorValue(course);
  return explicitMajor || (derivedMajor === "Dùng chung" ? "" : derivedMajor);
}
function sortMajors(values: string[]) {
  return [...values].sort((a, b) => {
    if (a === "Dùng chung") return -1;
    if (b === "Dùng chung") return 1;
    const ai = MAJOR_DISPLAY_ORDER.indexOf(a as (typeof MAJOR_DISPLAY_ORDER)[number]);
    const bi = MAJOR_DISPLAY_ORDER.indexOf(b as (typeof MAJOR_DISPLAY_ORDER)[number]);
    if (ai !== -1 || bi !== -1) return (ai === -1 ? 999 : ai) - (bi === -1 ? 999 : bi);
    return a.localeCompare(b, "vi");
  });
}
type TimetableCard = { id:number; title:string; schedule:string | null; room:string; term?:string; credits?:number };
const weekdays = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"];
function WeekSchedule({ items }: { items: TimetableCard[] }) {
  const rows: { key:string; day:number; time:string; title:string; room:string; term?:string }[] = [];
  for (const item of items) {
    for (const piece of (item.schedule || "").split(";")) {
      const normalized = piece.trim();
      const match = /^(Thứ\s*[2-7]|Chủ\s*nhật)\s+(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})$/iu.exec(normalized);
      const day = match ? (match[1].toLocaleLowerCase("vi-VN").startsWith("chủ") ? 6 : Number(match[1].match(/[2-7]/)?.[0]) - 2) : -1;
      rows.push({key:`${item.id}-${rows.length}`, day, time:match ? `${match[2]}–${match[3]}` : (normalized || "Chưa cập nhật lịch"), title:item.title, room:item.room, term:item.term});
    }
  }
  if (!rows.length) return <div className="empty-state">Chưa có lớp được phân công/đăng ký trong học kỳ này.</div>;
  const ordered = [...rows].sort((a,b)=>a.day === b.day ? a.time.localeCompare(b.time) : a.day-b.day);
  return <div className="week-schedule">{[-1,0,1,2,3,4,5,6].filter(day=>ordered.some(row=>row.day===day)).map(day => <section className="week-day" key={day}>
    <h3>{day===-1 ? "Lịch chưa đúng định dạng" : weekdays[day]}</h3>
    {ordered.filter(row=>row.day===day).map(row=><div className="week-entry" key={row.key}><b className="week-time">{row.time}</b><div><strong>{row.title}</strong>{row.term && <small>{row.term}</small>}</div><span>{row.room}</span></div>)}
  </section>)}</div>;
}

/** Shared accessible dialog for the Admin create/edit forms. */
function AdminFormDialog({ title, busy, onClose, children }: {
  title: string;
  busy?: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const busyRef = useRef(busy);
  busyRef.current = busy;

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const dialog = dialogRef.current;
    (dialog?.querySelector<HTMLElement>("input:not([disabled]), select:not([disabled]), textarea:not([disabled])") || dialog)?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        if (!busyRef.current) closeRef.current();
      }
      if (event.key !== "Tab" || !dialog) return;
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]'
      )).filter(element => element.getClientRects().length > 0);
      if (!focusable.length) { event.preventDefault(); dialog.focus(); return; }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, []);

  return <div className="admin-form-backdrop" onMouseDown={event => {
    if (event.target === event.currentTarget && !busy) onClose();
  }}>
    <div className="admin-form-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-form-title" ref={dialogRef} tabIndex={-1}>
      <header className="admin-form-header">
        <div><h2 id="admin-form-title">{title}</h2></div>
        <button type="button" className="admin-form-dismiss" onClick={onClose} disabled={busy} aria-label="Đóng biểu mẫu">Đóng</button>
      </header>
      {children}
    </div>
  </div>;
}

/** Thông báo nổi cố định: người dùng thấy được kể cả khi cuộn trang xuống. */
function ToastMessages({ error, notice, onCloseError, onCloseNotice }: {
  error: string;
  notice: string;
  onCloseError: () => void;
  onCloseNotice: () => void;
}) {
  // Gắn thẳng vào document.body để thông báo nổi độc lập với vùng cuộn,
  // sidebar, topbar và những thuộc tính CSS của layout bên trong ứng dụng.
  const [ready, setReady] = useState(false);
  useEffect(() => setReady(true), []);
  if (!ready || (!error && !notice)) return null;
  return createPortal(<div className="app-toast-region" aria-label="Thông báo hệ thống">
    {error && <div className="app-toast app-toast-error" role="alert">
      <div className="app-toast-content"><strong>Thông báo lỗi</strong><span>{error}</span></div>
      <button type="button" onClick={onCloseError} aria-label="Đóng thông báo lỗi">Đóng</button>
    </div>}
    {notice && <div className="app-toast app-toast-success" role="status">
      <div className="app-toast-content"><strong>Thông báo</strong><span>{notice}</span></div>
      <button type="button" onClick={onCloseNotice} aria-label="Đóng thông báo">Đóng</button>
    </div>}
  </div>, document.body);
}

export default function HomePage() {
  const [authChecking, setAuthChecking] = useState(true);
  const [token, setToken] = useState<string | null>(null); const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [role, setRole] = useState<LoginResponse["role"] | null>(null); const [displayName, setDisplayName] = useState("");
  const [classes, setClasses] = useState<ClassView[]>([]); const [registrations, setRegistrations] = useState<RegistrationView[]>([]); const [waitlist, setWaitlist] = useState<WaitlistView[]>([]); const [summary, setSummary] = useState<Summary | null>(null);
  const [studentSchedule, setStudentSchedule] = useState<StudySchedule>({currentTerm:"",classes:[]});
  const [adminDataStatus, setAdminDataStatus] = useState<{courses:number;terms:number;classes:number;openClassesInConfiguredPeriod:number;coursesMissingYearOrSemester:number;students:number;teachers:number;registrations:number;waitlistEntries:number;configuredOpenTerm:string;configuredOpenYear:string}|null>(null);
  const [overviewRefreshing, setOverviewRefreshing] = useState(false);
  const [studentScheduleTerm, setStudentScheduleTerm] = useState("current");
  const [programYearFilter, setProgramYearFilter] = useState("all");
  const [programMajorFilter, setProgramMajorFilter] = useState("all");
  const [tab, setTab] = useState<Tab>("overview"); const [query, setQuery] = useState("");
  const [notice, updateNotice] = useState(""); const [error, updateError] = useState(""); const [busy, setBusy] = useState(false);
  const [errorSequence, setErrorSequence] = useState(0);
  const [noticeSequence, setNoticeSequence] = useState(0);
  const setError = useCallback((message: string) => {
    updateError(message);
    if (message) setErrorSequence(value => value + 1);
  }, []);
  const setNotice = useCallback((message: string) => {
    updateNotice(message);
    if (message) setNoticeSequence(value => value + 1);
  }, []);
  // Mỗi lần phát sinh thông báo (kể cả cùng nội dung) đều đếm lại 3 giây.
  useEffect(() => {
    if (!error) return;
    const timeout = window.setTimeout(() => updateError(""), 3000);
    return () => window.clearTimeout(timeout);
  }, [error, errorSequence]);
  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => updateNotice(""), 3000);
    return () => window.clearTimeout(timeout);
  }, [notice, noticeSequence]);
  const [options, setOptions] = useState<Options>({ courses: [], terms: [], teachers: [] }); const [newClass, setNewClass] = useState({ name: "", courseId: "", termId: "", teacherId: "", room: "", schedule: "", capacity: "30" });

  const [conversations, setConversations] = useState<ConversationView[]>([]); const [conversationId, setConversationId] = useState<number | null>(null); const [messages, setMessages] = useState<MessageView[]>([]); const [chatText, setChatText] = useState(""); const [chatSubject, setChatSubject] = useState(""); const [profile, setProfile] = useState<ProfileView | null>(null);
  const [peerTyping, setPeerTyping] = useState(false);
  const [chatSending, setChatSending] = useState(false);
  const chatSendingRef = useRef(false);
  const [chatStatusBusy, setChatStatusBusy] = useState(false);
  const [studentActionBusy, setStudentActionBusy] = useState(false);
  const studentActionRef = useRef(false);
  const selectedChatRef = useRef<number | null>(null);
  const lastTypingSent = useRef<{id:number;at:number;active:boolean}|null>(null);
  const typingLastInputRef = useRef(0);
  const chatTextRef = useRef("");
  const chatListRef = useRef<HTMLDivElement | null>(null);
  const [adminRegistrationClass, setAdminRegistrationClass] = useState<number | null>(null);
  const [adminRegisteredStudents, setAdminRegisteredStudents] = useState<StudentRow[]>([]);
  const [adminStudentsLoading, setAdminStudentsLoading] = useState(false);
  const [adminStudentsError, setAdminStudentsError] = useState("");
  const [adminClassExportBusy, setAdminClassExportBusy] = useState(false);
  const adminStudentsRequest = useRef(0);
  const [adminRegistrationMajor, setAdminRegistrationMajor] = useState("all");
  const [adminRegistrationCourse, setAdminRegistrationCourse] = useState("all");
  const [adminRegistrationSection, setAdminRegistrationSection] = useState("all");
  const [adminRegistrationPage, setAdminRegistrationPage] = useState(0);
  type PersonFilters = {studentFaculties:string[];studentMajors:string[];studentCohorts:string[];teacherFaculties:string[];teacherSpecialties:string[];studentMajorCount:number};
  const [personFilters, setPersonFilters] = useState<PersonFilters>({studentFaculties:[],studentMajors:[],studentCohorts:[],teacherFaculties:[],teacherSpecialties:[],studentMajorCount:0});
  const [adminStudentSearch, setAdminStudentSearch] = useState("");
  const [teacherStudents, setTeacherStudents] = useState<StudentRow[]>([]); const [teacherClassId, setTeacherClassId] = useState<number | null>(null); const [adminWaitlist, setAdminWaitlist] = useState<AdminWaitlistItem[]>([]); const [decisions, setDecisions] = useState<DecisionItem[]>([]);
  const [rrRuns,setRrRuns]=useState<{id:number;classId:number;className:string;occurredAt:string;trigger:string;enrolledBefore:number;enrolledAfter:number;capacity:number;cursorBefore:number|null;cursorAfter:number|null;promotedCount:number;outcome:string}[]>([]);
  const [rrSelectedRun,setRrSelectedRun]=useState<number|null>(null);
  const [rrCandidates,setRrCandidates]=useState<{sequence:number;studentId:number;studentName:string;waitlistId:number|null;result:string;reason:string|null}[]>([]);
  const [rrError,setRrError]=useState("");
  type Person = { id: number; username: string | null; fullName: string; email: string; faculty: string | null; major: string | null; cohort: string | null; dateOfBirth: string | null; status: string };
  const [people, setPeople] = useState<Person[]>([]);
  const [peopleSearch, setPeopleSearch] = useState("");
  const [peopleCohort, setPeopleCohort] = useState("");
  const [peopleMajor, setPeopleMajor] = useState("");
  const [teacherSpecialty, setTeacherSpecialty] = useState("");
  const [peopleStatus, setPeopleStatus] = useState("");
  const [teacherQuery, setTeacherQuery] = useState("");
  const [teacherStudentQuery, setTeacherStudentQuery] = useState("");
  const [adminWaitQuery, setAdminWaitQuery] = useState("");
  const [decisionQuery, setDecisionQuery] = useState("");
  const [decisionResult, setDecisionResult] = useState("all");
  const [decisionFromDate, setDecisionFromDate] = useState("");
  const [decisionToDate, setDecisionToDate] = useState("");
  const [catalogPage, setCatalogPage] = useState(0);
  const [classEditing, setClassEditing] = useState<number | null>(null);
  const [capacityDraft, setCapacityDraft] = useState("");
  const [classEditDraft, setClassEditDraft] = useState({ name:"", courseId:"", termId:"", teacherId:"", room:"", schedule:"" });
  const [adminClassNameQuery, setAdminClassNameQuery] = useState("");
  const [statusDraft, setStatusDraft] = useState<"OPEN" | "CLOSED" | "CANCELLED">("CLOSED");
  const [classYear, setClassYear] = useState("all");
  const [classAcademicYear, setClassAcademicYear] = useState("all");
  const [selectedClassIds, setSelectedClassIds] = useState<number[]>([]);
  const [bulkClosing, setBulkClosing] = useState(false);
  const [classTerm, setClassTerm] = useState("all");
  const [classStatus, setClassStatus] = useState("all");
  const [classCourse, setClassCourse] = useState("all");
  const [classPage, setClassPage] = useState(0);
  const [adminPage, setAdminPage] = useState(0);
  const [catalogSearch, setCatalogSearch] = useState("");
  const [catalogYear, setCatalogYear] = useState("all");
  const [catalogMajor, setCatalogMajor] = useState("all");
  const [peoplePage, setPeoplePage] = useState(0);
  const [peopleTotal, setPeopleTotal] = useState(0);
  const [personDetail, setPersonDetail] = useState<Person | null>(null);
  const [studyProgress, setStudyProgress] = useState<StudyProgressView | null>(null);
  const [studyProgressLoading, setStudyProgressLoading] = useState(false);
  const [studyProgressError, setStudyProgressError] = useState("");
  const [myStudyProgress, setMyStudyProgress] = useState<StudyProgressView | null>(null);
  const [studyDraft, setStudyDraft] = useState({status:"ON_TRACK" as StudyProgressView["status"], effectiveYear:"", note:"", retakeCourseCodes:""});
  const [peopleLoading, setPeopleLoading] = useState(false);
  const peopleRequestId = useRef(0);
  const [adminEditor, setAdminEditor] = useState<AdminEditor>(null);
  const [peopleImportKind, setPeopleImportKind] = useState<"students"|"teachers"|null>(null);
  const [peopleImportFile, setPeopleImportFile] = useState<File|null>(null);
  const [peopleImportResult, setPeopleImportResult] = useState<PeopleImportSummary|null>(null);
  const [peopleImportBusy, setPeopleImportBusy] = useState(false);
  const [peopleExportBusy, setPeopleExportBusy] = useState<"xlsx" | "csv" | null>(null);
  // Chỉ xuất đúng bộ lọc đã áp dụng thành công cho bảng (không chỉ 20 dòng trang hiện tại).
  const [appliedPeopleFilters, setAppliedPeopleFilters] = useState<{kind:"students"|"teachers";query:string}|null>(null);
  const [newPerson, setNewPerson] = useState({email:"",fullName:"",faculty:"",major:""});
  const [courseForm, setCourseForm] = useState({ code: "", name: "", credits: "3", year: "", semester: "", major: "" });
  const [termForm, setTermForm] = useState({ name: "", academicYear: "", startDate: "", endDate: "" });
  const [termWindowId, setTermWindowId] = useState<number|null>(null);
  const [windowDraft, setWindowDraft] = useState({startsAt:"", endsAt:""});
  const [personEditDraft, setPersonEditDraft] = useState({fullName:"", faculty:"", major:"", cohort:"", dateOfBirth:""});
  const [editCourseId, setEditCourseId] = useState<number|null>(null);
  const [editTermId, setEditTermId] = useState<number|null>(null);
  const [catalogCourses, setCatalogCourses] = useState<CourseView[]>([]);
  const [catalogTerms, setCatalogTerms] = useState<{ id:number; name:string; academicYear:string; startDate:string|null; endDate:string|null; registrationOpen:boolean; registrationStartsAt:string|null; registrationEndsAt:string|null }[]>([]);
  // The saved registrationOpen flag alone does not mean the time window is active.
  // Re-evaluate while the user stays on the page (no reload needed at expiry).
  const [registrationClock, setRegistrationClock] = useState(() => Date.now());
  useEffect(() => {
    const refreshClock = () => setRegistrationClock(Date.now());
    refreshClock();
    const timer = window.setInterval(refreshClock, 10000);
    window.addEventListener("focus", refreshClock);
    document.addEventListener("visibilitychange", refreshClock);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refreshClock);
      document.removeEventListener("visibilitychange", refreshClock);
    };
  }, []);
  const [currentPass, setCurrentPass] = useState("");
  const [newPass, setNewPass] = useState("");
  const [confirmPass, setConfirmPass] = useState("");
  const loadCatalog = async () => {
    if (!token) return;
    try {
      const [courses, terms] = await Promise.all([
        api<typeof catalogCourses>("/api/admin/catalog/courses", token),
        api<typeof catalogTerms>("/api/admin/catalog/terms", token)
      ]);
      setCatalogCourses(courses); setCatalogTerms(terms);
      const o = await api<Options>("/api/admin/options", token);
      setOptions(o);
      setNewClass(v => ({...v, courseId: v.courseId || String(o.courses[0]?.id || ""), termId: v.termId || String(o.terms[0]?.id || "")}));
    } catch (e) { setError(e instanceof Error ? e.message : "Không tải được danh mục."); }
  };
  const closeAdminEditor = () => {
    setAdminEditor(null);
    setEditCourseId(null);
    setEditTermId(null);
    setError("");
  };
  const openAddPerson = () => {
    resetMessages(); setPersonDetail(null);
    setNewPerson({email:"",fullName:"",faculty:"",major:""});
    setAdminEditor("person");
  };
  const openAddCourse = () => {
    resetMessages(); setEditCourseId(null);
    setCourseForm({code:"",name:"",credits:"3",year:"",semester:"",major:""});
    setAdminEditor("course");
  };
  const openAddTerm = () => {
    resetMessages(); setEditTermId(null);
    setTermForm({name:"",academicYear:"",startDate:"",endDate:""});
    setAdminEditor("term");
  };
  const openAddClass = () => {
    resetMessages();
    setNewClass({name:"",courseId:String(options.courses[0]?.id || ""),termId:String(options.terms[0]?.id || ""),teacherId:"",room:"",schedule:"",capacity:"30"});
    setAdminEditor("class");
  };
  async function saveCourse(e: FormEvent) {
    e.preventDefault(); if (!token || busy) return; resetMessages();
    if (!!courseForm.year !== !!courseForm.semester) { setError("Năm đào tạo và học kỳ phải chọn đồng thời hoặc cùng để trống."); return; }
    setBusy(true);
    try {
      await api(editCourseId ? `/api/admin/catalog/courses/${editCourseId}` : "/api/admin/catalog/courses",token,{method:editCourseId?"PUT":"POST",body:JSON.stringify({...courseForm,credits:Number(courseForm.credits),year:courseForm.year ? Number(courseForm.year) : null,semester:courseForm.semester ? Number(courseForm.semester) : null,major:courseForm.major.trim() || null})});
      setNotice(editCourseId ? "Đã cập nhật học phần." : "Đã tạo học phần."); setEditCourseId(null); setCourseForm({code:"",name:"",credits:"3",year:"",semester:"",major:""}); await loadCatalog(); setAdminEditor(null);
    } catch (e) { setError(e instanceof Error ? e.message : "Không tạo được học phần."); }
    finally {setBusy(false);}
  }
  async function saveTerm(e: FormEvent) {
    e.preventDefault(); if (!token || busy) return; resetMessages(); setBusy(true);
    try {
      await api(editTermId ? `/api/admin/catalog/terms/${editTermId}` : "/api/admin/catalog/terms",token,{method:editTermId?"PUT":"POST",body:JSON.stringify({...termForm,startDate:termForm.startDate||null,endDate:termForm.endDate||null})});
      setNotice(editTermId ? "Đã cập nhật học kỳ." : "Đã tạo học kỳ."); setEditTermId(null); setTermForm({name:"",academicYear:"",startDate:"",endDate:""}); await loadCatalog(); setAdminEditor(null);
    } catch (e) { setError(e instanceof Error ? e.message : "Không tạo được học kỳ."); }
    finally {setBusy(false);}
  }
  async function deleteCatalog(kind:"courses"|"terms", id:number) {
    if (!token || !confirm("Bạn muốn xóa mục danh mục này? Chỉ có thể xóa khi chưa có lớp liên quan.")) return;
    resetMessages();
    try {
      await api(`/api/admin/catalog/${kind}/${id}`, token, {method:"DELETE"});
      setNotice("Đã xóa mục danh mục."); await loadCatalog();
    } catch (e) { setError(e instanceof Error ? e.message : "Không thể xóa mục này."); }
  }
  async function submitPeopleImport(e: FormEvent) {
    e.preventDefault();
    const kind = peopleImportKind;
    if (!token || !kind || !peopleImportFile || peopleImportBusy) return;
    if (peopleImportFile.size > 5 * 1024 * 1024) {
      setError("File nhập không được lớn hơn 5 MB."); return;
    }
    resetMessages(); setPeopleImportBusy(true); setPeopleImportResult(null);
    try {
      const body = new FormData();
      body.append("file", peopleImportFile);
      const result = await api<PeopleImportSummary>(`/api/admin/import/${kind}`, token, { method:"POST", body });
      setPeopleImportResult(result);
      setNotice(`Đã tạo ${result.created}, đã tồn tại ${result.skipped}, khác vai trò ${result.ignoredRole}, lỗi ${result.failed}.`);
      await loadPeople(kind, 0, "", true);
      await loadPersonFilters();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không nhập được tệp dữ liệu.");
    } finally {
      setPeopleImportBusy(false);
    }
  }
  async function downloadPeople(kind: "students" | "teachers", format: "xlsx" | "csv") {
    if (!token || role !== "ADMIN" || peopleExportBusy) return;
    if (!appliedPeopleFilters || appliedPeopleFilters.kind !== kind) {
      setError("Chưa tải xong danh sách hiện tại. Hãy thử bấm Áp dụng bộ lọc trước khi xuất.");
      return;
    }
    resetMessages();
    setPeopleExportBusy(format);
    try {
      const params = new URLSearchParams(appliedPeopleFilters.query);
      params.delete("page"); params.delete("size");
      params.set("format", format);
      const response = await fetch(`${API_URL}/api/admin/export/${kind}?${params.toString()}`, {
        credentials: "include", cache: "no-store"
      });
      if (!response.ok) {
        let reason = response.status === 404
          ? "API xuất Excel/CSV không tồn tại trên backend đang chạy (404). Hãy dừng backend cũ ở cổng 8081 và khởi động backend V20.39 trở lên bằng mvn clean spring-boot:run, sau đó tải lại trang."
          : `Không thể xuất dữ liệu (${response.status}).`;
        try {
          const body = await response.json() as {message?:string};
          if (body.message) reason = body.message;
        } catch { /* Backend may respond without JSON. */ }
        throw new Error(reason);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const element = document.createElement("a");
      element.href = url;
      element.download = `danh_sach_${kind === "students" ? "sinh_vien" : "giang_vien"}_${new Date().toISOString().slice(0, 10)}.${format}`;
      document.body.appendChild(element);
      element.click();
      element.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1500);
      setNotice(`Đã xuất danh sách ${kind === "students" ? "sinh viên" : "giảng viên"} theo bộ lọc đang áp dụng (${format.toUpperCase()}).`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể tải danh sách.");
    } finally {
      setPeopleExportBusy(null);
    }
  }
  function openPeopleImport(kind:"students"|"teachers") {
    setPeopleImportKind(kind);
    setPeopleImportFile(null);
    setPeopleImportResult(null);
    resetMessages();
  }
  function closePeopleImport() {
    if (peopleImportBusy) return;
    setPeopleImportKind(null);
    setPeopleImportFile(null);
    setPeopleImportResult(null);
  }
  async function createPerson(e: FormEvent) {
    e.preventDefault(); if (!token || (tab!=="students" && tab!=="teachers")) return;
    resetMessages();setBusy(true);
    try {
      const result = await api<{message:string;email:string}>("/api/admin/accounts",token,{
        method:"POST",body:JSON.stringify({...newPerson,role:tab==="students" ? "STUDENT":"TEACHER"})
      });
      setNotice(result.message);setNewPerson({email:"",fullName:"",faculty:"",major:""});setAdminEditor(null);
      setPeopleSearch("");setPeopleCohort("");setPeopleMajor("");setTeacherSpecialty("");setPeopleStatus("");
      await loadPeople(tab,0,"",true);
      await loadPersonFilters();
    } catch(e) {setError(e instanceof Error ? e.message : "Không tạo được tài khoản.");}
    finally {setBusy(false);}
  }
  async function updatePerson(e: FormEvent) {
    e.preventDefault(); if (!token || !personDetail || (tab!=="students" && tab!=="teachers")) return;
    resetMessages(); setBusy(true);
    try {
      const saved = await api<Person>(`/api/admin/${tab}/${personDetail.id}`, token, {
        method:"PUT",body:JSON.stringify({...personEditDraft,dateOfBirth:personEditDraft.dateOfBirth || null})
      });
      setPersonDetail(saved); setNotice("Đã cập nhật hồ sơ trong SQL Server.");
      await loadPeople(tab,peoplePage);
      await loadPersonFilters();
      setPersonDetail(saved);
    } catch (e) {setError(e instanceof Error ? e.message : "Không cập nhật được hồ sơ.");}
    finally {setBusy(false);}
  }
  async function saveRegistrationWindow(id:number, open:boolean) {
    if (!token) return;
    if (open && !confirm("Mở đăng ký học kỳ này? Mọi học kỳ đang mở khác sẽ được đóng.")) return;
    if (!open && !confirm("Đóng đợt đăng ký học kỳ này? Sinh viên sẽ không đăng ký mới được.")) return;
    resetMessages();setBusy(true);
    try {
      await api(`/api/admin/catalog/terms/${id}/registration`,token,{method:"PUT",body:JSON.stringify({open,
        startsAt:windowDraft.startsAt || null, endsAt:windowDraft.endsAt || null})});
      setNotice(open ? "Đã mở đợt đăng ký." : "Đã đóng đợt đăng ký.");setTermWindowId(null);
      await loadCatalog();await loadAdminDataStatus();await refresh();
    } catch(e) { setError(e instanceof Error ? e.message : "Không cập nhật được đợt đăng ký."); }
    finally {setBusy(false);}
  }
  async function changePassword(e: FormEvent) {
    e.preventDefault(); if (!token) return; resetMessages();
    if (newPass !== confirmPass) { setError("Mật khẩu xác nhận không khớp."); return; }
    try {
      const result = await api<{message:string}>("/api/auth/change-password",token,{method:"POST",body:JSON.stringify({currentPassword:currentPass,newPassword:newPass})});
      setCurrentPass(""); setNewPass(""); setConfirmPass(""); setNotice(result.message);
    } catch (e) { setError(e instanceof Error ? e.message : "Không đổi được mật khẩu."); }
  }
  async function loadPersonStudyProgress(personId:number) {
    if (!token) return;
    setStudyProgressLoading(true);setStudyProgressError("");
    try {
      const academic = await api<StudyProgressView>(`/api/admin/students/${personId}/study-progress`,token);
      setStudyProgress(academic);
      setStudyDraft({status:academic.status,effectiveYear:academic.effectiveYear?.toString()||"",
        note:academic.note||"",retakeCourseCodes:(academic.retakeCourseCodes||[]).join(", ")});
    } catch (e) {
      const message=e instanceof Error ? e.message : "Không tải được tiến độ.";
      setStudyProgressError(message.includes("404")
        ? "API tiến độ học tập chưa được phục vụ tại cổng 8081 (404). Backend đang chạy có thể là bản cũ. Dừng tiến trình Java cũ và chạy script KHOI_DONG_BACKEND_V20_50.ps1 ở thư mục gốc. Chỉ chạy migration SQL V20.48 nếu chưa có các bảng tiến độ."
        : `Không tải được tiến độ (${message}). Kiểm tra log backend và kết nối SQL Server; chỉ chạy migration V20.48 nếu chưa thực hiện.`);
    } finally {setStudyProgressLoading(false);}
  }
  async function openPersonDetail(person:Person) {
    resetMessages();setPersonDetail(person);setStudyProgress(null);setStudyProgressError("");
    setPersonEditDraft({fullName:person.fullName,faculty:person.faculty || "",major:person.major || "",cohort:person.cohort || "",dateOfBirth:person.dateOfBirth || ""});
    if (!token || (tab !== "students" && tab !== "teachers")) return;
    // Hai API độc lập: lỗi tải tiến độ không làm mất thông tin hồ sơ.
    if (tab === "students") void loadPersonStudyProgress(person.id);
    try {
      const detail = await api<Person>(`/api/admin/${tab}/${person.id}`,token);
      setPersonDetail(current => current?.id === detail.id ? detail : current);
      setPersonEditDraft(current => ({...current,fullName:detail.fullName,faculty:detail.faculty||"",major:detail.major||"",cohort:detail.cohort||"",dateOfBirth:detail.dateOfBirth||""}));
    } catch (e) {setError(e instanceof Error ? e.message : "Không tải được thông tin chi tiết.");}
  }
  async function saveStudyProgress(e:FormEvent) {
    e.preventDefault(); if (!personDetail || !token) return;
    resetMessages();setBusy(true);
    try {
      const result=await api<StudyProgressView>(`/api/admin/students/${personDetail.id}/study-progress`,token,{
        method:"PUT",body:JSON.stringify({status:studyDraft.status,effectiveYear:studyDraft.effectiveYear ? Number(studyDraft.effectiveYear) : null,
          note:studyDraft.note,retakeCourseCodes:studyDraft.retakeCourseCodes.split(/[,;\n]+/).map(x=>x.trim()).filter(Boolean)})
      });
      setStudyProgress(result);setNotice("Đã cập nhật tiến độ học tập. Khóa nhập học được giữ nguyên.");
    }catch(e){setError(e instanceof Error ? e.message : "Không cập nhật được tiến độ học tập.");}
    finally{setBusy(false);}
  }
  const resetMessages = () => { setError(""); setNotice(""); };
  const loadProfile = useCallback(async (t: string) => {
    try {
      const result = await api<ProfileView>("/api/auth/profile", t);
      setProfile(result);
      if(result.role === "STUDENT") {
        try { setMyStudyProgress(await api<StudyProgressView>("/api/student/study-progress",t)); }
        catch { setMyStudyProgress(null); }
      }
    }
    catch { setProfile(null); }
  }, []);
  const loadCore = useCallback(async (t: string, q = "", userRole: LoginResponse["role"] | null = "STUDENT") => {
    const endpoint = userRole === "ADMIN" ? "/api/admin/classes" : userRole === "TEACHER" ? "/api/teacher/classes" : "/api/classes";
    const url = userRole === "STUDENT" && q ? `${endpoint}?q=${encodeURIComponent(q)}` : endpoint;
    const [cs, courseList, termList] = await Promise.all([
      api<ClassView[]>(url, t), api<CourseView[]>("/api/catalog/courses", t), api<typeof catalogTerms>("/api/catalog/terms", t)
    ]);
    setCatalogCourses(courseList); setCatalogTerms(termList);
    const keyword = q.trim().toLocaleLowerCase("vi-VN");
    setClasses(userRole !== "STUDENT" && keyword
      ? cs.filter(c => [c.name, c.courseName, c.courseCode, c.teacherName].some(v => v?.toLocaleLowerCase("vi-VN").includes(keyword)))
      : cs);
    if (userRole === "STUDENT") { const [rs, ws] = await Promise.all([api<RegistrationView[]>("/api/me/registrations", t), api<WaitlistView[]>("/api/me/waitlist", t)]); setRegistrations(rs); setWaitlist(ws); }
    else { setRegistrations([]); setWaitlist([]); }
  }, []);
  // Never equate a failed session-probe request with a logout: a 404 can mean
  // that an older backend is still running, while 403 can mean a proxy/security mismatch.
  // /auth/me is the compatibility fallback and is authoritative for the current role.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const probe = await fetch("/backend/api/auth/session", { credentials: "include", cache: "no-store" });
        if (cancelled) return;
        if (probe.ok) {
          const session: { authenticated?: boolean } = await probe.json();
          if (session.authenticated === false) return; // Expected: anonymous visitor.
        }
        // The session endpoint was introduced after /me. Recover older deployments
        // without silently discarding a still-valid HttpOnly authentication cookie.
        const meResponse = await fetch("/backend/api/auth/me", { credentials: "include", cache: "no-store" });
        if (cancelled) return;
        if (meResponse.status === 401) return; // Truly unauthenticated.
        if (!meResponse.ok) {
          const hint = probe.status === 404 || probe.status === 403
            ? `API kiểm tra phiên trả ${probe.status}; /auth/me trả ${meResponse.status}. Hãy dừng mọi backend cũ, khởi động lại bản mới nhất ở cổng 8081 và kiểm tra proxy Next.js.`
            : `Không thể xác minh phiên đăng nhập (HTTP ${meResponse.status}). Kiểm tra backend 8081.`;
          setError(hint);
          return;
        }
        const user = (await meResponse.json()) as LoginResponse;
        if (cancelled) return;
        setToken("cookie"); setRole(user.role); setEmail(user.email);
        setDisplayName(user.displayName);
        setTab(tabFromAddress(user.role));
        await loadProfile("cookie");
      } catch {
        if (!cancelled) setError("Không kết nối được backend để kiểm tra phiên. Vui lòng kiểm tra Spring Boot ở cổng 8081; dữ liệu tài khoản chưa bị xóa.");
      } finally {
        if (!cancelled) setAuthChecking(false);
      }
    })();
    return () => { cancelled = true; };
  }, [loadProfile]);
  // Use semantic browser paths while data APIs remain on /backend/api/*.
  useEffect(() => {
    if (!token || !role || authChecking) return;
    const target = PAGE_ROUTES[role][tab] || PAGE_ROUTES[role][initialTab(role)] || "/";
    if (window.location.pathname !== target) window.history.pushState({tab}, "", target);
  }, [token, role, tab, authChecking]);
  useEffect(() => {
    if (!token || !role) return;
    const navigateBack = () => {setPersonDetail(null);setClassEditing(null);setAdminEditor(null);setTab(tabFromAddress(role));};
    window.addEventListener("popstate", navigateBack);
    return () => window.removeEventListener("popstate", navigateBack);
  }, [token,role]);
  useEffect(() => { if (!token) return; let cancelled = false; (async () => { try { await loadCore(token, "", role); await loadProfile(token); if (role === "STUDENT") await loadStudentSchedule(); if (role === "ADMIN") { const s = await api<Summary>("/api/admin/summary", token); if (!cancelled) setSummary(s); const o = await api<Options>("/api/admin/options", token); if (!cancelled) { setOptions(o); setNewClass(v => ({ ...v, courseId: v.courseId || String(o.courses[0]?.id || ""), termId: v.termId || String(o.terms[0]?.id || ""), teacherId: v.teacherId || String(o.teachers[0]?.id || "") })); } } } catch (e) { if (!cancelled) { setError(e instanceof Error ? e.message : "Không tải được dữ liệu."); } } })(); return () => { cancelled = true; }; }, [token, role, loadCore, loadProfile]);
  async function signIn(e: FormEvent) { e.preventDefault(); resetMessages(); setBusy(true); try { await api("/api/auth/csrf"); const r = await api<LoginResponse>("/api/auth/login", null, { method: "POST", body: JSON.stringify({ email, password }) }); setToken("cookie"); setRole(r.role); setDisplayName(r.displayName); setEmail(r.email); setPassword(""); setNotice("Đăng nhập thành công."); setTab(tabFromAddress(r.role)); await loadProfile("cookie"); } catch (e) { setError(e instanceof Error ? e.message : "Đăng nhập thất bại."); } finally { setBusy(false); } }
  async function signOut() { try { await api("/api/auth/logout", null, {method:"POST"}); } catch (e) { setError("Không thể đăng xuất an toàn. Vui lòng thử lại."); return; } window.history.replaceState({}, "", "/"); setToken(null); setRole(null); setProfile(null); setClasses([]); setRegistrations([]); setWaitlist([]); setStudentSchedule({currentTerm:"",classes:[]}); setMessages([]); setAdminEditor(null); resetMessages(); }
  async function refresh() { if (!token) return; try { await loadCore(token, query, role); await loadProfile(token); if (role === "STUDENT") await loadStudentSchedule(); if (role === "ADMIN") setSummary(await api<Summary>("/api/admin/summary", token)); } catch (e) { setError(e instanceof Error ? e.message : "Không tải lại được."); } }
  async function registerClass(id: number) { if (!token) return; resetMessages(); setBusy(true); try { const r = await api<{ message: string; queuePosition: number | null }>(`/api/classes/${id}/register`, token, { method: "POST" }); setNotice(r.message + (r.queuePosition ? ` Vị trí hàng chờ: ${r.queuePosition}.` : "")); await refresh(); } catch (e) { setError(e instanceof Error ? e.message : "Không thể đăng ký."); } finally { setBusy(false); } }
  async function cancelRegistration(id: number) {
    if (!token || studentActionRef.current || !confirm("Bạn có chắc muốn hủy đăng ký lớp này?")) return;
    studentActionRef.current = true; setStudentActionBusy(true); resetMessages();
    try {
      const r = await api<{ message: string }>(`/api/registrations/${id}`, token, { method: "DELETE" });
      setNotice(r.message); await refresh();
    } catch (e) { setError(e instanceof Error ? e.message : "Không thể hủy đăng ký."); }
    finally { studentActionRef.current = false; setStudentActionBusy(false); }
  }
  async function rejoinWaitlist(id:number) {
    if (!token) return;
    setStudentActionBusy(true);
    try {
      const r=await api<{message:string}>(`/api/waitlist/${id}/rejoin`,token,{method:"POST"});
      setNotice(r.message); await refresh();
    } catch(e) { setError(e instanceof Error?e.message:"Không thể tham gia lại danh sách chờ."); }
    finally { setStudentActionBusy(false); }
  }
  async function withdraw(id: number) {
    if (!token || studentActionRef.current || !confirm("Rút khỏi danh sách chờ?")) return;
    studentActionRef.current = true; setStudentActionBusy(true); resetMessages();
    try {
      const r = await api<{ message: string }>(`/api/waitlist/${id}`, token, { method: "DELETE" });
      setNotice(r.message); await refresh();
    } catch (e) { setError(e instanceof Error ? e.message : "Không thể rút lượt chờ."); }
    finally { studentActionRef.current = false; setStudentActionBusy(false); }
  }
  async function search(e: FormEvent) { e.preventDefault(); if (!token) return; setClassPage(0); setAdminPage(0); try { await loadCore(token, query, role); } catch (e) { setError(e instanceof Error ? e.message : "Tìm kiếm thất bại."); } }
  async function createClass(e: FormEvent) { e.preventDefault(); if (!token || busy) return; resetMessages(); setBusy(true); try { await api<ClassView>("/api/admin/classes", token, { method: "POST", body: JSON.stringify({ ...newClass, courseId: Number(newClass.courseId), termId: Number(newClass.termId), teacherId: Number(newClass.teacherId), capacity: Number(newClass.capacity) }) }); setNotice("Tạo lớp thành công."); setNewClass(v => ({ ...v, name: "" })); await refresh(); setAdminEditor(null); } catch (e) { setError(e instanceof Error ? e.message : "Không tạo được lớp."); } finally {setBusy(false);} }

  async function deleteUnusedClass(c: ClassView) {
    if (!token || role !== "ADMIN" || busy || bulkClosing) return;
    const confirmation = window.prompt(`XÓA VĨNH VIỄN LỚP HỌC PHẦN\n\n${c.name} (${c.courseCode})\n\nChỉ có thể xóa lớp chưa phát sinh đăng ký, danh sách chờ hoặc nhật ký quyết định.\nNhập chính xác tên lớp để xác nhận:`, "");
    if (confirmation === null) return;
    if (confirmation.trim() !== c.name.trim()) {
      setError(`Tên lớp xác nhận không khớp. Vui lòng nhập đúng: ${c.name}. Chưa xóa dữ liệu.`);
      return;
    }
    resetMessages(); setBusy(true);
    try {
      // Verify that Next.js is connected to a backend that actually supports safe deletion.
      // Older backend processes return 405 to DELETE even though the frontend has the button.
      let runtime: { version?: string; deleteUnusedClassSupported?: boolean };
      try {
        runtime = await api<{ version?: string; deleteUnusedClassSupported?: boolean }>("/api/admin/runtime", token);
      } catch (checkError) {
        const reason = checkError instanceof Error ? checkError.message : "Không kiểm tra được phiên bản backend";
        throw new Error(`Không xác minh được API xóa lớp trên backend (cổng 8081): ${reason}. Hãy chạy lại backend V20.65, chưa xóa dữ liệu.`);
      }
      if (runtime?.deleteUnusedClassSupported !== true) {
        throw new Error("Backend đang chạy chưa hỗ trợ xóa lớp học phần. Hãy dừng tiến trình Java cũ ở cổng 8081 và khởi động backend V20.65 bằng mvn clean spring-boot:run, rồi thử lại. Chưa xóa dữ liệu.");
      }
      await api(`/api/admin/classes/${c.id}`, token, { method: "DELETE" });
      setSelectedClassIds(old => old.filter(id => id !== c.id));
      setNotice(`Đã xóa lớp ${c.name}.`);
      await refresh();
    } catch (e) {
      const message = e instanceof Error ? e.message : "Không thể xóa lớp học phần.";
      setError(message.includes("(405)")
        ? "API xóa lớp trả về 405: backend ở cổng 8081 chưa chạy bản có chức năng DELETE /api/admin/classes/{id}. Hãy dừng backend cũ và khởi động lại backend V20.65. Chưa xóa dữ liệu."
        : message);
    } finally { setBusy(false); }
  }

  async function updateClassSettings(id: number) {
    if (!token || classEditing !== id) return;
    const current = classes.find(c => c.id === id);
    if (!current) return;
    const capacity = Number(capacityDraft);
    if (!Number.isInteger(capacity) || capacity < current.enrolled || capacity < 1 || capacity > 1000) {
      setError(`Sức chứa phải từ ${Math.max(1, current.enrolled)} đến 1000.`); return;
    }
    if (statusDraft !== current.status && statusDraft !== "OPEN" &&
        !confirm("Xác nhận đóng/hủy lớp? Sinh viên có thể không đăng ký được nữa.")) return;
    resetMessages(); setBusy(true);
    try {
      await api(`/api/admin/classes/${id}`, token, {method:"PUT", body:JSON.stringify({
        name:classEditDraft.name,courseId:Number(classEditDraft.courseId),termId:Number(classEditDraft.termId),teacherId:Number(classEditDraft.teacherId),
        room:classEditDraft.room,schedule:classEditDraft.schedule,capacity,status:statusDraft
      })});
      setNotice("Đã cập nhật lớp học."); setClassEditing(null);
      await refresh();
    } catch (e) {
      const message = e instanceof Error ? e.message : "Không cập nhật được lớp học.";
      // This exact rejection was removed from ClassService in V20.36. Receiving it proves
      // the request reached an older backend, even if the frontend has been updated.
      if (statusDraft === "OPEN" && message.includes("Chỉ được mở đăng ký")) {
        setError("Backend đang chạy mã cũ (trước V20.36) trên cổng API. Hãy dừng tiến trình Spring Boot cũ, mở đúng thư mục backend mới nhất và chạy 'mvn clean spring-boot:run'. Không cần mở lại học kỳ trong SQL.");
      } else setError(message);
    }
    finally { setBusy(false); }
  }
  async function closeSelectedClasses() {
    if (!token || role !== "ADMIN" || busy || bulkClosing) return;
    // Never include classes that have already been closed or cancelled.
    const ids = [...new Set(selectedClassIds)].filter(id => classes.some(c => c.id === id && c.status === "OPEN"));
    if (ids.length === 0) { setError("Vui lòng chọn ít nhất một lớp đang mở."); return; }
    if (ids.length > 200) { setError("Chỉ được đóng tối đa 200 lớp trong một lần."); return; }
    if (!confirm(`Bạn có muốn đóng ${ids.length} lớp đã chọn?\n\nSinh viên sẽ không thể đăng ký mới vào những lớp này. Các lượt đăng ký thành công, danh sách chờ và lịch sử vẫn được giữ nguyên.`)) return;
    resetMessages();
    setBulkClosing(true);
    try {
      const result = await api<{closed:number;skipped:number}>("/api/admin/classes/bulk-close", token, {
        method: "POST", body: JSON.stringify({classIds: ids})
      });
      setSelectedClassIds([]);
      setAdminPage(0);
      setNotice(`Đã đóng ${result.closed} lớp học phần.${result.skipped ? ` Bỏ qua ${result.skipped} lớp đã đóng hoặc đã hủy.` : ""}`);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không thể đóng các lớp đã chọn.");
    } finally { setBulkClosing(false); }
  }

  async function loadPersonFilters() {
    if (!token || role !== "ADMIN") return;
    try { setPersonFilters(await api<typeof personFilters>("/api/admin/people-filters", token)); }
    catch (e) { setError(e instanceof Error ? e.message : "Không tải được bộ lọc Khoa/Ngành/Chuyên môn/Khóa từ SQL Server."); }
  }
  async function loadPeople(kind: "students" | "teachers", page = 0, search = peopleSearch,
                            clearFilters = false, specialtyOverride?: string) {
    if (!token) return;
    const requestId = ++peopleRequestId.current;
    const selectedSpecialty = kind === "teachers"
      ? (clearFilters ? "" : (specialtyOverride ?? teacherSpecialty).trim()) : "";
    const params = new URLSearchParams({ page: String(page), size: "20", q: search.trim() });
    if (kind === "students") {
      params.set("cohort", clearFilters ? "" : peopleCohort);
      params.set("major", clearFilters ? "" : peopleMajor);
      params.set("status", clearFilters ? "" : peopleStatus);
    } else if (selectedSpecialty) {
      // GiangVien.BoMon -> Teacher.major -> PersonView.major; đây là CHUYÊN MÔN.
      // Gửi cả tham số cũ major để không hỏng API V20.26 đang chạy trước khi restart.
      params.set("specialty", selectedSpecialty);
      params.set("major", selectedSpecialty);
    }
    // Không để bảng còn dữ liệu cũ khi đang chuyển lựa chọn / chờ API.
    setPeopleLoading(true); setPersonDetail(null); setPeople([]); setPeopleTotal(0);
    setPeoplePage(page); setError("");
    try {
      const result = await api<{ content: Person[]; totalElements: number }>(`/api/admin/${kind}?${params.toString()}`, token);
      if (requestId !== peopleRequestId.current) return;
      // Nếu backend bản cũ bỏ qua tham số specialty thì KHÔNG hiển thị dữ liệu sai như đã lọc.
      if (kind === "teachers" && selectedSpecialty && result.content.some(person =>
          (person.major || "").trim().toLocaleLowerCase("vi-VN") !== selectedSpecialty.toLocaleLowerCase("vi-VN"))) {
        throw new Error("Backend đang trả giảng viên không đúng chuyên môn. Hãy dừng backend cũ, chạy lại backend V20.27 rồi bấm Áp dụng.");
      }
      setPeople(result.content); setPeopleTotal(result.totalElements); setPeoplePage(page);
      setAppliedPeopleFilters({kind, query:params.toString()});
    } catch (e) {
      if (requestId === peopleRequestId.current) {
        setPeople([]); setPeopleTotal(0); setAppliedPeopleFilters(null);
        setError(e instanceof Error ? e.message : "Không tải được danh sách giảng viên.");
      }
    } finally {
      if (requestId === peopleRequestId.current) setPeopleLoading(false);
    }
  }
  async function changePersonStatus(kind:"students"|"teachers", id:number, next:"ACTIVE"|"LOCKED") {
    if (!token || role!=="ADMIN" || !confirm(next==="LOCKED" ? "Khóa tài khoản này? Người dùng sẽ không thể truy cập kể cả với phiên đăng nhập cũ." : "Mở khóa tài khoản đã kích hoạt?")) return;
    resetMessages();
    try {
      await api(`/api/admin/${kind}/${id}/status`,token,{method:"PATCH",body:JSON.stringify({status:next})});
      setPersonDetail(null);
      await loadPeople(kind,peoplePage,peopleSearch);
      setNotice(next==="LOCKED" ? "Đã khóa tài khoản." : "Đã mở khóa tài khoản.");
    } catch (e) { setError(e instanceof Error ? e.message : "Không cập nhật được trạng thái tài khoản."); }
  }
  async function loadRoundRobinRuns() {
    try { setRrRuns(await api<typeof rrRuns>("/api/admin/round-robin-runs",token)); setRrError(""); }
    catch(e) { setRrError(e instanceof Error?e.message:"Không tải được nhật ký Round Robin."); }
  }
  async function showRoundRobinRun(id:number) {
    setRrSelectedRun(id); setRrCandidates([]);
    try { setRrCandidates(await api<typeof rrCandidates>(`/api/admin/round-robin-runs/${id}/candidates`,token)); setRrError(""); }
    catch(e) { setRrError(e instanceof Error?e.message:"Không tải được chi tiết lượt xét."); }
  }
  async function loadAdminWaitlist() { if (!token) return; try { setAdminWaitlist(await api<AdminWaitlistItem[]>("/api/admin/waitlist", token)); } catch (e) { setError(e instanceof Error ? e.message : "Không tải được danh sách chờ."); } }
  async function loadAdminRegisteredStudents(id:number) {
    if (!token) return;
    const request = ++adminStudentsRequest.current;
    setAdminRegistrationClass(id);
    setAdminRegisteredStudents([]);
    setAdminStudentsError("");
    setAdminStudentsLoading(true);
    try {
      const students = await api<StudentRow[]>(`/api/admin/classes/${id}/students`, token);
      if (request === adminStudentsRequest.current) setAdminRegisteredStudents(students);
    } catch (e) {
      if (request === adminStudentsRequest.current) {
        setAdminStudentsError(e instanceof Error ? e.message : "Không tải được danh sách sinh viên đăng ký.");
      }
    } finally {
      if (request === adminStudentsRequest.current) setAdminStudentsLoading(false);
    }
  }
  async function exportAdminClassStudents(classId: number, className: string) {
    if (!token || role !== "ADMIN" || adminClassExportBusy) return;
    setAdminClassExportBusy(true);
    resetMessages();
    const safeName = className.replace(/[^a-zA-Z0-9_-]/g, "_").slice(0, 70) || `lop_${classId}`;
    const saveFile = (blob: Blob, name: string) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = name;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1500);
    };
    try {
      // Next.js generates the .xlsx from the existing ADMIN-authorized class list API.
      // No dependency on an optional export endpoint in older Spring Boot instances.
      const response = await fetch(`/api/admin/classes/${classId}/students/export`, {
        credentials: "include", cache: "no-store"
      });
      if (response.ok) {
        saveFile(await response.blob(), `danh_sach_sinh_vien_${safeName}.xlsx`);
        setNotice(`Đã xuất Excel danh sách sinh viên lớp ${className}.`);
        return;
      }
      let reason = `Không thể xuất danh sách sinh viên (HTTP ${response.status}).`;
      try {
        const body = await response.json() as {message?: string};
        if (body.message) reason = body.message;
      } catch { /* Some responses are not JSON. */ }
      throw new Error(reason);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không thể xuất danh sách sinh viên của lớp.");
    } finally {
      setAdminClassExportBusy(false);
    }
  }
  function closeAdminRegisteredStudents() {
    ++adminStudentsRequest.current;
    setAdminRegistrationClass(null);
    setAdminRegisteredStudents([]);
    setAdminStudentsLoading(false);
    setAdminStudentsError("");
  }
  async function loadStudentSchedule() {
    if (!token || role !== "STUDENT") return;
    try {
      const data = await api<StudySchedule>("/api/me/schedule", token);
      setStudentSchedule(data);
      if (data.currentTerm.startsWith("Chưa có")) setStudentScheduleTerm("all");
    }
    catch(e) { setError(e instanceof Error ? e.message : "Không tải được lịch học từ SQL Server."); }
  }
  async function loadAdminDataStatus() {
    if (!token || role !== "ADMIN") return;
    try { setAdminDataStatus(await api<typeof adminDataStatus>("/api/admin/data-status", token)); }
    catch(e) { setError(e instanceof Error ? e.message : "Không tải được trạng thái dữ liệu SQL Server."); }
  }
  async function refreshAdminOverview() {
    if (!token || role !== "ADMIN" || overviewRefreshing) return;
    setOverviewRefreshing(true);
    try {
      await Promise.all([refresh(), loadAdminDataStatus(), loadDecisions()]);
    } finally {
      setOverviewRefreshing(false);
    }
  }
  // Backward-compatible name resolution: an older Spring Boot process may return
  // only classId/studentId even when the Next.js UI has already been updated.
  // Look up missing labels through existing, Admin-only read APIs; never guess names.
  async function loadDecisions() {
    if (!token) return;
    try {
      const rows = await api<DecisionItem[]>("/api/admin/decisions", token);
      const needsClasses = rows.some(row => !row.className || !row.courseName);
      const missingStudentIds = [...new Set(rows.filter(row => row.studentId != null && !row.studentName)
        .map(row => row.studentId as number))];
      if (!needsClasses && missingStudentIds.length === 0) { setDecisions(rows); return; }

      // Populate the view immediately; enrich it without disturbing the audit log.
      setDecisions(rows);
      let classLookup = new Map<number, ClassView>();
      if (needsClasses) {
        try {
          const sections = await api<ClassView[]>("/api/admin/classes", token);
          classLookup = new Map(sections.map(section => [section.id, section]));
        } catch { /* Preserve original history even when a lookup fails. */ }
      }
      const studentsById = new Map<number, {fullName: string; email: string}>();
      // Small batches avoid flooding SQL Server when the audit contains many students.
      for (let offset = 0; offset < missingStudentIds.length; offset += 8) {
        const batch = missingStudentIds.slice(offset, offset + 8);
        const results = await Promise.allSettled(batch.map(id =>
          api<{fullName:string; email:string}>(`/api/admin/students/${id}`, token)));
        results.forEach((result, index) => {
          if (result.status === "fulfilled") studentsById.set(batch[index], result.value);
        });
      }
      setDecisions(rows.map(row => {
        const section = classLookup.get(row.classId);
        const student = row.studentId == null ? undefined : studentsById.get(row.studentId);
        return {
          ...row,
          className: row.className || section?.name || null,
          courseCode: row.courseCode || section?.courseCode || null,
          courseName: row.courseName || section?.courseName || null,
          studentName: row.studentName || student?.fullName || null,
          studentEmail: row.studentEmail || student?.email || null,
        };
      }));
    } catch (e) { setError(e instanceof Error ? e.message : "Không tải được nhật ký quyết định."); }
  }
  async function loadTeacherClasses() { if (!token) return; try { setClasses(await api<ClassView[]>("/api/teacher/classes", token)); } catch (e) { setError(e instanceof Error ? e.message : "Không tải được lớp giảng dạy."); } }
  async function showStudents(id: number) { if (!token) return; setTeacherClassId(id); try { setTeacherStudents(await api<StudentRow[]>(`/api/teacher/classes/${id}/students`, token)); } catch (e) { setError(e instanceof Error ? e.message : "Không tải được danh sách sinh viên."); } }
  // Refresh data without requiring users to press the refresh button.
  async function initChat() {
    if (!token) return;
    try {
      const cs = await api<ConversationView[]>(role === "ADMIN" ? "/api/chat/admin/conversations" : "/api/chat/conversations", token);
      setConversations(cs);
      if (cs.length) {
        const id = cs.some(c => c.id === selectedChatRef.current) ? selectedChatRef.current! : cs[0].id;
        await loadMessages(id);
      } else { selectedChatRef.current = null; setConversationId(null); setMessages([]); setPeerTyping(false); }
    } catch (e) { setError(e instanceof Error ? e.message : "Không tải được cuộc trò chuyện."); }
  }
  async function updateChatStatus(id: number, status: "OPEN" | "IN_PROGRESS" | "CLOSED") {
    if (!token || role !== "ADMIN" || chatStatusBusy) return;
    setChatStatusBusy(true); resetMessages();
    try {
      const changed = await api<ConversationView>(`/api/chat/admin/conversations/${id}/status?value=${encodeURIComponent(status)}`, token, { method: "PATCH" });
      setConversations(prev => prev.map(item => item.id === id ? changed : item));
      setNotice("Đã cập nhật trạng thái cuộc trò chuyện.");
    } catch (e) { setError(e instanceof Error ? e.message : "Không cập nhật được trạng thái cuộc trò chuyện."); }
    finally { setChatStatusBusy(false); }
  }
  async function startChat() {
    if (!token || role !== "STUDENT") return;
    try {
      const conversation = await api<ConversationView>("/api/chat/conversations", token, { method: "POST" });
      if (chatSubject.trim()) {
        await api<MessageView>(`/api/chat/conversations/${conversation.id}/messages`, token, {method:"POST",body:JSON.stringify({content:chatSubject.trim()})});
        setChatSubject("");
      }
      await loadMessages(conversation.id);
      // Re-fetch the conversation list without switching the selected conversation.
      setConversations(await api<ConversationView[]>("/api/chat/conversations", token));
      setNotice("Đã mở cuộc trò chuyện hỗ trợ. Bạn có thể tiếp tục nhắn trong đoạn chat cũ.");
    } catch (e) { setError(e instanceof Error ? e.message : "Không tạo được cuộc trò chuyện."); }
  }
  async function loadMessages(id: number) {
    if (!token) return;
    if (selectedChatRef.current !== id) {
      selectedChatRef.current = id;
      setPeerTyping(false);
      chatTextRef.current = "";
      typingLastInputRef.current = 0;
      setChatText("");
    }
    setConversationId(id);
    try {
      const list = await api<MessageView[]>(`/api/chat/conversations/${id}/messages`, token);
      if (selectedChatRef.current === id) setMessages(list);
    } catch (e) { setError(e instanceof Error ? e.message : "Không tải được tin nhắn."); }
  }
  async function sendMessage(e: FormEvent) {
    e.preventDefault();
    if (!token || !conversationId || !chatText.trim() || chatSendingRef.current) return;
    chatSendingRef.current = true; setChatSending(true);
    const currentId = conversationId;
    const content = chatText.trim();
    try {
      const sent = await api<MessageView>(`/api/chat/conversations/${currentId}/messages`, token, { method: "POST", body: JSON.stringify({ content }) });
      chatTextRef.current = "";
      typingLastInputRef.current = 0;
      setChatText("");
      lastTypingSent.current = {id:currentId, at:Date.now(), active:false};
      void api(`/api/chat/conversations/${currentId}/typing`, token, {method:"POST", body:JSON.stringify({typing:false})}).catch(()=>{});
      if (selectedChatRef.current === currentId) setMessages(old => old.some(m=>m.id === sent.id) ? old : [...old,sent]);
    } catch (e) { setError(e instanceof Error ? e.message : "Không gửi được tin nhắn."); }
    finally { chatSendingRef.current = false; setChatSending(false); }
  }
  // Poll only while a chat page is visible. No writes/DB changes occur when fetching messages.
  useEffect(() => {
    if (!token || tab !== "chat") return;
    let disposed = false;
    let running = false;
    const refreshChat = async () => {
      if (running || disposed || document.visibilityState === "hidden") return;
      running = true;
      try {
        const list = await api<ConversationView[]>(role === "ADMIN" ? "/api/chat/admin/conversations" : "/api/chat/conversations", token);
        if (disposed) return;
        setConversations(list);
        const current = selectedChatRef.current;
        const id = current != null && list.some(c => c.id === current) ? current : list[0]?.id ?? null;
        if (id === null) {
          if (selectedChatRef.current !== null) { selectedChatRef.current=null; setConversationId(null); setMessages([]); setPeerTyping(false); }
          return;
        }
        if (id !== selectedChatRef.current) { selectedChatRef.current=id; setConversationId(id); setMessages([]); setPeerTyping(false); }
        const [newMessages, state] = await Promise.all([
          api<MessageView[]>(`/api/chat/conversations/${id}/messages`,token),
          api<{typing:boolean}>(`/api/chat/conversations/${id}/typing`,token)
        ]);
        if (!disposed && selectedChatRef.current === id) {
          setMessages(old => old.length === newMessages.length && old.every((m,i)=>m.id === newMessages[i].id) ? old : newMessages);
          setPeerTyping(state.typing);
        }
      } catch { /* Leave existing chat visible during intermittent network failures. */ }
      finally { running=false; }
    };
    void refreshChat();
    const timer = window.setInterval(() => void refreshChat(), 1600);
    return () => {disposed=true;window.clearInterval(timer);};
  }, [token,tab,role]);
  // One heartbeat loop per selected conversation. After 2.8s without keystrokes, typing stops.
  useEffect(() => {
    if (!token || tab !== "chat" || !conversationId) return;
    const id = conversationId;
    let closed=false;
    const heartbeat=()=>{
      if(closed || document.visibilityState === "hidden") return;
      const active=!!chatTextRef.current.trim() && Date.now()-typingLastInputRef.current<2800;
      const prev=lastTypingSent.current;
      if(!active && (prev?.id!==id || !prev.active)) return;
      if(active && prev?.id===id && prev.active && Date.now()-prev.at<1750) return;
      lastTypingSent.current={id,at:Date.now(),active};
      void api(`/api/chat/conversations/${id}/typing`,token,{method:"POST",body:JSON.stringify({typing:active})}).catch(()=>{});
    };
    heartbeat();
    const interval=window.setInterval(heartbeat,850);
    return ()=>{closed=true;window.clearInterval(interval);};
  }, [token,tab,conversationId]);
  function updateChatText(value: string) {
    chatTextRef.current=value;
    typingLastInputRef.current=Date.now();
    setChatText(value);
  }
  useEffect(() => {
    if (tab === "chat" && chatListRef.current) chatListRef.current.scrollTop = chatListRef.current.scrollHeight;
  }, [tab, messages, peerTyping]);
  useEffect(() => { if (token && tab === "chat") void initChat(); if (token && (tab === "teacher" || tab === "teacher-students" || (tab === "schedule" && role === "TEACHER") || (tab === "overview" && role === "TEACHER"))) void loadTeacherClasses(); if (token && tab === "admin-waitlist") void loadAdminWaitlist(); if (token && tab === "decisions") { void loadDecisions(); void loadRoundRobinRuns(); } if (token && (tab === "catalog" || tab === "terms" || tab === "admin")) void loadCatalog(); if (token && tab === "admin") void loadDecisions(); if (token && role === "ADMIN" && tab === "admin") void loadAdminDataStatus(); if (token && role === "STUDENT" && (tab === "schedule" || tab === "overview")) void loadStudentSchedule(); if (token && (tab === "students" || tab === "teachers")) { void loadPeople(tab, 0, ""); void loadPersonFilters(); } }, [tab, token]);
  // Học kỳ trong chương trình đào tạo chỉ có 1 và 2 ở mỗi năm học (1–4).
  // Học kỳ thực tế của lớp (bảng HocKy) vẫn được giữ riêng cho lịch và đợt đăng ký.
  useEffect(() => {
    // Hạ phiên bản khi Next.js HMR còn giữ bộ lọc Học kỳ 3–8 của V20.7.
    if (role === "ADMIN" && !["all", "1", "2"].includes(classTerm)) {
      setClassTerm("all");
      setClassCourse("all");
      setAdminPage(0);
    }
  }, [role, classTerm]);
  const termChoices: (string | number)[] = role === "ADMIN"
    ? [1, 2]
    : Array.from(new Set(classes.map(c => c.term))).sort((a,b)=>a.localeCompare(b,"vi"));
  const courseByCode = new Map(catalogCourses.map(c => [c.code.toUpperCase(), c] as const));
  const yearOf = (code: string) => courseByCode.get(code.toUpperCase())?.year ?? null;
  const curriculumSemester = (code:string) => courseByCode.get(code.toUpperCase())?.semester ?? null;
  const curriculumLabel = (code:string) => {
    const course=courseByCode.get(code.toUpperCase());
    return course?.year != null && course.semester != null
      ? `Năm ${course.year} · Học kỳ ${course.semester}` : "Chưa phân học kỳ";
  };
  // Backend /api/classes is authoritative: it checks effective study year, retake approvals,
  // suspension and major before returning student classes. Never filter again by cohort on client.
  const studentScopedClasses = classes;
  const ownTermLabel = catalogTerms.find(t => getRegistrationWindowStatus(t, registrationClock) === "open");
  const effectiveStudyYear = myStudyProgress?.effectiveYear ?? myStudyProgress?.inferredYear ?? 0;
  const studySuspended = myStudyProgress?.status === "SUSPENDED";
  const invalidStudentCohort = role === "STUDENT" && !effectiveStudyYear && !studySuspended;
  // Khi chọn một năm học cụ thể, chỉ gợi ý học phần thực sự có lớp trong năm đó.
  const offeredCoursesInAcademicYear = new Set(classes.filter(c => classAcademicYear === "all" ||
    c.term.replace(/–/g, "-").includes(classAcademicYear)).map(c => c.courseCode));
  // Admin: với "Tất cả năm học", tra cứu toàn bộ danh mục. Với một năm cụ thể,
  // chỉ hiển thị học phần có lớp mở trong năm học đó.
  // Sinh viên: chỉ cho chọn học phần đang xuất hiện trong danh sách lớp của mình.
  const availableCourses = (role === "ADMIN"
    ? catalogCourses.filter(c =>
        (classAcademicYear === "all" || offeredCoursesInAcademicYear.has(c.code)) &&
        (classYear === "all" || (classYear === "other" ? c.year == null : c.year === Number(classYear))) &&
        (classTerm === "all" || c.semester === Number(classTerm))
      ).map(c => ({code:c.code, name:c.name}))
    : Array.from(new Map(studentScopedClasses.map(c => [c.courseCode, { code: c.courseCode, name: c.courseName }])).values())
  ).sort((a, b) => a.name.localeCompare(b.name, "vi"));
  // Năm học được lấy từ học kỳ của các lớp SQL thực tế (không tạo năm giả).
  const classAcademicYears = Array.from(new Set(classes.map(c =>
    c.term.match(/(?:19|20)\d{2}\s*[-–]\s*(?:19|20)\d{2}/)?.[0]?.replace(/\s/g, "").replace("–", "-") || ""
  ).filter(Boolean))).sort((a, b) => b.localeCompare(a));
  const visibleClasses = studentScopedClasses.filter(c =>
    (role !== "ADMIN" || classAcademicYear === "all" || c.term.replace(/–/g, "-").includes(classAcademicYear)) &&
    (role === "STUDENT" || classYear === "all" || (classYear === "other" ? yearOf(c.courseCode) == null : yearOf(c.courseCode) === Number(classYear))) &&
    (role === "STUDENT" || classTerm === "all" || (role === "ADMIN" ? curriculumSemester(c.courseCode) === Number(classTerm) : c.term === classTerm)) &&
    (role === "STUDENT" || classStatus === "all" || c.status === classStatus) &&
    (classCourse === "all" || c.courseCode === classCourse) &&
    (role !== "ADMIN" || !adminClassNameQuery.trim() || c.name.toLocaleLowerCase("vi-VN").includes(adminClassNameQuery.trim().toLocaleLowerCase("vi-VN")))
  );
  const CLASS_PAGE_SIZE = role === "STUDENT" ? 24 : 12;
  const ADMIN_PAGE_SIZE = 15;
  const shownClasses = visibleClasses.slice(classPage * CLASS_PAGE_SIZE, (classPage + 1) * CLASS_PAGE_SIZE);
  const shownAdminClasses = visibleClasses.slice(adminPage * ADMIN_PAGE_SIZE, (adminPage + 1) * ADMIN_PAGE_SIZE);
  const selectableShownAdminClasses = shownAdminClasses.filter(c => c.status === "OPEN");
  const validSelectedClassIds = selectedClassIds.filter(id => classes.some(c => c.id === id && c.status === "OPEN"));
  const allShownOpenSelected = selectableShownAdminClasses.length > 0 && selectableShownAdminClasses.every(c => validSelectedClassIds.includes(c.id));
  const programMajorChoices = sortMajors(Array.from(new Set(catalogCourses.map(courseMajorValue))));
  const filteredCatalog = catalogCourses.filter(c =>
    (catalogYear === "all" || (catalogYear === "other" ? c.year == null : c.year === Number(catalogYear))) &&
    (catalogMajor === "all" || courseMajorValue(c) === catalogMajor) &&
    [c.code, c.name].some(v => v.toLocaleLowerCase("vi-VN").includes(catalogSearch.trim().toLocaleLowerCase("vi-VN")))
  );
  const catalogPageCount = Math.max(1, Math.ceil(filteredCatalog.length / 15));
  const shownCatalog = filteredCatalog.slice(Math.min(catalogPage, catalogPageCount - 1) * 15, (Math.min(catalogPage, catalogPageCount - 1) + 1) * 15);
  const activeRegistrations = new Set(registrations.filter(r => r.status === "ACTIVE").map(r => r.classId));
  const activeWaitlist = new Set(waitlist.filter(w => w.status === "WAITING").map(w => w.classId));
  const filteredTeacherClasses = classes.filter(c => [c.name, c.courseCode, c.courseName, c.term].some(v => v.toLocaleLowerCase("vi-VN").includes(teacherQuery.toLocaleLowerCase("vi-VN"))));
  const filteredTeacherStudents = teacherStudents.filter(s => [s.fullName, s.email].some(v => v.toLocaleLowerCase("vi-VN").includes(teacherStudentQuery.toLocaleLowerCase("vi-VN"))));
  const filteredAdminWait = adminWaitlist.filter(w => [w.className, w.studentName, w.studentEmail].some(v => v.toLocaleLowerCase("vi-VN").includes(adminWaitQuery.toLocaleLowerCase("vi-VN"))));
  const filteredDecisions = decisions.filter(d => {
    const keyword = decisionQuery.trim().toLocaleLowerCase("vi-VN");
    const matchesText = !keyword || [String(d.classId), String(d.studentId || ""), d.className || "", d.courseCode || "", d.courseName || "", d.studentName || "", d.studentEmail || "", studentCode(d.studentEmail, d.studentId || ""), decisionResultLabel(d.result), d.result, d.reason || ""].some(v => v.toLocaleLowerCase("vi-VN").includes(keyword));
    const matchesResult = decisionResult === "all" || d.result === decisionResult;
    const date = new Date(d.occurredAt);
    const localDay = Number.isNaN(date.getTime()) ? "" : `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;
    return matchesText && matchesResult && (!decisionFromDate || (localDay !== "" && localDay >= decisionFromDate)) && (!decisionToDate || (localDay !== "" && localDay <= decisionToDate));
  });
  const registrationCourseLookup = new Map(catalogCourses.map(c => [c.code.toUpperCase(), c] as const));
  const registrationClassMajor = (code: string): string => {
    const course = registrationCourseLookup.get(code.toUpperCase());
    return course ? courseMajorValue(course) : "Chưa phân ngành";
  };
  const registrationMajorChoices = sortMajors(Array.from(new Set(classes.map(c => registrationClassMajor(c.courseCode)))));
  const classesByRegistrationMajor = classes.filter(c => {
    const major = registrationClassMajor(c.courseCode);
    return adminRegistrationMajor === "all" || major === adminRegistrationMajor
      || (adminRegistrationMajor !== "Dùng chung" && adminRegistrationMajor !== "Chưa phân ngành" && major === "Dùng chung");
  });
  const registrationCourseChoices = Array.from(new Map(classesByRegistrationMajor.map(c => [c.courseCode, c.courseName] as const)).entries())
    .sort((a,b) => a[1].localeCompare(b[1], "vi"));
  const classesByRegistrationCourse = classesByRegistrationMajor.filter(c =>
    adminRegistrationCourse === "all" || c.courseCode === adminRegistrationCourse
  );
  const registrationSectionChoices = [...classesByRegistrationCourse].sort((a,b) => a.name.localeCompare(b.name, "vi"));
  const adminRegistrationSearchKey = adminStudentSearch.trim().toLocaleLowerCase("vi-VN");
  const filteredAdminRegistrationClasses = classesByRegistrationCourse.filter(c =>
    (adminRegistrationSection === "all" || String(c.id) === adminRegistrationSection) &&
    [c.name, c.courseCode, c.courseName, c.teacherName, c.term].some(v => v?.toLocaleLowerCase("vi-VN").includes(adminRegistrationSearchKey))
  ).sort((a, b) => {
    const courseOrder = a.courseCode.localeCompare(b.courseCode, "vi");
    if (courseOrder !== 0) return courseOrder;
    const termOrder = a.term.localeCompare(b.term, "vi");
    if (termOrder !== 0) return termOrder;
    return a.name.localeCompare(b.name, "vi");
  });
  const REGISTRATION_PAGE_SIZE = 10;
  const registrationPageCount = Math.max(1, Math.ceil(filteredAdminRegistrationClasses.length / REGISTRATION_PAGE_SIZE));
  const currentRegistrationPage = Math.min(adminRegistrationPage, registrationPageCount - 1);
  const shownAdminRegistrationClasses = filteredAdminRegistrationClasses.slice(
    currentRegistrationPage * REGISTRATION_PAGE_SIZE, (currentRegistrationPage + 1) * REGISTRATION_PAGE_SIZE
  );
  const selectedAdminRegistrationClass = classes.find(c => c.id === adminRegistrationClass) ?? null;
  const totalRegisteredStudents = summary?.activeRegistrations ?? classes.reduce((sum, c) => sum + c.enrolled, 0);
  const configuredRegistrationTerm = catalogTerms.find(t => t.registrationOpen) ?? null;
  const activeRegistrationTerm = catalogTerms.find(t => getRegistrationWindowStatus(t, registrationClock) === "open") ?? null;
  const overviewRegistrationTerm = activeRegistrationTerm ?? configuredRegistrationTerm;
  const overviewRegistrationStatus = overviewRegistrationTerm ? getRegistrationWindowStatus(overviewRegistrationTerm, registrationClock) : "closed";
  const studentWindowMessage = configuredRegistrationTerm
    ? overviewRegistrationStatus === "expired" ? "Đợt đăng ký học phần đã hết hạn."
      : overviewRegistrationStatus === "upcoming" ? "Chưa đến thời gian mở đăng ký học phần."
      : "Chưa có học kỳ mở đăng ký."
    : "Chưa có học kỳ mở đăng ký.";
  const adminOverviewKpis: {label:string;value:number | undefined;helper:string;destination:Tab}[] = [
    {label:"Sinh viên",value:adminDataStatus?.students,helper:"Quản lý hồ sơ",destination:"students"},
    {label:"Giảng viên",value:adminDataStatus?.teachers,helper:"Quản lý hồ sơ",destination:"teachers"},
    {label:"Học phần",value:adminDataStatus?.courses ?? catalogCourses.length,helper:"Danh mục đào tạo",destination:"catalog"},
    {label:"Lớp học phần",value:summary?.classes ?? adminDataStatus?.classes,helper:"Lớp đã tạo",destination:"create-class"}
  ];
  const adminOverviewActivity: {label:string;value:number | undefined;destination:Tab;description:string}[] = [
    {label:"Đăng ký thành công",value:summary?.activeRegistrations,destination:"admin-registrations",description:"Lượt đăng ký đang có hiệu lực"},
    {label:"Đang trong danh sách chờ",value:summary?.waitingStudents,destination:"admin-waitlist",description:"Lượt chờ chưa được phân bổ"},
    {label:"Quyết định xét gần đây",value:decisions.length,destination:"decisions",description:"Bản ghi trả về từ nhật ký"}
  ];
  const adminOverviewShortcuts: {label:string;tab:Tab}[] = [
    {label:"Quản lý sinh viên",tab:"students"},
    {label:"Quản lý giảng viên",tab:"teachers"},
    {label:"Quản lý học phần",tab:"catalog"},
    {label:"Quản lý lớp học phần",tab:"create-class"},
    {label:"Chương trình đào tạo",tab:"program"},
    {label:"Tin nhắn / hỗ trợ",tab:"chat"}
  ];
  const profileRoleLabel = role === "ADMIN" ? "Quản trị viên" : role === "TEACHER" ? "Giảng viên" : "Sinh viên";
  const classMap = new Map(classes.map(c => [c.id, c] as const));
  const registrationRows = registrations.map(r => ({ ...r, classInfo: classMap.get(r.classId) ?? null }));
  const activeRegistrationRows = registrationRows.filter(r => r.status === "ACTIVE");
  const currentStudyRows = studentSchedule.classes.filter(c => c.term === studentSchedule.currentTerm);
  const studentCurrentCredits = currentStudyRows.reduce((sum,c) => sum + c.credits, 0);
  // Sinh viên chỉ xem chương trình thuộc ngành trong hồ sơ, kèm các môn dùng chung.
  // Admin/Giảng viên giữ bộ lọc tra cứu ngành như V20.20.
  // Trường hợp hồ sơ sinh viên thiếu ngành: không hiện toàn bộ danh mục ngoài ý muốn.
  // Backend vẫn quyết định quyền đăng ký học phần, độc lập với giao diện tra cứu này.
  const studentProgramMajor = role === "STUDENT" ? normalizeLabel(profile?.major) : "";
  const effectiveProgramMajor = role === "STUDENT" ? studentProgramMajor : programMajorFilter;
  const studentMajorMissing = role === "STUDENT" && !studentProgramMajor;
  const visibleProgramCourses = catalogCourses.filter(c => {
    if (studentMajorMissing) return false;
    const major = courseMajorValue(c).toLocaleLowerCase("vi-VN");
    const selected = effectiveProgramMajor.toLocaleLowerCase("vi-VN");
    const matchesMajor = selected === "all" || major === selected
      || (selected !== "dùng chung" && major === "dùng chung");
    const matchesYear = programYearFilter === "all"
      || (programYearFilter === "other" ? c.year == null : c.year === Number(programYearFilter));
    return matchesMajor && matchesYear;
  });
  const studentWaitCount = waitlist.filter(w => w.status === "WAITING").length;
  const teacherStudentCount = classes.reduce((sum, c) => sum + c.enrolled, 0);
  const studentScheduleCards = studentSchedule.classes.map(c => ({ id:c.registrationId, title:`${c.courseName} · ${c.className}`, schedule:c.schedule || "Chưa cập nhật thời gian học", room:c.room || "Chưa cập nhật phòng", term:c.term, credits:c.credits, teacher:c.teacherName }));
  const studentCurrentScheduleCards = studentScheduleCards.filter(c => c.term === studentSchedule.currentTerm);
  const selectedStudentScheduleCards = studentScheduleCards.filter(c => studentScheduleTerm === "all" || (studentScheduleTerm === "current" ? c.term === studentSchedule.currentTerm : c.term === studentScheduleTerm));
  const classFilters = <div className="filter-panel" aria-label="Bộ lọc lớp học">
    <div className="filter-heading"><strong>{role === "ADMIN" ? "Tìm và lọc lớp học phần" : role === "STUDENT" ? "Lọc học phần phù hợp" : "Lọc danh sách lớp"}</strong></div>
    <div className="filter-controls">
      {role === "ADMIN" && <label className="admin-class-query-field">Từ khóa<input aria-label="Tìm theo tên hoặc mã lớp" type="search" placeholder="Tên lớp hoặc mã lớp..." value={adminClassNameQuery} onChange={e => {setAdminClassNameQuery(e.target.value);setAdminPage(0);setSelectedClassIds([]);}} /></label>}
      {role === "ADMIN" && <label>Năm học<select aria-label="Lọc lớp theo năm học" value={classAcademicYear} onChange={e => { setClassAcademicYear(e.target.value); setClassCourse("all"); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}><option value="all">Tất cả năm học</option>{classAcademicYears.map(y => <option key={y} value={y}>{y}</option>)}</select></label>}
      {role !== "STUDENT" && <label>Năm đào tạo<select aria-label="Năm đào tạo" value={classYear} onChange={e => { setClassYear(e.target.value); setClassCourse("all"); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}><option value="all">Tất cả các năm</option>{[1,2,3,4].map(y => <option key={y} value={y}>Năm {y}</option>)}{role === "ADMIN" && <option value="other">Học phần cũ / chưa phân loại</option>}</select></label>}
      <label>Học phần<select aria-label="Học phần" value={classCourse} onChange={e => { setClassCourse(e.target.value); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}><option value="all">Tất cả học phần</option>{availableCourses.map(c => <option key={c.code} value={c.code}>{c.code} · {c.name}</option>)}</select></label>
      {role !== "STUDENT" && <label>Học kỳ<select aria-label="Học kỳ" value={classTerm} onChange={e => { setClassTerm(e.target.value); setClassCourse("all"); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}><option value="all">Tất cả học kỳ</option>{termChoices.map(t => <option key={t} value={t}>{typeof t === "number" ? `Học kỳ ${t}` : t}</option>)}</select></label>}
      {role !== "STUDENT" && <label>Trạng thái<select aria-label="Trạng thái lớp" value={classStatus} onChange={e => { setClassStatus(e.target.value); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}><option value="all">Tất cả trạng thái</option><option value="OPEN">Đang mở</option><option value="CLOSED">Đã đóng</option><option value="CANCELLED">Đã hủy</option></select></label>}
      <button className="secondary filter-reset" type="button" onClick={() => { setClassAcademicYear("all"); setClassYear("all"); setClassCourse("all"); setClassTerm("all"); setClassStatus("all"); if (role === "ADMIN") setAdminClassNameQuery(""); setClassPage(0); setAdminPage(0); setSelectedClassIds([]); }}>Xóa lọc</button>
    </div>
  </div>;
  const pager = (current: number, total: number, size: number, setPage: (p: number) => void) => total > size ? <div className="list-pager"><span>Trang {current + 1}/{Math.ceil(total / size)} · {total} lớp</span><div><button className="secondary" disabled={current === 0} onClick={() => setPage(Math.max(0, current - 1))}>Trước</button><button className="secondary" disabled={(current + 1) * size >= total} onClick={() => setPage(current + 1)}>Tiếp</button></div></div> : null;
  const navItems: { id: Tab; label: string }[] = [
    ...(role === "STUDENT" ? [
      { id: "overview" as Tab, label: "Tổng quan" },
      { id: "classes" as Tab, label: "Đăng ký học phần" },
      { id: "program" as Tab, label: "Chương trình đào tạo" },
      { id: "registrations" as Tab, label: "Lớp học đã đăng ký" },
      { id: "waitlist" as Tab, label: "Danh sách chờ" },
      { id: "schedule" as Tab, label: "Lịch học cá nhân" },
      { id: "chat" as Tab, label: "Tin nhắn hỗ trợ" },
      { id: "profile" as Tab, label: "Thông tin cá nhân" }
    ] : []),
    ...(role === "TEACHER" ? [
      { id: "overview" as Tab, label: "Tổng quan" },
      { id: "program" as Tab, label: "Tra cứu chương trình đào tạo" },
      { id: "teacher" as Tab, label: "Lớp học phần phụ trách" },
      { id: "teacher-students" as Tab, label: "Danh sách sinh viên" },
      { id: "schedule" as Tab, label: "Lịch giảng dạy" },
      { id: "profile" as Tab, label: "Thông tin cá nhân" }
    ] : []),
    ...(role === "ADMIN" ? [
      { id: "admin" as Tab, label: "Tổng quan" },
      { id: "students" as Tab, label: "Quản lý sinh viên" },
      { id: "teachers" as Tab, label: "Quản lý giảng viên" },
      { id: "catalog" as Tab, label: "Quản lý học phần" },
      { id: "program" as Tab, label: "Chương trình đào tạo" },
      { id: "create-class" as Tab, label: "Quản lý lớp học phần" },
      { id: "admin-registrations" as Tab, label: "Theo dõi đăng ký học phần" },
      { id: "terms" as Tab, label: "Quản lý học kỳ" },
      { id: "admin-waitlist" as Tab, label: "Danh sách chờ" },
      { id: "decisions" as Tab, label: "Lịch sử xét đăng ký" },
      { id: "chat" as Tab, label: "Tin nhắn / hỗ trợ" }
    ] : [])
  ];
  if (authChecking) return <main className="reference-login school-login"><div className="school-session-check" role="status"><SchoolLogo /><p>Đang kiểm tra phiên đăng nhập...</p></div></main>;
  if (!token) return <main className="reference-login school-login">
    <ToastMessages error={error} notice={notice} onCloseError={() => setError("")} onCloseNotice={() => setNotice("")} />
    <div className="school-login-card">
      <section className="school-login-intro" aria-labelledby="portal-title">
        <header className="school-login-brand"><SchoolLogo /><div><strong>CỔNG ĐÀO TẠO</strong><span>Đăng ký học phần trực tuyến</span></div></header>
        <div className="school-login-intro-copy">
          <span className="school-login-eyebrow">SẴN SÀNG CHO HỌC KỲ MỚI</span>
          <h1 id="portal-title">Chủ động kế hoạch.<br /><span>Vững bước học tập.</span></h1>
          <p>Đăng ký học phần và theo dõi việc học của bạn trong cùng một nơi.</p>
        </div>
        <ul className="school-login-features">
          <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 6c-3-2-6-2-9-1v14c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1Zm0 0v14" /></svg><span>Tìm và đăng ký học phần</span></li>
          <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4m10-4v4M3 11h18m-13 5h2m4 0h2" /></svg><span>Theo dõi lịch học cá nhân</span></li>
          <li><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg><span>Cập nhật danh sách chờ</span></li>
        </ul>
      </section>
      <section className="school-login-form-panel" aria-labelledby="login-title">
      <div className="reference-login-heading"><span className="school-login-eyebrow">TÀI KHOẢN ĐÀO TẠO</span><h2 id="login-title">Chào mừng trở lại</h2><p>Đăng nhập để tiếp tục đến cổng đào tạo.</p></div>
      <form className="reference-login-form" onSubmit={signIn} aria-label="Đăng nhập cổng đào tạo" aria-busy={busy}>
        <label htmlFor="school-email">Email trường<input id="school-email" name="email" type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="ten@truong.edu.vn" autoComplete="username" autoCapitalize="none" spellCheck={false} required /></label>
        <PasswordField label="Mật khẩu" name="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Nhập mật khẩu của bạn" autoComplete="current-password" required />
        <button className="primary full" disabled={busy} type="submit">{busy ? "Đang đăng nhập..." : "Đăng nhập"}</button>
      </form>
      <p className="school-login-support"><strong>Bạn cần hỗ trợ?</strong><span>Liên hệ phòng đào tạo để được hỗ trợ tài khoản.</span></p>
      </section>
    </div>
    <footer className="school-login-footer">Hệ thống đăng ký lớp học phần <span aria-hidden="true">·</span> Cổng đào tạo trực tuyến</footer>
  </main>;
  const currentPageLabel = navItems.find(i => i.id === tab)?.label || (tab === "password" ? "Đổi mật khẩu" : "Tổng quan");
  return <main className={`app-shell ${role === "ADMIN" ? "reference-admin" : "reference-portal"}`}><ToastMessages error={error} notice={notice} onCloseError={() => setError("")} onCloseNotice={() => setNotice("")} /><aside className="sidebar"><div className="sidebar-brand"><SchoolLogo /><div><b>Đăng ký học phần</b><small>Cổng đào tạo</small></div></div>{role === "ADMIN" ? <div className="reference-sidebar-user"><b>Quản trị viên</b><span>Quản trị viên</span></div> : <div className="reference-sidebar-user"><b>{profile?.fullName || displayName}</b><span>{profileRoleLabel}</span></div>}<nav>{navItems.map(n => <button key={n.id} className={`nav-item ${tab === n.id ? "active" : ""}`} onClick={() => { closeAdminEditor(); setPersonDetail(null); setClassEditing(null); setTab(n.id); resetMessages(); }}>{n.label}{n.id === "waitlist" && studentWaitCount > 0 ? <span className="nav-count">{studentWaitCount}</span> : null}</button>)}</nav><div className="sidebar-bottom">{<button type="button" className="sidebar-signout" onClick={()=>setTab("password")}><span>Đổi mật khẩu</span></button>}<button type="button" className="sidebar-signout" onClick={signOut}><span>Đăng xuất</span></button></div></aside><section className="main-area"><header className="topbar topbar-with-title"><div className="topbar-page-heading"><h1>{currentPageLabel}</h1></div><div className="profile-area"><div className="profile-name"><b>{profile?.fullName || displayName}</b><small>{profileRoleLabel}</small></div></div></header><div className="content-area">

    {tab === "overview" && role === "STUDENT" && <>
      
      <div className="reference-summary-grid portal-summary-grid">
        <article className="reference-summary-card"><span>Lớp đã đăng ký</span><strong>{activeRegistrationRows.length}</strong></article>
        <article className="reference-summary-card"><span>Số tín chỉ hiện tại</span><strong>{studentCurrentCredits}</strong></article>
        <article className="reference-summary-card"><span>Lượt trong danh sách chờ</span><strong>{studentWaitCount}</strong></article>
      </div>
      <section className="info-card simple-stack"><h3>Lịch học sắp tới</h3>{studentCurrentScheduleCards.length ? studentCurrentScheduleCards.slice(0, 3).map(item => <div key={item.id} className="schedule-strip"><div><b>{item.title}</b><p>{item.schedule}</p></div><span>{item.room}</span></div>) : <div className="empty-state">Chưa có lớp đăng ký trong {studentSchedule.currentTerm || "học kỳ đang mở"}.</div>}</section>
    </>}

    {tab === "overview" && role === "TEACHER" && <>
      
      <div className="reference-summary-grid portal-summary-grid">
        <article className="reference-summary-card"><span>Lớp đang phụ trách</span><strong>{classes.length}</strong></article>
        <article className="reference-summary-card"><span>Tổng lượt đăng ký trong các lớp</span><strong>{teacherStudentCount}</strong></article>
      </div>
      <section className="info-card simple-stack"><div className="section-heading-inline"><h3>Lịch giảng dạy</h3><button type="button" className="secondary" onClick={() => setTab("schedule")}>Xem lịch</button></div>{classes.length ? classes.slice(0, 3).map(c => <div key={c.id} className="schedule-strip"><div><b>{c.courseName} · {c.name}</b><p>{c.schedule || "Chưa cập nhật thời gian học"}</p></div><span>{c.room || "Chưa cập nhật phòng"}</span></div>) : <div className="empty-state">Chưa có lớp học phần được phân công.</div>}</section>
    </>}

    {tab === "schedule" && role === "STUDENT" && <>
      
      <div className="program-tools"><label>Học kỳ <select value={studentScheduleTerm} onChange={e=>setStudentScheduleTerm(e.target.value)}><option value="current">{studentSchedule.currentTerm || "Học kỳ đang mở"}</option><option value="all">Tất cả học kỳ</option>{Array.from(new Set(studentScheduleCards.map(c=>c.term))).filter(t=>t!==studentSchedule.currentTerm).map(t=><option key={t} value={t}>{t}</option>)}</select></label></div><section className="info-card"><WeekSchedule items={selectedStudentScheduleCards} /></section>
    </>}

    {tab === "schedule" && role === "TEACHER" && <>
      
      <section className="info-card"><WeekSchedule items={classes.map(c=>({id:c.id,title:`${c.courseName} · ${c.name}`,schedule:c.schedule,room:c.room || "Chưa cập nhật phòng",term:c.term}))}/></section>
    </>}

    {tab === "profile" && <section className="student-profile-v2052" aria-label="Thông tin cá nhân">
      <div className="personal-head">
        <div className="personal-avatar" aria-hidden="true">{(profile?.fullName || displayName).trim().split(/\s+/).slice(-2).map(part=>part.charAt(0)).join("").toUpperCase()}</div>
        <div className="personal-identity">
          <span className="personal-overline">Hồ sơ tài khoản</span>
          <h2>{profile?.fullName || displayName}</h2>
          <p>{role === "STUDENT" ? `Sinh viên · ${profile?.major || "Chưa có ngành đào tạo"}` : role === "TEACHER" ? `Giảng viên · ${profile?.faculty || "Chưa có khoa"}` : profileRoleLabel}</p>
          <div className="personal-meta">
            <span>{role === "STUDENT" ? `MSSV: ${studentCode(profile?.email,profile?.username || profile?.id || "-")}` : `Tên đăng nhập: ${profile?.username || "Chưa có"}`}</span>
            {role === "STUDENT" && <span>Khóa {profile?.cohort || "chưa cập nhật"}</span>}
            <span className={profile?.accountStatus === "ACTIVE" ? "personal-tag is-active" : "personal-tag"}>{profile?.accountStatus === "ACTIVE" ? "Tài khoản hoạt động" : `Tài khoản: ${profile?.accountStatus || "Chưa có trạng thái"}`}</span>
          </div>
        </div>
        <div className="personal-head-actions">
          <button className="secondary" type="button" onClick={()=>setTab("password")}>Đổi mật khẩu</button>
        </div>
      </div>

      <div className="personal-columns">
        <div className="personal-main-column">
          <section className="personal-panel">
            <header className="personal-panel-head"><div><h3>Thông tin cơ bản</h3><p>Thông tin được lưu trong hồ sơ của bạn</p></div></header>
            <dl className="personal-details">
              <div><dt>Họ và tên</dt><dd>{profile?.fullName || displayName}</dd></div>
              <div><dt>{role === "STUDENT" ? "Mã sinh viên" : "Tên đăng nhập"}</dt><dd>{role === "STUDENT" ? studentCode(profile?.email,profile?.username || profile?.id || "-") : profile?.username || "Chưa cập nhật"}</dd></div>
              <div><dt>Email</dt><dd className="personal-wrap">{profile?.email || "Chưa cập nhật"}</dd></div>
              <div><dt>Ngày sinh</dt><dd>{profile?.dateOfBirth ? profile.dateOfBirth.split("-").reverse().join("/") : "Chưa cập nhật"}</dd></div>
              <div><dt>Vai trò</dt><dd>{profileRoleLabel}</dd></div>
              <div><dt>Khoa</dt><dd>{profile?.faculty || "Chưa cập nhật"}</dd></div>
              {role === "STUDENT" && <><div><dt>Ngành đào tạo</dt><dd>{profile?.major || "Chưa cập nhật"}</dd></div><div><dt>Khóa nhập học</dt><dd>{profile?.cohort || "Chưa cập nhật"}</dd></div></>}
              {role === "TEACHER" && <div><dt>Ngành phụ trách</dt><dd>{profile?.major || "Chưa cập nhật"}</dd></div>}
            </dl>
          </section>

          {role === "STUDENT" && <section className="personal-panel">
            <header className="personal-panel-head"><div><h3>Tiến độ học tập</h3><p>Do nhà trường xác nhận; không phụ thuộc vào năm sinh</p></div><span className="personal-tag">{myStudyProgress ? studyStatusLabels[myStudyProgress.status] : "Chưa xác nhận"}</span></header>
            <div className="personal-study-stats">
              <div><span>Năm đào tạo thực tế</span><strong>{myStudyProgress?.effectiveYear ? `Năm ${myStudyProgress.effectiveYear}` : "Theo khóa nhập học"}</strong></div>
              <div><span>Khóa nhập học</span><strong>{profile?.cohort || "Chưa cập nhật"}</strong></div>
            </div>
            <dl className="personal-details compact">
              <div><dt>Học phần được duyệt học lại</dt><dd>{myStudyProgress?.retakeCourseCodes?.length ? myStudyProgress.retakeCourseCodes.join(", ") : "Chưa có"}</dd></div>
              {myStudyProgress?.note && <div><dt>Ghi chú của nhà trường</dt><dd className="personal-wrap">{myStudyProgress.note}</dd></div>}
            </dl>
            <div className="personal-panel-foot"><span>Việc điều chỉnh tiến độ và quyền học lại do quản trị viên phê duyệt.</span></div>
          </section>}
          <section className="personal-panel personal-system-panel">
            <header className="personal-panel-head"><div><h3>Thông tin hệ thống</h3><p>Chỉ dùng khi cần đối chiếu hoặc liên hệ hỗ trợ</p></div></header>
            <dl className="personal-details compact"><div><dt>Mã tài khoản nội bộ</dt><dd>{profile?.id ?? "-"}</dd></div><div><dt>Tên đăng nhập</dt><dd>{profile?.username || "Chưa cập nhật"}</dd></div></dl>
          </section>
        </div>
        <div className="personal-side-column">
          {role !== "ADMIN" && <section className="personal-panel personal-readonly-panel" aria-label="Quyền cập nhật hồ sơ">
            <header className="personal-panel-head"><div><h3>Quản lý thông tin cá nhân</h3><p>Hồ sơ chỉ xem, do nhà trường quản lý và xác minh</p></div><span className="personal-tag">Chỉ xem</span></header>
            <p>Để bảo đảm thông tin chính xác, sinh viên và giảng viên không thể tự sửa họ tên, ngày sinh, khoa, ngành hoặc các thông tin hồ sơ khác.</p>
            <div className="personal-contact-notice"><strong>Cần điều chỉnh thông tin?</strong><span>Vui lòng liên hệ Quản trị viên và cung cấp mã sinh viên hoặc tên đăng nhập, thông tin cần sửa và căn cứ xác minh phù hợp. Quản trị viên sẽ kiểm tra trước khi cập nhật.</span></div>
            {role === "STUDENT" && <button className="secondary personal-support-action" type="button" onClick={()=>setTab("chat")}>Liên hệ qua Tin nhắn hỗ trợ</button>}
            <p className="personal-helper">Bạn vẫn có thể tự đổi mật khẩu trong mục bảo mật tài khoản. Đây không phải là chỉnh sửa hồ sơ.</p>
          </section>}
          <section className="personal-panel">
            <header className="personal-panel-head"><div><h3>Truy cập nhanh</h3><p>Đi đến các chức năng thường dùng</p></div></header>
            <div className="personal-shortcuts">
              {role === "STUDENT" && <><button type="button" onClick={()=>setTab("classes")}>Đăng ký học phần <span aria-hidden="true">→</span></button><button type="button" onClick={()=>setTab("registrations")}>Lớp học đã đăng ký <span aria-hidden="true">→</span></button><button type="button" onClick={()=>setTab("schedule")}>Lịch học cá nhân <span aria-hidden="true">→</span></button></>}
              {role === "TEACHER" && <><button type="button" onClick={()=>setTab("teacher")}>Lớp được phân công <span aria-hidden="true">→</span></button><button type="button" onClick={()=>setTab("schedule")}>Lịch giảng dạy <span aria-hidden="true">→</span></button></>}
              <button type="button" onClick={()=>setTab("password")}>Đổi mật khẩu <span aria-hidden="true">→</span></button>
            </div>
          </section>
        </div>
      </div>
    </section>}

    {tab === "teacher-students" && role === "TEACHER" && <>
      
      <section className="table-wrap"><table className="data-table"><thead><tr><th>Học phần</th><th>Mã lớp</th><th>Lịch / thời gian</th><th>Phòng</th><th>Sức chứa</th><th>Danh sách</th></tr></thead><tbody>{classes.length ? classes.map(c => <tr key={c.id}><td><b>{c.courseCode}</b><div>{c.courseName}</div></td><td>{c.name}</td><td>{c.schedule || "Chưa cập nhật"}</td><td>{c.room || "-"}</td><td>{c.capacity}</td><td><button type="button" className="secondary" onClick={() => void showStudents(c.id)}>Xem sinh viên</button></td></tr>) : <tr><td colSpan={6}>Chưa có lớp học phần được phân công.</td></tr>}</tbody></table></section>
      {teacherClassId !== null && <section className="table-wrap"><div className="section-heading-inline"><h3>Danh sách sinh viên của lớp {classes.find(c => c.id === teacherClassId)?.name || `#${teacherClassId}`}</h3><small>{teacherStudents.length} sinh viên</small></div><table className="data-table"><thead><tr><th>Họ và tên</th><th>Email</th><th>Khoa</th><th>Thời điểm đăng ký</th></tr></thead><tbody>{teacherStudents.length ? teacherStudents.map(sv => <tr key={sv.studentId}><td>{sv.fullName}</td><td>{sv.email}</td><td>{sv.faculty || "—"}</td><td>{fmt(sv.registeredAt)}</td></tr>) : <tr><td colSpan={4}>Chưa có sinh viên nào đăng ký lớp này.</td></tr>}</tbody></table></section>}
    </>}

    {tab === "program" && <section className={`program-page ${role === "ADMIN" ? "admin-program-compact-v2061" : ""}`} aria-label="Chương trình đào tạo">
      
      <div className="program-toolbar">
        <div className="program-overview compact">
          <div className="program-stat compact"><div><small>Số học phần</small><strong>{visibleProgramCourses.length} học phần</strong></div></div>
          <div className="program-stat compact"><div><small>Tổng tín chỉ danh mục</small><strong>{visibleProgramCourses.reduce((sum,c)=>sum+c.credits,0)} tín chỉ</strong></div></div>
        </div>
        <div className={`program-tools compact ${role === "ADMIN" ? "admin-program-filter-panel" : ""}`}>
          <label>Hiển thị năm đào tạo <select value={programYearFilter} onChange={e=>setProgramYearFilter(e.target.value)}><option value="all">Tất cả năm</option>{[1,2,3,4].map(y=><option key={y} value={String(y)}>Năm {y}</option>)}<option value="other">Chưa phân năm</option></select></label>
          {role === "STUDENT"
            ? <div className="program-major-readonly"><span>Ngành học</span><strong>{studentProgramMajor || "Chưa cập nhật"}</strong></div>
            : <label>Ngành học <select value={programMajorFilter} onChange={e=>setProgramMajorFilter(e.target.value)}><option value="all">Tất cả ngành</option>{programMajorChoices.map(major=><option key={major} value={major}>{major}</option>)}</select></label>}
          {role === "ADMIN" && <button type="button" className="secondary admin-program-clear" onClick={()=>{setProgramYearFilter("all");setProgramMajorFilter("all");}}>Xóa lọc</button>}
        </div>
      </div>
      {studentMajorMissing ? <p className="program-scope-note" role="status">Hồ sơ sinh viên chưa có ngành đào tạo. Vui lòng liên hệ quản trị viên để cập nhật ngành trước khi xem chương trình.</p>
        : effectiveProgramMajor !== "all" && effectiveProgramMajor !== "Dùng chung" &&
          <p className="program-scope-note">Bao gồm học phần dùng chung và học phần ngành {effectiveProgramMajor}. Danh mục học phần mang tính tham khảo, không thay thế khung chương trình đào tạo chính thức của trường.</p>}
      <div className="program-grid">{[1,2,3,4,0].filter(y=>programYearFilter === "all" || (programYearFilter === "other" ? y === 0 : y === Number(programYearFilter))).map(year=>{
        const courses = visibleProgramCourses.filter(c => (c.year ?? 0) === year);
        if (!courses.length) return null;
        return <article className="program-year" key={year}><header><h3>{year ? `Năm ${year}` : "Chưa phân năm đào tạo"}</h3><span>{courses.reduce((sum,c)=>sum+c.credits,0)} tín chỉ</span></header>
          <div className="program-semesters">{[1,2,0].map(semester=>{
            const list = courses.filter(c => (c.semester ?? 0) === semester);
            if (!list.length) return null;
            return <section key={semester} className="program-semester"><div className="program-term-heading"><b>{semester ? `Học kỳ ${semester}` : "Chưa phân học kỳ"}</b><span>{list.reduce((sum,c)=>sum+c.credits,0)} tín chỉ</span></div>{list.map(c=><div className="program-subject" key={c.id}><div><span>{c.name}</span><small>{c.code} · {courseMajorValue(c)}</small></div><b>{c.credits} TC</b></div>)}</section>;
          })}</div>
        </article>;
      })}</div>
      {!studentMajorMissing && !visibleProgramCourses.length && <div className="empty-state">Chưa có học phần phù hợp.</div>}
    </section>}
    {tab === "classes" && <div className={role === "STUDENT" ? "student-class-browser" : undefined}>
      {role === "STUDENT" && !activeRegistrationTerm && <div className="notice error">{studentWindowMessage}</div>}
      <div className="page-actions"><button className="secondary" onClick={() => void refresh()}>Làm mới</button></div>{role !== "TEACHER" && <form className="search-row" onSubmit={search}><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Tìm theo tên lớp, mã học phần hoặc giảng viên..." /><button className="primary">Tìm kiếm</button></form>}{classFilters}{role === "STUDENT" && <p className={invalidStudentCohort || studySuspended ? "notice error" : "student-course-scope"} role="status">{!profile?.major || !profile?.cohort
        ? "Hồ sơ thiếu ngành đào tạo hoặc khóa nhập học. Vui lòng liên hệ Admin."
        : studySuspended ? `Bạn đang bảo lưu (khóa ${profile.cohort}). Chưa được đăng ký lớp mới.`
        : invalidStudentCohort ? `Khóa ${profile.cohort} không tự quyết định năm học thực tế. Admin cần duyệt tiến độ học tập hoặc các học phần học lại.`
        : `Khóa ${profile.cohort} · ${studyStatusLabels[myStudyProgress?.status||"ON_TRACK"]} · Năm đào tạo ${effectiveStudyYear} · ${ownTermLabel ? `${ownTermLabel.name} ${ownTermLabel.academicYear}` : "Chưa có đợt đăng ký"}. Chỉ hiện lớp đúng ngành và học phần được phép đăng ký.`}</p>}<div className="results-label">Hiển thị {shownClasses.length}/{visibleClasses.length} lớp</div><div className="class-grid">{shownClasses.map(c => {
        const classWindowOpen = !!activeRegistrationTerm && `${activeRegistrationTerm.name} ${activeRegistrationTerm.academicYear}` === c.term;
        return <article className="class-card" key={c.id}><div className="card-topline"><span className="course-code">{c.courseCode}</span><span className={`status-pill ${c.status === "OPEN" && !classWindowOpen && role === "STUDENT" ? "expired" : c.status.toLowerCase()}`}>{c.status === "OPEN" && !classWindowOpen && role === "STUDENT" ? "Ngoài đợt đăng ký" : classStatusLabel(c.status)}</span></div><h3>{c.courseName}</h3><p className="class-subtitle">Lớp {c.name}</p><div className="class-meta"><span>{c.schedule || "Chưa xếp lịch"}</span><span>{c.room || "Chưa xếp phòng"}</span><span>{c.teacherName}</span><span>{c.term}</span></div><div className="capacity-block"><div className="capacity-label"><span>Sĩ số</span><b>{c.enrolled}/{c.capacity}</b></div><div className="progress-track"><div className={`progress-fill ${c.remaining === 0 ? "full-capacity" : ""}`} style={{ width: `${Math.min(100, c.capacity ? c.enrolled / c.capacity * 100 : 0)}%` }} /></div><small>{c.remaining > 0 ? `Còn ${c.remaining} chỗ trống` : "Đã đủ sĩ số · Có thể tham gia hàng chờ"}</small></div>{role === "STUDENT" && (activeRegistrations.has(c.id)
      ? <button className="secondary card-action" onClick={() => setTab("registrations")}>Đã đăng ký · Xem lớp của tôi</button>
      : activeWaitlist.has(c.id)
        ? <button className="secondary card-action" onClick={() => setTab("waitlist")}>Đang trong hàng chờ</button>
        : <button className="primary card-action" disabled={busy || c.status !== "OPEN" || !classWindowOpen} onClick={() => void registerClass(c.id)}>{c.status !== "OPEN" ? "Không nhận đăng ký" : !classWindowOpen ? "Ngoài thời gian đăng ký" : c.remaining > 0 ? "Đăng ký lớp" : "Vào danh sách chờ"}</button>)}</article>;
      })}</div>{visibleClasses.length === 0 && <div className="empty-state">Không có lớp phù hợp.</div>}{pager(classPage, visibleClasses.length, CLASS_PAGE_SIZE, setClassPage)}</div>}
    {tab === "registrations" && <><div className="page-actions"><button className="secondary" onClick={() => void refresh()}>Làm mới</button></div><div className="table-card responsive-table"><table><thead><tr><th>Lớp học</th><th>Mã học phần</th><th>Thời điểm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{registrations.map(r => <tr key={r.id}><td><b>{r.className}</b><small className="cell-sub">{r.courseName}</small></td><td>{r.courseCode}</td><td>{fmt(r.registeredAt)}</td><td><span className={`status-pill ${r.status.toLowerCase()}`}>{r.status === "ACTIVE" ? "Đã đăng ký" : "Đã hủy"}</span></td><td>{r.status === "ACTIVE" ? <button className="danger-link" disabled={studentActionBusy} onClick={() => void cancelRegistration(r.id)}>Hủy đăng ký</button> : <span className="muted">—</span>}</td></tr>)}</tbody></table>{registrations.length === 0 && <div className="empty-state">Bạn chưa có đăng ký nào.</div>}</div></>}
    {tab === "waitlist" && <><div className="page-actions"><button className="secondary" onClick={() => void refresh()}>Làm mới</button></div><div className="waitlist-grid">{waitlist.map(w => <article className="wait-card" key={w.id}><div className="wait-number">{w.status === "WAITING" ? String(w.position).padStart(2, "0") : "—"}</div><div className="wait-details"><span className="course-code">{w.courseCode}</span><h3>{w.courseName}</h3><p>Lớp {w.className}</p><div className="wait-meta">Tham gia lúc {fmt(w.joinedAt)}</div></div><div className="wait-action"><span className={`status-pill ${w.status.toLowerCase()}`}>{w.status === "WAITING" ? "Đang chờ" : w.status === "PROMOTED" ? "Đã được chuyển" : w.status === "WITHDRAWN" ? "Đã rút" : "Không đủ điều kiện"}</span>{w.status === "INELIGIBLE" && <div className="waitlist-rejoin"><p><strong>Lý do:</strong> {w.ineligibleReason || "Không đủ điều kiện tại thời điểm xét; liên hệ quản trị viên nếu cần."}</p><small>Cập nhật: {w.updatedAt ? fmt(w.updatedAt) : "Chưa rõ"}. Khắc phục điều kiện trước khi gửi yêu cầu.</small><button className="secondary" disabled={studentActionBusy} onClick={() => void rejoinWaitlist(w.id)}>Yêu cầu tham gia lại</button></div>}{w.status === "WAITING" && <button className="danger-link" disabled={studentActionBusy} onClick={() => void withdraw(w.id)}>Rút khỏi hàng chờ</button>}</div></article>)}</div>{waitlist.length === 0 && <div className="empty-state">Bạn chưa tham gia danh sách chờ nào.</div>}</>}
    {role === "ADMIN" && tab === "admin" && <section className="admin-overview" aria-label="Bảng tổng quan quản trị">
      <div className="admin-overview-toolbar">
        <p>Thông tin tổng hợp từ hệ thống đăng ký học phần</p>
        <button className="secondary" type="button" onClick={()=>void refreshAdminOverview()} disabled={overviewRefreshing}>{overviewRefreshing ? "Đang cập nhật..." : "Làm mới dữ liệu"}</button>
      </div>
      <div className="admin-overview-kpis" aria-label="Chỉ số quản lý">
        {adminOverviewKpis.map(kpi=><button key={kpi.label} type="button" className="admin-overview-kpi" onClick={()=>setTab(kpi.destination)}>
          <span>{kpi.label}</span><strong>{kpi.value === undefined ? "—" : kpi.value.toLocaleString("vi-VN")}</strong>
          <small>{kpi.helper}</small>
        </button>)}
      </div>
      <div className="admin-overview-primary-grid">
        <section className="admin-overview-panel admin-overview-registration" aria-label="Đợt đăng ký hiện tại">
          <div className="admin-overview-section-heading"><h2>Đợt đăng ký học phần</h2><span className={`admin-overview-window-status ${overviewRegistrationStatus === "open" ? "enabled" : "inactive"}`}>{overviewRegistrationTerm ? registrationWindowLabels[overviewRegistrationStatus] : adminDataStatus ? "Chưa mở đăng ký" : "Đang tải trạng thái"}</span></div>
          <div className="admin-overview-term">
            <strong>{overviewRegistrationTerm ? `${overviewRegistrationTerm.name} · ${overviewRegistrationTerm.academicYear}` : adminDataStatus ? "Chưa có học kỳ mở đăng ký" : "Đang tải thông tin học kỳ..."}</strong>
            <span>{overviewRegistrationTerm ? registrationWindowLabels[overviewRegistrationStatus] : "Mở đợt đăng ký tại mục Quản lý học kỳ"}</span>
          </div>
          <div className="admin-overview-open-classes"><span>Lớp đang nhận đăng ký trong đợt</span><strong>{adminDataStatus ? (activeRegistrationTerm ? adminDataStatus.openClassesInConfiguredPeriod : 0).toLocaleString("vi-VN") : "—"}</strong></div>
          {overviewRegistrationTerm && <p className="admin-overview-window-times">Thời gian: {overviewRegistrationTerm.registrationStartsAt?.replace("T"," ") || "Không giới hạn bắt đầu"} – {overviewRegistrationTerm.registrationEndsAt?.replace("T"," ") || "Không giới hạn kết thúc"}</p>}
          <div className="admin-overview-panel-actions"><button type="button" className="primary" onClick={()=>setTab("terms")}>Quản lý học kỳ</button><button type="button" className="secondary" onClick={()=>setTab("admin-registrations")}>Theo dõi đăng ký</button></div>
        </section>
        <section className="admin-overview-panel" aria-label="Hoạt động đăng ký">
          <div className="admin-overview-section-heading"><h2>Hoạt động đăng ký</h2></div>
          <div className="admin-overview-activity-list">
            {adminOverviewActivity.map(activity=><button key={activity.label} type="button" className="admin-overview-activity" onClick={()=>setTab(activity.destination)}>
              <span><strong>{activity.label}</strong><small>{activity.description}</small></span>
              <b>{activity.value === undefined ? "—" : activity.value.toLocaleString("vi-VN")}</b>
            </button>)}
          </div>
        </section>
      </div>
      <div className="admin-overview-secondary-grid">
        <section className="admin-overview-panel" aria-label="Truy cập nhanh">
          <div className="admin-overview-section-heading"><h2>Truy cập nhanh</h2></div>
          <div className="admin-overview-shortcuts">{adminOverviewShortcuts.map(link=><button key={link.tab} type="button" onClick={()=>setTab(link.tab)}>{link.label}</button>)}</div>
        </section>
        <section className="admin-overview-panel admin-overview-data-panel" aria-label="Tình trạng dữ liệu SQL Server">
          <div className="admin-overview-section-heading"><h2>Tình trạng dữ liệu</h2><button className="secondary" type="button" onClick={()=>void loadAdminDataStatus()}>Kiểm tra lại</button></div>
          {adminDataStatus ? <>
            <div className="admin-overview-data-grid">
              <div><span>Học kỳ trong hệ thống</span><strong>{adminDataStatus.terms.toLocaleString("vi-VN")}</strong></div>
              <div><span>Học phần chưa phân năm/học kỳ</span><strong>{adminDataStatus.coursesMissingYearOrSemester.toLocaleString("vi-VN")}</strong></div>
            </div>
            {adminDataStatus.coursesMissingYearOrSemester > 0 ? <p className="admin-overview-data-note warning">Có học phần cần bổ sung năm đào tạo hoặc học kỳ.</p> : <p className="admin-overview-data-note">Các học phần đều đã có năm đào tạo và học kỳ.</p>}
            {(adminDataStatus.courses === 0 || adminDataStatus.terms === 0 || adminDataStatus.classes === 0) && <p className="admin-overview-data-note warning">Một số danh mục đào tạo hiện chưa có dữ liệu.</p>}
          </> : <p className="muted">Chưa tải được tình trạng dữ liệu. Chọn Kiểm tra lại để thử lần nữa.</p>}
        </section>
      </div>
    </section>}
    {tab === "password" && <section className="form-card">
      
      <form onSubmit={changePassword} className="form-grid">
        <PasswordField label="Mật khẩu hiện tại" required autoComplete="current-password" value={currentPass} onChange={e => setCurrentPass(e.target.value)} />
        <PasswordField label="Mật khẩu mới" required minLength={12} maxLength={100} autoComplete="new-password" value={newPass} onChange={e => setNewPass(e.target.value)} />
        <PasswordField label="Nhập lại mật khẩu mới" required autoComplete="new-password" value={confirmPass} onChange={e => setConfirmPass(e.target.value)} />
        <div className="form-actions"><button className="primary" type="submit">Lưu mật khẩu mới</button></div>
      </form>
    </section>}
    {role === "ADMIN" && (tab === "catalog" || tab === "terms") && <section className="reference-catalog">
      <div className="page-actions page-actions-with-total">
        <div className="page-total"><span>{tab === "catalog" ? "Tổng học phần" : "Tổng học kỳ"}</span><strong>{(tab === "catalog" ? catalogCourses.length : catalogTerms.length).toLocaleString("vi-VN")}</strong>{tab === "catalog" && filteredCatalog.length !== catalogCourses.length && <small>Hiển thị {filteredCatalog.length.toLocaleString("vi-VN")}</small>}</div>
        <div className="form-actions admin-add-actions"><button className="secondary" type="button" onClick={() => void loadCatalog()}>Làm mới</button><button className="primary" type="button" onClick={tab === "catalog" ? openAddCourse : openAddTerm}>Thêm {tab === "catalog" ? "học phần" : "học kỳ"}</button></div>
      </div>
      {tab === "catalog" && <div className="admin-compact-v2061"><div className="admin-compact-filters"><h3>Tìm và lọc học phần</h3><div className="admin-compact-filter-grid course-filter-grid"><label className="admin-compact-field"><span>Từ khóa</span><input aria-label="Tìm học phần" value={catalogSearch} onChange={e => { setCatalogSearch(e.target.value); setCatalogPage(0); }} placeholder="Mã hoặc tên học phần..." /></label><label className="admin-compact-field"><span>Năm đào tạo</span><select aria-label="Lọc học phần theo năm" value={catalogYear} onChange={e => { setCatalogYear(e.target.value); setCatalogPage(0); }}><option value="all">Tất cả các năm</option>{[1,2,3,4].map(y => <option key={y} value={y}>Năm {y}</option>)}<option value="other">Chưa phân loại / danh mục cũ</option></select></label><label className="admin-compact-field"><span>Ngành học</span><select aria-label="Lọc học phần theo ngành" value={catalogMajor} onChange={e => { setCatalogMajor(e.target.value); setCatalogPage(0); }}><option value="all">Tất cả ngành</option>{programMajorChoices.map(major => <option key={major} value={major}>{major}</option>)}</select></label><button type="button" className="secondary admin-compact-clear" onClick={()=>{setCatalogSearch("");setCatalogYear("all");setCatalogMajor("all");setCatalogPage(0);}}>Xóa lọc</button></div></div><div className="table-card responsive-table admin-compact-table">
                <table><thead><tr><th>Mã học phần</th><th>Tên học phần</th><th>Tín chỉ</th><th>Năm / Học kỳ</th><th>Ngành</th><th>Thao tác</th></tr></thead><tbody>
          {shownCatalog.map(c=><tr key={c.id}><td>{c.code}</td><td>{c.name}</td><td>{c.credits}</td><td>{c.year != null && c.semester != null ? `Năm ${c.year} · HK ${c.semester}` : "Chưa phân loại"}</td><td>{courseMajorValue(c)}</td><td><button className="secondary" onClick={() => { setEditCourseId(c.id); setCourseForm({code:c.code,name:c.name,credits:String(c.credits),year:c.year ? String(c.year) : "",semester:c.semester ? String(c.semester) : "",major:editableCourseMajor(c)});setAdminEditor("course"); }}>Sửa</button> <button className="secondary admin-compact-delete" onClick={() => void deleteCatalog("courses",c.id)}>Xóa</button></td></tr>)}
        </tbody></table>{filteredCatalog.length === 0 && <div className="empty-state">Không có học phần phù hợp.</div>}
        {pager(Math.min(catalogPage, catalogPageCount - 1), filteredCatalog.length, 15, setCatalogPage)}
      </div></div>}
      {tab === "terms" && <div className="table-card responsive-table"><h3>Học kỳ</h3>
        
        <table><thead><tr><th>Học kỳ</th><th>Năm học</th><th>Thời gian học</th><th>Đợt đăng ký</th><th>Thao tác</th></tr></thead><tbody>
          {catalogTerms.map(t=><tr key={t.id}><td>{t.name}</td><td>{t.academicYear}</td><td>{t.startDate || "—"} – {t.endDate || "—"}</td><td><span className={`status-pill ${getRegistrationWindowStatus(t, registrationClock)}`}>{registrationWindowLabels[getRegistrationWindowStatus(t, registrationClock)]}</span><small className="cell-sub">{t.registrationStartsAt?.replace("T"," ") || "Không giới hạn bắt đầu"} đến {t.registrationEndsAt?.replace("T"," ") || "Không giới hạn kết thúc"}</small></td><td><button className="secondary" onClick={() => {setEditTermId(t.id);setTermForm({name:t.name,academicYear:t.academicYear,startDate:t.startDate||"",endDate:t.endDate||""});setAdminEditor("term");}}>Sửa</button> <button className="secondary" onClick={() => {setTermWindowId(t.id);setWindowDraft({startsAt:t.registrationStartsAt?.slice(0,16) || "",endsAt:t.registrationEndsAt?.slice(0,16) || ""});}}>Đợt đăng ký</button> <button className="secondary" onClick={() => void deleteCatalog("terms",t.id)}>Xóa</button></td></tr>)}
        </tbody></table>
        {termWindowId !== null && <div className="form-card"><h3>Cấu hình đợt đăng ký: {catalogTerms.find(t=>t.id===termWindowId)?.name} {catalogTerms.find(t=>t.id===termWindowId)?.academicYear}</h3>
          <div className="form-grid"><label>Mở từ (không bắt buộc)<input type="datetime-local" value={windowDraft.startsAt} onChange={e=>setWindowDraft(v=>({...v,startsAt:e.target.value}))}/></label>
          <label>Đóng lúc (không bắt buộc)<input type="datetime-local" value={windowDraft.endsAt} onChange={e=>setWindowDraft(v=>({...v,endsAt:e.target.value}))}/></label></div>
          <div className="form-actions"><button className="primary" disabled={busy} onClick={()=>void saveRegistrationWindow(termWindowId,true)}>Mở đăng ký</button><button className="secondary" disabled={busy} onClick={()=>void saveRegistrationWindow(termWindowId,false)}>Đóng đăng ký</button><button className="secondary" onClick={()=>setTermWindowId(null)}>Hủy</button></div>
        </div>}
      </div>}
    </section>}
    {tab === "create-class" && <>
      <div className="page-actions page-actions-with-total">
        <div className="page-total"><span>Tổng lớp học phần</span><strong>{classes.length.toLocaleString("vi-VN")}</strong></div>
        <div className="admin-class-header-actions"><button className="secondary" type="button" onClick={()=>void refresh()}>Làm mới</button><button className="primary" type="button" onClick={openAddClass}>Thêm lớp học phần</button></div>
      </div>
      <div className="reference-admin-classes reference-admin-classes-compact">
        {classFilters}
        <div className="admin-class-bulkbar">
          <div className="admin-class-bulkcount"><strong>Đã chọn {validSelectedClassIds.length} lớp đang mở</strong><span>Chỉ đóng nhận đăng ký mới, không xóa sinh viên đã đăng ký.</span></div>
          <div className="admin-class-bulk-actions">
            <button className="secondary" type="button" disabled={bulkClosing || validSelectedClassIds.length === 0} onClick={()=>setSelectedClassIds([])}>Bỏ chọn</button>
            <button className="primary" type="button" disabled={bulkClosing || busy || validSelectedClassIds.length === 0} onClick={()=>void closeSelectedClasses()}>{bulkClosing ? "Đang đóng lớp..." : `Đóng các lớp đã chọn (${validSelectedClassIds.length})`}</button>
          </div>
        </div>
        <div className="table-card responsive-table"><table>
          <thead><tr><th className="class-selection-column"><input type="checkbox" aria-label="Chọn tất cả lớp đang mở trong trang này" checked={allShownOpenSelected} disabled={bulkClosing || selectableShownAdminClasses.length === 0} onChange={e=>{
            const pageIds = selectableShownAdminClasses.map(c=>c.id);
            if (e.target.checked) setSelectedClassIds(old=>[...new Set([...old,...pageIds])]);
            else setSelectedClassIds(old=>old.filter(id=>!pageIds.includes(id)));
          }}/></th><th>Lớp / Học phần</th><th>Giảng viên</th><th>Sĩ số</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
          <tbody>{shownAdminClasses.map(c=><tr key={c.id} className={validSelectedClassIds.includes(c.id) ? "admin-class-row-selected" : ""}>
            <td className="class-selection-column">{c.status === "OPEN" ? <input type="checkbox" aria-label={`Chọn lớp ${c.name} để đóng`} checked={validSelectedClassIds.includes(c.id)} disabled={bulkClosing} onChange={e=>setSelectedClassIds(old=>e.target.checked?[...new Set([...old,c.id])]:old.filter(id=>id!==c.id))}/> : <span className="muted">—</span>}</td>
            <td><b>{c.name}</b><small className="cell-sub">{c.courseCode} · {c.courseName} · {curriculumLabel(c.courseCode)} · {c.term}</small></td>
            <td>{c.teacherName}</td><td>{c.enrolled}/{c.capacity}</td><td><span className={`status-pill ${c.status.toLowerCase()}`}>{classStatusLabel(c.status)}</span></td>
            <td><div className="class-row-actions"><button className="secondary" type="button" disabled={bulkClosing || busy} onClick={()=>{setClassEditing(c.id);setCapacityDraft(String(c.capacity));setStatusDraft(c.status);setClassEditDraft({name:c.name,courseId:String(catalogCourses.find(course=>course.code===c.courseCode)?.id||""),termId:String(options.terms.find(term=>term.label.trim()===c.term.trim())?.id||""),teacherId:String(c.teacherId),room:c.room||"",schedule:c.schedule||""});}}>Sửa</button><button className="secondary class-delete-button" type="button" disabled={bulkClosing || busy} title="Chỉ xóa lớp chưa phát sinh đăng ký, chờ hoặc lịch sử quyết định" onClick={()=>void deleteUnusedClass(c)}>Xóa</button></div></td>
          </tr>)}</tbody></table>
          {shownAdminClasses.length===0 && <div className="empty-state">Chưa có lớp phù hợp.</div>}
          {pager(adminPage,visibleClasses.length,ADMIN_PAGE_SIZE,setAdminPage)}
        </div>
{classEditing!==null && <AdminFormDialog title={`Sửa lớp ${classes.find(c=>c.id===classEditing)?.name || "học phần"}`} busy={busy} onClose={()=>setClassEditing(null)}>
  <form className="admin-form-body class-edit-form" onSubmit={e=>{e.preventDefault();void updateClassSettings(classEditing);}}>
    <div className="class-edit-grid">
      <label>Tên lớp<input required maxLength={100} value={classEditDraft.name} onChange={e=>setClassEditDraft(v=>({...v,name:e.target.value}))}/></label>
      <label>Học phần *<select required value={classEditDraft.courseId} onChange={e=>setClassEditDraft(v=>({...v,courseId:e.target.value}))}><option value="">Chọn học phần</option>{options.courses.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
      <label>Học kỳ của lớp *<select required value={classEditDraft.termId} onChange={e=>setClassEditDraft(v=>({...v,termId:e.target.value}))}><option value="">Chọn học kỳ</option>{options.terms.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
      <label>Giảng viên<select required value={classEditDraft.teacherId} onChange={e=>setClassEditDraft(v=>({...v,teacherId:e.target.value}))}>{options.teachers.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
      <label>Phòng học<input maxLength={100} value={classEditDraft.room} onChange={e=>setClassEditDraft(v=>({...v,room:e.target.value}))}/></label>
      <label>Lịch học<input maxLength={200} value={classEditDraft.schedule} onChange={e=>setClassEditDraft(v=>({...v,schedule:e.target.value}))}/></label>
      <label>Sức chứa<input type="number" min={Math.max(1,classes.find(c=>c.id===classEditing)?.enrolled||0)} max={1000} value={capacityDraft} onChange={e=>setCapacityDraft(e.target.value)}/></label>
      <label>Trạng thái lớp<select value={statusDraft} onChange={e=>setStatusDraft(e.target.value as "OPEN"|"CLOSED"|"CANCELLED")}><option value="OPEN">{classStatusLabel("OPEN")}</option><option value="CLOSED">{classStatusLabel("CLOSED")}</option><option value="CANCELLED">{classStatusLabel("CANCELLED")}</option></select></label>
      <div className="class-edit-help" aria-live="polite"><p>{classStatusDescription(statusDraft)}</p><p>Học kỳ thực tế của lớp quyết định việc sinh viên có nhìn thấy lớp trong đợt đăng ký hiện tại. Chỉ được đổi học phần hoặc học kỳ khi chưa có đăng ký, danh sách chờ hoặc nhật ký quyết định.</p></div>
    </div>
    <div className="form-actions"><button className="secondary" type="button" disabled={busy} onClick={()=>setClassEditing(null)}>Hủy</button><button className="primary" type="submit" disabled={busy}>{busy ? "Đang lưu..." : "Lưu thay đổi"}</button></div>
  </form>
</AdminFormDialog>}</div></>}

    {role === "ADMIN" && tab === "admin-registrations" && <section className="reference-registered registration-monitor">
      <div className="registration-monitor-summary">
        <div className="page-total"><span>Tổng số lớp</span><strong>{classes.length.toLocaleString("vi-VN")}</strong></div>
        <div className="page-total"><span>Sinh viên đã đăng ký thành công</span><strong>{totalRegisteredStudents.toLocaleString("vi-VN")}</strong></div>
      </div>
      <div className="registration-monitor-layout">
        <aside className="table-card registration-filter-sidebar" aria-label="Chọn lớp học phần">
          <h3>Chọn lớp học phần</h3>
          <div className="registration-filter-fields">
            <label>Ngành học
              <select aria-label="Lọc lớp theo ngành học" value={adminRegistrationMajor} onChange={e => {setAdminRegistrationMajor(e.target.value);setAdminRegistrationCourse("all");setAdminRegistrationSection("all");setAdminRegistrationPage(0);}}>
                <option value="all">Tất cả ngành</option>
                {registrationMajorChoices.map(major=><option key={major} value={major}>{major}</option>)}
              </select>
            </label>
            <label>Học phần
              <select aria-label="Lọc theo học phần" value={adminRegistrationCourse} onChange={e => {setAdminRegistrationCourse(e.target.value);setAdminRegistrationSection("all");setAdminRegistrationPage(0);}}>
                <option value="all">Tất cả học phần</option>
                {registrationCourseChoices.map(([code,name])=><option key={code} value={code}>{code} · {name}</option>)}
              </select>
            </label>
            <label>Lớp học phần
              <select aria-label="Lọc theo lớp học phần" value={adminRegistrationSection} onChange={e => {setAdminRegistrationSection(e.target.value);setAdminRegistrationPage(0);}}>
                <option value="all">Tất cả lớp</option>
                {registrationSectionChoices.map(c=><option key={c.id} value={String(c.id)}>{c.name} · {c.courseCode}</option>)}
              </select>
            </label>
            <label>Tìm kiếm
              <input aria-label="Tìm lớp học phần" placeholder="Mã lớp, tên môn, giảng viên..." value={adminStudentSearch} onChange={e => {setAdminStudentSearch(e.target.value);setAdminRegistrationPage(0);}} />
            </label>
          </div>
          <div className="registration-filter-bottom">
            <span>{filteredAdminRegistrationClasses.length.toLocaleString("vi-VN")} lớp phù hợp</span>
            <button type="button" className="secondary" onClick={() => {setAdminRegistrationMajor("all");setAdminRegistrationCourse("all");setAdminRegistrationSection("all");setAdminStudentSearch("");setAdminRegistrationPage(0);}}>Xóa lọc</button>
          </div>
        </aside>
        <div className="table-card registration-class-results">
          <div className="registration-results-header">
            <div><h3>Danh sách lớp</h3><p>{filteredAdminRegistrationClasses.length.toLocaleString("vi-VN")} lớp trong kết quả lọc</p></div>
            <button className="secondary" type="button" onClick={()=>void refresh()}>Làm mới</button>
          </div>
          <div className="registration-compact-list">
            {shownAdminRegistrationClasses.map(c=><article key={c.id} className={`registration-compact-row ${adminRegistrationClass===c.id?"selected":""}`}>
              <div className="registration-compact-main"><strong>{c.name}</strong><span>{c.courseCode} · {c.courseName}</span><small>{c.term} · {c.teacherName}</small></div>
              <div className="registration-compact-info"><span>{registrationClassMajor(c.courseCode)}</span><small>{c.enrolled}/{c.capacity} sinh viên</small></div>
              <button type="button" className="secondary" onClick={()=>void loadAdminRegisteredStudents(c.id)}>Xem danh sách sinh viên</button>
            </article>)}
          </div>
          {filteredAdminRegistrationClasses.length === 0 && <div className="empty-state">Không tìm thấy lớp học phần phù hợp với bộ lọc.</div>}
          {filteredAdminRegistrationClasses.length > REGISTRATION_PAGE_SIZE && <div className="registration-results-pager"><span>Trang {currentRegistrationPage+1}/{registrationPageCount}</span><div><button className="secondary" type="button" disabled={currentRegistrationPage===0} onClick={()=>setAdminRegistrationPage(p=>Math.max(0,p-1))}>Trước</button><button className="secondary" type="button" disabled={currentRegistrationPage>=registrationPageCount-1} onClick={()=>setAdminRegistrationPage(p=>Math.min(registrationPageCount-1,p+1))}>Tiếp</button></div></div>}
        </div>
      </div>
      {selectedAdminRegistrationClass && <AdminFormDialog title={`Danh sách sinh viên – ${selectedAdminRegistrationClass.name}`} onClose={closeAdminRegisteredStudents}>
        <div className="admin-form-body registration-student-dialog">
          <div className="registration-student-context"><div><strong>{selectedAdminRegistrationClass.courseName}</strong><small>{selectedAdminRegistrationClass.courseCode} · {selectedAdminRegistrationClass.term}</small></div><span>{selectedAdminRegistrationClass.enrolled}/{selectedAdminRegistrationClass.capacity} sinh viên</span></div>
          {adminStudentsLoading ? <div className="empty-state">Đang tải danh sách sinh viên...</div>
           : adminStudentsError ? <div role="alert" className="notice error">{adminStudentsError}<button type="button" className="secondary" onClick={()=>void loadAdminRegisteredStudents(selectedAdminRegistrationClass.id)}>Thử lại</button></div>
           : <div className="responsive-table registration-student-table"><table><thead><tr><th>Mã sinh viên</th><th>Họ tên</th><th>Email</th><th>Khoa</th><th>Ngày đăng ký</th></tr></thead><tbody>{adminRegisteredStudents.map(x=><tr key={x.studentId}><td>{studentCode(x.email,x.studentId)}</td><td>{x.fullName}</td><td>{x.email}</td><td>{x.faculty||"—"}</td><td>{fmt(x.registeredAt)}</td></tr>)}</tbody></table>{adminRegisteredStudents.length===0 && <div className="empty-state">Lớp chưa có sinh viên đăng ký thành công.</div>}</div>}
          <div className="form-actions"><button type="button" className="secondary" onClick={()=>void exportAdminClassStudents(selectedAdminRegistrationClass.id, selectedAdminRegistrationClass.name)} disabled={adminClassExportBusy || adminStudentsLoading || !!adminStudentsError}>{adminClassExportBusy ? "Đang xuất..." : "Xuất danh sách sinh viên"}</button><button type="button" className="secondary" onClick={()=>void loadAdminRegisteredStudents(selectedAdminRegistrationClass.id)} disabled={adminStudentsLoading}>Tải lại danh sách</button><button type="button" className="secondary" onClick={closeAdminRegisteredStudents}>Đóng</button></div>
        </div>
      </AdminFormDialog>}
    </section>}

    {role === "ADMIN" && (tab === "students" || tab === "teachers") && <>
      <div className="page-actions page-actions-with-total"><div className="page-total"><span>{tab === "students" ? "Tổng sinh viên" : "Tổng giảng viên"}</span><strong>{peopleTotal.toLocaleString("vi-VN")}</strong></div><div className="form-actions people-action-buttons"><button className="secondary" type="button" disabled={peopleExportBusy !== null || peopleLoading || appliedPeopleFilters?.kind !== tab} onClick={() => void downloadPeople(tab === "students" ? "students" : "teachers", "xlsx")}>{peopleExportBusy === "xlsx" ? "Đang xuất..." : "Xuất Excel"}</button><button className="secondary" type="button" disabled={peopleExportBusy !== null || peopleLoading || appliedPeopleFilters?.kind !== tab} onClick={() => void downloadPeople(tab === "students" ? "students" : "teachers", "csv")}>{peopleExportBusy === "csv" ? "Đang xuất..." : "Xuất CSV"}</button><button className="secondary" type="button" onClick={() => openPeopleImport(tab === "students" ? "students" : "teachers")}>Nhập Excel / CSV</button><button className="primary" type="button" onClick={openAddPerson}>Thêm {tab === "students" ? "sinh viên" : "giảng viên"}</button></div></div>
      <form className="people-filter-panel" onSubmit={e => { e.preventDefault(); void loadPeople(tab, 0, peopleSearch); }}>
        <div className="filter-heading"><strong>Tìm và lọc hồ sơ</strong></div>
        <div className="people-controls">
          <label className="grow-field">Từ khóa<input value={peopleSearch} onChange={e => setPeopleSearch(e.target.value)} placeholder={tab === "students" ? "MSSV, họ tên hoặc email" : "Mã giảng viên, họ tên hoặc email"} /></label>
          {tab === "students" && <><label>Khóa<select value={peopleCohort} onChange={e => setPeopleCohort(e.target.value)}><option value="">Tất cả khóa</option>{personFilters.studentCohorts.map(c=><option value={c} key={c}>{c}</option>)}</select></label><label>Ngành học<select value={peopleMajor} onChange={e => setPeopleMajor(e.target.value)}><option value="">Tất cả ngành</option>{personFilters.studentMajors.map(c=><option value={c} key={c}>{c}</option>)}</select></label><label>Trạng thái<select value={peopleStatus} onChange={e => setPeopleStatus(e.target.value)}><option value="">Tất cả trạng thái</option><option value="ACTIVE">Hoạt động</option><option value="PENDING">Chưa kích hoạt</option><option value="LOCKED">Bị khóa</option></select></label></>}
          {tab === "teachers" && <label>Chuyên môn<select aria-label="Lọc giảng viên theo chuyên môn" value={teacherSpecialty} onChange={e => { const specialty = e.target.value; setTeacherSpecialty(specialty); void loadPeople("teachers", 0, peopleSearch, false, specialty); }}><option value="">Tất cả chuyên môn</option>{personFilters.teacherSpecialties.map(specialty=><option value={specialty} key={specialty}>{specialty}</option>)}</select></label>}
          <button className="primary" type="submit">Áp dụng</button>
          <button className="secondary" type="button" onClick={() => { setPeopleSearch(""); setPeopleCohort(""); setPeopleMajor(""); setTeacherSpecialty(""); setPeopleStatus(""); void loadPeople(tab, 0, "", true); }}>Xóa lọc</button>
        </div>
      </form>
      <div className="table-card responsive-table"><table><thead><tr><th>{tab === "students" ? "MSSV" : "Mã GV"}</th><th>Họ tên</th><th>Email</th><th>Khoa</th><th>{tab === "students" ? "Ngành học" : "Chuyên môn"}</th>{tab === "students" && <th>Khóa</th>}<th>Trạng thái</th><th></th></tr></thead><tbody>
        {people.map(p => <tr key={p.id}><td><b>{p.username || "—"}</b></td><td>{p.fullName}</td><td>{p.email}</td><td>{p.faculty || "—"}</td><td>{p.major || "—"}</td>{tab === "students" && <td>{p.cohort || "—"}</td>}<td>{p.status === "ACTIVE" ? "Hoạt động" : p.status}</td><td><button className="text-link-button" onClick={() => openPersonDetail(p)}>Chi tiết</button></td></tr>)}
      </tbody></table>{!peopleLoading && people.length === 0 && <div className="empty-state">Không có hồ sơ phù hợp.</div>}{peopleLoading && <div className="empty-state">Đang tải dữ liệu...</div>}</div>
      <div className="form-actions"><button className="secondary" disabled={peoplePage === 0 || peopleLoading} onClick={() => void loadPeople(tab, peoplePage - 1)}>Trang trước</button><span>Trang {peoplePage + 1} / {Math.max(1, Math.ceil(peopleTotal / 20))}</span><button className="secondary" disabled={(peoplePage + 1) * 20 >= peopleTotal || peopleLoading} onClick={() => void loadPeople(tab, peoplePage + 1)}>Trang sau</button></div>
      {personDetail && <AdminFormDialog title={`Chi tiết ${tab === "students" ? "sinh viên" : "giảng viên"}`} busy={busy} onClose={() => setPersonDetail(null)}><div className="admin-form-body person-detail-body">
        <div className="form-grid"><p><b>Mã:</b> {personDetail.username}</p><p><b>Họ tên:</b> {personDetail.fullName}</p><p><b>Email:</b> {personDetail.email}</p><p><b>Ngày sinh:</b> {personDetail.dateOfBirth || "Chưa có"}</p><p><b>Khoa:</b> {personDetail.faculty || "—"}</p><p><b>{tab === "students" ? "Ngành học" : "Chuyên môn"}:</b> {personDetail.major || "—"}</p>{tab === "students" && <p><b>Khóa:</b> {personDetail.cohort || "—"}</p>}</div>
        <form className="profile-edit-panel" onSubmit={updatePerson}>
          <h4>Cập nhật hồ sơ người dùng</h4><div className="form-grid">
          <label>Họ tên<input required maxLength={150} value={personEditDraft.fullName} onChange={e=>setPersonEditDraft(v=>({...v,fullName:e.target.value}))}/></label>
          <label>Khoa<DatabaseChoice key={`detail-faculty-${personDetail.id}`} label="Khoa" value={personEditDraft.faculty} choices={tab === "students" ? personFilters.studentFaculties : personFilters.teacherFaculties} onChange={value=>setPersonEditDraft(v=>({...v,faculty:value}))}/></label>
          <label>{tab === "students" ? "Ngành học" : "Chuyên môn"}<DatabaseChoice key={`detail-major-${personDetail.id}`} label={tab === "students" ? "Ngành học" : "Chuyên môn"} value={personEditDraft.major} choices={tab === "students" ? personFilters.studentMajors : personFilters.teacherSpecialties} onChange={value=>setPersonEditDraft(v=>({...v,major:value}))}/></label>
          {tab === "students" && <label>Khóa học<DatabaseChoice key={`detail-cohort-${personDetail.id}`} label="Khóa học" value={personEditDraft.cohort} choices={personFilters.studentCohorts} onChange={value=>setPersonEditDraft(v=>({...v,cohort:value}))}/></label>}
          <label>Ngày sinh<input type="date" value={personEditDraft.dateOfBirth} onChange={e=>setPersonEditDraft(v=>({...v,dateOfBirth:e.target.value}))}/></label></div>
          <div className="form-actions"><button className="primary" disabled={busy} type="submit">Lưu hồ sơ</button></div>
        </form>
        {tab === "students" && <form className="profile-edit-panel" onSubmit={saveStudyProgress}>
          <h4>Tiến độ học tập và học lại</h4>
          <p className="muted">Khóa {personDetail.cohort||"—"} là năm nhập học, không thay đổi khi học chậm hoặc bảo lưu. Năm đào tạo thực tế do Admin phê duyệt, không tự tính theo tuổi.</p>
          <div className="form-grid">
            <label>Trạng thái học tập<select value={studyDraft.status} onChange={e=>setStudyDraft(v=>({...v,status:e.target.value as StudyProgressView["status"]}))}>{Object.entries(studyStatusLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
            <label>Năm đào tạo thực tế<select value={studyDraft.effectiveYear} onChange={e=>setStudyDraft(v=>({...v,effectiveYear:e.target.value}))}><option value="">Theo khóa nhập học</option>{[1,2,3,4].map(y=><option key={y} value={y}>Năm {y}</option>)}</select></label>
            <label>Mã học phần được duyệt học lại<input placeholder="VD: CO2017, WEB201" value={studyDraft.retakeCourseCodes} onChange={e=>setStudyDraft(v=>({...v,retakeCourseCodes:e.target.value}))}/></label>
            <label>Ghi chú / căn cứ phê duyệt<input maxLength={500} placeholder="VD: Học chậm do bảo lưu 2 năm" value={studyDraft.note} onChange={e=>setStudyDraft(v=>({...v,note:e.target.value}))}/></label>
          </div>
          {studyProgressError && <div role="alert" className="form-error"><p>{studyProgressError}</p><button className="secondary" type="button" onClick={() => void loadPersonStudyProgress(personDetail.id)}>Thử tải lại tiến độ</button></div>}
          <div className="form-actions"><span className="muted">{studyProgressLoading ? "Đang tải tiến độ..." : studyProgress ? `Đã lưu: ${studyStatusLabels[studyProgress.status]}` : "Chưa tải được tiến độ"}</span><button className="primary" disabled={busy||studyProgressLoading||!studyProgress} type="submit">{busy ? "Đang lưu..." : "Lưu tiến độ học tập"}</button></div>
        </form>}
        <div className="form-actions"><span className="muted">Trạng thái hiện tại: {personDetail.status}</span>{personDetail.status === "ACTIVE" && <button className="secondary" onClick={()=>void changePersonStatus(tab,personDetail.id,"LOCKED")}>Khóa tài khoản</button>}{personDetail.status === "LOCKED" && <button className="primary" onClick={()=>void changePersonStatus(tab,personDetail.id,"ACTIVE")}>Mở khóa tài khoản</button>}{personDetail.status === "PENDING" && <span className="status-pill">Chưa kích hoạt</span>}</div>
      </div></AdminFormDialog>}
    </>}

    {tab === "teacher" && <><section className="table-wrap"><table className="data-table"><thead><tr><th>Học phần</th><th>Mã lớp</th><th>Lịch / thời gian</th><th>Phòng</th><th>Sĩ số</th><th>Trạng thái</th></tr></thead><tbody>{filteredTeacherClasses.length ? filteredTeacherClasses.map(c => <tr key={c.id}><td><b>{c.courseCode}</b><div>{c.courseName}</div></td><td>{c.name}</td><td>{c.schedule || "Chưa cập nhật"}</td><td>{c.room || "-"}</td><td>{c.enrolled}/{c.capacity}</td><td><span className={`status-pill ${c.status.toLowerCase()}`}>{classStatusLabel(c.status)}</span></td></tr>) : <tr><td colSpan={6}>Chưa có lớp học phần được phân công.</td></tr>}</tbody></table></section></>}
    {tab === "admin-waitlist" && <section className="admin-compact-v2061" aria-label="Quản lý danh sách chờ"><div className="page-actions page-actions-with-total"><div className="page-total"><span>Tổng lượt chờ</span><strong>{adminWaitlist.length.toLocaleString("vi-VN")}</strong>{filteredAdminWait.length !== adminWaitlist.length && <small>Hiển thị {filteredAdminWait.length.toLocaleString("vi-VN")}</small>}</div><button className="secondary" type="button" onClick={() => void loadAdminWaitlist()}>Làm mới</button></div><div className="admin-compact-filters"><h3>Tìm và lọc danh sách chờ</h3><label className="admin-compact-field"><span>Từ khóa</span><input aria-label="Tìm hàng chờ" value={adminWaitQuery} onChange={e => setAdminWaitQuery(e.target.value)} placeholder="Tên sinh viên, email hoặc tên lớp..." /></label></div><div className="table-card responsive-table"><table><thead><tr><th>Vị trí</th><th>Sinh viên</th><th>Lớp</th><th>Thời điểm tham gia</th><th>Trạng thái</th></tr></thead><tbody>{filteredAdminWait.map(w => <tr key={w.id}><td><b>{w.status === "WAITING" ? `#${w.position}` : "—"}</b></td><td><b>{w.studentName}</b><small className="cell-sub">{w.studentEmail}</small></td><td><b>{w.className}</b><small className="cell-sub">Mã lớp #{w.classId}</small></td><td>{fmt(w.joinedAt)}</td><td><span className={`status-pill ${w.status.toLowerCase()}`}>{w.status}</span></td></tr>)}</tbody></table>{filteredAdminWait.length === 0 && <div className="empty-state">{adminWaitlist.length === 0 ? "Hiện không có lượt chờ." : "Không tìm thấy lượt chờ phù hợp."}</div>}</div></section>}
    {tab === "decisions" && <section className="admin-compact-v2061" aria-label="Lịch sử xét đăng ký"><div className="page-actions page-actions-with-total"><div className="page-total"><span>Tổng bản ghi</span><strong>{decisions.length.toLocaleString("vi-VN")}</strong>{filteredDecisions.length !== decisions.length && <small>Hiển thị {filteredDecisions.length.toLocaleString("vi-VN")}</small>}</div><button className="secondary" onClick={() => void loadDecisions()}>Làm mới</button></div><div className="admin-compact-filters"><h3>Tìm và lọc lịch sử xét đăng ký</h3><div className="admin-decision-filter-grid">
      <label className="admin-compact-field"><span>Từ khóa</span><input aria-label="Tìm lịch sử xét đăng ký" value={decisionQuery} onChange={e => setDecisionQuery(e.target.value)} placeholder="Tên/MSSV, lớp, tên/mã học phần..." /></label>
      <label className="admin-compact-field"><span>Kết quả</span><select aria-label="Lọc theo kết quả xét đăng ký" value={decisionResult} onChange={e => setDecisionResult(e.target.value)}><option value="all">Tất cả kết quả</option>{Array.from(new Set([...Object.keys(decisionResultLabels), ...decisions.map(d=>d.result)])).map(result=><option key={result} value={result}>{decisionResultLabel(result)}</option>)}</select></label>
      <label className="admin-compact-field"><span>Từ ngày</span><input aria-label="Lọc từ ngày" type="date" value={decisionFromDate} max={decisionToDate || undefined} onChange={e=>setDecisionFromDate(e.target.value)} /></label>
      <label className="admin-compact-field"><span>Đến ngày</span><input aria-label="Lọc đến ngày" type="date" value={decisionToDate} min={decisionFromDate || undefined} onChange={e=>setDecisionToDate(e.target.value)} /></label>
      <button type="button" className="secondary admin-compact-clear" onClick={()=>{setDecisionQuery("");setDecisionResult("all");setDecisionFromDate("");setDecisionToDate("");}}>Xóa lọc</button>
    </div></div><div className="table-card responsive-table"><table><thead><tr><th>Thời điểm</th><th>Lớp học phần</th><th>Học phần</th><th>Sinh viên</th><th>Kết quả</th><th>Lý do</th></tr></thead><tbody>{filteredDecisions.map(d => <tr key={d.id}><td>{fmt(d.occurredAt)}</td><td><b>{d.className || `Lớp #${d.classId}`}</b></td><td><b>{d.courseName || "Chưa có thông tin"}</b>{d.courseCode && <small className="cell-sub">{d.courseCode}</small>}</td><td><b>{d.studentName || (d.studentId ? `Sinh viên #${d.studentId}` : "—")}</b>{d.studentEmail && <small className="cell-sub">{studentCode(d.studentEmail,d.studentId || "")} · {d.studentEmail}</small>}</td><td><span className="status-pill">{decisionResultLabel(d.result)}</span></td><td>{d.reason || "—"}</td></tr>)}</tbody></table>{filteredDecisions.length === 0 && <div className="empty-state">{decisions.length === 0 ? "Chưa có quyết định nào." : "Không tìm thấy bản ghi phù hợp."}</div>}</div>
    <section className="table-card rr-audit-panel"><div className="rr-audit-heading"><h3>Chi tiết vòng xét Round Robin</h3><button className="secondary" type="button" onClick={()=>void loadRoundRobinRuns()}>Làm mới vòng xét</button></div>
      {rrError && <p className="form-error">{rrError}</p>}
      <div className="responsive-table"><table><thead><tr><th>Thời điểm</th><th>Lớp</th><th>Nguyên nhân</th><th>Sĩ số trước → sau</th><th>Con trỏ trước → sau</th><th>Được cấp</th><th>Chi tiết</th></tr></thead><tbody>{rrRuns.map(run=><tr key={run.id}><td>{fmt(run.occurredAt)}</td><td>{run.className}</td><td>{run.trigger}</td><td>{run.enrolledBefore} → {run.enrolledAfter} / {run.capacity}</td><td>{run.cursorBefore ?? "—"} → {run.cursorAfter ?? "—"}</td><td>{run.promotedCount}</td><td><button className="secondary" type="button" onClick={()=>void showRoundRobinRun(run.id)}>Xem lượt #{run.id}</button></td></tr>)}</tbody></table>{!rrRuns.length && <div className="empty-state">Chưa có lượt xét Round Robin phát sinh kể từ khi bật nhật ký V20.68.</div>}</div>
      {rrSelectedRun!==null && <div className="rr-candidates"><h4>Ứng viên lượt xét #{rrSelectedRun}</h4><div className="responsive-table"><table><thead><tr><th>Thứ tự</th><th>Sinh viên</th><th>Kết quả</th><th>Lý do</th></tr></thead><tbody>{rrCandidates.map(c=><tr key={`${c.sequence}-${c.studentId}`}><td>{c.sequence}</td><td>{c.studentName}</td><td>{decisionResultLabel(c.result)}</td><td>{c.reason||"—"}</td></tr>)}</tbody></table>{rrCandidates.length===0 && <p className="empty-state">Không có ứng viên hoặc chưa tải được chi tiết.</p>}</div></div>}
    </section>
    </section>}
    {tab === "chat" && (role === "ADMIN" ? <div className="chat-layout"><section className="chat-list"><div className="chat-list-head"><b>Hộp thư hỗ trợ</b><button className="chat-refresh-button" type="button" onClick={() => void initChat()}>Làm mới</button></div>{conversations.map(c => <button key={c.id} className={`conversation-row ${conversationId === c.id ? "selected" : ""}`} onClick={() => void loadMessages(c.id)}><b>{c.userName}</b><small>{c.userEmail}</small><span className="status-pill">{c.status}</span></button>)}</section><section className="chat-window"><div className="chat-window-head"><div><b>{conversations.find(c => c.id === conversationId)?.userEmail || "Chưa chọn cuộc trò chuyện"}</b></div>{conversationId !== null && <label className="chat-status-control">Trạng thái<select aria-label="Trạng thái cuộc trò chuyện" value={conversations.find(c => c.id === conversationId)?.status || "OPEN"} disabled={chatStatusBusy} onChange={e => void updateChatStatus(conversationId, e.target.value as "OPEN" | "IN_PROGRESS" | "CLOSED")}><option value="OPEN">Mới</option><option value="IN_PROGRESS">Đang xử lý</option><option value="CLOSED">Đã đóng</option></select></label>}</div><div className="message-list" ref={chatListRef}>{messages.map(m => <div key={m.id} className={`message ${m.senderEmail === email ? "own" : ""}`}><div className="message-bubble"><p>{m.content}</p><small>{m.senderRole === "ADMIN" ? "Quản trị viên" : m.senderEmail} · {fmt(m.sentAt)}</small></div></div>)}{messages.length === 0 && <div className="empty-state">Chưa có tin nhắn.</div>}{peerTyping && <div className="chat-typing" role="status" aria-label="Người kia đang soạn tin nhắn"><span className="typing-dots"><i/><i/><i/></span><small>Đang soạn tin nhắn...</small></div>}</div><form className="chat-composer" onSubmit={sendMessage}><input value={chatText} onChange={e => updateChatText(e.target.value)} placeholder="Nhập tin nhắn hỗ trợ..." required /><button className="primary" disabled={!conversationId || chatSending}>{chatSending ? "Đang gửi..." : "Gửi"}</button></form></section></div> : <><section className="info-card"><div className="section-heading-inline"><div><h3>Chủ đề cần hỗ trợ</h3></div></div><div className="support-create"><input value={chatSubject} onChange={e => setChatSubject(e.target.value)} placeholder="Nhập chủ đề hỗ trợ" /><button type="button" className="primary" onClick={() => void startChat()}>Mở / tiếp tục trò chuyện</button></div></section><div className="chat-layout portal-chat"><section className="chat-list"><div className="chat-list-head"><b>Cuộc trò chuyện</b><button className="chat-refresh-button" type="button" onClick={() => void initChat()}>Làm mới</button></div>{conversations.map(c => <button key={c.id} className={`conversation-row ${conversationId === c.id ? "selected" : ""}`} onClick={() => void loadMessages(c.id)}><b>{`Hỗ trợ: ${c.userName || c.userEmail}`}</b><small>{c.status} · {fmt(c.updatedAt)}</small></button>)}{conversations.length === 0 && <div className="empty-state">Chưa có cuộc trò chuyện nào.</div>}</section><section className="chat-window"><div className="chat-window-head"><div><b>{conversationId ? "Nội dung trao đổi" : "Chưa chọn cuộc trò chuyện"}</b></div></div><div className="message-list" ref={chatListRef}>{messages.map(m => <div key={m.id} className={`message ${m.senderEmail === email ? "own" : ""}`}><div className="message-bubble"><p>{m.content}</p><small>{m.senderRole === "ADMIN" ? "Quản trị viên" : "Sinh viên"} · {fmt(m.sentAt)}</small></div></div>)}{messages.length === 0 && <div className="empty-state">{conversationId ? "Chưa có tin nhắn." : "Chưa chọn cuộc trò chuyện"}</div>}{peerTyping && <div className="chat-typing" role="status" aria-label="Người kia đang soạn tin nhắn"><span className="typing-dots"><i/><i/><i/></span><small>Đang soạn tin nhắn...</small></div>}</div><form className="chat-composer" onSubmit={sendMessage}><input value={chatText} onChange={e => updateChatText(e.target.value)} placeholder="Nhập tin nhắn hỗ trợ..." required /><button className="primary" disabled={!conversationId || chatSending}>{chatSending ? "Đang gửi..." : "Gửi"}</button></form></section></div></>)}

    {role === "ADMIN" && peopleImportKind &&
      <AdminFormDialog title={`Nhập ${peopleImportKind === "students" ? "sinh viên" : "giảng viên"} từ Excel / CSV`} busy={peopleImportBusy} onClose={closePeopleImport}>
        <form className="admin-form-body people-import-form" onSubmit={submitPeopleImport}>
          <p className="muted">Hỗ trợ tệp .xlsx, .xls, .csv (tối đa 5 MB, 5.000 dòng). Dòng mới được tạo tài khoản chờ kích hoạt. Dữ liệu đã tồn tại được giữ nguyên.</p>
          <p className="muted">Cột bắt buộc: <b>Email, Họ và tên</b>. Các cột khác: Mã {peopleImportKind === "students" ? "sinh viên, Ngày sinh, Khoa, Ngành học, Khóa" : "giảng viên, Ngày sinh, Khoa, Chuyên môn"}. Ngày sinh dùng YYYY-MM-DD. Tệp có tiêu đề tiếng Anh cũ vẫn nhập được.</p>
          <div className="people-import-file-row">
            <label htmlFor="admin-people-import-file">Chọn tệp dữ liệu</label>
            <input id="admin-people-import-file" type="file" accept=".csv,.xlsx,.xls" required disabled={peopleImportBusy}
              onChange={e => { setPeopleImportFile(e.target.files?.[0] ?? null); setPeopleImportResult(null); }}/>
          </div>
          <div className="people-import-template">
            <div className="people-import-template-links">
              <a href={peopleImportKind === "students" ? "/templates/sinh_vien_mau.csv" : "/templates/giang_vien_mau.csv"} download>Tải mẫu CSV tiếng Việt</a>
            </div>
            <small>Tải mẫu CSV tiếng Việt để điền dữ liệu và nhập lại vào hệ thống. Vẫn hỗ trợ tải lên tệp Excel (.xlsx, .xls) và file users.csv cũ; mật khẩu trong tệp bị bỏ qua.</small>
          </div>
          {peopleImportResult && <div className="people-import-report" role="status">
            <h3>Kết quả nhập dữ liệu</h3>
            <div className="people-import-numbers"><span>Tổng dòng <b>{peopleImportResult.total}</b></span><span>Đã tạo <b>{peopleImportResult.created}</b></span><span>Đã tồn tại <b>{peopleImportResult.skipped}</b></span><span>Khác vai trò <b>{peopleImportResult.ignoredRole}</b></span><span>Lỗi <b>{peopleImportResult.failed}</b></span></div>
            <p>{peopleImportResult.message}</p>
            {peopleImportResult.errors.length > 0 && <div className="people-import-errors"><b>Chi tiết lỗi (tối đa 50 dòng)</b><div className="people-import-error-list">{peopleImportResult.errors.map((item,index)=><p key={`${item.row}-${index}`}>Dòng {item.row}: {item.reason}</p>)}</div></div>}
          </div>}
          <div className="form-actions"><button type="button" className="secondary" disabled={peopleImportBusy} onClick={closePeopleImport}>Đóng</button><button type="submit" className="primary" disabled={peopleImportBusy || !peopleImportFile}>{peopleImportBusy ? "Đang nhập dữ liệu..." : "Nhập dữ liệu"}</button></div>
        </form>
      </AdminFormDialog>}

    {role === "ADMIN" && adminEditor === "person" && (tab === "students" || tab === "teachers") &&
      <AdminFormDialog title={`Thêm ${tab === "students" ? "sinh viên" : "giảng viên"}`} busy={busy} onClose={closeAdminEditor}>
        <form className="admin-form-body" onSubmit={createPerson}>
          
          <div className="form-grid">
            <label>Email trường *<input type="email" required autoComplete="off" maxLength={255} value={newPerson.email} onChange={e => setNewPerson(v => ({...v,email:e.target.value}))} placeholder={tab === "students" ? "sinhvien@truong.edu.vn" : "giangvien@truong.edu.vn"} /></label>
            <label>Họ và tên *<input required maxLength={150} value={newPerson.fullName} onChange={e => setNewPerson(v => ({...v,fullName:e.target.value}))} placeholder="Nhập họ và tên" /></label>
            <label>Khoa<DatabaseChoice key={`faculty-${tab}`} label="Khoa" value={newPerson.faculty} choices={tab === "students" ? personFilters.studentFaculties : personFilters.teacherFaculties} onChange={v => setNewPerson(current=>({...current,faculty:v}))}/></label>
            <label>{tab === "students" ? "Ngành học" : "Chuyên môn"}<DatabaseChoice key={`major-${tab}`} label={tab === "students" ? "Ngành học" : "Chuyên môn"} value={newPerson.major} choices={tab === "students" ? personFilters.studentMajors : personFilters.teacherSpecialties} onChange={v => setNewPerson(current=>({...current,major:v}))}/></label>
          </div>
          {error && <p className="admin-form-error" role="alert">{error}</p>}
          <div className="form-actions"><button type="button" className="secondary" onClick={closeAdminEditor} disabled={busy}>Hủy</button><button className="primary" type="submit" disabled={busy}>{busy ? "Đang lưu..." : "Tạo tài khoản"}</button></div>
        </form>
      </AdminFormDialog>}
    {role === "ADMIN" && adminEditor === "course" && tab === "catalog" &&
      <AdminFormDialog title={editCourseId ? "Sửa học phần" : "Thêm học phần"} busy={busy} onClose={closeAdminEditor}>
        <form className="admin-form-body" onSubmit={saveCourse}>
          <div className="form-grid">
            <label>Mã học phần *<input required maxLength={30} value={courseForm.code} onChange={e=>setCourseForm(v=>({...v,code:e.target.value}))} placeholder="VD: JAVA101" /></label>
            <label>Tên học phần *<input required maxLength={200} value={courseForm.name} onChange={e=>setCourseForm(v=>({...v,name:e.target.value}))} placeholder="Lập trình Java" /></label>
            <label>Số tín chỉ *<input type="number" required min={1} max={10} value={courseForm.credits} onChange={e=>setCourseForm(v=>({...v,credits:e.target.value}))} /></label>
            <label>Năm đào tạo<select value={courseForm.year} onChange={e=>setCourseForm(v=>({...v,year:e.target.value}))}><option value="">Chưa phân năm</option>{[1,2,3,4].map(y=><option value={y} key={y}>Năm {y}</option>)}</select></label>
            <label>Học kỳ dự kiến<select value={courseForm.semester} disabled={!courseForm.year} onChange={e=>setCourseForm(v=>({...v,semester:e.target.value}))}><option value="">Chưa phân học kỳ</option><option value="1">Học kỳ 1</option><option value="2">Học kỳ 2</option></select></label>
            <label>Ngành đào tạo<input maxLength={150} value={courseForm.major} onChange={e=>setCourseForm(v=>({...v,major:e.target.value}))} placeholder="Để trống nếu dùng chung" /></label>
          </div>
          {error && <p className="admin-form-error" role="alert">{error}</p>}
          <div className="form-actions"><button type="button" className="secondary" onClick={closeAdminEditor} disabled={busy}>Hủy</button><button className="primary" disabled={busy} type="submit">{busy ? "Đang lưu..." : editCourseId ? "Lưu học phần" : "Thêm học phần"}</button></div>
        </form>
      </AdminFormDialog>}
    {role === "ADMIN" && adminEditor === "term" && tab === "terms" &&
      <AdminFormDialog title={editTermId ? "Sửa học kỳ" : "Thêm học kỳ"} busy={busy} onClose={closeAdminEditor}>
        <form className="admin-form-body" onSubmit={saveTerm}>
          <div className="form-grid">
            <label>Tên học kỳ *<input required maxLength={30} value={termForm.name} onChange={e=>setTermForm(v=>({...v,name:e.target.value}))} placeholder="Học kỳ 1" /></label>
            <label>Năm học *<input required maxLength={20} value={termForm.academicYear} onChange={e=>setTermForm(v=>({...v,academicYear:e.target.value}))} placeholder="2026-2027" /></label>
            <label>Ngày bắt đầu<input type="date" value={termForm.startDate} onChange={e=>setTermForm(v=>({...v,startDate:e.target.value}))} /></label>
            <label>Ngày kết thúc<input type="date" min={termForm.startDate || undefined} value={termForm.endDate} onChange={e=>setTermForm(v=>({...v,endDate:e.target.value}))} /></label>
          </div>
          {error && <p className="admin-form-error" role="alert">{error}</p>}
          <div className="form-actions"><button type="button" className="secondary" onClick={closeAdminEditor} disabled={busy}>Hủy</button><button className="primary" type="submit" disabled={busy}>{busy ? "Đang lưu..." : editTermId ? "Lưu học kỳ" : "Thêm học kỳ"}</button></div>
        </form>
      </AdminFormDialog>}
    {role === "ADMIN" && adminEditor === "class" && tab === "create-class" &&
      <AdminFormDialog title="Thêm lớp học phần" busy={busy} onClose={closeAdminEditor}>
        <form className="admin-form-body" onSubmit={createClass}>
          <div className="form-grid">
            <label>Tên lớp *<input required maxLength={100} value={newClass.name} onChange={e=>setNewClass(v=>({...v,name:e.target.value}))} placeholder="VD: JAVA101-02" /></label>
            <label>Học phần *<select required value={newClass.courseId} onChange={e=>setNewClass(v=>({...v,courseId:e.target.value}))}><option value="">Chọn học phần</option>{options.courses.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
            <label>Học kỳ *<select required value={newClass.termId} onChange={e=>setNewClass(v=>({...v,termId:e.target.value}))}><option value="">Chọn học kỳ</option>{options.terms.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
            <label>Giảng viên *<select required value={newClass.teacherId} onChange={e=>setNewClass(v=>({...v,teacherId:e.target.value}))}><option value="">Chọn giảng viên</option>{options.teachers.map(o=><option key={o.id} value={o.id}>{o.label}</option>)}</select></label>
            <label>Phòng học<input maxLength={100} value={newClass.room} onChange={e=>setNewClass(v=>({...v,room:e.target.value}))} placeholder="VD: A.203" /></label>
            <label>Lịch học<input maxLength={200} value={newClass.schedule} onChange={e=>setNewClass(v=>({...v,schedule:e.target.value}))} placeholder="VD: Thứ 3 13:30-15:30" /></label>
            <label>Sức chứa *<input type="number" required min={1} max={1000} value={newClass.capacity} onChange={e=>setNewClass(v=>({...v,capacity:e.target.value}))} /></label>
          </div>
          {(options.courses.length === 0 || options.terms.length === 0 || options.teachers.length === 0) && <p className="notice error">Thiếu học phần, học kỳ hoặc giảng viên.</p>}
          {error && <p className="admin-form-error" role="alert">{error}</p>}
          <div className="form-actions"><button type="button" className="secondary" onClick={closeAdminEditor} disabled={busy}>Hủy</button><button className="primary" type="submit" disabled={busy || !options.courses.length || !options.terms.length || !options.teachers.length}>{busy ? "Đang lưu..." : "Lưu lớp học"}</button></div>
        </form>
      </AdminFormDialog>}
</div></section></main>;
}
