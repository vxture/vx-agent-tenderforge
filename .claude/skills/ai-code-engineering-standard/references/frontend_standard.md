# AI 编码工程规范

# 前端开发规范（React + Vite + TypeScript）

本章节定义基于 React 19 + Vite 7 + Tailwind CSS v4 的前端开发规范，适用于企业级 SPA 应用开发。

### 3.0 技术栈说明

- **React 19**: 使用函数组件 + Hooks 模式
- **Vite 7**: 快速构建工具，支持 HMR，使用 `@tailwindcss/vite` 插件集成 Tailwind
- **TypeScript 5.9**: 严格类型检查，提升代码质量
- **Tailwind CSS v4**: 原子化 CSS 框架，**CSS-first 配置**（无 `tailwind.config.js`），使用 `oklch()` 颜色空间
- **shadcn/ui**（推荐）: 基于 Radix UI 的可定制组件库（new-york 风格）
- **Zustand 5**: 轻量级状态管理方案
- **React Router 7**: 声明式路由管理（import 从 `react-router` 而非 `react-router-dom`）
- **TanStack Query**: 服务端状态管理与数据请求（替代手动 Axios 封装）
- **@pt/utils**: 内部工具库，提供 `createHttpInstance`（Axios 封装）、storage 等
- **lucide-react**: 图标库，提供丰富的 SVG 图标组件
- **vite-plugin-svgr**: 自定义 SVG 图标支持，通过 `?react` 后缀导入为 React 组件

### 3.1 JSDoc 规范（@preconditions, @sideEffects, @errorHandling）

#### 3.1.1 JSDoc 注释格式

```typescript
// GENERATED_BY_AI
// MODEL: claude-3.7-sonnet
// DATE: 2026-01-06

/**
 * 创建订单
 *
 * @param userId - 用户 ID
 * @param productId - 商品 ID
 * @param quantity - 购买数量
 * @param price - 商品单价
 * @returns 订单 ID
 *
 * @preconditions
 * - userId 必须是有效的用户 ID
 * - productId 必须存在且库存充足
 * - quantity > 0 且 <= 1000
 * - price > 0
 *
 * @sideEffects
 * - 调用后端 API POST /api/orders
 * - 更新本地订单列表缓存
 * - 触发订单创建成功通知
 *
 * @errorHandling
 * - 400: 参数验证失败 -> 显示错误提示
 * - 404: 商品不存在 -> 跳转到商品列表
 * - 409: 库存不足 -> 显示库存不足提示
 * - 500: 服务器错误 -> 显示通用错误信息
 */
export async function createOrder(
  userId: string,
  productId: string,
  quantity: number,
  price: number
): Promise<string> {
  // 实现代码...
}
```

#### 3.1.2 文件级 AI 标记

```typescript
// GENERATED_BY_AI
// MODEL: claude-3.7-sonnet
// DATE: 2026-01-06
```

### 3.2 目录结构

