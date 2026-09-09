# TenderForge 上线部署规划

对照基准产品 `vx-agent-vxtpl` 的部署链梳理，产出本产品的资源清单、三级密钥
与变量划分、以及执行顺序。

**这是规划，不是已完成状态。** 每一条打勾的是已核实存在的事实，未打勾的是缺口。

---

## 1. 现状核实

| 项 | 状态 |
| --- | --- |
| 远端仓库 | `github.com/vxture/vx-agent-bid`，`main` 停在初始提交 `6759760` |
| 本地未推送 | `feat/platform-integration` 上 **23 个提交** |
| `.github/workflows/` | **不存在**（基准仓有 8 个工作流） |
| `deploy/deploy.sh` | **不存在**（基准仓有 159 行的宿主机生命周期脚本） |
| `deploy/.env.example` | 存在，但**比 `docker-compose.yml` 少 36 个键** |
| 分支保护 / ruleset | 未应用 |
| GitHub Environment | 未创建 |

`.env.example` 那一条是最容易致命的：基准仓的规矩是它作为**权威键表**，
`ENV_FILE_BASE64` 从它起手。照当前这份上线，整个平台接入（OIDC、C2、C3、Atlas）
的配置一个都不会写进宿主机 `.env`——服务会因为阶段守卫拒绝启动，
那是好事；但 Temporal 的库口令、上传上限这些没有守卫的项会静默走默认值。

**缺失的 36 个键**：
`ALLOW_MOCK_ON_DEPLOY` `APP_VERSION` `ATLAS_API_URL` `ATLAS_TIMEOUT_SECONDS`
`ATLAS_USE_DEDICATED_ENDPOINTS` `BID_GENERATION_MAX_CONCURRENCY` `CONSOLE_BASE_URL`
`DATABASE_POOL_SIZE` `DEPLOY_STAGE` `DOCUMENT_SERVICE_BASE_URL`
`DOCUMENT_SERVICE_ENABLED` `DOCUMENT_SERVICE_TIMEOUT_SECONDS` `MOCK_BUNDLED`
`MOCK_STATUS` `MOCK_TIER` `MOCK_USAGE_GATED` `OIDC_CLIENT_ID` `OIDC_CLIENT_SECRET`
`OIDC_ISSUER` `OIDC_POST_LOGOUT_REDIRECT_URI` `OIDC_REDIRECT_URI` `OIDC_RP_ENABLED`
`OIDC_SCOPES` `PLATFORM_API_URL` `PLATFORM_INTERNAL_AUTH_TOKEN`
`RP_SESSION_SECURE_COOKIE` `RP_SESSION_TTL` `TEMPORAL_ADDRESS` `TEMPORAL_DB_PASSWORD`
`TEMPORAL_ENABLED` `TENDERFORGE_PROVISION_WEBHOOK_SECRET`
`TENDERFORGE_PROVISION_WEBHOOK_SECRET_NEXT` `UPLOAD_MAX_FILE_SIZE`
`UPLOAD_MAX_REQUEST_SIZE` `USAGE_FLUSH_BATCH_SIZE` `USAGE_FLUSH_INTERVAL_MS`

`.env.example` 里还有一个**必须删掉**的东西：`WEB_PORT=5274` 与
`CORS_ALLOWED_ORIGINS=http://124.222.17.146:5274`。端口由组织端口登记表分配，
仓内restate 一个端口就成了第二个来源，而第二个来源就是会过期的那个。

---

## 2. 与基准产品的形态差异

**不能照抄 vxtpl 的工作流**，差异在下面这几条，每一条都改变部署链的形状：

| | vxtpl | TenderForge |
| --- | --- | --- |
| 镜像数 | 1（Next.js 单体） | **3**：java、python、web |
| 有状态服务 | Postgres + Redis | **MySQL 8.4 + Temporal**（含 `auto-setup` 与一次性 `temporal-db-init`） |
| DB 结构变更 | 独立 `db-init.yml`，**绝不走部署链** | 应用启动时 **Flyway** 自动迁移（V1–V30） |
| 构建期密钥 | `NODE_AUTH_TOKEN`（CI 环境变量） | 前端 Dockerfile 用 **BuildKit secret** `github_packages_token` |
| 持久化卷 | Postgres 数据 | `mysql-data` + **`private-files`**（招标原件与导出的 DOCX） |
| 长任务 | 无 | Temporal 工作流，正文生成可跑数分钟 |

### 2.1 三镜像带来的改动

`build.yml` 要构建并推送三个镜像，`deploy.sh` 要拉三个。tag 必须**同一个 SHA**：
三个镜像各自按分支最新构建会让 api 与 web 版本错开，而那种错开的表现是
前端调一个后端还没有的接口——404，没有任何一侧报错说"版本不匹配"。

建议：`ghcr.io/vxture/tenderforge-{api,ai,web}:sha-<short>`，三者同 tag 同批推送。

