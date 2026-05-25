
# ML Kit 文本识别 - 实施计划

## [x] 任务 1: 集成 ML Kit 依赖
- **优先级**: P0
- **依赖**: 无
- **描述**: 
  - 在 app/build.gradle 中添加 ML Kit Text Recognition SDK 依赖
  - 验证 Gradle 同步成功
- **接受标准**: AC-1
- **测试需求**: 
  - programmatic: Gradle 构建成功，无依赖冲突
- **备注**: 使用最新稳定版本的 ML Kit Text Recognition

## [x] 任务 2: 创建文本识别服务 (TextRecognitionService)
- **优先级**: P0
- **依赖**: 任务 1
- **描述**: 
  - 创建 TextRecognitionService 单例类（参考 ImageMatchingService 的架构）
  - 实现初始化方法，初始化 TextRecognizer
  - 实现从 Bitmap 检测文本的方法
  - 创建 TextMatchResult 数据类，包含匹配位置、相似度、文本内容
  - 实现文本搜索方法，支持包含匹配，返回最佳匹配
  - 实现调试模式功能，在截图上绘制文本检测框
- **接受标准**: AC-1, AC-4
- **测试需求**: 
  - programmatic: 服务能正确初始化并处理 Bitmap 输入
- **备注**: 与现有代码风格保持一致，使用单例模式

## [x] 任务 3: 扩展 ScriptAction 数据模型
- **优先级**: P0
- **依赖**: 无
- **描述**: 
  - 在 Script.kt 中添加 FindText 数据类作为 ScriptAction 的新类型
  - 扩展 ConditionType 枚举，添加 TEXT_FOUND 和 TEXT_NOT_FOUND
  - 更新 ScriptConverters 类，添加 FindText 动作的序列化/反序列化逻辑
  - 更新条件类型的处理逻辑
- **接受标准**: AC-1, AC-2
- **测试需求**: 
  - programmatic: 新类型能正确序列化为 JSON 并反序列化
- **备注**: 保持与现有动作类型（如 FindImage）相同的设计模式

## [x] 任务 4: 更新自动化引擎
- **优先级**: P0
- **依赖**: 任务 2, 任务 3
- **描述**: 
  - 在 AutomationEngine 中添加 findText 私有方法，实现 FindText 动作的执行
  - 更新 checkCondition 方法，添加对 TEXT_FOUND 和 TEXT_NOT_FOUND 条件的支持
  - 实现变量保存功能，将文本识别结果存入 variableStore
  - 集成调试模式，保存带文本框标记的截图
  - 更新 executeAction 方法，添加对 ScriptAction.FindText 的处理
- **接受标准**: AC-1, AC-2, AC-5
- **测试需求**: 
  - programmatic: 包含 FindText 动作的脚本能正常执行
  - programmatic: 文本条件判断逻辑正确工作

## [x] 任务 5: 更新 UI 界面 (AddActionDialog 和 EditDialogs)
- **优先级**: P1
- **依赖**: 任务 3
- **描述**: 
  - 在 AddActionDialog 中添加 "Find Text" 动作选项
  - 创建 EditFindTextDialog Composable，支持配置：
    - 目标文本
    - 超时时间
    - 相似度阈值（可选，默认 0.8）
    - 保存结果到变量
    - 调试模式开关
  - 更新 EditConditionDialog，添加对 TEXT_FOUND / TEXT_NOT_FOUND 的配置支持
- **接受标准**: AC-3
- **测试需求**: 
  - human-judgement: UI 界面完整且可用

## [x] 任务 6: 更新 ActionItemCard 和 UI 辅助组件
- **优先级**: P1
- **依赖**: 任务 3, 任务 5
- **描述**: 
  - 更新 ActionItemCard，添加对 FindText 动作的显示支持
  - 更新 EditActionDialog，添加对 FindText 的编辑分支
  - 确保在脚本列表和编辑界面正确显示新动作类型
- **接受标准**: AC-3
- **测试需求**: 
  - human-judgement: UI 正确显示新动作

## [x] 任务 7: 集成测试和验证
- **优先级**: P1
- **依赖**: 所有上述任务
- **描述**: 
  - 创建测试脚本，验证 FindText 动作
  - 测试条件判断功能
  - 测试变量引用功能（从文本识别结果中获取坐标）
  - 调试模式验证
  - 错误处理验证
- **接受标准**: AC-1, AC-2, AC-4, AC-5
- **测试需求**: 
  - programmatic: 所有测试场景通过
  - human-judgement: 功能符合预期