```
frontend/project-name-web/
├── src/
│   ├── api/                     # API 请求封装
│   │   ├── index.ts             # createHttpInstance 实例
│   │   ├── config/
│   │   │   └── servicePort.ts   # 服务端口配置
│   │   ├── interface/
│   │   │   └── index.ts         # API 类型定义
│   │   └── modules/             # 按业务模块拆分 API
│   │       └── login.ts
│   ├── assets/                  # 静态资源
│   │   └── icons/
│   │       └── ptDesignIcon/    # 自定义 SVG 图标集
│   ├── components/              # 通用组件
│   │   └── ui/                  # shadcn/ui 组件（CLI 自动生成）
│   │       └── button.tsx
│   ├── config/                  # 应用配置
│   ├── hooks/                   # 通用 Hooks
│   │   └── index.ts
│   ├── layouts/                 # 布局组件
│   │   ├── index.tsx            # 布局导出
│   │   ├── MainLayout.tsx       # 主布局（Header + Menu + Outlet）
│   │   ├── Header.tsx           # 顶部导航
│   │   ├── Menu.tsx             # 侧边栏菜单
│   │   ├── SubMenuBar.tsx       # 子菜单栏
│   │   └── menuConfig.ts       # 菜单配置
│   ├── lib/                     # 工具库
│   │   └── utils.ts             # cn() 函数（clsx + tailwind-merge）
│   ├── pages/                   # 页面组件（扁平结构）
│   │   ├── index.ts             # 页面统一导出
│   │   ├── Home/
│   │   ├── About/
│   │   ├── IconExamples/        # lucide-react 图标示例
│   │   ├── SvgIcons/            # 自定义 SVG 图标示例
│   │   ├── Forbidden/           # 403 页面
│   │   ├── NotFound/            # 404 页面
│   │   └── ServerError/         # 500 页面
│   ├── router/                  # 路由配置
│   │   ├── index.tsx            # createBrowserRouter 路由定义
│   │   └── AuthGuard.tsx        # 认证守卫组件
│   ├── stores/                  # Zustand 状态管理
│   │   ├── auth.ts              # 认证状态
│   │   ├── global.ts            # 全局状态（含 persist）
│   │   └── index.ts             # 统一导出
│   ├── styles/                  # 全局样式
│   │   └── globals.css          # Tailwind v4 CSS-first 配置 + 主题变量
│   ├── types/                   # 全局类型定义
│   ├── utils/                   # 工具函数
│   ├── App.tsx                  # 根组件（RouterProvider + QueryClientProvider）
│   └── main.tsx                 # 应用入口
├── docs/                        # 项目文档
│   └── ICONS.md                 # 图标使用文档
├── public/                      # 静态资源
├── index.html                   # HTML 模板
├── vite.config.ts               # Vite 配置（含 @tailwindcss/vite 插件）
├── components.json              # shadcn/ui 配置
├── tsconfig.json                # TypeScript 配置
├── eslint.config.ts             # ESLint 配置
└── package.json                 # 依赖管理（pnpm）
```

**目录说明**：

