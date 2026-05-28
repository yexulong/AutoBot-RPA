
# 脚本分组管理 - Product Requirement Document

## Overview
- **Summary**: 为 AutoBot-RPA 应用添加脚本分组管理功能，允许用户创建、编辑、删除分组，并将脚本归类到不同分组中，实现脚本的有序组织。
- **Purpose**: 帮助用户更好地管理和组织大量的自动化脚本，提升脚本的查找和使用效率。
- **Target Users**: 需要管理多个自动化脚本的 AutoBot 用户。

## Goals
- 支持创建、编辑、删除脚本分组
- 支持为脚本分配、修改、移除分组
- 支持按分组查看和筛选脚本
- 保持现有脚本功能的完整性

## Non-Goals (Out of Scope)
- 脚本分组的嵌套层级（只支持单层级分组）
- 分组的图标、颜色等样式定制
- 分组间脚本的批量移动

## Background &amp; Context
- 项目当前使用 Room 数据库管理脚本数据，数据库版本为 1
- 现有 Script 数据模型包含基本信息（名称、描述、动作列表等）
- 脚本列表界面展示所有脚本，无分组筛选功能
- 技术栈：Kotlin、Compose UI、Room 数据库、Hilt DI

## Functional Requirements
- **FR-1**: 用户可以创建、编辑、删除脚本分组
- **FR-2**: 用户可以为脚本分配分组
- **FR-3**: 用户可以在脚本列表界面按分组筛选显示
- **FR-4**: 未分配分组的脚本默认归为"未分组"

## Non-Functional Requirements
- **NFR-1**: 分组管理操作响应时间 &lt; 500ms
- **NFR-2**: 数据库升级平滑，不丢失现有数据
- **NFR-3**: UI 符合现有设计风格

## Constraints
- **Technical**: Android 最小支持版本保持不变，使用现有的 Room 和 Compose 技术栈
- **Business**: 需在当前项目结构中实现，不引入重大架构变更
- **Dependencies**: 依赖 Room 数据库迁移框架

## Assumptions
- 用户理解"分组"的概念
- 现有脚本可以无分组（归为"未分组"）
- 数据库迁移能正确处理现有数据

## Acceptance Criteria

### AC-1: 分组的创建
- **Given**: 用户在脚本列表界面
- **When**: 用户点击创建分组并输入分组名称
- **Then**: 新分组被成功创建并显示在分组列表中
- **Verification**: `programmatic`

### AC-2: 分组的编辑
- **Given**: 用户已创建至少一个分组
- **When**: 用户编辑分组名称并保存
- **Then**: 分组名称被成功更新
- **Verification**: `programmatic`

### AC-3: 分组的删除
- **Given**: 用户已创建至少一个分组
- **When**: 用户删除该分组
- **Then**: 分组被删除，该分组下的脚本自动归为"未分组"
- **Verification**: `programmatic`

### AC-4: 脚本分配分组
- **Given**: 用户有至少一个分组和一个未分组的脚本
- **When**: 用户为脚本选择一个分组
- **Then**: 脚本成功归入该分组
- **Verification**: `programmatic`

### AC-5: 按分组筛选脚本
- **Given**: 用户有多个分组和脚本
- **When**: 用户在脚本列表选择某个分组
- **Then**: 只显示该分组下的脚本
- **Verification**: `human-judgment`

### AC-6: 数据升级兼容
- **Given**: 用户有旧版本数据（无分组功能）
- **When**: 应用升级到新版本
- **Then**: 现有脚本全部归为"未分组"，功能正常
- **Verification**: `programmatic`

## Open Questions
- [ ] 分组是否需要支持排序功能？
- [ ] 是否需要分组的图标或颜色标识？
- [ ] "未分组"是否允许重命名或删除？
