# 联络函：tenderforge 改用四条通用路由，撤回六个专属 endpoint 的请求

- Stamp: 2609142131（2026-09-14 21:31）
- From: tenderforge 线
- To: Atlas 线（抄送平台线）
- Status: informational（取代 [10-2609111009](./10-2609111009-tenderforge-atlas-endpoint-request.md)）

## 一句话

owner 2026-09-14 确认本产品已被授权四条通用路由：`chat/deterministic`、`chat/fast`、
`chat/default`、`chat/reasoning`。产品侧已按业务把全部 AI 调用分到这四条上，
**2026-09-11 那封请求登记六个 `chat/tenderforge-*` 专属 endpoint 的函就此撤回**，
Atlas 侧不需要为本产品新建任何 endpoint。

## 产品侧怎么分的

分档依据是直连时代逐条实测出来的 thinking / 温度 / 模型档位，不是按路由名字猜：
开 thinking 的走 reasoning；温度为 0 的走 deterministic；Quality 模型但关 thinking 的
走 default；其余走 fast。

| endpointCode | operation | 业务 |
| --- | --- | --- |
| `chat/deterministic` | `project_overview_source_selection` · `project_overview_extraction` · `technical_scoring_extraction` | 解读阶段的事实搬运；评分条款改一个分值标书就作废 |
| `chat/fast` | `outline_branch_expansion` · `chapter_drafting` | 量最大的两个环节；正文续写的调用次数占全流程绝大多数 |
| `chat/default` | `outline_skeleton_planning` · `section_revision` | 要质量，但**刻意不开 thinking**——补全预算要留给输出本身 |
| `chat/reasoning` | `bid_strategy_planning` · `branch_blueprint_planning` · `consistency_review` | 评分响应策略、分支蓝图、全文一致性审查：没有推理做不了 |

比 09-11 那封多了两个 operation（`bid_strategy_planning`、`branch_blueprint_planning`）：
当时的映射表漏了它们，是产品侧的疏漏，不影响 Atlas。

## 请 Atlas 线核对的只有一件事：四条路由挂的模型是否满足下面的下限

产品不再能按 operation 传参数，所以这些要求落在路由上。**不满足时没有错误码**，
只会让标书变差或偶发截断：

| 路由 | 必须 | 为什么 |
| --- | --- | --- |
| `chat/deterministic` | 温度 0、关 thinking、上下文 ≥ 64K | 概述抽取单次送入约 48,000 字符源正文 |
| `chat/fast` | 关 thinking、上下文 ≥ 32K | 正文续写单次输入约 22,000 字符 |
| `chat/default` | **关 thinking**、单次输出 ≥ 16384 token | 目录骨架实测 16384 是下限，更小会截断，截断即整个目录任务重跑 |
| `chat/reasoning` | 开 thinking、**上下文 ≥ 128K，500 页标书需 256K** | 一致性审查把全部章节摘录（每章 ≤16,000 字符）一起送入，随标书规模线性增长 |

四条路由都要能**稳定输出结构化 JSON**：请求体里没有 `response_format`，
产品侧靠宽容解码 + Pydantic 校验 + 一次修复兜底，模型这项能力不稳，成本直接翻倍。

`chat/fast` 上同时有温度 0.2 的目录展开和温度 0.4 的正文续写，建议按正文调。

## 怎么确认配对了

- `GET /v1/endpoints` 返回的四条 `state` 全为 `active`。
- 产品侧上线后，AI 服务启动日志会打出
  `模型出口：Atlas …（按任务分走 chat/default / chat/deterministic / chat/fast / chat/reasoning）`；
  任一条出现 `403 NOT_ENTITLED` 即授权与路由名不一致。
- 审查那一档单独验一次长输入——它是唯一可能撞上下文上限的，而撞上时的错误来自上游，
  读起来不像「上下文不够」。

## 产品侧随之退役的东西

`ATLAS_USE_DEDICATED_ENDPOINTS` 这个降级开关删除：它只为「专属 endpoint 等授权」那段
时期存在。路由不做成配置项——operation 到路由的对应只写在
`backend/project-name-python/czghagent_ai/services/atlas_endpoints.py` 一处，
路由挂哪个模型由运营改指向，不需要产品发版。
