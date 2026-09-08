
## 2. 强制注释要求

### 2.1 表注释

**规则**：所有表必须添加 COMMENT，说明业务用途、数据来源、更新频率及生命周期。

```sql
CREATE TABLE resource_usage_log (
  id BIGINT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  usage_amount DECIMAL(10,2),
  recorded_at DATETIME
) COMMENT = '资源使用记录表，记录每次资源使用情况，数据来源：业务系统实时上报，保留最近 90 天数据，每日凌晨 2 点归档';
```

### 2.2 字段注释

**规则**：所有字段必须添加 COLUMN COMMENT，包含以下信息：

1. **业务含义**：字段的业务用途
2. **取值范围**：枚举值或范围说明
3. **物理单位**：如适用
4. **敏感信息标识**：如适用

```sql
CREATE TABLE monitoring_station (
  id BIGINT PRIMARY KEY COMMENT '主键ID',
  station_code VARCHAR(32) NOT NULL COMMENT '监测站编码，遵循国家标准',
  station_name VARCHAR(100) NOT NULL COMMENT '监测站名称',
  latitude DECIMAL(10,6) COMMENT '纬度，单位：度，精度 0.000001，范围：-90 至 90',
  longitude DECIMAL(11,6) COMMENT '经度，单位：度，精度 0.000001，范围：-180 至 180',
  water_level DECIMAL(8,3) COMMENT '当前水位，单位：米，精度 0.001',
  status TINYINT COMMENT '运行状态：0=正常, 1=离线, 2=故障, 3=维护中',
  contact_phone VARCHAR(20) COMMENT '联系电话，敏感信息，需脱敏处理',
  created_at DATETIME COMMENT '创建时间，时区：UTC+8',
  updated_at DATETIME COMMENT '最后更新时间，时区：UTC+8'
) COMMENT = '监测站基础信息表，数据来源：站点管理系统，每小时同步一次';
```

## 3. 数据字典一致性

### 3.1 枚举值标准化

**规则**：所有枚举值、状态码、分类代码必须保持一致。

```sql
-- ✅ 正确：统一的状态定义
-- 表 A
status TINYINT COMMENT '状态：0=正常, 1=异常, 2=维护'

-- 表 B（同样的状态概念）
status TINYINT COMMENT '状态：0=正常, 1=异常, 2=维护'

-- ❌ 错误：同一概念不同定义
-- 表 A
status TINYINT COMMENT '状态：0=正常, 1=异常'

-- 表 B
state INT COMMENT '状态：1=正常, 2=故障, 3=离线'
```

### 3.2 字段名一致性

**规则**：同一业务概念在不同表中必须使用相同字段名。

```sql
-- ✅ 正确：统一使用 user_id
CREATE TABLE orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '用户ID'
);

CREATE TABLE order_logs (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL COMMENT '用户ID'
);

-- ❌ 错误：同义异名
CREATE TABLE orders (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '用户ID'
);

CREATE TABLE order_logs (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL COMMENT '操作人ID'
);
```

## 4. 面向智能体的可读性设计

### 4.1 自解释性

**原则**：字段命名和注释应具备自解释性，AI 无需额外文档即可理解数据含义。

```sql
-- ✅ 正确：自解释性强
CREATE TABLE water_quality_measurement (
  id BIGINT PRIMARY KEY COMMENT '主键ID',
  station_id BIGINT NOT NULL COMMENT '监测站ID，关联 monitoring_station.id',
  ph_value DECIMAL(4,2) COMMENT 'pH值，范围：0-14，正常范围：6.5-8.5',
  dissolved_oxygen DECIMAL(6,2) COMMENT '溶解氧，单位：mg/L，正常范围：≥5',
  temperature DECIMAL(5,2) COMMENT '水温，单位：摄氏度',
  measured_at DATETIME COMMENT '测量时间，时区：UTC+8',
  is_abnormal BOOLEAN COMMENT '是否异常：true=异常, false=正常'
) COMMENT = '水质监测数据表，每 15 分钟采集一次，保留最近 1 年数据';

-- ❌ 错误：缺乏自解释性
CREATE TABLE wq_data (
  id BIGINT,
  sid BIGINT,
  ph DECIMAL(4,2),
  do DECIMAL(6,2),
  temp DECIMAL(5,2),
  time DATETIME,
  flag INT
);
```

### 4.2 扁平化宽表结构

**原则**：优先采用扁平化宽表，避免过度范式化。

```sql
-- ✅ 正确：扁平化宽表（适合智能体分析）
CREATE TABLE resource_snapshot (
  id BIGINT PRIMARY KEY,
  resource_id BIGINT NOT NULL,
  resource_name VARCHAR(100),
  resource_type VARCHAR(50),
  owner_id BIGINT,
  owner_name VARCHAR(100),
  department_id BIGINT,
  department_name VARCHAR(100),
  status VARCHAR(20),
  created_at DATETIME,
  updated_at DATETIME
) COMMENT = '资源快照表，包含关联信息的冗余字段，便于直接查询和分析';

-- ❌ 错误：过度范式化（需要多表 JOIN，不利于智能体理解）
CREATE TABLE resources (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100),
  type VARCHAR(50),
  owner_id BIGINT
);

CREATE TABLE users (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100),
  department_id BIGINT
);

CREATE TABLE departments (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100)
);
```

### 4.3 时间字段规范

**规则**：时间字段统一使用 DATETIME 或 TIMESTAMP 类型，并在注释中明确时区。

```sql
-- ✅ 正确：明确时区
created_at DATETIME COMMENT '创建时间，时区：UTC+8（北京时间）'
updated_at DATETIME COMMENT '更新时间，时区：UTC+8（北京时间）'
measured_at TIMESTAMP COMMENT '测量时间，存储为 UTC，显示时转换为 UTC+8'

-- ❌ 错误：时区不明确
created_at DATETIME COMMENT '创建时间'
time INT COMMENT '时间戳'
```

## 5. 禁止的设计模式

### 5.1 禁止动态 Schema

```sql
-- ❌ 错误：EAV 模型（Entity-Attribute-Value）
CREATE TABLE entity_attributes (
  entity_id BIGINT,
  attribute_name VARCHAR(100),
  attribute_value TEXT
);

-- ✅ 正确：固定 Schema
CREATE TABLE resources (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100),
  type VARCHAR(50),
  status VARCHAR(20),
  config_timeout INT,
  config_retry INT
);
```

### 5.2 禁止 JSON 存储结构化数据

```sql
-- ❌ 错误：关键业务数据存储为 JSON
config JSON COMMENT '配置信息'

-- ✅ 正确：拆分为独立字段
config_timeout INT COMMENT '超时时间，单位：秒'
config_retry INT COMMENT '重试次数'
config_enabled BOOLEAN COMMENT '是否启用'
```

## 6. 最佳实践

1. **命名自解释**：表名和字段名清晰表达业务含义
2. **注释完整**：每个表和字段都有详细注释
3. **枚举值明确**：所有枚举值在注释中列出
4. **单位标注**：物理量字段标注单位
5. **时区明确**：时间字段标注时区
6. **扁平化优先**：优先使用宽表，减少 JOIN
7. **避免动态 Schema**：使用固定结构
8. **敏感信息标识**：在注释中标注敏感字段

---

**记住核心原则**：标准化、语义化、自解释、智能体友好。
