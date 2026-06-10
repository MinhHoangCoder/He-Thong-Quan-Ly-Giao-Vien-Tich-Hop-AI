// Chính sách mật khẩu — PHẢI khớp với regex @Pattern ở backend
// (RegisterRequest.java / ResetPasswordRequest.java): 8–72 ký tự, có hoa + thường + số.
// FE validate trước để báo lỗi tức thì; backend vẫn là chốt chặn cuối cùng.

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/

export const PASSWORD_HINT = '8–72 ký tự, gồm chữ hoa, chữ thường và chữ số'

export function isStrongPassword(value) {
  return PASSWORD_REGEX.test(value)
}
