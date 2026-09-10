# TenderForge 上线部署规划

对照基准产品 `vx-agent-vxtpl` 的部署链梳理，产出本产品的资源清单、三级密钥
与变量划分、以及执行顺序。

**这是规划，不是已完成状态。** 每一条打勾的是已核实存在的事实，未打勾的是缺口。

---

## 1. 现状核实

| 项 | 状态 |
| --- | --- |
| 远端仓库 | `github.com/vxture/vx-agent-tenderforge`，`main` 停在初始提交 `6759760` |
| 本地未推送 | `feat/platform-integration` 上 **23 个提交** |
| `.github/workflows/` | **不存在**（基准仓有 8 个工作流） |
| `deploy/deploy.sh` | **不存在**（基准仓有 159 行的宿主机生命周期脚本） |
| `.env.example` | 存在，但**比 `docker-compose.yml` 少 36 个键** |
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
| 有状态服务 | Postgres + Redis | **PostgreSQL 18 + Temporal**（含 `auto-setup` 与一次性 `temporal-db-init`） |
| DB 结构变更 | 独立 `db-init.yml`，**绝不走部署链** | **同左**（2026-09-10 整改完成，见详细设计 §13b） |
| 构建期密钥 | `NODE_AUTH_TOKEN`（CI 环境变量） | 前端 Dockerfile 用 **BuildKit secret** `github_packages_token` |
| 持久化 | bind mount 到 `${DATA_DIR}`（= `<stack_root>/data`） | **同左**：`data/postgres` + `data/private`（招标原件与导出的 DOCX） |
| 长任务 | 无 | Temporal 工作流，正文生成可跑数分钟 |

### 2.1 三镜像带来的改动

`build.yml` 要构建并推送三个镜像，`deploy.sh` 要拉三个。tag 必须**同一个 SHA**：
三个镜像各自按分支最新构建会让 api 与 web 版本错开，而那种错开的表现是
前端调一个后端还没有的接口——404，没有任何一侧报错说"版本不匹配"。

建议：`ghcr.io/vxture/tenderforge-{api,ai,web}:sha-<short>`，三者同 tag 同批推送。

### 2.2 DB 结构变更路径（**已整改**）

2026-09-10 之前本产品是 Flyway 在 api 启动时自动跑迁移，与治理规范
「常规部署链不跑 migration/seed」冲突。现已改成规范要求的形态：

* DDL 单一权威 = `deploy/database/ddl/`（`00_baseline` + `97_service_role`
  + `98_column_locks` + `incr/`），手写、create-once
* 施加通道 = `db-init.yml`（`confirm=yes` + `expected_sha` + 生产环境审批门）
* 应用以最小权限角色连库，**连 CREATE 的权限都没有**——就算有人把 Flyway
  加回来，建表也会被库直接拒绝。配置可以被改错，权限不会

整改过程与暴露出的四个问题记在详细设计 §13b。

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

### 3.2 仓库级（`vx-agent-tenderforge`）

治理规范 §3 把仓库级限定为「仓库专属的**公开标识**」——不是凭证，不是主机信息。

| 类型 | 名称 | 备注 |
| --- | --- | --- |
| var | `ALIYUN_ACR_NAMESPACE` | **按实际 ACR 取**，不要想当然写 `vxture`；错的 namespace 表现为 `pull access denied` |
| secret（Dependabot 命名空间） | `VXTURE_PACKAGES_READ_TOKEN` | **classic PAT**，只要 `read:packages` |

`VXTURE_PACKAGES_READ_TOKEN` 那条值得单独强调：**Dependabot 与 Actions 是两个
互不可见的密钥命名空间**，而 GitHub Packages 的 npm 源**不接受细粒度 token**。
漏配的表现是 npm 侧依赖更新永久且安静地失败——`github-actions` 照常出 PR，
`npm` 再也不出，而没有任何地方报告「坏了」。加完之后要去
Insights → Dependency graph → Dependabot 手动跑一次 "Check for updates"，
确认日志里没有 401。

### 3.3 环境级（`production` Environment，带必需审批人）

治理规范 §6：**每个部署目标一个环境**，各自携带本目标的主机信息。
同一个 deploy job 靠 `environment: <route>` 路由到正确主机。

| 类型 | 名称 | 备注 |
| --- | --- | --- |
| secret | `DEPLOY_HOST` | 目标主机的 tailnet 名 |
| secret | `DEPLOY_USER` | |
| secret | `DEPLOY_PORT` | |
| secret | `DEPLOY_DIR` | **必须是精确的 stack 目录**——含 compose 与 `.env` 的<b>那一层</b>。差一级的表现是镜像能拉、compose 找不到 env_file 而失败 |
| secret | `DEPLOY_SSH_KEY`（+ 可选 `_PASSPHRASE`） | |
| secret | `DEPLOY_KNOWN_HOSTS` | **必填**。连接动作对空 known_hosts **fail-closed**，拒绝 `ssh-keyscan` 的 TOFU 回落 |
| secret | `ENV_FILE_BASE64` | 宿主机 `.env` 的 base64 |

