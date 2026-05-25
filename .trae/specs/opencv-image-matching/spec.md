# OpenCV 图片查找 - 产品需求文档

## Overview
- **Summary**: 将当前自定义的图片匹配算法替换为 OpenCV 实现，以提升图片查找的准确性和性能
- **Purpose**: 解决当前自定义算法在复杂场景、光照变化和部分匹配下的局限性，提供更可靠的图片查找能力
- **Target Users**: AutoBot RPA 应用的用户，特别是需要自动化处理包含大量视觉元素的场景的用户

## Goals
- 集成 OpenCV 库到 Android 项目中
- 使用 OpenCV 实现模板匹配功能替换当前自定义算法
- 保持与现有代码接口的兼容性
- 提供至少与当前算法相当或更好的匹配性能和准确度

## Non-Goals (Out of Scope)
- 不改变 ImageMatchingService 的公共 API 接口
- 不实现特征点匹配等更高级的算法
- 不重构 ImageMatchingService 以外的代码

## Background & Context
- 当前 ImageMatchingService 使用自定义的灰度像素相关系数算法进行模板匹配
- 当前算法在简单场景下工作良好，但在复杂场景、光照变化或需要缩放匹配时效果有限
- OpenCV 是业界标准的计算机视觉库，提供了成熟、高效的模板匹配实现
- 项目目前未包含 OpenCV 依赖

## Functional Requirements
- **FR-1**: 集成 OpenCV Android SDK 到项目中
- **FR-2**: 使用 OpenCV 的模板匹配算法实现图片查找功能
- **FR-3**: 保持现有的 findMatch() 接口签名不变
- **FR-4**: 支持多种 OpenCV 匹配方法

## Non-Functional Requirements
- **NFR-1**: 匹配性能应与当前算法相当或更好
- **NFR-2**: 内存占用应保持在合理范围内
- **NFR-3**: OpenCV 库的添加不应显著增加 APK 大小

## Constraints
- **Technical**: 
  - 必须支持 Android API 26+
  - 必须支持 arm64-v8a 架构
  - 现有 MatchResult 数据类和接口必须保持不变
- **Business**: 无
- **Dependencies**: OpenCV Android SDK

## Assumptions
- OpenCV Android SDK 可以正常集成到项目中
- 当前的 API 使用方式不会改变
- 现有代码的其他部分不依赖于当前自定义匹配算法的内部实现

## Acceptance Criteria

### AC-1: 成功集成 OpenCV
- **Given**: 项目配置正确
- **When**: 构建项目
- **Then**: OpenCV 库成功集成，无构建错误
- **Verification**: `programmatic`
- **Notes**: 检查 build.gradle 配置和依赖是否正确

### AC-2: 保持现有接口兼容
- **Given**: ImageMatchingService 类存在
- **When**: 查看 findMatch() 方法
- **Then**: 方法签名保持不变，返回类型和参数类型与之前相同
- **Verification**: `programmatic`

### AC-3: 图片查找功能正常工作
- **Given**: 有屏幕截图和模板图片
- **When**: 调用 findMatch() 方法
- **Then**: 返回正确的匹配位置和相似度
- **Verification**: `programmatic`
- **Notes**: 需要测试各种匹配场景

### AC-4: 性能符合预期
- **Given**: 典型的匹配场景
- **When**: 执行图片查找
- **Then**: 匹配时间与当前算法相当或更短
- **Verification**: `programmatic`

## Open Questions
- [ ] 应该使用哪种 OpenCV 匹配方法作为默认方法？
- [ ] 是否需要保留当前自定义算法作为备选方案？