1. **api/**: 统一 API 请求层，使用 `@pt/utils` 的 `createHttpInstance` 创建 Axios 实例
2. **components/ui/**: shadcn/ui 组件，通过 `npx shadcn@latest add` 自动生成
3. **layouts/**: 布局组件，`MainLayout` 使用 `<Outlet />` 渲染子路由
4. **lib/utils.ts**: `cn()` 工具函数，路径别名 `@/lib/utils`
5. **pages/**: 扁平结构，每个页面一个目录
6. **stores/**: Zustand 5 状态管理，按功能拆分 Store
7. **router/**: 路由配置 + `AuthGuard` 认证守卫
8. **注意**：项目**不使用** `tailwind.config.js`，Tailwind v4 采用 CSS-first 配置

### 3.2.1 shadcn/ui 组件库（推荐）

shadcn/ui 是一个基于 Radix UI 和 Tailwind CSS 的可定制组件库，推荐用于快速构建 UI。

#### 初始化配置

项目已通过 `components.json` 配置（new-york 风格，Tailwind CSS v4 模式）：

```json
{
  "style": "new-york",
  "rsc": false,
  "tsx": true,
  "tailwind": { "config": "", "css": "src/index.css", "cssVariables": true },
  "iconLibrary": "lucide",
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  }
}
```

**注意**：`tailwind.config` 为空字符串，表示使用 Tailwind CSS v4 的 CSS-first 配置模式。

#### 添加组件

```bash
# 添加单个组件
npx shadcn@latest add button
npx shadcn@latest add input
npx shadcn@latest add card

# 添加多个组件
npx shadcn@latest add button input card dialog
```

组件会自动生成到 `src/components/ui/` 目录。

#### 使用示例

```typescript
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

export function OrderForm() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>创建订单</CardTitle>
      </CardHeader>
      <CardContent>
        <Input placeholder="商品名称" />
        <Button>提交订单</Button>
      </CardContent>
    </Card>
  );
}
```

#### 组件定制

shadcn/ui 组件是复制到项目中的，可以直接修改源码：

```typescript
// src/components/ui/button.tsx
import { cn } from "@/lib/utils";

export function Button({ className, ...props }) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center rounded-md",
        "bg-primary text-primary-foreground",
        "hover:bg-primary/90",
        "px-4 py-2",
        className
      )}
      {...props}
    />
  );
}
```

#### 最佳实践

1. **按需添加**: 只添加项目需要的组件，避免冗余
2. **统一风格**: 所有 UI 组件优先使用 shadcn/ui
3. **自定义封装**: 对于复杂业务组件，基于 shadcn/ui 组件封装
4. **主题一致**: 使用 CSS 变量统一管理主题色

```typescript
// ✅ 正确：基于 shadcn/ui 封装业务组件
import { Button } from "@/components/ui/button";

export function SubmitOrderButton({ orderId, ...props }) {
  const handleSubmit = () => {
    // 业务逻辑
  };

  return (
    <Button onClick={handleSubmit} {...props}>
      提交订单
    </Button>
  );
}

// ❌ 错误：自己实现基础 UI 组件
export function CustomButton() {
  return <button className="custom-button">...</button>;
}
```

---

### 3.3 代码规范

#### 3.3.1 TypeScript 严格模式

```typescript
// ✅ 正确：完整的类型定义
interface Order {
  id: string;
  userId: string;
  productId: string;
  quantity: number;
  price: number;
  status: OrderStatus;
  createdAt: Date;
}

function getOrder(id: string): Promise<Order> {
  return request.get<Order>(`/api/orders/${id}`);
}

// ❌ 错误：使用 any 类型
function getOrder(id: string): Promise<any> {
  return request.get(`/api/orders/${id}`);
}
```

#### 3.3.2 Hooks 封装逻辑

```typescript
// ✅ 正确：逻辑在 Hook 中
// hooks/useOrders.ts
export function useOrders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchOrders = async () => {
    setLoading(true);
    try {
      const data = await orderApi.getOrders();
      setOrders(data);
    } finally {
      setLoading(false);
    }
  };

  return { orders, loading, fetchOrders };
}

// pages/OrderList.tsx
export function OrderList() {
  const { orders, loading, fetchOrders } = useOrders();

  useEffect(() => {
    fetchOrders();
  }, []);

  return <div>{/* 渲染逻辑 */}</div>;
}

// ❌ 错误：业务逻辑直接写在页面组件
export function OrderList() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    orderApi.getOrders().then((data) => {
      setOrders(data);
      setLoading(false);
    });
  }, []);

  return <div>{/* 渲染逻辑 */}</div>;
}
```

#### 3.3.3 统一请求封装

使用 `@pt/utils` 的 `createHttpInstance` 创建 Axios 实例，配合 TanStack Query 管理服务端状态：

```typescript
// api/index.ts
import { createHttpInstance } from "@pt/utils";
import { servicePort } from "./config/servicePort";

// 创建 HTTP 实例
export const http = createHttpInstance({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
});

// api/modules/user.ts
import { http } from "@/api";
import type { UserInfo } from "@/api/interface";

export const userApi = {
  getUsers: () => http.get<UserInfo[]>("/api/users"),
  getUser: (id: string) => http.get<UserInfo>(`/api/users/${id}`),
  createUser: (data: CreateUserDto) => http.post("/api/users", data),
};
```

**配合 TanStack Query 使用**：

```typescript
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { userApi } from "@/api/modules/user";

// 查询数据
export function useUsers() {
  return useQuery({
    queryKey: ["users"],
    queryFn: userApi.getUsers,
  });
}