### 2.2 Flyway 与 db-init 的冲突（**需决策**）

基准仓的规矩写得很硬：「DB 结构变更走 `db-init.yml`，绝不走部署链」，
理由是一次失败的结构变更不该由一次常规发布触发。

本产品目前是 Flyway 在 api 启动时自动跑迁移。两者不能同时成立。三个选项：

1. **保持 Flyway 自动迁移**。优点：迁移与代码同批次，不可能漏；本产品已有 30 个
   迁移在这条路上跑通过。缺点：与基准仓纪律不一致；MySQL 没有 DDL 事务，
   一次失败的迁移会留下 `success=0` 的行，之后每次启动都失败——
   这个坑本轮已经踩过两次，需要人工删行才能恢复。
2. **改成 `flyway.enabled=false` + 独立工作流**。与基准仓一致，但要新写
   迁移执行链路，且失去"迁移与代码同批"的保证。
3. **折中**：保留 Flyway，但在部署链之外加一个 `db-migrate.yml` 用于**预检**
   （`flyway validate` / `info`），发布前先看清楚这一批会跑哪些迁移。

我的建议是 **3**：不改变已验证的执行路径，同时把"这次发布会动数据库结构吗"
变成发布前可见的事实，而不是发布后从日志里读。

### 2.3 Temporal 是第二个有状态服务

`temporal-db-init` 是一次性 job，`temporal` 用 `auto-setup` 镜像。首次部署要保证
它在 api 之前就绪；`init-temporal.sh` 的换行符问题本轮已经用 `.gitattributes` 修掉
（CRLF 会让容器内 `sh` 报 `set: -: invalid option`）。

---

## 3. 三级密钥与变量划分

### 3.1 组织级（`vxture` org，多产品共享，基准仓已在用）

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| secret | `NODE_AUTH_TOKEN` | CI 解析 `@vxture/*` 私有包 |
| secret | `ALIYUN_ACR_USERNAME` / `ALIYUN_ACR_PASSWORD` | 镜像 ACR 镜像源 |
| secret | `TAILSCALE_OAUTH_CLIENT_ID` / `_SECRET` | CI 加入 tailnet 后 SSH |
| var | `ALIYUN_ACR_REGISTRY` / `ALIYUN_ACR_NAMESPACE` | ACR 地址 |
| var | `VXTURE_NPM_REGISTRY` | 私有 npm 源 |
| var | `TAILSCALE_OAUTH_CLIENT_TAG` | tailnet 节点标签 |

**本产品直接复用，不新增组织级条目。**

### 3.2 仓库级（`vx-agent-bid`）

| 类型 | 名称 | 值来源 | 备注 |
| --- | --- | --- | --- |
| secret | `DEPLOY_HOST` | 运维 | 部署主机的 tailnet 名 |
| secret | `DEPLOY_USER` | 运维 | |
| secret | `DEPLOY_PORT` | 运维 | 通常 22 |
| secret | `DEPLOY_DIR` | 运维 | 栈根目录，建议 `/srv/md0/tenderforge` |
| secret | `DEPLOY_SSH_KEY` | **所有者** | 授权在目标主机上的私钥 |
| secret | `DEPLOY_SSH_KEY_PASSPHRASE` | 所有者 | 可选 |
| secret | `DEPLOY_KNOWN_HOSTS` | 所有者 | `ssh-keyscan`，**fail-closed，无 TOFU 兜底** |
| secret（Dependabot 命名空间） | `VXTURE_PACKAGES_READ_TOKEN` | 所有者 | **classic PAT**，只要 `read:packages`；细粒度 token 不被 GitHub Packages npm 源接受 |

`VXTURE_PACKAGES_READ_TOKEN` 那条值得单独强调：**Dependabot 与 Actions 是两个
互不可见的密钥命名空间**。漏配的表现是 npm 侧依赖更新永久且安静地失败——
`github-actions` 照常出 PR，`npm` 再也不出，而没有任何地方报告"坏了"。
加完之后要去 Insights → Dependency graph → Dependabot 手动跑一次
"Check for updates"，确认日志里没有 401。

### 3.3 环境级（`production` Environment，带必需审批人）

| 类型 | 名称 | 备注 |
| --- | --- | --- |
| secret | `ENV_FILE_BASE64` | 宿主机 `.env` 的 base64。**只在目标文件不存在时写入**——重新裁这个密钥不会更新正在运行的主机，漂移是静默的 |

环境级只放这一个，是刻意的：宿主机 `.env` 里装着 OIDC client secret、
平台内部令牌、webhook 密钥、数据库口令——它们必须被审批门挡一道。

### 3.4 宿主机 `.env`（不进 GitHub，由 `ENV_FILE_BASE64` 投递）

按来源分三类：