**必需审批人必须配。** 零保护 = tag 一推就直接部署、不停等审批。
配 reviewers 可以用 `gh api --method PUT repos/{o}/{r}/environments/{env}`；
但**部署本身的 Approve 是所有者手点**，不自审。

`ENV_FILE_BASE64` **只在目标文件不存在时写入**——重新裁这个密钥不会更新正在
运行的主机，漂移是静默的。

**迁仓/新仓不继承**：`DEPLOY_*`（环境级）与 `NAMESPACE`（仓库级）不会带到新仓，
必须重建；而组织级共享凭证（ACR / tailscale / npm）在组织配一次、
把本仓加入共享名单即可。

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
- webhook 投递地址：`https://tenderforge.vxture.com/api/platform/provisioning/webhook`
- 需要授权的 Atlas endpoint：见 `atlas_endpoints.required_endpoint_codes()`；
  授权到位前保持 `ATLAS_USE_DEDICATED_ENDPOINTS=false`，全部走 `chat/default`

---

## 4b. SCA 闸门的现状（实测，**不是推测**）

治理规范 §9 要求 `audit` 是 `main` 的硬阻断检查。本地用 CI 里同一条命令实跑了
osv-scanner 2.4.0，先后发现两类问题：**门本身是瞎的**，以及**门后面确实有东西**。

### 4b.1 门是瞎的（已修）

osv-scanner 直接对着仓内清单文件扫，三个生态的成色完全不同：

| 生态 | 直接扫清单的结果 | 问题 |
| --- | --- | --- |
| npm | `pnpm-lock.yaml` 478 个包 | 无——锁文件本身就是完整解析结果 |
| Maven | 「Scanned pom.xml 并找到 6 个包」+ 一行 `failed resolution` | 传递树**一个都没扫**，报告却是绿的 |
| PyPI | 6 个包受影响、49 条 | 传递依赖**版本是错的**，多报 22 条假阳性 |

**Maven** 那条的真实原因是 osv-scanner 自己去 `repo.maven.apache.org` 拉
`spring-boot-starter-parent:3.5.6` 时拿到 **HTTP 429**（限流，不是网络不通：
同一容器里 curl 拉同一个 URL 返回 200）。于是只有各模块直接声明的 25 个依赖
被扫，Spring Boot 拉进来的整棵树一个没扫。第一版那条「三个生态都扫到」的检查
只 grep 文件名是否出现，会一路放行这种情况。

**PyPI** 那条更隐蔽：它确实解析了传递依赖，但解析出 `idna 3.9.0`、
`pygments 2.9.0`，而真正装进镜像的是 `idna 3.19`、`pygments 2.21.0`。
多出来的 22 条全是不存在的问题，而且按它说的去「修」是无效动作——版本本来
就比它以为的新。一个会喊狼来了的闸门等于没有闸门。

**修法**：两边都不用 osv-scanner 自己的解析器，改由**各自生态的解析器**先把
树解析出来，再扫解析结果（`scripts/ci/sca-scan.sh`）：

* Maven → `cyclonedx-maven-plugin:makeAggregateBom`，25 → **112** 个包
* PyPI  → `uv pip compile`，15 → **42** 个包，版本与镜像里装的一致
* npm   → 直接扫 `pnpm-lock.yaml`，478 个包

三趟扫描是显式指定文件的，准确，但代价是仓里新出现一个 `go.mod` 不会有人提醒。
所以额外跑一趟递归**盘点**，把发现到的清单集合与脚本里的 `EXPECTED_MANIFESTS`
对账，多一个少一个都红。

六条断言全部做过反证（故意弄坏、确认会红）：SBOM 缺失、SBOM 退化到只剩直接
依赖、Python 解析产物缺失、清单多一个、清单少一个、把扫描对象指回未解析的
`pom.xml`。脚本还刻意把「门是瞎的」排在「门拦下了东西」之前报——一个没扫全的
绿灯比一个红灯坏得多。

（另有两个实测出来的坑：osv-scanner 靠**文件名**挑提取器，SBOM 叫
`bom-bumped.json` 就会得到 `could not determine extractor suitable to this file`
然后静默跳过；`--experimental-disable-plugins` 传一个不存在的插件名**不报错**，
所以那两个 flag 只用在不计退出码的盘点趟上，真正的结论不依赖它们生效。）

### 4b.2 门后面确实有东西（已清零）