// 变更数据
export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: userApi.createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });
}
```

#### 3.3.4 路由配置

使用 React Router 7（注意：import 从 `react-router` 而非 `react-router-dom`）。

##### 路由结构

```typescript
// router/index.tsx
import { createBrowserRouter } from "react-router";
import { AuthGuard } from "./AuthGuard";
import { MainLayout } from "@/layouts";
import { Home, About, IconExamples, SvgIcons, Forbidden, NotFound, ServerError } from "@/pages";

export const router = createBrowserRouter([
  {
    path: "/",
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Home /> },
      { path: "about", element: <About /> },
      { path: "icons", element: <IconExamples /> },
      { path: "svg-icons", element: <SvgIcons /> },
      // 新增页面路由在此添加
    ],
  },
  { path: "/403", element: <Forbidden /> },
  { path: "/404", element: <NotFound /> },
  { path: "/500", element: <ServerError /> },
  { path: "*", element: <NotFound /> },
]);
```

##### AuthGuard 认证守卫

```typescript
// router/AuthGuard.tsx
import { useEffect } from "react";
import { useLocation } from "react-router";

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const location = useLocation();

  useEffect(() => {
    // 检查 token 和用户信息
    // 未登录时重定向到 SSO 登录页
  }, [location.pathname]);

  return <>{children}</>;
}
```

##### 路由可寻址性（必需）

每个核心功能页面必须拥有独立、可寻址的路由：

```typescript
// ✅ 正确：独立路由
/                           # 首页
/about                      # 关于
/users                      # 用户列表
/users/:id                  # 用户详情

// ❌ 错误：无真实路由
/app                        # 所有功能都在这个页面，通过内部状态切换
```

#### 3.3.5 状态管理（Zustand）

使用 Zustand 进行轻量级状态管理，支持持久化和中间件。

##### 全局状态示例

```typescript
// GENERATED_BY_AI
// MODEL: claude-3.7-sonnet
// DATE: 2026-01-23

// stores/globalStore.ts
import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

interface ThemeConfig {
  mode: "light" | "dark";
  primaryColor: string;
}

interface GlobalState {
  theme: ThemeConfig;
  sidebarCollapsed: boolean;
  setTheme: (theme: Partial<ThemeConfig>) => void;
  toggleSidebar: () => void;
}

export const useGlobalStore = create<GlobalState>()(
  persist(
    (set) => ({
      theme: {
        mode: "light",
        primaryColor: "#1890ff",
      },
      sidebarCollapsed: false,

      setTheme: (theme) =>
        set((state) => ({
          theme: { ...state.theme, ...theme },
        })),

      toggleSidebar: () =>
        set((state) => ({
          sidebarCollapsed: !state.sidebarCollapsed,
        })),
    }),
    {
      name: "global-storage",
      storage: createJSONStorage(() => localStorage),
    }
  )
);
```

##### 认证状态示例

```typescript
// stores/authStore.ts
import { create } from "zustand";
import { persist } from "zustand/middleware";

interface User {
  id: string;
  name: string;
  email: string;
}

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      isAuthenticated: false,

      login: (token, user) =>
        set({
          token,
          user,
          isAuthenticated: true,
        }),

      logout: () =>
        set({
          token: null,
          user: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: "auth-storage",
    }
  )
);
```

##### 使用状态

```typescript
// pages/OrderList.tsx
import { useGlobalStore } from "@/stores/globalStore";
import { useAuthStore } from "@/stores/authStore";

