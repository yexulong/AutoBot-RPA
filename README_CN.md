# AutoBot RPA - Android 自动化应用

## 项目概述

AutoBot RPA 是一个完整的 Android 自动化应用，类似于"按键精灵"，采用现代 Android 开发实践构建。

## 项目结构

```
AutoBotRPA/
├── app/
│   ├── src/main/
│   │   ├── java/com/autobot/rpa/
│   │   │   ├── AutoBotApplication.kt          # Hilt 应用类
│   │   │   ├── MainActivity.kt                # 主活动
│   │   │   ├── data/
│   │   │   │   ├── database/
│   │   │   │   │   ├── AutoBotDatabase.kt    # Room 数据库
│   │   │   │   │   └── ScriptDao.kt         # 脚本数据访问对象
│   │   │   │   ├── model/
│   │   │   │   │   └── Script.kt             # 脚本和动作的数据模型
│   │   │   │   └── repository/
│   │   │   │       └── ScriptRepository.kt   # 仓储模式
│   │   │   ├── di/
│   │   │   │   └── DatabaseModule.kt         # Hilt 依赖注入模块
│   │   │   ├── service/
│   │   │   │   ├── AutoBotAccessibilityService.kt  # 用于手势的无障碍服务
│   │   │   │   ├── AutoBotForegroundService.kt      # 前台服务
│   │   │   │   ├── AutomationEngine.kt               # 核心自动化引擎
│   │   │   │   ├── ScreenshotManager.kt              # 截图捕获和管理
│   │   │   │   ├── CoordinateRecorderService.kt      # 坐标记录服务
│   │   │   └── ui/
│   │   │       ├── AutoBotApp.kt             # 主应用导航
│   │   │       ├── theme/                    # Material 3 主题
│   │   │       │   ├── Color.kt
│   │   │       │   ├── Theme.kt
│   │   │       │   └── Type.kt
│   │   │       ├── screens/
│   │   │       │   ├── ScriptListScreen.kt   # 脚本管理
│   │   │       │   ├── ScriptEditorScreen.kt  # 脚本编辑
│   │   │       │   ├── ScriptExecutionScreen.kt # 脚本执行和日志
│   │   │       │   ├── SettingsScreen.kt      # 应用设置
│   │   │       │   └── ViewModels            # MVVM 视图模型
│   │   │       └── components/
│   │   │           └── ActionItemCard.kt     # 可复用的动作卡片
│   │   ├── res/
│   │   │   ├── values/                       # 资源（字符串、颜色、主题）
│   │   │   ├── xml/
│   │   │   │   └── accessibility_service_config.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle                              # 根构建配置
├── settings.gradle                           # 项目设置
├── gradle.properties                         # Gradle 属性
└── SPEC.md                                   # 项目规格说明
```

## 已实现的主要功能

### 1. 核心 RPA 动作
- **触摸操作**：点击、滑动、长按
- **文本输入**：向输入框输入文本
- **按键**：模拟物理按键
- **延时**：等待指定时长

### 2. 图像识别
- **截图**：完整实现的屏幕捕获功能
- **截图管理**：在设置中查看、分享和删除已捕获的截图
- **查找图片**：模板匹配（基础实现）

### 3. 流程控制
- **循环**：重复执行动作（N次或无限）
- **条件**：根据图像/颜色进行分支
- **注释**：为脚本添加备注

### 4. 脚本管理
- 创建、编辑、删除脚本
- 组织脚本中的动作
- 通过拖拽重新排序动作
- 使用 Room 数据库保存/加载脚本

### 5. 执行引擎
- 实时执行控制（运行/暂停/停止）
- 带时间戳的实时日志
- 执行统计
- 用于后台运行的前台服务

### 6. 无障碍服务
- 使用 Android AccessibilityService 执行手势
- 在坐标位置点击
- 滑动手势
- 长按支持

## 技术栈

- **语言**：Kotlin 2.0.0
- **UI**：Jetpack Compose + Material Design 3
- **架构**：MVVM + 整洁架构
- **依赖注入**：Hilt 2.51
- **数据库**：Room 2.6.1
- **导航**：Jetpack Navigation Compose
- **最低 SDK**：26 (Android 8.0)
- **目标 SDK**：34 (Android 14)
- **构建工具**：Gradle 8.5 + Android Gradle Plugin 8.5.0

## 构建项目

### 前置条件
1. 安装 Java 17 或 21
2. 安装 Android SDK (API 34)
3. 确保可以访问 Google Maven 和 Maven Central

### 构建命令

```bash
# 设置环境变量
set ANDROID_HOME=C:\path\to\android\sdk
set JAVA_HOME=C:\path\to\java17-or-21

# 构建 debug APK
gradlew.bat assembleDebug

# 或者使用系统 Gradle
gradle assembleDebug

# 清理并重新构建
gradlew.bat clean assembleDebug
```

### 构建输出

APK 将生成在：
```
app\build\outputs\apk\debug\app-debug.apk
```

## 应用权限

应用需要以下权限：

