/** Vietnamese display labels for class lifecycle states stored as enums in SQL/API. */
export type ClassStatus = "OPEN" | "CLOSED" | "CANCELLED";

const CLASS_STATUS_LABELS: Record<ClassStatus, string> = {
  OPEN: "Đang mở",
  CLOSED: "Đã đóng",
  CANCELLED: "Đã hủy",
};

const CLASS_STATUS_DESCRIPTIONS: Record<ClassStatus, string> = {
  OPEN: "Mở lại trạng thái lớp. Sinh viên chỉ được đăng ký hoặc được xét danh sách chờ khi học kỳ còn trong đợt đăng ký.",
  CLOSED: "Ngừng nhận đăng ký mới; lớp và các lượt đăng ký đã có vẫn được giữ nguyên.",
  CANCELLED: "Lớp không tiếp tục tổ chức; chỉ được hủy khi không còn sinh viên đăng ký thành công.",
};

export function classStatusLabel(status: ClassStatus): string {
  return CLASS_STATUS_LABELS[status];
}

export function classStatusDescription(status: ClassStatus): string {
  return CLASS_STATUS_DESCRIPTIONS[status];
}
