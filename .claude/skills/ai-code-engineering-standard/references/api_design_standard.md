# AI 编码工程规范

# 接口设计规范（面向智能体）

**核心理念**：通过统一、简洁、语义清晰的接口契约，确保接口既适用于传统系统集成，也能被 AI 智能体准确理解、安全调用，实现"一次开发，多端复用"。

## 1. OpenAPI 3.0 规范（必需）

所有对外提供的 API 必须提供符合 OpenAPI 3.0 规范的接口定义文件。

### 1.1 文档位置

```
接口现状统一记录在 `docs/30-design/10-detailed-design.md`；运行时 OpenAPI 由 Springdoc 提供。
```

### 1.2 完整示例

```yaml
openapi: 3.0.0
info:
  title: 资源管理 API
  version: 1.0.0
  description: 提供资源的创建、查询、更新和删除功能

paths:
  /api/resources:
    get:
      summary: 获取资源列表
      description: 查询所有资源，支持分页和筛选
      parameters:
        - name: page
          in: query
          required: false
          schema:
            type: integer
            default: 1
          description: 页码，从 1 开始
        - name: pageSize
          in: query
          required: false
          schema:
            type: integer
            default: 20
            maximum: 100
          description: 每页数量，最大 100
        - name: status
          in: query
          required: false
          schema:
            type: string
            enum: [active, inactive, pending]
          description: 资源状态筛选
      responses:
        '200':
          description: 成功返回资源列表
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    example: 200
                  data:
                    type: object
                    properties:
                      items:
                        type: array
                        items:
                          \$ref: '#/components/schemas/Resource'
                      total:
                        type: integer
                      page:
                        type: integer
                      pageSize:
                        type: integer
        '400':
          description: 请求参数错误
          content:
            application/json:
              schema:
                \$ref: '#/components/schemas/Error'
        '500':
          description: 服务器内部错误
          content:
            application/json:
              schema:
                \$ref: '#/components/schemas/Error'

components:
  schemas:
    Resource:
      type: object
      required:
        - id
        - name
        - status
      properties:
        id:
          type: string
          description: 资源唯一标识
        name:
          type: string
          description: 资源名称
        status:
          type: string
          enum: [active, inactive, pending]
          description: 资源状态
        createdAt:
          type: string
          format: date-time
          description: 创建时间，ISO 8601 格式
    Error:
      type: object
      properties:
        code:
          type: integer
          description: 错误码
        message:
          type: string
          description: 错误信息
        details:
          type: string
          description: 详细错误描述
        timestamp:
          type: string
          format: date-time
          description: 错误发生时间
        path:
          type: string
          description: 请求路径
        traceId:
          type: string
          description: 追踪ID
```

## 2. 参数设计约束

### 2.1 参数数量限制

**原则**：单个接口的请求参数（含 query、body、path）总数不超过 10 个。

```typescript
// ✅ 正确：参数数量合理
GET /api/resources?page=1&pageSize=20&status=active&keyword=test

// ❌ 错误：参数过多，应拆分接口
GET /api/resources?page=1&pageSize=20&status=active&keyword=test&startDate=2024-01-01&endDate=2024-12-31&category=A&subcategory=B&region=C&department=D&operator=E&priority=high
```

**解决方案**：拆分为多个职责单一的原子接口

```typescript
// 拆分为基础查询 + 高级筛选
GET /api/resources?page=1&pageSize=20&status=active
POST /api/resources/search  // 复杂查询使用 POST，参数放 body
```

### 2.2 参数层级限制

**规则**：请求体中的 JSON 对象嵌套层级不得超过 2 层。

```json
// ✅ 正确：2 层嵌套
{
  "name": "资源A",
  "config": {
    "enabled": true,
    "timeout": 3000
  }
}

// ❌ 错误：3 层嵌套
{
  "name": "资源A",
  "config": {
    "network": {
      "timeout": 3000,
      "retry": 3
    }
  }
}

// ✅ 正确：扁平化设计
{
  "name": "资源A",
  "networkTimeout": 3000,
  "networkRetry": 3
}
```

