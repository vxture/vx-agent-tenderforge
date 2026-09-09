#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
#
# SCA 闸门（治理规范 §9）。本产品有**三个生态**：npm（前端）、Maven（后端）、
# PyPI（AI 服务）。只扫一个的表现是另外两个的漏洞永远不出现，而报告是绿的。
#
# 这个脚本单独成文件而不是写进 ci.yml，是因为它必须能在本地逐字复跑——
# 下面每一条断言都是先在本地实跑出反例才写下来的。
#
# ── 结构：一个生态扫一次，扫的都是「解析过的完整树」──────────────────────
#
# osv-scanner 直接对着仓内清单文件扫，三个生态的成色完全不同：
#
# * `pnpm-lock.yaml` 本身就是完整解析结果（478 个包），直接扫就对。
# * `pom.xml` 要它自己去 Maven registry 拉父 POM 才能展开。实测被
#   repo.maven.apache.org 以 **HTTP 429** 拒绝，而失败的表现极坏：
#   「Scanned pom.xml 并找到 6 个包」照常打印，紧接着一行 failed resolution，
#   于是只有直接声明的 25 个依赖被扫，Spring Boot 拉进来的整棵树一个没扫。
# * `requirements.txt` 它会解析传递依赖，但**解析出来的版本是错的**：实测它
#   认定 idna 3.9.0、pygments 2.9.0，而真正装进镜像的是 idna 3.19、
#   pygments 2.21.0。多报出来的 22 条全是不存在的问题，而按它说的去「修」
#   是无效动作——版本本来就比它以为的新。一个会喊狼来了的闸门等于没有闸门。
#
# 所以两边都不用它的解析器，改由**各自生态自己的解析器**先把树解出来：
#
#   Maven → `cyclonedx-maven-plugin:makeAggregateBom` → 25 → **112** 个包
#   PyPI  → `uv pip compile`                          → 15 → **42** 个包
#
# 解析那一步在 ci.yml 里做（要 JDK 与 uv），这里只消费产物并断言它确实完整。
#
# ── 清单盘点：防「新加了一个生态但没人接进来」────────────────────────────
#
# 三趟扫描是**显式指定文件**的，好处是准确，代价是仓里新出现一个 go.mod 或
# 第二个 package.json 时不会有任何人提醒。所以额外跑一趟递归发现，把发现到的
# 清单集合与下面的 EXPECTED_MANIFESTS 对账，多一个少一个都红。
#
# 用法：
#   OSV_SCANNER=/tmp/osv-bin/osv-scanner scripts/ci/sca-scan.sh

set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$ROOT"

OSV=${OSV_SCANNER:-osv-scanner}
REPORT_DIR=${REPORT_DIR:-/tmp}

# 三个生态各自的扫描对象。文件名是**有意义**的：osv-scanner 靠文件名挑提取器，
# 把 SBOM 叫成 bom-bumped.json 就会得到「could not determine extractor suitable
# to this file」然后**静默跳过**——实测过。
NPM_LOCK=${NPM_LOCK:-$ROOT/frontend/project-name-web/pnpm-lock.yaml}
JAVA_BOM=${JAVA_BOM:-$ROOT/backend/project-name-java/target/bom.json}
PYTHON_RESOLVED=${PYTHON_RESOLVED:-$ROOT/backend/project-name-python/target/requirements.txt}

# 下限守的是「解析静默退化成只剩直接依赖」——那种情况下产物照样生成、照样被扫、
# 照样是绿的，只有包数会掉回直接声明的那个量级。
# 实测：npm 478；Java 直接声明 25 → 解析后 112；Python 直接钉 15 → 解析后 42。
MIN_NPM_PACKAGES=${MIN_NPM_PACKAGES:-400}
MIN_JAVA_PACKAGES=${MIN_JAVA_PACKAGES:-80}
MIN_PYTHON_PACKAGES=${MIN_PYTHON_PACKAGES:-30}

# 递归发现应该正好看到这些清单。新增一个 → 红（去把它接进真正的扫描）；
# 少一个 → 也红（说明某一趟的输入没了，而那一趟会安静地扫了个空）。
EXPECTED_MANIFESTS=$(cat <<'LIST'
backend/project-name-java/pom.xml
backend/project-name-java/project-name-application-command/pom.xml
backend/project-name-java/project-name-application-query/pom.xml
backend/project-name-java/project-name-coverage/pom.xml
backend/project-name-java/project-name-domain/pom.xml
backend/project-name-java/project-name-infrastructure/pom.xml
backend/project-name-java/project-name-start/pom.xml
backend/project-name-java/project-name-web/pom.xml
backend/project-name-python/requirements.txt
backend/project-name-python/uv.lock
frontend/project-name-web/pnpm-lock.yaml
LIST
)

problems=()
worst_status=0

note() { printf '\n== %s ==\n' "$1"; }