export function OrderList() {
  // 读取状态
  const { theme, sidebarCollapsed } = useGlobalStore();
  const { user, isAuthenticated } = useAuthStore();

  // 调用 actions
  const toggleSidebar = useGlobalStore((state) => state.toggleSidebar);
  const logout = useAuthStore((state) => state.logout);

  // 选择器优化（避免不必要的重渲染）
  const themeMode = useGlobalStore((state) => state.theme.mode);

  return <div>{/* 组件内容 */}</div>;
}
```

##### 最佳实践

1. **状态拆分**: 按功能域拆分 Store，避免单个巨大的 Store
2. **选择器优化**: 使用选择器只订阅需要的状态
3. **持久化**: 需要持久化的状态使用 `persist` 中间件
4. **类型安全**: 为所有 Store 定义完整的 TypeScript 类型

```typescript
// ✅ 正确：按功能拆分 Store
stores/
├── auth.ts           # 认证相关
├── global.ts         # 全局配置
├── orderStore.ts     # 订单相关
└── index.ts          # 统一导出

// ❌ 错误：所有状态放在一个 Store
stores/
└── appStore.ts       # 包含所有状态
```

#### 3.3.6 Tailwind CSS v4 使用规范

##### CSS-first 配置（无 tailwind.config.js）

Tailwind CSS v4 采用全新的 **CSS-first 配置**范式，不再使用 `tailwind.config.js`。所有配置直接在 CSS 文件中完成：

```css
/* src/styles/globals.css */

/* 1. 导入 Tailwind（v4 语法，替代 @tailwind base/components/utilities） */
@import 'tailwindcss';
@import 'tw-animate-css';
@import 'shadcn/tailwind.css';

/* 2. 暗色模式变体（class 策略） */
@custom-variant dark (&:is(.dark *));

/* 3. CSS 变量定义（:root 亮色 / .dark 暗色） */
:root {
  --primary: oklch(0.488 0.243 264.376);
  --primary-foreground: oklch(0.97 0.014 254.604);
  /* ... 更多变量 */
}

.dark {
  --primary: oklch(0.42 0.18 266);
  /* ... 暗色覆盖 */
}

/* 4. @theme inline 将 CSS 变量映射为 Tailwind utility class */
@theme inline {
  --font-sans: 'Inter Variable', sans-serif;
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  /* ... 更多映射 */
}
```

**关键变化（v3 → v4）**：

| v3（旧） | v4（新） |
|----------|---------|
| `@tailwind base; @tailwind components; @tailwind utilities;` | `@import 'tailwindcss';` |
| `tailwind.config.js` 中定义 theme | `@theme inline { }` 在 CSS 中定义 |
| `hsl(var(--primary))` | `oklch(...)` 颜色空间 |
| `darkMode: ["class"]` 在 JS 配置 | `@custom-variant dark (&:is(.dark *));` |
| `require("tailwindcss-animate")` 插件 | `@import 'tw-animate-css';` |
| `content: ["./src/**/*.{tsx}"]` | 自动检测，无需配置 |

##### Vite 集成

```typescript
// vite.config.ts
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(), // 替代 PostCSS 插件方式
  ],
});
```

##### 使用 cn 工具函数

```typescript
// src/lib/utils.ts
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

##### 使用示例

```typescript
import { cn } from "@/lib/utils";

// ✅ 正确：使用 cn 合并类名
export function Button({ className, variant, ...props }) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center rounded-md",
        "px-4 py-2 text-sm font-medium",
        "transition-colors focus-visible:outline-none",
        {
          "bg-primary text-primary-foreground hover:bg-primary/90": variant === "default",
          "bg-destructive text-destructive-foreground hover:bg-destructive/90": variant === "destructive",
        },
        className
      )}
      {...props}
    />
  );
}

// ❌ 错误：直接拼接字符串
export function Button({ className }) {
  return <button className={"btn " + className} />;
}
```

##### 最佳实践

1. **使用项目主题变量**: 优先使用 `bg-primary`、`text-muted-foreground` 等语义化 class
2. **避免内联样式**: 尽量使用 Tailwind 类名，避免 `style` 属性
3. **响应式设计**: 使用 `sm:`, `md:`, `lg:` 等响应式前缀
4. **暗色模式**: 使用 `dark:` 前缀支持暗色主题
5. **不要使用 @apply**: Tailwind v4 推荐直接使用 utility class，减少 `@apply` 使用

