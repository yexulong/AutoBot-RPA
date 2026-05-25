# OpenCV 图片查找 - 实施计划

## [x] 任务 1: 添加 OpenCV 依赖到 build.gradle
- **优先级**: P0
- **依赖项**: None
- **描述**: 
  - 在 app/build.gradle 中添加 OpenCV Android SDK 依赖
  - 更新当前注释 "No OpenCV dependency needed - using custom image matching"
  - 使用稳定版本的 OpenCV 库
- **验收标准**: [AC-1]
- **测试要求**:
  - `programmatic` TR-1.1: 项目能够成功构建，无依赖错误
  - `programmatic` TR-1.2: OpenCV 库能够正确导入
- **注意事项**: 
  - 可以使用官方 OpenCV Android 库或社区维护的版本
  - 确保仅包含 arm64-v8a 架构的库以减小 APK 大小

## [x] 任务 2: 实现 OpenCV 初始化和 Bitmap 转换工具
- **优先级**: P0
- **依赖项**: 任务 1
- **描述**: 
  - 在 ImageMatchingService 中初始化 OpenCV
  - 实现 Bitmap 到 OpenCV Mat 的转换方法
  - 实现 OpenCV Mat 到 Bitmap 的转换方法
  - 实现灰度转换
- **验收标准**: [AC-1, AC-2]
- **测试要求**:
  - `programmatic` TR-2.1: OpenCV 能够成功初始化
  - `programmatic` TR-2.2: Bitmap 能够正确转换为 Mat 并还原回来

## [x] 任务 3: 使用 OpenCV 实现模板匹配
- **优先级**: P0
- **依赖项**: 任务 2
- **描述**: 
  - 替换当前 tryFastSearch() 方法，使用 OpenCV 的 matchTemplate
  - 使用合适的匹配方法（建议使用 CV_TM_CCOEFF_NORMED）
  - 保持现有的阈值处理逻辑
  - 保持现有 MatchResult 数据类和 findMatch() 接口
- **验收标准**: [AC-2, AC-3, AC-4]
- **测试要求**:
  - `programmatic` TR-3.1: findMatch() 接口保持不变
  - `programmatic` TR-3.2: 能够找到正确的匹配位置
  - `programmatic` TR-3.3: 相似度计算正确
  - `programmatic` TR-3.4: 性能不低于当前算法

## [x] 任务 4: 测试和验证
- **优先级**: P1
- **依赖项**: 任务 3
- **描述**: 
  - 验证各种匹配场景
  - 测试性能和内存占用
  - 修复发现的问题
- **验收标准**: [AC-3, AC-4]
- **测试要求**:
  - `programmatic` TR-4.1: 在各种场景下能够正确匹配
  - `programmatic` TR-4.2: 阈值处理工作正常
  - `human-judgement` TR-4.3: 代码质量良好，遵循现有风格