# 扫一份完整的依赖清单，并断言包数没有退化。
# $1 标签  $2 文件  $3 包数下限  $4 报告路径
scan_tree() {
  local label=$1 artifact=$2 floor=$3 report=$4 status=0 scanned found

  if [[ ! -f $artifact ]]; then
    problems+=("找不到 $label 的扫描对象：${artifact#$ROOT/}——这半边等于没扫")
    return 0
  fi

  "$OSV" scan source --config .osv-scanner.toml --lockfile "$artifact" \
    >"$report" 2>&1 || status=$?
  cat "$report"

  scanned=$(grep -E "Scanned .* file and found [0-9]+ packages" "$report" | head -1 || true)
  if [[ -z $scanned ]]; then
    problems+=("osv-scanner 不认 ${artifact#$ROOT/}——它靠文件名挑提取器，名字不对整个文件就被跳过")
  else
    found=$(printf '%s' "$scanned" | grep -oE "[0-9]+ packages" | grep -oE "[0-9]+")
    if (( found < floor )); then
      problems+=("$label 只有 $found 个包（下限 $floor）——传递依赖没解析出来，扫的只是直接声明的那几个")
    else
      echo "$label：$found 个包（下限 $floor）"
    fi
  fi

  if (( status > worst_status )); then worst_status=$status; fi
  return 0
}

# ── 盘点：仓里到底有哪些依赖清单 ───────────────────────────────────────────
# 这一趟**只用来发现文件**，退出码与告警都不算数（它用的是 osv-scanner 自己
# 那两个不可靠的解析器；真正的结论来自下面三趟）。
note "盘点仓内的依赖清单"
inventory_report=$REPORT_DIR/osv-inventory.txt
"$OSV" scan source --config .osv-scanner.toml --recursive . \
  --experimental-disable-plugins transitivedependency/pomxml \
  --experimental-disable-plugins transitivedependency/requirements \
  >"$inventory_report" 2>&1 || true

found_manifests=$(grep -oE "Scanned [^ ]+ file and found" "$inventory_report" \
                  | sed -E 's#^Scanned ##; s# file and found$##' \
                  | sed "s#^$ROOT/##" | sort -u)
expected_sorted=$(printf '%s\n' "$EXPECTED_MANIFESTS" | sort -u)

printf '%s\n' "$found_manifests" | sed 's/^/  /'

added=$(comm -13 <(printf '%s\n' "$expected_sorted") <(printf '%s\n' "$found_manifests"))
removed=$(comm -23 <(printf '%s\n' "$expected_sorted") <(printf '%s\n' "$found_manifests"))
if [[ -n $added ]]; then
  problems+=("仓里出现了新的依赖清单，但没有任何一趟扫描覆盖它：
$(printf '%s\n' "$added" | sed 's/^/       /')
     把它接进 sca-scan.sh，再更新 EXPECTED_MANIFESTS")
fi
if [[ -n $removed ]]; then
  problems+=("预期中的依赖清单不见了：
$(printf '%s\n' "$removed" | sed 's/^/       /')
     对应那一趟扫描可能正在扫一个空文件")
fi

# ── 三趟真正的扫描：一个生态一趟，扫的都是完整解析结果 ──────────────────────
note "npm（pnpm-lock.yaml，本身就是完整锁文件）"
scan_tree "npm 依赖树" "$NPM_LOCK" "$MIN_NPM_PACKAGES" "$REPORT_DIR/osv-npm.txt"

note "Maven（cyclonedx 聚合 SBOM）"
scan_tree "Java 依赖树" "$JAVA_BOM" "$MIN_JAVA_PACKAGES" "$REPORT_DIR/osv-java.txt"

note "PyPI（uv pip compile 的解析结果）"
scan_tree "Python 依赖树" "$PYTHON_RESOLVED" "$MIN_PYTHON_PACKAGES" "$REPORT_DIR/osv-python.txt"

# ── 三趟都不许有解析失败 ───────────────────────────────────────────────────
# 解析失败不是「少扫了一点」，是那一整块依赖<b>一个都没扫</b>，而输出仍是绿的。
for report in "$REPORT_DIR"/osv-npm.txt "$REPORT_DIR"/osv-java.txt "$REPORT_DIR"/osv-python.txt; do
  [[ -f $report ]] || continue
  if grep -qE "failed resolution|Error during extraction" "$report"; then
    problems+=("$(basename "$report") 里有依赖树解析失败——那部分依赖实际未被扫描：
$(grep -E "failed resolution|Error during extraction" "$report" | head -3 | sed 's/^/       /')")
  fi
done

if (( ${#problems[@]} > 0 )); then
  echo >&2
  echo "!! SCA 闸门是瞎的——先修下面这些，再谈扫出来的告警：" >&2
  for problem in "${problems[@]}"; do
    echo "   - $problem" >&2
  done
  exit 1
fi

# 覆盖完好之后，才轮到「扫出了漏洞」这件事本身。顺序是有意的：
# 一个没扫全的绿灯比一个红灯坏得多，所以先报「门是瞎的」，再报「门拦下了东西」。
if (( worst_status != 0 )); then
  echo >&2
  echo "!! 依赖漏洞扫描未通过（osv-scanner 退出码 $worst_status）" >&2
  echo "   整顿方法见治理规范 §9 与 docs/50-deployment/10-deployment-plan.md §4b——是抬版本，不是加忽略。" >&2
  exit 1
fi

echo
echo "SCA 闸门通过：三个生态各扫了一趟完整依赖树，清单盘点无出入，无已知漏洞。"
