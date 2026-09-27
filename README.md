# cc-warranty-remedy

序列化产品保修资格判断与维修、换货、退款处置服务。

管理序列号产品的保修链，受理带外部申请号与购买凭证的保修申请，在受理时固化资格判断，
并根据产品状态批准/执行维修、换货、退款三类处置；支持未执行处置的撤销与已执行处置的
纠正追加。所有关键并发规则由数据库约束与条件更新保证。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA / Hibernate，默认 H2 内存库）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Product` | 序列化产品：序列号（唯一）、销售日期、保修期限（月）、状态、换货链前后继 |
| `WarrantyClaim` | 保修申请：外部申请号（唯一）、故障现象、活动去重键、证据列表、资格判断、处置 |
| `ClaimEvidence` | 申请证据（购买凭证、故障影像等），随申请写入，只能追加 |
| `EligibilityDecision` | 受理时固化的资格判断（合格与否、基准日、到期日、原因码），永不修改 |
| `Disposition` | 处置决定（维修/换货/退款），与申请一对一，含选定替换品与纠正记录 |
| `DispositionCorrection` | 已执行处置的纠正记录（返工、追加退款、善意换货等），只能追加 |

产品状态：`IN_STOCK`（换货库存）→`HELD`（已被批准占用）→`ACTIVE`（换货承接保修后）；
正常产品为 `ACTIVE`，换货后变 `REPLACED`，退款后变 `REFUNDED`。

申请状态：`SUBMITTED → APPROVED → EXECUTED`；未执行可进入 `CANCELLED`。

## 主要业务规则

### 资格判断

- 资格 = 产品状态为 `ACTIVE` 且当前日期不晚于保修到期日（到期日当天仍合格）。
- 保修到期日 = 销售日期 + 保修月数；判断结果（含基准日、到期日、不合格原因码）
  在**受理时固化**，之后不随时间或产品状态变化而改变，且不可修改。
- 不合格原因码：`WARRANTY_EXPIRED`（过期）、`PRODUCT_REPLACED`（已换货，保修关系转移）、
  `WARRANTY_TERMINATED`（已退款，资格终止）、`NOT_SOLD`（库存/占用中的替换品无保修关系）。
- 提交申请必须至少附带一份 `PURCHASE_PROOF`（购买凭证）。

### 申请受理与幂等

- **外部申请号全局唯一、受理幂等**：重复提交（含并发提交）同一外部申请号，所有调用返回
  同一笔原申请，故障描述等原内容不被覆盖。
- **同一产品同一故障只能有一笔活动申请**：以"产品 + 规范化故障描述"（去首尾空白、压缩
  连续空白、转小写后取 SHA-256）作为活动去重键，带数据库唯一约束；申请进入终态
  （已执行/已撤销）时去重键释放，同故障可再次申请。并发提交时数据库保证至多一笔成功。
- 原申请（外部申请号、产品、故障现象）、资格判断、处置决定一经写入不可修改。

### 批准与执行处置

- 处置依据申请资格与产品状态选择：维修（`REPAIR`）、换货（`REPLACEMENT`）、退款（`REFUND`）；
  只有 `SUBMITTED` 的申请可批准，重复/并发批准只有一笔成功（申请行悲观锁 + 唯一约束）。
- **换货原子锁定替换品**：批准时执行条件更新
  `UPDATE product SET status='HELD' WHERE id=? AND status='IN_STOCK'`，
  影响行数为 1 才成功；多个申请并发占用同一替换品时**只有一个事务成功**，其余得到 409。
- 占用替换品与写入处置记录在**同一事务**：此后任一步失败整体回滚，条件更新随之撤销，
  绝不留下"被占用但未关联处置"的替换品。
- 执行换货：原产品变 `REPLACED`，替换品继承原**销售日期与保修期限**（保修不重新起算），
  双方建立换货链，替换品变 `ACTIVE`；保修资格转移到新序列号。
- 执行退款：原产品变 `REFUNDED`，后续保修资格终止。
- 执行维修：产品序列号与保修关系不变。

### 撤销与纠正

- **尚未执行**的申请/处置可以撤销；换货撤销时以条件更新把替换品 `HELD → IN_STOCK`
  原子释放，立即可被其他申请占用。
- **已执行**处置不能撤销、不能修改；只能追加纠正记录（返工、追加退款/差额、善意换货等），
  纠正记录本身也不可修改、不可删除。

### 查询

- 产品保修链：`GET /api/products/{serial}/warranty-chain`，沿换货链返回
  源头序列号 → … → 当前有效序列号的完整节点。
- 申请证据与资格判断、处置与替换关系：`GET /api/claims/by-ref/{externalRef}`、`/api/claims/{id}`。
- 产品处置记录：`GET /api/claims?serialNumber=...`，命中"作为申请产品"或"作为替换品"
  的全部处置（按决定时间升序），含纠正记录。

## API 一览

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/products` | 登记已售产品（序列号、销售日期、保修月数） |
| `POST /api/products/stock-units` | 登记换货库存替换品 |
| `GET  /api/products/stock-units` | 当前在库（可占用）替换品 |
| `GET  /api/products/{serial}` | 产品详情 |
| `GET  /api/products/{serial}/warranty-chain` | 保修链 |
| `POST /api/claims` | 提交申请（外部申请号幂等） |
| `POST /api/claims/{id}/approvals` | 批准处置（换货带 `replacementSerial`） |
| `POST /api/claims/{id}/execution` | 执行已批准处置 |
| `POST /api/claims/{id}/cancellation` | 撤销未执行处置并释放资源 |
| `POST /api/claims/{id}/corrections` | 已执行处置追加纠正记录 |
| `GET  /api/claims/by-ref/{externalRef}` | 按外部申请号查申请详情 |
| `GET  /api/claims/{id}` | 按 id 查申请详情 |
| `GET  /api/claims?serialNumber=...` | 产品处置记录 |

错误响应统一为 `{ "error", "message", "timestamp" }`：
`404 NOT_FOUND`、`422 VALIDATION_FAILED`（含资格不合格/缺凭证）、
`409 BUSINESS_RULE_VIOLATION` 与 `409 CONCURRENCY_CONFLICT`、`400 INVALID_REQUEST`。

## 自动化测试

- `EligibilityEvaluatorTest`：资格边界（到期日当天合格、次日不合格）、换货/退款/库存状态。
- `DedupeKeyTest`：同故障规范化与活动去重键规则。
- `ClaimWorkflowIntegrationTest`：受理幂等、活动申请去重、三类处置全生命周期、
  换货保修转移与保修链、撤销释放、已执行不可撤销、纠正追加、不可变性、处置历史查询。
- `ClaimConcurrencyIntegrationTest`：8 线程并发——并发批准同一申请、并发抢占同一替换品
  （仅一笔成功且无孤儿占用）、并发同号提交全部幂等返回、并发同故障仅一笔活动、
  占用后失败事务回滚释放替换品。
- `WarrantyApiIntegrationTest`：MockMvc 端到端 HTTP 流程与错误状态码映射。