门修好之后先看到的存量，三个生态一共 **142 条**：

| 生态 | 包数 | 受影响包 | 条数 | 最高 |
| --- | --- | --- | --- | --- |
| npm | 478 | 21 | 59 | High ×37 |
| Maven | 112 | 17 | 56 | **Critical ×7** |
| PyPI | 42 | 3 | 27 | High ×17 |

Maven 那 56 条此前**完全不可见**。整顿之后三边全部归零，`audit` 现在是绿的
（三套测试也全绿：Java 365、Python 125、前端 28+11）。

**Maven** — 抬 `spring-boot-starter-parent` 3.5.6 → 3.5.16 与
`temporal.version` 1.27.0 → 1.38.0 解决绝大多数（Boot BOM 连带抬了
tomcat / spring / jackson / logback / micrometer；Temporal 抬掉了
`protobuf-java` 3.21.7 与 `grpc-*` 1.54.1）。剩下 4 项在 `<properties>` 里
**显式压过 Boot BOM**——这些是安全下限，不是选型，Boot 升级后要逐个回头看，
钉的版本追上了就删掉，留着一个比 BOM 低的值反而会把版本按回去：

| 属性 | 值 | 为什么 |
| --- | --- | --- |
| `tomcat.version` | 10.1.59 | 3 条，含 9.8/9.1/9.1；Boot 3.5.16 钉 10.1.55 |
| `jackson-bom.version` | 2.21.5 | 3 条；Boot 3.5.16 钉 2.21.4 |
| `commons-lang3.version` | 3.18.0 | GHSA-j288-q9x7-2f5v；Boot 仍钉 3.17.0 |
| `log4j2.version` | 2.25.5 | GHSA-qv9r-c865-cp47；Boot 仍钉 2.24.3 |

> **一个只有实际构建才能发现的坑**：告警写的 tomcat 修复版是 **10.1.58**，
> 而 Apache **跳过了这个版本号**——Central 上 10.1.57 之后直接是 10.1.59。
> 照着告警写，SBOM 那一步（不下载 jar）照样生成、照样报「0 个漏洞」，
> 而 `mvn verify` 会以 `was not found in repo.maven.apache.org` 失败。
> 抄告警里的版本号是不够的，得跑一次真构建。

**npm** — 按规范 §9 分三类，**不是抑制**：

* 直接依赖抬 `package.json` 的 caret 下限：`react-router` ^7.18.2、
  `vite` ^7.3.5、`vitest` ^4.1.11（唯一跨 major 的一个，`@vitest/coverage-v8`
  必须跟着走）。
* 纯传递依赖走根 `pnpm.overrides`。树里同时存在多个 major 的必须用 `pkg@N`
  选择器**分别定**，一个笼统的 override 会把不该动的那一支也按过去：
  `minimatch@3`/`minimatch@9`（树里还有个 10.2.6 不能碰）、
  `brace-expansion@1`/`brace-expansion@2`（还有个 5.0.9）、`ajv@6`、`glob@10`。
  其余单版本的直接写：`@babel/core`、`@humanfs/node`、
  `baseline-browser-mapping`、`browserslist`、`flatted`、`js-yaml`、`nanoid`、
  `picomatch`、`postcss`、`rollup`。
* **peer 精确同版的一族只能整体精确钉**：tiptap 全家的 peer 要求是
  `@tiptap/pm@<完全相同的版本>`。原来 `@tiptap/pm` 是精确钉、其余是 caret，
  抬下限后 caret 漂到 3.31.3 而 pm 停在 3.30.5，`pnpm install` 直接报
  unmet peer。五个包一起精确钉到 3.31.3。

（`pnpm.overrides` 是 JSON，写不进注释——每一条的来由与摘除条件就记在这张表
和上面这段里。删 override 的条件是：上游那个直接依赖自己抬到了修复版以上。）

**PyPI** — 三个都是直接钉的：`Pillow` 11.2.1 → 12.3.0、
`python-multipart` 0.0.20 → 0.0.31、`pytest` 8.4.0 → 9.0.3。

## 4c. Temporal SDK 1.38.0 对服务端 1.27.2（本地整栈实测）

SDK 与 server 是**两条独立的版本线**——1.38 和 1.27 不是同一个数字，不存在
「差了 11 个版本」这回事。server 1.27.2 距当时最新的 1.29.x 只差两个小版本。

单元测试覆盖不到这一层（集成测试被 `-DskipITs` 跳过），所以在本地整栈上
实跑了一遍：换掉 api 与 worker 的镜像，**Temporal / MySQL / AI 都不动**，
仍然是原来那个 1.27.2 的服务端。

