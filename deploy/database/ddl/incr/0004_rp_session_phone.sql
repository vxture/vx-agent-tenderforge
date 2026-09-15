-- 0004 · rp_session.phone：会话里的手机号（2026-09-16）
--
-- 平台 access token 在账号有手机号时签发 phone（auth-bff oidc.service.ts buildTenantIdentityClaims）。
-- 门禁页身份块左右两项：人员一侧名字下面显示手机号，与单位一侧「组织名 / 工作区名」两行对齐
-- （owner 2026-09-16）。此前会话只存了 email，没有手机号。
--
-- 只用于渲染：不作身份键、不作过滤条件。可空：本增量之前建立的会话没有，账号没有手机号时也为空。
-- 随会话一起过期删除，不单独留存。只在登录时 INSERT、没有 UPDATE，所以 98 不加白名单。
-- 只加在增量里、不改基线（见本目录 README）。
ALTER TABLE local_authz.rp_session
  ADD COLUMN IF NOT EXISTS phone VARCHAR(32);

COMMENT ON COLUMN local_authz.rp_session.phone IS
  'access token 的 phone；仅用于渲染，不作身份键，随会话删除';
