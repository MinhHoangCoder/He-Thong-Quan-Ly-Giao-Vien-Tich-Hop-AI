import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'
import globals from 'globals'

export default [
  {
    name: 'app/files-to-lint',
    files: ['**/*.{js,mjs,jsx,vue}'],
  },

  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/dist-ssr/**', '**/coverage/**', '**/node_modules/**'],
  },

  // Biến toàn cục của trình duyệt (window, document, console, ...) và Node
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],

  // Tắt các rule format của ESLint để nhường việc format cho Prettier (phải đặt cuối)
  skipFormatting,

  // Component NỀN trong components/ui/ được đặt tên một từ (Pagination, và các primitive
  // thêm sau này). Rule multi-word sinh ra để tránh trùng tên thẻ HTML thật và để tên
  // component nghiệp vụ đủ đặc thù — cả hai lý do đó không áp cho thư mục primitive dùng
  // chung. Đổi tên Pagination sẽ phải sửa 13 file cho một quy ước thuần hình thức.
  {
    name: 'app/ui-primitives',
    files: ['src/components/ui/**/*.vue'],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },

  // Tùy chỉnh rule cho dự án
  {
    name: 'app/custom-rules',
    rules: {
      // Bỏ qua tham số hàm chưa dùng (vd: to, from trong route guard, $event trong handler)
      // nhưng vẫn báo lỗi biến thật sự thừa. Biến bắt đầu bằng _ cũng được bỏ qua.
      'no-unused-vars': [
        'error',
        { args: 'none', varsIgnorePattern: '^_', caughtErrors: 'none' },
      ],
    },
  },
]
