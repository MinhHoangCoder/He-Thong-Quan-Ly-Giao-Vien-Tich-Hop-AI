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
