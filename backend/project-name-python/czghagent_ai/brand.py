# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-08
"""产品身份，Python 侧的唯一真源。

与 Java 的 ``ProductIdentity.PRODUCT_CODE`` 和前端的 ``BRAND.productCode``
是同一个值的三处承载。这一份存在的理由是出站调用：本服务直接调 Atlas 时，
换票要声明自己是谁。

<b>它是源码字面量，不是环境变量。</b>环境变量意味着同一份镜像可以冒充另一个产品
上报用量；产品码属于「这份代码是谁」，不属于「这次部署在哪」。
"""

from __future__ import annotations

#: 平台登记的产品码。匹配 ``^[a-z][a-z0-9_-]{0,31}$``。
PRODUCT_CODE = "tenderforge"

#: 平台自身 ``/platform/*`` 与 ``/usage/*`` 的换票受众。
#: 调 Atlas / Runos 时受众是对方的产品码，不是这个值。
PLATFORM_AUDIENCE = "vxture"
