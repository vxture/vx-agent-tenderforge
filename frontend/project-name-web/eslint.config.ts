import { defineConfig, globalIgnores } from 'eslint/config'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import simpleImportSort from 'eslint-plugin-simple-import-sort'
import globals from 'globals'
import tseslint from 'typescript-eslint'

import js from '@eslint/js'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfig([
  globalIgnores([
    '**/dist/**',
    '**/dist-ssr/**',
    '**/coverage/**',
    '**/public/**',
    '**/node_modules/**',
    '**/*.d.ts',
  ]),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'simple-import-sort': simpleImportSort,
    },
    rules: {
      // ===== ESLint 基础规则 =====
      'no-var': 'error', // 要求使用 let 或 const 而不是 var
      'no-multiple-empty-lines': ['error', { max: 1 }], // 不允许多个空行
      'no-use-before-define': 'off', // 禁止在定义之前使用
      'prefer-const': 'off', // 不强制使用 const
      'no-irregular-whitespace': 'off', // 允许不规则的空白
      'no-prototype-builtins': 'off', // 允许直接使用 Object.prototype 方法
      'no-undef': 'off', // TypeScript 已处理未定义变量

      // ===== TypeScript 规则 =====
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
      '@typescript-eslint/no-inferrable-types': 'off', // 允许显式类型声明
      '@typescript-eslint/no-namespace': 'off', // 允许使用 namespace
      '@typescript-eslint/no-explicit-any': 'off', // 允许使用 any
      '@typescript-eslint/ban-types': 'off', // 允许使用特定类型
      '@typescript-eslint/explicit-function-return-type': 'off', // 不强制函数返回类型
      '@typescript-eslint/no-var-requires': 'off', // 允许 require
      '@typescript-eslint/no-empty-function': 'off', // 允许空函数
      '@typescript-eslint/ban-ts-comment': 'off', // 允许 @ts-ignore 等注释
      '@typescript-eslint/no-non-null-assertion': 'off', // 允许非空断言
      '@typescript-eslint/explicit-module-boundary-types': 'off', // 不强制导出函数的类型
      '@typescript-eslint/consistent-type-imports': [
        'error',
        {
          prefer: 'type-imports', // 强制使用 type import
          disallowTypeAnnotations: false,
          fixStyle: 'separate-type-imports', // 强制类型导入和值导入分开
        },
      ],
      '@typescript-eslint/no-this-alias': [
        'error',
        {
          allowedNames: ['_this', 'self'],
        },
      ],

      // ===== Import 排序规则 =====
      'simple-import-sort/imports': [
        'error',
        {
          groups: [
            // react 相关包优先，然后是其他以字母开头的包
            ['^react', '^[a-z]'],
            // @word 开头的包 (如 @tanstack, @radix-ui 等)
            ['^@\\w+'],
            // @/ 开头的内部导入 (别名导入)
            ['^@/.*'],
            // ~ 开头的导入
            ['^~'],
            // ./ 开头的相对导入
            ['^\\./(?=.*/)(?!/?$)', '^\\.(?!/?$)', '^\\./?$'],
            // ../ 开头的父级导入
            ['^\\.\\.(?!/?$)', '^\\.\\./?'],
            // 样式文件导入
            ['^.+\\.(s?css|less)$'],
            // 副作用导入
            ['^\\u0000'],
          ],
        },
      ],
      'simple-import-sort/exports': 'error',

      // ===== React Hooks 规则 =====
      // 在 effect 中 fetch 数据并 setState 是常见的合理模式，关闭该规则
      'react-hooks/set-state-in-effect': 'off',
    },
  },

  // shadcn/ui 组件文件允许导出 variants 常量
  {
    files: ['**/components/ui/**/*.{ts,tsx}'],
    rules: {
      'react-refresh/only-export-components': 'off',
    },
  },

  skipFormatting,
])