```typescript
// ✅ 正确：使用项目主题变量
<div className="bg-card text-card-foreground rounded-lg border p-6">
  <h2 className="text-lg font-semibold text-foreground">标题</h2>
  <p className="text-sm text-muted-foreground">描述文字</p>
  <Button className="bg-primary text-primary-foreground">操作</Button>
</div>

// ✅ 正确：响应式和暗色模式
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
  {/* 内容 */}
</div>

// ❌ 错误：使用内联样式
<div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)" }}>
  {/* 内容 */}
</div>

// ❌ 错误：使用硬编码颜色而非主题变量
<div className="bg-blue-500 text-white">
  {/* 应该使用 bg-primary text-primary-foreground */}
</div>
```

#### 3.3.7 CSS 主题变量与样式规范

**开发前端页面前，务必先阅读 `src/styles/globals.css` 了解项目主题变量。**

##### 项目定义的 CSS 变量清单

以下变量在 `:root`（亮色）和 `.dark`（暗色）中分别定义，使用 `oklch()` 颜色空间：

| 变量名 | 用途 | Tailwind class 示例 |
|--------|------|---------------------|
| `--background` | 页面背景 | `bg-background` |
| `--foreground` | 主要文字 | `text-foreground` |
| `--card` | 卡片背景 | `bg-card` |
| `--card-foreground` | 卡片文字 | `text-card-foreground` |
| `--popover` | 弹出层背景 | `bg-popover` |
| `--popover-foreground` | 弹出层文字 | `text-popover-foreground` |
| `--primary` | 主色（品牌色） | `bg-primary`, `text-primary`, `border-primary` |
| `--primary-foreground` | 主色上的文字 | `text-primary-foreground` |
| `--secondary` | 次要色 | `bg-secondary`, `text-secondary` |
| `--secondary-foreground` | 次要色上的文字 | `text-secondary-foreground` |
| `--muted` | 柔和背景 | `bg-muted` |
| `--muted-foreground` | 柔和文字（次要信息） | `text-muted-foreground` |
| `--accent` | 强调色（hover 等） | `bg-accent` |
| `--accent-foreground` | 强调色上的文字 | `text-accent-foreground` |
| `--destructive` | 危险/删除操作 | `bg-destructive`, `text-destructive` |
| `--border` | 边框 | `border-border` |
| `--input` | 输入框边框 | `border-input` |
| `--ring` | 聚焦环 | `ring-ring` |
| `--chart-1` ~ `--chart-5` | 图表色系（蓝色渐变） | `bg-chart-1` ~ `bg-chart-5` |
| `--radius` | 基础圆角 | `rounded-sm/md/lg/xl/2xl/3xl/4xl` |
| `--sidebar` | 侧边栏背景 | `bg-sidebar` |
| `--sidebar-foreground` | 侧边栏文字 | `text-sidebar-foreground` |
| `--sidebar-primary` | 侧边栏主色 | `bg-sidebar-primary` |
| `--sidebar-accent` | 侧边栏强调色 | `bg-sidebar-accent` |
| `--sidebar-border` | 侧边栏边框 | `border-sidebar-border` |

##### @theme inline 映射机制

`globals.css` 中的 `@theme inline { }` 块将 CSS 变量映射为 Tailwind 可用的 design token：

```css
@theme inline {
  --color-primary: var(--primary);           /* → bg-primary, text-primary */
  --color-primary-foreground: var(--primary-foreground); /* → text-primary-foreground */
  --color-muted: var(--muted);               /* → bg-muted */
  --color-muted-foreground: var(--muted-foreground);     /* → text-muted-foreground */
  /* ... 所有颜色变量都有对应映射 */

  --radius-sm: calc(var(--radius) - 4px);    /* → rounded-sm */
  --radius-md: calc(var(--radius) - 2px);    /* → rounded-md */
  --radius-lg: var(--radius);                /* → rounded-lg */
  --radius-xl: calc(var(--radius) + 4px);    /* → rounded-xl */
}
```

