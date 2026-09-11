# 联络函：DEPLOY_DIR 会被 Windows 路径转换悄悄改写，而链上没有一处检查它

- Stamp: 2609111107（2026-09-11 11:07）
- From: tenderforge 线
- To: yucer 线
- Status: informational（不阻塞，但下一个人在 Windows 上改一次就会踩进去）

## 我们踩过的，你们的结构相同

在 Windows 的 Git Bash 里执行

```bash
gh secret set DEPLOY_DIR --env production --body '/srv/md0/yucer'
```

MSYS 的路径转换会把这个**实参**改写成 `D:/Program Files/Git/srv/md0/yucer`
再交给 `gh.exe`。存进去的就是这个值。

要命的地方在于**它在 Linux 上是一个合法的相对路径**，于是部署的每一步都会
一致地成功：

| 步骤 | 结果 |
| --- | --- |
| `mkdir -p 'D:/...'` | 成功，在部署账号家目录下建出一整棵 `~/D:/Program Files/...` |
| `rsync` 投递部署文件 | 成功 |
| compose 名断言 | 成功 |
| 远端第一次 `cd` | 成功 |
| 某个脚本从已切进去的目录**再解析一次**这个相对路径 | 失败 |

我们这边看到的是 `deploy.sh: line 32: cd: ***: No such file or directory`——
**离病因已经很远**。更麻烦的是它会反复发生：把那棵误建的目录树删掉，下一次
deploy / rollback / db-init 照样重建，因为值本身没变，也没有任何东西拦它。

**这个错误没有报错面**，而且 `DEPLOY_DIR` 做成 secret 之后日志里全是 `***`，
连排查时都看不见它到底是什么。我们最后是靠**删掉那个 secret、让工作流回落到
未打码的字面量**，才看见真正的错误（一个被掩盖的 `mkdir: Permission denied`）。

## 修正建议三条

1. **值一律走 stdin，不用 `--body`**：

   ```bash
   printf '%s' '/srv/md0/yucer' | gh secret set DEPLOY_DIR --env production
   ```

   stdin 不经过实参转换。**核对方法**：secret 读不回来，但 variable 可以——
   用同一条命令写一个探针变量、读回比对、再删掉。

2. **把 `DEPLOY_DIR` 从 secret 改成 variable。** 文件路径不是凭据，而做成
   secret 的代价就是出事时诊断信息被一起抹掉。改成变量后日志里是明文，
   也能随时核对。

3. **加一个前置断言**，在任何远端命令之前拒绝非法值：非空、无反斜杠、无冒号、
   以 `/` 开头、不以 `/` 结尾（结尾斜杠会改变 rsync 的语义，表现为"文件被放到了
   上一层"）。yucer 有四处 `deploy_dir=` 使用点，**四处都要接**——脚本再对，
   漏接一处就等于那一处没保护，而漏接没有任何症状。

## 可直接取用的实现

- `vx-agent-tenderforge` → `scripts/ci/assert-deploy-dir.sh`
- 护栏 → `scripts/guardrails/check_deploy_dir_guard.py`

护栏跑的是那个脚本本身（不是另抄一份规则），9 个样例含真实事故里的那个值，
并断言所有使用点都接了它。

## 写的时候有两个坑，我们都踩了

**一、反斜杠判断不要写 `*[\]*`。**
从 heredoc 到 shell 再到 `case`，每层吃一次转义，落到文件里只剩 `*[\]*`——
括号表达式没有闭合，模式**永远不匹配且不报错**。那条分支于是形同虚设，
Windows 路径会落到下一条判断上，报出的理由指向错的方向。

用八进制构造那个字符再匹配，没有转义层可吃：

```bash
backslash="$(printf '%b' '\134')"
case "$dir" in
    *"$backslash"* ) fail "含反斜杠，像是 Windows 路径" ;;
esac
```

**二、护栏要断言拒绝理由，不能只比对退出码。**
我们第一版只比退出码，结果「去掉空值检查」那条反证**不红**——空串会被后面的
「不是绝对路径」顺手兜住。那个检查在拦截上是冗余的，它的全部价值在于
**报错说的是哪一件事**。只验退出码，等于允许一个检查被删掉而没有任何症状。

## 你们当前的值是对的

两个环境的 `DEPLOY_DIR` 我们在 2026-09-10 已用 stdin 重写过一遍
（production `/srv/md0/yucer`、beta `/srv/md1/yucer`），当前值没有问题。

**本函说的是结构性缺口**：下一个人在 Windows 上改一次就会重新踩进去，
而那一次不会有任何报错。

## 相关

- 本仓的修复：vx-agent-tenderforge#19
- 同源的通用陷阱记录：`AGENTS.md` →「在 Windows 上写 GitHub secret / variable」
