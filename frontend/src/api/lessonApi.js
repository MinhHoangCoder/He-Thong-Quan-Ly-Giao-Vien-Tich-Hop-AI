/**
 * lessonApi.js — Tầng giao tiếp API cho module Bài giảng
 * Dùng trong Vue 3 với Composition API.
 *
 * Dùng: import { lessonApi } from '@/api/lessonApi'
 */

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

/** Lấy JWT token từ localStorage (điều chỉnh theo auth store thực tế) */
function getToken() {
  return localStorage.getItem('access_token') ?? ''
}
function getActorId() {
  return localStorage.getItem('user_id') ?? '1'
}

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${getToken()}`,
      'X-Actor-User-Id': getActorId(),
      ...options.headers,
    },
    ...options,
  })

  if (!res.ok) {
    let msg = `Lỗi ${res.status}`
    try {
      msg = (await res.json()).message ?? msg
    } catch {
      /* empty */
    }
    throw new Error(msg)
  }
  if (res.status === 204) return null
  return res.json()
}

export const lessonApi = {
  // ── Danh sách có lọc + phân trang ────────────────────────────────
  /**
   * @param {Object} filter { keyword, subjectId, teacherId, branchId,
   *                          status, difficultyLevel, gradeLevel,
   *                          page, size, sortBy, sortDir }
   * @returns Promise<Page<LessonSummaryDto>>
   */
  list(filter = {}) {
    const params = new URLSearchParams()
    Object.entries(filter).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params.append(k, v)
    })
    return request(`/lessons?${params}`)
  },

  // ── Chi tiết 1 bài giảng ─────────────────────────────────────────
  /** @returns Promise<LessonResponse> */
  get(id) {
    return request(`/lessons/${id}`)
  },

  // ── Thống kê theo trạng thái ──────────────────────────────────────
  /** @returns Promise<{ DRAFT: number, PUBLISHED: number, ARCHIVED: number }> */
  stats(branchId) {
    const q = branchId ? `?branchId=${branchId}` : ''
    return request(`/lessons/stats${q}`)
  },

  // ── Tạo mới ───────────────────────────────────────────────────────
  /**
   * @param {Object} payload LessonRequest
   * @returns Promise<LessonResponse>
   */
  create(payload) {
    return request('/lessons', { method: 'POST', body: JSON.stringify(payload) })
  },

  // ── Cập nhật ──────────────────────────────────────────────────────
  /**
   * @param {number} id
   * @param {Object} payload LessonRequest
   * @returns Promise<LessonResponse>
   */
  update(id, payload) {
    return request(`/lessons/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
  },

  // ── Đổi trạng thái ────────────────────────────────────────────────
  /**
   * @param {number} id
   * @param {'DRAFT'|'PUBLISHED'|'ARCHIVED'} status
   * @returns Promise<LessonResponse>
   */
  changeStatus(id, status) {
    return request(`/lessons/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    })
  },

  // ── Xóa mềm ──────────────────────────────────────────────────────
  /** @returns Promise<null> */
  delete(id) {
    return request(`/lessons/${id}`, { method: 'DELETE' })
  },

  // ── Thêm file đính kèm ────────────────────────────────────────────
  /**
   * @param {number} lessonId
   * @param {{ fileName, fileUrl, fileType?, fileSizeKb? }} payload
   * @returns Promise<LessonResponse>
   */
  addFile(lessonId, payload) {
    return request(`/lessons/${lessonId}/files`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  // ── Xóa file đính kèm ─────────────────────────────────────────────
  /** @returns Promise<null> */
  removeFile(lessonId, fileId) {
    return request(`/lessons/${lessonId}/files/${fileId}`, { method: 'DELETE' })
  },
}
