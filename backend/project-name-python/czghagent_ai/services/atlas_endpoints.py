# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""operation 到 Atlas endpointCode 的<b>唯一映射点</b>。

Atlas 的 ``POST /v1/chat`` 请求体只有
``{endpointCode, messages, tenantId, taskId, requestId}``——没有 temperature、
没有 max_tokens、没有 response_format、没有 thinking 开关。这不是遗漏：
Atlas 的路由优先级是 ``modelCode > endpointCode > taskProfile``，而 endpointCode
的定义就是「运营侧配置好的具名路由目标」。生成参数属于那个配置，不属于调用方。

于是本产品原来散在代码里的模型档位和生成参数，变成了对 Atlas 线的一份
<b>配置请求</b>——下面每一行都写清楚了那个 endpoint 必须被配成什么。
把参数塞进请求体是另一条路，但通则明确禁止在产品仓里发明被调方的接口形状；
一个 Atlas 不认识的字段最好的结果是被忽略（参数悄悄失效），
最坏的结果是每一次调用 400。

**尚未确认的一件事**：这些 endpointCode 还没有被 Atlas 线登记和授权。
在授权到位之前，每一次调用都会是 ``403 NOT_ENTITLED``，与令牌是否有效无关。
``chat/default`` 是 Atlas 参考实现里的通用兜底，先用它保证链路可通。
"""

from __future__ import annotations

from dataclasses import dataclass

#: Atlas 参考实现给出的全局稳定兜底 endpoint。
#: 没有专属 endpoint 时全部落到它上面——链路能通，但所有 operation 共用
#: 一套生成参数，也就是<b>失去了按任务分档</b>这件事本身。
DEFAULT_ENDPOINT_CODE = "chat/default"


@dataclass(frozen=True)
class EndpointRequirement:
    """一个 endpoint 必须被配置成什么样。

    这些数字原来是本产品直连时自己发的请求参数，逐条实测调出来的。
    迁到 Atlas 之后它们必须在 endpoint 配置里复现，否则同一份提示词会产出
    不同质量的结果——而那不会报错，只会让标书变差。
    """

    endpoint_code: str
    tier: str
    temperature: float
    max_tokens: int | None
    thinking: bool
    note: str


#: 逐 operation 的 endpoint 需求。
#:
#: 分两档不是为了省钱而已：``consistency_review`` 开 thinking 且温度为 0，
#: 而 ``chapter_drafting`` 温度 0.4 且不开 thinking——把它们并到一个 endpoint 上，
#: 要么审查失去推理深度，要么正文变得刻板重复。
ENDPOINT_REQUIREMENTS: dict[str, EndpointRequirement] = {
    "project_overview_source_selection": EndpointRequirement(
        "chat/tenderforge-fast-deterministic", "fast", 0.0, 4096, False,
        "从整份招标文件里挑证据片段，只返回 id 列表，任何随机性都是噪声",
    ),
    "project_overview_extraction": EndpointRequirement(
        "chat/tenderforge-fast-deterministic", "fast", 0.0, 8192, False,
        "抽取项目概述，事实搬运，不允许发挥",
    ),
    "technical_scoring_extraction": EndpointRequirement(
        "chat/tenderforge-fast-deterministic", "fast", 0.0, 8192, False,
        "评分条款排序，改写分值会直接毁掉标书",
    ),
    "outline_skeleton_planning": EndpointRequirement(
        "chat/tenderforge-quality-planning", "quality", 0.15, 16384, False,
        "一二级目录骨架；16384 是实测下限，更小会截断",
    ),
    "outline_branch_expansion": EndpointRequirement(
        "chat/tenderforge-fast-planning", "fast", 0.2, 8192, False,
        "三级目录展开，量大，走 fast 档",
    ),
    "chapter_drafting": EndpointRequirement(
        "chat/tenderforge-fast-drafting", "fast", 0.4, None, False,
        "正文续写；上限用提供方默认值，正文长度不该被产品这一侧钉死",
    ),
    "section_revision": EndpointRequirement(
        "chat/tenderforge-quality-revision", "quality", 0.2, None, False,
        "受约束改写；刻意不开 thinking——补全预算要留给替换正文本身",
    ),
    "consistency_review": EndpointRequirement(
        "chat/tenderforge-quality-review", "quality", 0.0, None, True,
        "全文一致性审查，唯一开 thinking 的 operation",
    ),
}


def endpoint_for(operation: str, *, use_dedicated_endpoints: bool) -> str:
    """这次调用该路由到哪个 endpoint。

    ``use_dedicated_endpoints`` 为假时全部落到 ``chat/default``。这是给
    「Atlas 通了但专属 endpoint 还没授权」那段时间用的开关——那段时间一定存在，
    而在它期间让每次调用都 403 等于把整个产品停掉。
    """
    if not use_dedicated_endpoints:
        return DEFAULT_ENDPOINT_CODE
    requirement = ENDPOINT_REQUIREMENTS.get(operation)
    return requirement.endpoint_code if requirement else DEFAULT_ENDPOINT_CODE


def required_endpoint_codes() -> list[str]:
    """需要 Atlas 线授权的全部 endpoint，去重后按字典序。

    这个列表是给接入信用的：授权缺一个，对应的那几个 operation 全部 403，
    而产品这一侧看到的只是「某个环节坏了」。
    """
    return sorted({item.endpoint_code for item in ENDPOINT_REQUIREMENTS.values()})
