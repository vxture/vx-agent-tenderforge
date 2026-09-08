# AI 编码工程规范

# Python 后端开发规范（FastAPI + ACAP 协议）

### 2.1 ACAP 协议（AI-Centric Annotation Protocol）

ACAP 协议是为 AI 编码设计的注释规范，包含三个核心部分：

1. **Preconditions（前置条件）**：函数执行前必须满足的条件
2. **Side Effects（副作用）**：函数执行对系统状态的改变
3. **Error Semantics（错误语义）**：可能抛出的异常及其含义

#### 2.1.1 ACAP 注释格式

```python
# GENERATED_BY_AI
# MODEL: claude-3.7-sonnet
# DATE: 2026-01-06

from typing import Optional
from decimal import Decimal

async def create_order(
    user_id: str,
    product_id: str,
    quantity: int,
    price: Decimal
) -> str:
    """
    创建订单

    Args:
        user_id: 用户 ID
        product_id: 商品 ID
        quantity: 购买数量
        price: 商品单价

    Returns:
        str: 订单 ID

    Preconditions:
        - user_id 必须存在于系统中
        - product_id 必须存在且库存充足（>= quantity）
        - quantity > 0
        - price > 0

    Side Effects:
        - 在数据库中创建新订单记录
        - 减少商品库存（product.stock -= quantity）
        - 发送订单创建事件到消息队列
        - 记录审计日志

    Error Semantics:
        - UserNotFoundError: 用户不存在
        - ProductNotFoundError: 商品不存在
        - InsufficientStockError: 库存不足
        - ValueError: quantity 或 price 为非法值（<= 0）
        - DatabaseError: 数据库操作失败
    """
    # 实现代码...
```

#### 2.1.2 文件级 AI 标记

每个由 AI 生成的文件必须在文件头部包含以下标记：

```python
# GENERATED_BY_AI
# MODEL: claude-3.7-sonnet  # 或其他模型名称
# DATE: 2026-01-06           # YYYY-MM-DD 格式
```

### 2.2 目录结构

```
{module}/
├── main.py                  # FastAPI 入口
├── api/                     # 路由层
│   └── {module}_router.py
├── models/                  # Request/Response DTO
│   └── {module}_models.py
├── services/                # 业务逻辑层
│   └── {module}_service.py
└── infrastructure/          # 基础设施层
    ├── types/              # 数据库实体
    │   └── {entity}.py
    ├── db/                 # 数据库操作
    │   └── {entity}_repository.py
    └── remote/             # 外部服务调用
        └── {service}_client.py
```

### 2.3 代码规范

#### 2.3.1 类型注解（必需）

```python
# ✅ 正确：完整的类型注解
async def get_user(user_id: str) -> Optional[User]:
    pass

# ❌ 错误：缺少类型注解
async def get_user(user_id):
    pass
```

#### 2.3.2 异步优先

```python
# ✅ 正确：使用 async/await
async def fetch_data() -> dict:
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        return response.json()

# ❌ 错误：同步阻塞调用
def fetch_data() -> dict:
    response = requests.get(url)
    return response.json()
```

#### 2.3.3 Pydantic 数据验证

```python
from pydantic import BaseModel, Field, validator

class CreateOrderRequest(BaseModel):
    user_id: str = Field(..., min_length=1, max_length=50)
    product_id: str = Field(..., min_length=1, max_length=50)
    quantity: int = Field(..., gt=0, le=1000)

    @validator('quantity')
    def validate_quantity(cls, v):
        if v <= 0:
            raise ValueError('数量必须大于 0')
        return v
```

#### 2.3.4 结构化日志

```python
import structlog

logger = structlog.get_logger()

async def process_order(order_id: str) -> None:
    logger.info("processing_order", order_id=order_id)
    # 处理逻辑...
    logger.info("order_processed", order_id=order_id, status="success")
```

### 2.4 复杂度和规模限制

| 指标         | AI 生成代码 | 人工代码 |
| ------------ | ----------- | -------- |
| **圈复杂度** | ≤ 7         | ≤ 10     |
| **函数行数** | ≤ 50        | ≤ 80     |
| **文件行数** | ≤ 300       | ≤ 500    |

### 2.5 命名规范

```python
# ✅ 正确命名
class OrderService:
    async def create_order(self, request: CreateOrderRequest) -> str:
        pass

# ❌ 禁止的命名
class OrderSvc:  # 不要使用缩写
    async def do_something(self, data):  # 命名不清晰
        pass
```
### 2.5 包导入规范
1. 跨目录导入，一律使用 from <project_pkg>...
2. 相对导入只允许 from .xxx import yyy
3. 任何会被直接执行的文件，不能包含相对导入
4. 所有生成代码，默认使用绝对包导入

---