- `BIND_ACCESSIBILITY_SERVICE` - 用于自动化触摸和手势
- `FOREGROUND_SERVICE` - 用于在后台运行自动化
- `POST_NOTIFICATIONS` - 用于自动化运行时的通知
- `SYSTEM_ALERT_WINDOW` - 用于悬浮窗功能
- 屏幕捕获权限 - 用于捕获截图（通过运行时请求授予）

## 使用说明

### 1. 首次启动

1. 在 Android 设备上安装 APK
2. 按提示授予无障碍服务权限
3. 在设置中授予其他必要权限（包括屏幕捕获权限）

### 2. 创建脚本

1. 在脚本屏幕点击 "+" 按钮
2. 输入脚本名称
3. 点击"添加动作"添加动作
4. 选择动作类型并配置参数
5. 保存脚本

### 3. 运行脚本

1. 转到执行标签页
2. 选择你的脚本
3. 点击"运行"开始执行
4. 使用"暂停"暂停，"停止"停止
5. 在日志面板查看日志

### 4. 管理截图

1. 转到设置 > 查看截图
2. 在网格视图中查看所有已捕获的截图
3. 点击截图可查看全屏显示
4. 使用分享按钮分享截图
5. 使用删除按钮移除不需要的截图

### 5. 示例脚本流程

```
1. 延时：2000ms（等待应用加载）
2. 点击：(500, 500)（点击按钮）
3. 延时：1000ms（等待响应）
4. 查找图片：button.png（等待图片）
5. 如果找到：
   - 点击：(500, 500)
6. 循环：10次
   - 点击：(300, 800)
   - 延时：500ms
7. 结束循环
```

## 架构详情

### MVVM 模式

- **模型**：带动作列表的脚本数据类
- **视图**：Jetpack Compose 屏幕
- **视图模型**：使用 StateFlow 进行状态管理

### 整洁架构层级

1. **UI 层**：Compose 屏幕和组件
2. **领域层**：业务逻辑（AutomationEngine）
3. **数据层**：Room 数据库和仓储

### 状态管理

- 带有 `StateFlow` 的视图模型用于响应式 UI
- 用于执行状态的密封类
- 不可变数据模型

## TODO 列表 - 未实现功能

### 高优先级 - 核心自动化功能

- [ ] **循环逻辑实现** - 在 `AutomationEngine.executeActions()` 中实现实际的循环执行，包括循环开始/结束检测
- [ ] **条件分支实现** - 添加实际的条件评估和真/假分支执行
- [ ] **条件序列化** - 修复 `ScriptConverters` 以正确序列化/反序列化 `Condition` 动作中的 `trueBranch` 和 `falseBranch`
- [x] **截图功能** - 在 `takeScreenshot()` 中实现实际的屏幕捕获和文件保存
- [ ] **图像识别（查找图片）** - 使用 OpenCV 或其他图像识别库实现模板匹配
- [ ] **文本输入修复** - 使用 AccessibilityService 或 InputMethodManager 实现向聚焦字段的实际文本输入
- [ ] **颜色匹配** - 实现 `COLOR_MATCH`/`COLOR_NOT_MATCH` 条件的颜色检测和比较逻辑

### 中优先级 - 附加功能

- [ ] **脚本导入/导出** - 添加备份和恢复脚本的功能（推荐 JSON 格式）
- [ ] **脚本分组** - 添加分组/分类功能用于组织脚本
- [ ] **双击动作** - 向 `ScriptAction` 添加 `DoubleTap` 动作类型并在引擎中实现
- [ ] **脚本执行暂停/恢复** - 验证并测试暂停/恢复功能
- [ ] **坐标记录器** - 测试并确保从屏幕记录坐标功能正常工作

### 低优先级 - 增强功能

- [ ] **变量系统** - 添加在脚本中存储和使用变量的支持
- [ ] **OCR 文字识别** - 添加识别屏幕上文字的功能
- [ ] **云共享** - 添加脚本共享功能
- [ ] **动作录制模式** - 添加将用户动作记录为脚本步骤的功能

***

## 已知限制

1. **查找图片功能**：目前只是占位符，没有实际实现
2. **循环与条件**：只存在 UI 存根，没有执行逻辑
3. **无障碍服务**：需要明确的用户权限
4. **后台执行**：需要前台服务通知

## 未来增强

1. 使用 OpenCV 进行高级图像识别
2. OCR 文字识别
3. 用于存储数据的变量系统
4. 导入/导出脚本
5. 云脚本共享
6. 录制模式（记录用户动作）
7. 循环嵌套和复杂条件

## 故障排除

### 无障碍服务不工作

1. 转到设置 > 无障碍
2. 找到 AutoBot RPA
3. 启用服务
4. 授予所有权限

### 脚本不执行

1. 检查无障碍服务是否已启用
2. 确保显示前台服务通知
3. 检查日志中的错误
4. 验证所有动作都有有效参数

### 构建问题

1. 确保安装了 Java 17/21
2. 验证 ANDROID_HOME 是否已设置
3. 检查与 Google Maven 的网络连接
4. 清理 Gradle 缓存：`gradlew.bat clean`

## 许可证

本项目仅供教育和个人使用。请负责任地使用并遵守应用商店政策。

## 联系与支持

如有问题或功能请求，请参考项目仓库。
