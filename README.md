# Order Tracking Web Service（销售订单跟踪系统）
Spring Boot + MySQL + Flyway + Session Login + Vanilla HTML/JS

> 这是一个“网页形式”的订单流程跟踪系统：打开浏览器即可使用（前端页面由后端服务直接托管），后端提供 REST API，数据库用 Flyway 自动建表/升级。

---

## 1. 这个项目能做什么（功能清单）
- ✅ 登录（用户名/密码），Session（Cookie）保持登录状态
- ✅ 角色权限控制（GM / SALES / PMC / PRODUCTION / WAREHOUSE / GUEST）
- ✅ 订单增删改查（GM/SALES）
- ✅ 敏感字段隐藏（非 GM/SALES：客户/金额/单价/付款条款会显示 ***）
- ✅ 生产计划（PMC）
- ✅ 物料评估（PMC，支持 外购/库存）
- ✅ 工序进度（PRODUCTION/PMC）
- ✅ 入仓记录（WAREHOUSE：支持多次/部分入仓日志）
- ✅ 入仓确认（WAREHOUSE：要求“工序完成”后才能确认）
- ✅ 出货计划（SALES）
- ✅ 出货确认归档（WAREHOUSE：确认后订单进入 ARCHIVED）
- ✅ 订单照片上传/删除（image/*，单张 ≤ 10MB，存本地 uploads 目录）
- ✅ 导出 CSV（订单列表筛选后导出）
- ✅ 导入订单（Excel/CSV 上传 或 直接粘贴表格文本）
- ✅ 用户管理（GM：创建用户、重置密码、启用/禁用、删除）
- ✅ 操作记录 Audit Logs（GM：搜索/过滤/分页）

---

## 2. 运行环境要求
- JDK 17+
- Maven 3.9+
- MySQL 8.0+

---

## 3. 数据库初始化（只需要做一次）
建议数据库名：`order_tracking_auth`

在 MySQL 执行：
```sql
CREATE DATABASE IF NOT EXISTS order_tracking_auth
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
