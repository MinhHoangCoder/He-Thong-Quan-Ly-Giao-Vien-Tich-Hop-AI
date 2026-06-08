import http from './http'

// Chi nhánh (đọc) — dùng cho dropdown khi tạo tài khoản GV/trường.
export const branchApi = {
  list() {
    return http.get('/branches')
  },
}
