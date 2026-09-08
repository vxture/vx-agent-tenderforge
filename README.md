# TenderAgent

TenderAgent 是面向投标文件编制人员的本地智能工作台。系统覆盖招标文件解析、解读冻结、
三级目录规划、长篇正文生成与编辑、成稿审查、DOCX 排版交付、个人素材和账户管理。

## 快速启动

准备环境文件并在其中配置模型密钥：

```powershell
Copy-Item deploy/.env.example deploy/.env
# 编辑 deploy/.env，将 AI_MODEL_API_KEY 替换为真实 Key
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
docker compose --env-file deploy/.env -f deploy/docker-compose.yml ps
```

`.env.example` 默认展示阿里云百炼托管 DeepSeek 的华北 2（北京）配置；启动前必须把
`AI_MODEL_BASE_URL` 中的 `replace-with-workspace-id` 替换为实际业务空间 ID。其他提供方的
配置和请求方言见 [详细设计](doc/DETAILED_DESIGN.md)。

`deploy/.env` 已被 Git 忽略，禁止将其内容提交、粘贴到工单或写入日志。环境变量会进入容器
配置，可被具备 Docker 管理权限的人员通过容器检查命令读取。

- Web：<http://124.222.17.146:5274>
- Temporal UI：<http://127.0.0.1:8233>
- Java 健康检查：容器内 `http://api:8081/actuator/health`
- Python 健康检查：容器内 `http://ai:8000/health`

首次启动使用 `deploy/.env` 中的 `BOOTSTRAP_PLANNER_PASSWORD` 和
`BOOTSTRAP_ADMIN_PASSWORD` 创建或更新本地 `planner`、`admin` 账号。正式环境必须替换
示例密码和 `AI_SERVICE_INTERNAL_TOKEN`。

停止服务时不要删除 `mysql-data` 和 `private-files` 卷，它们分别保存业务数据和私有文件：

```powershell
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down
```

## 仓库入口

```text
frontend/project-name-web/       React 19 + Vite 用户界面
backend/project-name-java/       Java 25 + Spring Boot 业务 API 与 Temporal Worker
backend/project-name-python/     FastAPI 文档解析、AI 契约和 DOCX 渲染服务
deploy/                          当前 Docker Compose 部署
scripts/qa/                      核心 AI 与端到端验收脚本
doc/DETAILED_DESIGN.md           当前系统唯一详细设计
```

系统范围、路由、接口、状态机、数据表、任务队列、配置和接手指南统一维护在
[详细设计](doc/DETAILED_DESIGN.md)。代码变更必须同步更新该文档，不在仓库长期保留提案、
任务清单或历史过程文档。

## 本地验证

```powershell
Set-Location frontend/project-name-web
pnpm install
pnpm test
pnpm exec eslint . --no-cache
pnpm build

Set-Location ../../backend/project-name-python
pytest
ruff check .
mypy .

Set-Location ../project-name-java
mvn test
```

Java 构建必须使用 JDK 25。完整环境可通过 `docker compose ... config` 校验部署配置。
