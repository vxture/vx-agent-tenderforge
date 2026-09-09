# Agent 开发指南

本文件定义 TenderAgent 仓库的维护规则。现行产品与技术事实以
`docs/30-design/10-detailed-design.md` 为唯一来源。

## 项目结构

```text
backend/project-name-java/       Java 25，DDD + CQRS，6 个 Maven 模块
backend/project-name-python/     Python，FastAPI 文档/AI/排版服务
frontend/project-name-web/       React 19 + Vite + TypeScript
deploy/                          MySQL、Temporal、Java、Python、Nginx Compose 部署
scripts/qa/                      真实 AI 和浏览器端到端验收
docs/30-design/10-detailed-design.md           唯一现行详细设计
.claude/skills/                  当前工程与 Java 编码规范
```

目录名中的 `project-name` 是现有构建路径，修改前需同步 Dockerfile、Compose、Maven、脚本
和文档，不能只改目录名。

## 变更流程

1. 先读取 `docs/30-design/10-detailed-design.md` 和涉及模块的代码。
2. 新功能、跨模块重构先在任务中形成设计并确认边界，但不把提案、任务清单或过程记录
   长期提交到仓库。
3. 实现完成后同步更新 `docs/30-design/10-detailed-design.md` 中受影响的功能、接口、状态、数据、配置
   和运维说明；该文档只描述最终现状。
4. Bug、样式和配置修复可直接开发，但仍需更新受影响的现行设计。
5. 按风险运行测试，提交信息说明最终行为，不记录无关开发过程。

## 构建与测试

### 前端

```powershell
Set-Location frontend/project-name-web
pnpm test
pnpm exec eslint . --no-cache
pnpm build
pnpm dev -- --host 127.0.0.1 --port 5174 --strictPort
```

修改完成并重启前端前，先停止旧服务，复用原主机和端口并启用 `--strictPort`，禁止让 Vite
自动递增端口。

### Java

```powershell
Set-Location backend/project-name-java
mvn clean compile
mvn test
mvn clean package
mvn -pl project-name-start spring-boot:run
```

必须使用 JDK 25。数据库迁移位于
`project-name-start/src/main/resources/sql/V1__*.sql` 至 `V23__*.sql`；即使早期迁移含旧
产品表，也不得删除、改名或改写，存量数据库升级依赖完整校验链。新增变更只能追加迁移。

### Python

```powershell
Set-Location backend/project-name-python
pip install -r requirements.txt
pytest
ruff check .
mypy .
uvicorn main:app --reload --port 8000
```

### 部署

```powershell
docker compose config
docker compose up -d --build
```

不得删除 `mysql-data`、`private-files` 或本地 `deploy/dify/volumes` 遗留数据，除非用户明确
授权数据清理。

## 编码约束

- TypeScript 使用 2 空格、单引号、严格类型；Java/Python 使用 4 空格，行宽不超过 120。
- 导入顺序：标准/框架、第三方、内部模块、类型、样式；优先遵循现有 lint/format 配置。
- API 不直接暴露私有对象键、密钥和模型原始敏感内容；文件读取始终校验所有权。
- 写操作使用 `revision` 做乐观并发控制；长任务必须保持幂等、可重试和可观测。
- 复杂度目标：AI 代码圈复杂度不超过 7，函数不超过 50 行；超限时拆分职责。
- 所有 AI 新增代码包含文件级 ACAP 标记，关键业务方法说明前置条件、副作用和错误语义。
- 详细规范按需读取：
  `.claude/skills/ai-code-engineering-standard/SKILL.md`、
  `.claude/skills/backend-java-code-engineering/SKILL.md`。

## 文档约束

- `docs/30-design/10-detailed-design.md` 是唯一产品和技术详细设计，不新建平行规格、OpenSpec 或模块
  README。
- 根 `README.md` 只保留项目入口、启动、验证和详细设计链接。
- 设计必须描述当前代码实际行为，不保留迭代历史、方案比较、完成清单或未来承诺。
- 接口、路由、表结构、环境变量、工作流或部署拓扑变化时，文档与代码在同一变更中更新。
