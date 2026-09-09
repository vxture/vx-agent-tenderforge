---
name: AI_Code_Engineering_Standard
description: TenderAgent 全栈代码工程规范
version: 3.0
last_updated: 2026-08-14
---

# TenderAgent AI Code Engineering Standard

## 工作流程

1. 先读根目录 `AGENTS.md`、`docs/30-design/10-detailed-design.md` 和涉及模块代码。
2. 新功能或跨模块变更先在当前任务中完成设计，确认状态、接口、数据和失败语义；不在仓库
   长期保存提案、任务清单或过程记录。
3. 实现时遵循现有目录、领域端口、统一响应、错误码和测试模式，不引入平行框架。
4. 代码完成后同步更新 `docs/30-design/10-detailed-design.md` 的最终现状。
5. 运行受影响模块测试、静态检查、构建和 `git diff --check`。

## 分层与依赖

- 前端页面只通过 `src/api/modules` 访问 Java API，业务状态来自服务端。
- Java 依赖方向为 Web/Start -> Application -> Domain；Infrastructure 实现 Domain 端口。
- Python 只提供内部解析、AI 和文档契约，不向浏览器开放业务接口。
- 跨服务数据使用明确 DTO/Pydantic/record，不以未校验 Map 或自由文本替代稳定契约。
- 新数据库结构追加 Flyway 迁移，不修改 V1-V22。

## 类型、复杂度和错误

- TypeScript 启用严格类型，不新增 `any`；Java 使用泛型/record；Python 提供完整类型注解。
- AI 代码圈复杂度目标不超过 7，函数不超过 50 行；超限拆分职责。
- Web 错误保留稳定错误码、HTTP 状态和 `traceId`；不要吞掉异常或向用户暴露堆栈。
- 写操作使用 revision 乐观锁；长任务使用稳定任务 ID、幂等键、可重试步骤和持久化状态。
- 密钥、内部 Token、私有对象键、完整敏感正文不得写入日志、响应或 Git。

## AI 代码标记

新增或整体改写的代码文件使用当前仓库 ACAP 标记：

```text
// GENERATED_BY_AI
// MODEL: <model>
// DATE: YYYY-MM-DD
```

关键业务方法说明 Preconditions、Side Effects 和 Error Semantics。简单访问器和自解释纯函数
不需要重复注释。

## 按需参考

- 前端：`references/frontend_standard.md`
- Python：`references/backend_python_standard.md`
- API：`references/api_design_standard.md`
- 数据库：`references/database_standard.md`
- 评审：`references/code_review_guide.md`
- Java：`../backend-java-code-engineering/SKILL.md`

项目代码和 `docs/30-design/10-detailed-design.md` 与通用参考冲突时，以项目现状和根 `AGENTS.md` 为准。