**a. 本产品自持（部署时生成，运维保管）**
`MYSQL_PASSWORD` `MYSQL_ROOT_PASSWORD` `TEMPORAL_DB_PASSWORD`
`AI_SERVICE_INTERNAL_TOKEN` `BOOTSTRAP_ADMIN_PASSWORD` `BOOTSTRAP_PLANNER_PASSWORD`

**b. 平台线提供（见 §4）**
`OIDC_*` `PLATFORM_API_URL` `PLATFORM_INTERNAL_AUTH_TOKEN`
`TENDERFORGE_PROVISION_WEBHOOK_SECRET(_NEXT)` `ATLAS_API_URL` `CONSOLE_BASE_URL`

**c. 部署形态（运维决定）**
`DEPLOY_STAGE=production` `APP_VERSION` `ALLOW_MOCK_ON_DEPLOY=false`
`APP_PUBLISH_PORT`（**向端口登记表申请**）`CORS_ALLOWED_ORIGINS`
`RP_SESSION_SECURE_COOKIE=true` `TEMPORAL_ENABLED=true`

`ALLOW_MOCK_ON_DEPLOY` 必须是 `false`。它是四个通道的显式降级开关；置真会让
产品带着编造的身份、编造的权益、不入账的用量和不入账的推理跑起来，
而界面一切正常。

---

## 4. 平台线依赖（本产品无法自行解决）

这些不到位时，代码已经就绪但功能不成立：

| 依赖 | 缺了会怎样 |
| --- | --- |
| `tenderforge` 产品登记 + OIDC client 对 | 登录不可用；**所有 S2S 调用铸不出票**（一对凭据同时解锁两者） |
| `PLATFORM_API_URL` + 内部令牌 | C2 权益与 C3 用量上报落到替身，部署态拒绝启动 |
| `TENDERFORGE_PROVISION_WEBHOOK_SECRET` | 开通/停用事件全部被拒；未配密钥时接收端一律拒绝 |
| Atlas `ATLAS_API_URL` + **endpoint 授权** | 缺授权时每次调用 `403 NOT_ENTITLED`，与令牌是否有效无关 |
| 工作空间覆盖 | 铸币校验的是**调用方**是否覆盖该工作空间 |

**一条耦合关系必须写在前面**：Atlas 铸票需要真实 workspace，而本地口令登录的
租户是 `local:<用户id>`，平台那边不存在。所以 **Atlas 迁移在 C1 身份切换之前
无法真正生效**——这两件事不能分别排期。

需要交给平台线的两个具体值：
- webhook 投递地址：`https://tender.vxture.com/api/platform/provisioning/webhook`
- 需要授权的 Atlas endpoint：见 `atlas_endpoints.required_endpoint_codes()`；
  授权到位前保持 `ATLAS_USE_DEDICATED_ENDPOINTS=false`，全部走 `chat/default`

---

## 5. 待决策清单

1. **端口** — 必须向组织端口登记表申请。仓内现有的 `5274` 是遗留值，要删。
2. **DB 结构变更路径** — §2.2 三选一，建议方案 3。
3. **部署主机与栈根目录** — 基准产品在 `vx-worker-02` / `/srv/md0/vxtpl`；
   本产品多两个有状态服务，磁盘与内存需求更高，需要确认落在哪台。
4. **是否要 beta 环境** — 基准产品是 prod only（ADR-002）。本产品有 Temporal
   与数据库迁移，一个 beta 环境的价值可能更高，代价是第二套宿主机资源。
5. **三镜像的 tag 与推送策略** — 建议同 SHA 同批。

---

## 6. 执行顺序

**阶段一：把仓库准备好（不依赖任何外部输入）**
1. 补齐 `deploy/.env.example` 的 36 个键，删掉 restate 的端口。
2. 写 `.github/workflows/ci.yml`（Java + Python + 前端三套测试与覆盖率）。
3. 首次推送 `main`，让 CI 跑一次产出必需检查的 context。
4. 应用分支 ruleset（**顺序不能反**：空仓上先加限制性 ruleset 会挡住首次导入）。
5. 开启 secret scanning + push protection。

**阶段二：部署链（依赖运维给主机信息）**
6. 写 `build.yml`（三镜像，GHCR 主 + ACR 备）与 `deploy/deploy.sh`。
7. 写 `deploy.yml`（tag `v*.*.*` → `production` 环境）与 `rollback.yml`。
8. 配置仓库级 secrets（§3.2）与 `production` 环境 + 审批人。

**阶段三：首次上线（依赖平台线）**
9. 平台线交付 §4 的凭据，运维裁 `ENV_FILE_BASE64`。
10. 目标主机建栈根目录，确认 GHCR/ACR 登录。
11. 打 tag、审批、观察 `/api/status`——**四条通道应全部 `active`，
    `degraded` 应为 `false`**。任何一条是 `mock` 或 `direct` 都说明有配置没到位。

阶段一现在就能做，且不阻塞任何人。