先确认镜像里装的确实是新版（防构建缓存骗人）：`temporal-sdk 1.38.0`、
`grpc-netty-shaded 1.76.0`、`protobuf-java 3.25.8`、`tomcat-embed-core 10.1.59`、
`jackson-databind 2.21.5`、`spring-core 6.2.19`、`commons-lang3 3.18.0`、
`log4j-api 2.25.5`。

**worker 侧**：四个任务队列的 poller 全部在服务端可见（identity 与新容器的
hostname 对得上）。用同一条探针工作流对比新旧两版的事件序列，**完全一致**：

```
WorkflowExecutionStarted → WorkflowTaskScheduled/Started/Completed
→ ActivityTaskScheduled/Started/Failed        （BidLayout_Render，探针 bid 不存在）
→ WorkflowTaskScheduled/Started/Completed
→ ActivityTaskScheduled/Started/Completed     （BidLayout_Fail 补偿）
→ WorkflowTaskScheduled/Started/Completed → WorkflowExecutionFailed
```

这条路径覆盖的比happy path 还宽：工作流任务收发、活动派发、活动重试
（日志里 attempt=1/2/3 到 `RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED`）、
失败编码、补偿活动、工作流失败上报。

**客户端侧**：产品的业务端点有前置条件（`BID_CONTENT_NOT_FROZEN`），起不到
工作流，所以另写了一个只依赖 SDK 1.38 的独立探针直接发
`StartWorkflowExecution`——能力协商（`GetSystemInfo`）通过、start 被接受、
长轮询取结果正常。这条路是产品每次起工作流都要走的，值得单独验。

两个容器全程 **0 条** `UNIMPLEMENTED` / `FAILED_PRECONDITION` /
`INVALID_ARGUMENT`。结论：这个组合可用，服务端**不需要**跟着升。

> **一个会骗人的运维细节**：SDK 1.38 把
> `Created WorkflowServiceStubs...` 与 `Poller - start: Poller{...}` 这些
> INFO 日志**降级了**。1.27 时代靠 grep 这两行确认 worker 起来的做法，
> 在新版上会得到「一行都没有」，看起来像 worker 根本没连上——而它其实
> 一直在轮询。判断依据要换成服务端的
> `tctl taskqueue describe --taskqueue <队列>` 有没有 poller。

---

## 5. 待决策清单

1. **端口** — 必须向组织端口登记表申请。仓内现有的 `5274` 是遗留值，要删。
2. **DB 结构变更路径** — §2.2 三选一，建议方案 3。
3. **部署主机与栈根目录** — 基准产品在 `vx-worker-02` / `/srv/md0/vxtpl`；
   本产品多两个有状态服务，磁盘与内存需求更高，需要确认落在哪台。
4. **是否要 beta 环境** — 基准产品是 prod only（ADR-002）。本产品有 Temporal
   与数据库迁移，一个 beta 环境的价值可能更高，代价是第二套宿主机资源。
5. **三镜像的 tag 与推送策略** — 建议同 SHA 同批。
~~**Maven 依赖树在 CI 里怎么解析**~~ —— 已解决，见 §4b.1。
~~**依赖告警的整顿窗口**~~ —— 已做完，142 → 0，见 §4b.2。
~~**Temporal SDK 1.38.0 与服务端 1.27.2 的配合**~~ —— 已在本地整栈实测，见 §4c。
~~**端口**~~ —— owner 2026-09-10 分配 4050/4051（L3 #5），两张登记册已改。
~~**部署主机与栈根目录**~~ —— worker-02 / `/srv/md0/tenderforge`。
~~**DB 结构变更路径**~~ —— owner 决定本轮不动，已登记为 TD-001
（详细设计 §13b），连同 TD-002/003 一起等库层整改排期。
~~**三镜像的 tag 与推送策略**~~ —— 同 SHA 同批，`build.yml` 已实现并登记为 TD-004。

**仍未决**：是否要 beta 环境。端口子块里 4051 已经为它预留，开不开是资源决定。

---

## 6. 执行顺序

**阶段一：把仓库准备好（不依赖任何外部输入）**
1. 补齐 `.env.example` 的 36 个键，删掉 restate 的端口。
2. 写 `.github/workflows/ci.yml`（Java + Python + 前端三套测试与覆盖率）。
3. 首次推送 `main`，让 CI 跑一次产出必需检查的 context。
4. 开启 secret scanning + push protection（治理规范 §2 的第一层）。
5. ~~整顿 SCA 告警~~ —— 已完成（§4b.2），`audit` 现在是绿的，可以转正为
   必需检查了。这一步必须在应用 ruleset 之前，否则 `audit` 一成为必需检查、
   所有 PR 立刻卡死。
6. 应用分支 ruleset（**顺序不能反**：空仓上先加限制性 ruleset 会挡住首次导入）。

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
