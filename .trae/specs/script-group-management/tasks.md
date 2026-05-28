
# 脚本分组管理 - The Implementation Plan (Decomposed and Prioritized Task List)

## [x] Task 1: 创建分组数据模型和 DAO
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 创建 ScriptGroup 数据实体类
  - 创建 GroupDao 接口，包含分组的增删改查操作
  - 修改 Script 实体，添加 groupId 外键字段
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-3, AC-4, AC-6]
- **Test Requirements**:
  - `programmatic` TR-1.1: ScriptGroup 实体包含必要字段（id, name, createdAt, updatedAt）
  - `programmatic` TR-1.2: Script 实体新增 groupId 字段，默认值为 null
  - `programmatic` TR-1.3: GroupDao 包含完整的 CRUD 方法
- **Notes**: ScriptGroup 表需与 Script 表通过 groupId 关联

## [x] Task 2: 数据库升级和迁移
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 升级数据库版本从 1 到 2
  - 创建数据库迁移逻辑
  - 添加 ScriptGroup 表和 Script 表的 groupId 列
- **Acceptance Criteria Addressed**: [AC-6]
- **Test Requirements**:
  - `programmatic` TR-2.1: 数据库版本更新为 2
  - `programmatic` TR-2.2: 迁移过程不丢失现有脚本数据
  - `programmatic` TR-2.3: 迁移后现有脚本的 groupId 为 null
- **Notes**: 使用 Room 的 Migration 类实现数据迁移

## [x] Task 3: 创建 GroupRepository 和更新 ScriptRepository
- **Priority**: P0
- **Depends On**: Task 1, Task 2
- **Description**: 
  - 创建 GroupRepository 类管理分组数据
  - 更新 ScriptRepository 支持按分组查询
  - 更新 DI 模块以注入新的依赖
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-3, AC-4]
- **Test Requirements**:
  - `programmatic` TR-3.1: GroupRepository 包含分组的增删改查方法
  - `programmatic` TR-3.2: ScriptRepository 支持按 groupId 筛选脚本
  - `programmatic` TR-3.3: 删除分组时，相关脚本的 groupId 设置为 null
- **Notes**: 遵循现有 Repository 的设计模式

## [x] Task 4: 实现分组管理 UI 组件
- **Priority**: P1
- **Depends On**: Task 3
- **Description**: 
  - 创建分组列表展示组件
  - 创建分组创建/编辑对话框
  - 创建分组删除确认对话框
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-3]
- **Test Requirements**:
  - `human-judgment` TR-4.1: 分组列表正确显示所有分组，包括"未分组"
  - `human-judgment` TR-4.2: 分组创建/编辑对话框功能正常
  - `human-judgment` TR-4.3: 删除分组时有确认提示
- **Notes": "未分组"作为特殊分组，不允许编辑和删除

## [x] Task 5: 更新脚本列表界面支持分组筛选
- **Priority**: P1
- **Depends On**: Task 4
- **Description**: 
  - 在 ScriptListScreen 顶部添加分组选择器
  - 根据选中的分组筛选显示脚本
  - 添加分组管理入口（如菜单按钮）
- **Acceptance Criteria Addressed**: [AC-5]
- **Test Requirements**:
  - `human-judgment` TR-5.1: 分组选择器正常显示所有可用分组
  - `human-judgment` TR-5.2: 选择不同分组时，脚本列表正确更新
  - `human-judgment` TR-5.3: 分组管理入口可正常打开
- **Notes**: 保持现有 UI 风格一致性

## [x] Task 6: 支持脚本分配分组
- **Priority**: P1
- **Depends On**: Task 3, Task 5
- **Description**: 
  - 在脚本卡片或编辑器中添加分组选择功能
  - 允许用户为脚本选择或更改分组
- **Acceptance Criteria Addressed**: [AC-4]
- **Test Requirements**:
  - `human-judgment` TR-6.1: 脚本卡片或编辑器中显示当前分组
  - `human-judgment` TR-6.2: 可以成功更改脚本分组
  - `programmatic` TR-6.3: 脚本分组更改后数据库正确更新
- **Notes": "可以在 ScriptCard 中添加分组显示和编辑入口

## [x] Task 7: 完整功能测试和集成
- **Priority**: P2
- **Depends On**: Task 4, Task 5, Task 6
- **Description**: 
  - 端到端测试所有分组管理功能
  - 测试数据库迁移兼容性
  - 性能和稳定性测试
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6]
- **Test Requirements**:
  - `programmatic` TR-7.1: 所有单元测试通过
  - `human-judgment` TR-7.2: 所有功能按预期工作
  - `programmatic` TR-7.3: 操作响应时间符合 NFR-1 要求
