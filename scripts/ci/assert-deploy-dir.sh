#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-11
#
# 断言 DEPLOY_DIR 是一个能在目标主机上用的绝对路径。
#
# **为什么需要它：这个错误没有任何报错面。**
#
# 2026-09-10 的 v0.1.0 部署里，DEPLOY_DIR 被 Git Bash 的 MSYS 路径转换写成了
# `D:/Program Files/Git/srv/md0/tenderforge`。在 Linux 上那是一个合法的**相对
# 路径**，于是：
#
#   mkdir -p 'D:/...'   成功（在 $HOME 下建出一整棵树）
#   rsync 投递           成功
#   compose 名断言       成功
#   远端第一次 cd        成功
#   deploy.sh 再 cd      失败 —— 而它已经离病因很远了
#
# 中间每一步都一致地成功，因为它们用的是同一个错值。唯一暴露它的是最后那次
# 相对路径从新工作目录再解析一遍。**那是偶然，不是设计。**
#
# 更糟的是它会反复发生：删掉那棵误建的树，下一次部署照样重建——因为没有任何
# 一处检查过这个值的形态。四个使用点，零个校验点。
#
# 所以这里在**任何远端命令之前**把话说死。宁可在 CI 上红一次，也不要在目标
# 主机上安静地建出一棵谁也不认识的目录树。
set -euo pipefail

dir="${1-}"

fail() {
    echo "::error::DEPLOY_DIR 不可用：$1" >&2
    echo "        当前值：'${dir}'" >&2
    echo "        它必须是目标主机上的绝对路径，例如 /srv/md0/tenderforge。" >&2
    echo "        在 Windows 上用 \`gh variable set X --body '/abs/path'\` 会被 MSYS" >&2
    echo "        路径转换改写；值一律走 stdin：printf '%s' '/abs/path' | gh variable set X" >&2
    exit 1
}

[ -n "$dir" ] || fail "为空"

# 反斜杠字符用八进制构造，**不写字面量**。
#
# 这里原本是 `*[\\]*`，实测匹配不到任何东西：从 heredoc 到 shell 再到
# case，每一层都会吃掉一次转义，落到文件里只剩 `*[\]*`——括号表达式没有
# 闭合，模式永远不匹配。**它不报错，只是那条分支形同虚设**，Windows 路径会落到
# 下一条上，报出的理由指向错的方向。
#
# 八进制没有转义层可吃：printf 之后它就是一个字符，模式里加引号就是字面匹配。
backslash="$(printf '%b' '\134')"

case "$dir" in
    # 先查 Windows 形态：它比"不是绝对路径"更具体，报出来能直接指向病因。
    # 反斜杠与盘符冒号都不该出现在一个 POSIX 路径里。
    *"$backslash"* ) fail "含反斜杠，像是 Windows 路径" ;;
    *:* )    fail "含冒号，像是被 MSYS 路径转换改写过的盘符路径" ;;
esac

case "$dir" in
    /*) ;;
    *) fail "不是绝对路径" ;;
esac

case "$dir" in
    # 结尾的斜杠会让 "$dir/deploy" 变成 "//deploy"。在多数情形下无害，
    # 但 rsync 对结尾斜杠的语义是**不同的**，而那个差别会以"文件被放到了
    # 上一层"的形式出现。与其解释它，不如不允许。
    */) fail "不要以斜杠结尾——rsync 对结尾斜杠的语义不同" ;;
esac

echo "[assert] DEPLOY_DIR = ${dir}"
