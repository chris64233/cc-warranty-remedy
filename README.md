# cc-warranty-remedy

序列化产品的保修资格判断与维修、换货、退款三类处置管理服务。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

- **产品实例（ProductUnit）**：序列号（全局唯一）、销售日期、保修期限（月）、状态
  （ACTIVE / REPLACED / REFUNDED）与换货链关系（originUnitId / replacedByUnitId）。
  历史处置通过处置记录按产品查询。
- **保修申请（WarrantyClaim）**：故障代码、故障现象、购买凭证、外部申请号。
  创建后不可修改。
- **资格判断（EligibilityDecision）**：随申请提交生成一次，此后不可修改。
- **处置决定（Remedy）**：类型（REPAIR / REPLACEMENT / REFUND）与执行状态
  （PENDING / EXECUTED / CANCELLED）。决定内容创建后不可修改。
- **纠正记录（RemedyCorrection）**：对已执行处置只能追加，不可修改或删除。
- **替换品库存（ReplacementUnit）**：AVAILABLE / LOCKED / CONSUMED。

## 主要业务规则

### 保修资格

- 产品状态为 ACTIVE 且当前日期不超过 `销售日期 + 保修期限` 时具备保修资格。
- 换货后原产品保修资格转移至新序列号（新实例沿用原销售日期与保修期限）；
  退款后原产品保修资格终止，后续申请一律判定为不具备资格。

### 申请

- 外部申请号全局唯一，是幂等键：重复提交（含并发提交）返回已存在的申请，
  不会产生重复记录。
- 同一产品同一故障代码只允许一笔活动申请（OPEN / APPROVED）；通过对产品行
  加悲观写锁串行化检查与创建。申请关闭（处置执行或撤销）后，同一故障可再次申请。
- 提交时即完成资格判断并留存结果；不具备资格的申请直接置为 REJECTED。

### 处置决策

- 同一故障首次发生 → **维修**；同一故障在维修执行完成后复发 → **换货**
  （有可用替换品时），无可用替换品则降级为 **退款**。
- 批准是幂等的：同一申请已有未撤销的处置时直接返回该决定；并发批准通过对
  申请行加悲观写锁串行化，只有一笔处置被创建。

### 换货与替换品

- 批准换货时即通过条件更新（`UPDATE ... WHERE status = 'AVAILABLE'`）原子锁定
  替换品；多笔申请并发竞争同一替换品时只有一笔成功，其余降级为退款。
- 执行换货在同一事务内完成：校验锁定归属、以替换品序列号创建新产品实例、
  转移保修关系、原产品置为 REPLACED、替换品出库（CONSUMED）。任一步失败
  整体回滚，不会留下被占用但未关联任何处置的替换品。

### 撤销与纠正

- 尚未执行（PENDING）的处置可以撤销：释放锁定的替换品，申请回到 OPEN 可重新批准。
- 已执行（EXECUTED）的处置不可撤销，只能追加纠正记录。
- 原申请、资格判断与处置决定均不可修改（无更新入口，状态流转仅通过上述动作）。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/products` | 注册产品实例 |
| GET | `/api/products/{id}` | 产品详情与当前保修资格 |
| GET | `/api/products/{id}/warranty-chain` | 产品保修链（换货序列） |
| GET | `/api/products/{id}/remedies` | 产品历史处置记录 |
| POST | `/api/replacement-units` | 登记替换品库存 |
| GET | `/api/replacement-units` | 替换品库存列表 |
| POST | `/api/claims` | 提交保修申请（外部申请号幂等） |
| GET | `/api/claims/{id}` | 申请证据与资格判断结果 |
| POST | `/api/claims/{id}/approve` | 批准申请并生成处置决定（幂等） |
| GET | `/api/remedies/{id}` | 处置记录（含替换关系与纠正记录） |
| POST | `/api/remedies/{id}/execute` | 执行处置 |
| POST | `/api/remedies/{id}/cancel` | 撤销未执行的处置并释放资源 |
| POST | `/api/remedies/{id}/corrections` | 对已执行处置追加纠正记录 |

错误语义：404 资源不存在；409 业务状态冲突（重复活动申请、非法状态流转等）；
422 产品不具备保修资格；400 参数校验失败。