**禁止**：使用无固定结构的动态对象

```json
// ❌ 错误：动态 key-value 结构
{
  "metadata": {
    "key1": "value1",
    "key2": "value2",
    "arbitraryKey": "arbitraryValue"
  }
}

// ✅ 正确：固定结构
{
  "tags": ["tag1", "tag2"],
  "properties": [
    { "key": "key1", "value": "value1" },
    { "key": "key2", "value": "value2" }
  ]
}
```

### 2.3 命名规范

**规则**：所有字段名必须采用小驼峰命名法（camelCase）。

```json
// ✅ 正确：camelCase
{
  "userId": "123",
  "startTime": "2024-01-01T00:00:00Z",
  "maxRetryCount": 3
}

// ❌ 错误：snake_case
{
  "user_id": "123",
  "start_time": "2024-01-01T00:00:00Z",
  "max_retry_count": 3
}

// ❌ 错误：大写缩写
{
  "USER_ID": "123",
  "START_TIME": "2024-01-01T00:00:00Z"
}

// ❌ 错误：拼音或模糊缩写
{
  "yhbh": "123",
  "kssj": "2024-01-01"
}
```

## 3. 技能中心注册（智能体集成）

所有对外提供的业务接口必须在技能中心完成注册，支持智能体自动调用。

### 3.1 注册信息示例

```json
{
  "apiSpec": "openapi-3.0-resource-api.yaml",
  "summary": "资源管理接口，支持资源的创建、查询、更新和删除操作",
  "scenarios": ["资源管理", "数据查询", "系统集成"],
  "endpoints": [
    {
      "path": "/api/resources",
      "method": "GET",
      "function": "查询资源列表",
      "description": "根据筛选条件查询资源，支持分页",
      "agentCallable": true,
      "requiredScopes": ["resource:read"]
    },
    {
      "path": "/api/resources",
      "method": "POST",
      "function": "创建资源",
      "description": "创建新的资源记录",
      "agentCallable": true,
      "requiredScopes": ["resource:write"]
    }
  ]
}
```

### 3.2 接口功能摘要编写规范

- ✅ **长度限制**：不超过 100 字
- ✅ **语义清晰**：说明接口能完成什么任务
- ✅ **避免技术术语**：使用业务语言

```text
// ✅ 正确：清晰的业务描述
"查询指定时间范围内的资源使用情况，支持按状态和类别筛选，返回分页结果"

// ❌ 错误：技术术语
"GET 请求，返回 JSON 格式的数据列表"

// ❌ 错误：过于简单
"查询接口"
```

## 4. 标准化错误码

### 4.1 错误码定义

| 错误码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | 成功 | 请求成功处理 |
| 400 | 请求参数错误 | 参数验证失败、格式错误 |
| 401 | 未授权 | Token 无效或过期 |
| 403 | 禁止访问 | 权限不足 |
| 404 | 资源不存在 | 请求的资源未找到 |
| 409 | 冲突 | 资源状态冲突（如重复创建） |
| 429 | 请求过于频繁 | 超过速率限制 |
| 500 | 服务器内部错误 | 系统异常 |
| 503 | 服务不可用 | 服务暂时不可用 |

### 4.2 错误响应格式

```json
{
  "code": 400,
  "message": "请求参数错误",
  "details": "参数 'userId' 不能为空",
  "timestamp": "2024-01-23T10:00:00Z",
  "path": "/api/resources",
  "traceId": "abc123def456"
}
```

## 5. 最佳实践

1. **接口职责单一**：一个接口只做一件事
2. **参数扁平化**：避免深层嵌套，优先使用扁平结构
3. **语义化命名**：字段名自解释，无需额外文档
4. **完整文档**：OpenAPI 文档包含所有细节
5. **标准错误码**：使用统一的错误码体系
6. **版本管理**：使用 URL 版本号（如 `/v1/api/resources`）
7. **幂等性**：POST/PUT/DELETE 操作支持幂等
8. **分页规范**：统一使用 page/pageSize 参数

---

**记住核心原则**：简洁、清晰、语义化、智能体友好。
