-- 0003 · rp_session.org_name / workspace_name：会话里的组织名与工作空间名（2026-09-15）
--
-- 平台 access token 签发 active_org_name / active_workspace_name（发现文档 claims_supported 列出，
-- auth-bff token/access-claims.ts 写入）。此前会话只存了两个标识，门禁页「当前工作区」一栏
-- 只能显示兜底文案——读者分不清被拒的是哪个工作区，而这一栏存在的全部意义就是让他分清。
--
-- 只用于渲染：业务过滤一律按 org_id / workspace_id。可空：本增量之前建立的会话没有，
-- 平台未签发时也为空。只在登录时 INSERT、没有 UPDATE，所以 98 不加白名单。
-- 只加在增量里、不改基线（见本目录 README）。
ALTER TABLE local_authz.rp_session
  ADD COLUMN IF NOT EXISTS org_name VARCHAR(255);
ALTER TABLE local_authz.rp_session
  ADD COLUMN IF NOT EXISTS workspace_name VARCHAR(255);

COMMENT ON COLUMN local_authz.rp_session.org_name IS
  'access token 的 active_org_name；组织显示名，仅用于渲染，不作过滤键';
COMMENT ON COLUMN local_authz.rp_session.workspace_name IS
  'access token 的 active_workspace_name；工作空间显示名，仅用于渲染，不作过滤键';
