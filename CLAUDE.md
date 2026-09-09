# 标书编写智能体（TenderForge）— AI 协作纲领

产品名 **标书编写智能体**，产品码 `tenderforge`，仓 `vx-agent-tenderforge`，
域名 `tenderforge.vxture.com`。组织登记：L3 行业智能体 #5，
端口子块 4050–4059（prod 4050 / beta 4051），主机 vx-worker-02，
stack_root `/srv/md0/tenderforge`。

> **别和 `bidproposal` 搞混。** 组织里另有一个「标书方案智能体 /
> Proposal Writing Agent」（产品码 `bidproposal`，仓 `vx-agent-bidproposal`），
> 那是 Ruyin 桌面端的云端能力面，从 vxtpl 复制而来的 Node 服务，与本仓
> 是两个产品。本仓一度叫 `vx-agent-bid`，与那条线撞名，2026-09-10 改名。
> 平台仓 issue #198 的正文里还留着指向旧仓名的链接，那是过期的。

## 先读什么

1. **`AGENTS.md`** —— 本仓的工程纲领（唯一权威，本文件不重述它的内容）
2. **`docs/30-design/10-detailed-design.md`** —— 唯一现行详细设计；
   §12 是**已登记的标准偏离**清单，动之前先看那里有没有记过
3. **`docs/50-deployment/10-deployment-plan.md`** —— 部署规划与三级密钥划分

## 三条最容易踩的

**端口不在仓内文档里取。** 取号唯一源是组织端口登记表（需要登录，
coding agent 读不到）。仓内只允许出现**回退默认值**，且必须等于登记表里的号
（端口登记表 R2/R3）。本仓的值是 `APP_PUBLISH_PORT=4050`。想加新服务的端口，
先去登记表在 4052–4059 里占号，再写代码——反过来做是 R1 明令禁止的。

**compose 的 `environment:` 是白名单。** 不在那里列出的变量，写进 `.env`
完全没有反应：服务照常起来、照常用默认值、没有任何报错。
`scripts/guardrails/check_env_example.py` 在 CI 里守着三方一致
（`.env.example` ↔ compose ↔ `application.yml`）。

**部署态拒绝以替身启动。** 四条平台通道（C1 身份 / C1b 换票 / C2 权益 /
C3 用量与开通）与 Atlas 模型出口都有同一道闸门：`DEPLOY_STAGE` 是部署态
且配置缺失时**抛异常拒绝启动**，而不是降级运行。这是有意的——带着编造的
身份、编造的权益、不入账的用量跑起来，界面一切正常，月底才看得见缺口。
显式降级的开关是 `ALLOW_MOCK_ON_DEPLOY=true`，它会在 `/api/status` 自报。

## 验证的纪律

改完不等于验过。本仓反复出现过的一类问题是**测试涨了但不成立**：
断言写在了永远不会被执行到的分支上，或者被测对象的默认返回值恰好让断言通过。
判据只有一条——**故意把生产代码弄坏，确认那条测试会红**。
`docs/30-design/10-detailed-design.md` 里多处记着这类反证的具体做法。