##### Tailwind v4 内置变量

除了项目自定义变量，还可以使用 Tailwind v4 内置的所有 design token：

- **间距**: `p-4`(1rem), `m-8`(2rem), `gap-6`(1.5rem) 等（基于 4px 网格）
- **排版**: `text-sm`(0.875rem), `text-base`(1rem), `text-lg`(1.125rem), `font-medium`, `font-semibold` 等
- **内置颜色**: `red-500`, `blue-600`, `gray-100` 等 Tailwind 默认调色板
- **阴影**: `shadow-sm`, `shadow-md`, `shadow-lg` 等
- **完整参考**: https://tailwindcss.com/docs/theme#default-theme-variable-reference

##### 颜色使用优先级

```typescript
// ✅ 优先级 1：使用项目语义化主题变量
<Button className="bg-primary text-primary-foreground" />
<div className="bg-destructive text-white" />
<p className="text-muted-foreground" />
<div className="border-border rounded-lg" />

// ✅ 优先级 2：需要具体颜色时使用 Tailwind 内置色
<span className="text-green-600">成功</span>
<span className="text-yellow-500">警告</span>

// ❌ 避免：硬编码 oklch/hsl/hex 值
<div style={{ color: "oklch(0.488 0.243 264.376)" }} />
<div className="text-[#3b82f6]" />
```

##### 暗色模式

项目使用 `:root` / `.dark` 双主题切换。CSS 变量在两个主题中分别定义不同的值，组件**无需手动添加 `dark:` 前缀**——主题变量会自动适配：

```typescript
// ✅ 正确：使用主题变量，自动适配暗色模式
<div className="bg-background text-foreground">
  <div className="bg-card border-border rounded-lg p-4">
    <p className="text-muted-foreground">自动适配亮/暗色</p>
  </div>
</div>

// ⚠️ 仅在使用 Tailwind 内置颜色时需要 dark: 前缀
<div className="bg-white dark:bg-gray-900">
  {/* 非主题变量需要手动处理暗色 */}
</div>
```

#### 3.3.8 示例页面参考

**开发新页面前，建议先参考以下示例页面了解项目的组件写法和样式风格。**

##### 图标使用

项目有两套图标系统：

**1. lucide-react 图标（通用图标）**

示例页面：`src/pages/IconExamples/index.tsx`（路由 `/icons`）

```typescript
import { Search, Plus, Settings, ChevronRight } from "lucide-react";

// 基础使用
<Search className="h-4 w-4" />
<Plus className="h-5 w-5 text-primary" />

// 在按钮中使用
<Button>
  <Plus className="mr-2 h-4 w-4" />
  新增
</Button>
```

**2. 自定义 SVG 图标（业务图标）**

示例页面：`src/pages/SvgIcons/index.tsx`（路由 `/svg-icons`）

```typescript
// 使用 ?react 后缀导入 SVG 为 React 组件
import PdfIcon from "@/assets/icons/ptDesignIcon/file-types/cf-pdf.svg?react";
import MysqlIcon from "@/assets/icons/ptDesignIcon/databases/cf-mysql.svg?react";

// 使用 className 控制尺寸
<PdfIcon className="h-12 w-12" />
<MysqlIcon className="h-8 w-8" />
```

**注意**：
- 自定义 SVG 必须使用 `?react` 后缀导入
- 彩色 SVG 保留原始颜色，无法通过 className 修改颜色
- 使用 `h-*` 和 `w-*` class 控制尺寸

完整图标文档：`docs/ICONS.md`

##### 页面布局模式

所有业务页面在 `MainLayout` 内渲染（通过 `<Outlet />`），自带 Header + 侧边栏菜单：

