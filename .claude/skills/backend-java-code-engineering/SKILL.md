---
name: backend-java-code-engineering
description: TenderAgent Java 25 + Spring Boot 3.5 + DDD/CQRS 开发规范
version: 3.0
last_updated: 2026-08-14
---

# TenderAgent Java 开发规范

## 开始前

1. 阅读根 `AGENTS.md` 和 `doc/DETAILED_DESIGN.md`。
2. 查看目标包的 `package-info.java`、模块 `pom.xml` 和相邻测试。
3. 确认变更属于 Domain、Application Command/Query、Infrastructure、Web 或 Start。

## 六模块边界

- `project-name-domain`：业务记录、规则、异常、仓储和外部端口，不依赖 Spring 实现。
- `project-name-application-command`：写用例、事务、审计、AI/Temporal 编排。
- `project-name-application-query`：会话、账户、管理员和标书读模型。
- `project-name-infrastructure`：JDBC、文件、密码、HTTP 客户端和端口实现。
- `project-name-web`：Controller、DTO、认证过滤器、`ApiResponse` 和异常映射。
- `project-name-start`：Spring Boot 入口、配置、引导数据、Flyway 和集成测试。

Controller 只处理 HTTP 输入、身份上下文和响应。事务放在应用服务；不变量放在 Domain；SQL、
文件和远程调用放在 Infrastructure。不要让 Domain 引用 Spring、JDBC、Servlet 或 JSON 实现。

## 当前基础设施

- JDK 25、Spring Boot 3.5.6、Maven 多模块。
- MySQL 8.4；开发默认 H2 MySQL mode。
- Flyway `classpath:sql`，V1-V22 是不可修改的升级链，新变更追加 V23+。
- Temporal Java SDK 1.27；任务需确定性、幂等、可恢复。
- Java 通过内部 Token 调用 FastAPI，不直接调用浏览器或暴露私有对象键。

## 编码要求

- 4 空格、UTF-8、行宽不超过 120；JDK -> 框架 -> 第三方 -> 内部导入。
- 用 `BusinessException(errorCode, message, httpStatus)` 表达预期业务失败。
- REST 成功与失败统一使用项目 `ApiResponse`；分页使用项目 `PageResult`。
- 查询和写入都必须携带当前用户所有权条件；管理员权限不能替代标书所有权。
- 写入聚合检查 revision；重试路径用唯一约束/幂等键避免重复版本。
- 长 Activity 不记录密钥或全文；错误只持久化过滤摘要和诊断哈希。

## 验证

```powershell
Set-Location backend/project-name-java
mvn clean compile
mvn test
mvn clean package
```

Java 必须使用 JDK 25。接口、状态、表、任务队列或配置变更时，同步更新
`doc/DETAILED_DESIGN.md`，不创建平行规格或过程文档。

实现细节以当前仓库代码、相邻测试、根 `AGENTS.md` 和详细设计为准，不引用外部项目模板。