```typescript
// 典型的页面组件结构
export default function UserList() {
  return (
    <div className="space-y-6 p-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">用户管理</h1>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          新增用户
        </Button>
      </div>

      {/* 内容区域 */}
      <div className="rounded-lg border bg-card p-4">
        {/* 表格或列表 */}
      </div>
    </div>
  );
}
```

### 3.4 复杂度和规模限制

| 指标           | AI 生成代码 | 人工代码 |
| -------------- | ----------- | -------- |
| **圈复杂度**   | ≤ 7         | ≤ 10     |
| **函数行数**   | ≤ 50        | ≤ 80     |
| **组件行数**   | ≤ 300       | ≤ 500    |
| **Props 数量** | ≤ 5         | ≤ 8      |

### 3.5 命名规范

```typescript
// ✅ 正确命名
// 组件：PascalCase
export function OrderList() {}

// Hooks：camelCase + use 前缀
export function useOrders() {}

// API：camelCase
export const orderApi = {
  getOrders: () => {},
  createOrder: () => {},
};

// ❌ 禁止的命名
export function order_list() {} // 不要使用下划线
export function useorder() {} // use 后首字母要大写
export const OrderAPI = {}; // API 对象不要用 PascalCase
```

### 3.6 禁止的命名

以下命名会导致 AI 混淆，严禁使用：

```typescript
// ❌ 禁止
const data = ...;           // 太泛化
const info = ...;           // 太泛化
const temp = ...;           // 临时变量应该有明确含义
const handleClick = ...;    // 应该说明点击什么
const doSomething = ...;    // 应该说明做什么

// ✅ 正确
const orderData = ...;
const userInfo = ...;
const pendingOrder = ...;
const handleSubmitOrder = ...;
const validateOrderForm = ...;
```

### 3.7 导入排序和类型导入规范

#### 3.7.1 导入排序

使用 `simple-import-sort` 进行导入排序，导入顺序如下：

```typescript
// 1. React 相关包优先
import { useState, useEffect, useMemo } from "react";
import { useNavigate, useParams } from "react-router";

// 2. 第三方包
import { useQuery } from "@tanstack/react-query";
import { create } from "zustand";
import { clsx } from "clsx";

// 3. @ 开头的内部包（shadcn/ui 等）
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

// 4. @/ 开头的内部导入（别名导入）
import { useAuthStore } from "@/stores/auth";
import { userApi } from "@/api/modules/user";
import { cn } from "@/lib/utils";

// 5. 相对路径导入
import { OrderCard } from "./components/OrderCard";
import { useOrders } from "../hooks/useOrders";

// 6. 样式文件导入
import "./styles.css";
```

#### 3.7.2 类型导入规范

强制使用 `type` 导入，以便更好的 Tree-shaking：

```typescript
// ✅ 正确：使用 type 导入
import type { UserInfo, AuthState } from "@/types/auth";
import type { Order, OrderStatus } from "@/types/order";

// ❌ 错误：混合导入类型
import { UserInfo, fetchUser } from "@/api/user";

// ✅ 正确：分开导入
import type { UserInfo } from "@/api/user";
import { fetchUser } from "@/api/user";
```

### 3.8 构建配置

Vite 配置要点（`vite.config.ts`）：

```typescript
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import svgr from "vite-plugin-svgr";
import path from "path";

export default defineConfig({
  plugins: [
    svgr(),          // SVG → React 组件（?react 后缀）
    react(),
    tailwindcss(),   // Tailwind CSS v4 Vite 插件
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    minify: "esbuild",
    target: "esnext",
  },
});
```

**关键配置**：
- `@tailwindcss/vite` 替代 PostCSS 方式集成 Tailwind
- `vite-plugin-svgr` 支持 `import Icon from "./icon.svg?react"` 语法
- `@` 路径别名指向 `src/`
- 使用 `pnpm` 作为包管理器

---
