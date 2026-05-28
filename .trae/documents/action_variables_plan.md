# 所有动作实现变量功能计划

## 1. 现状分析

当前项目中：
- 只有 Tap、Swipe、LongPress 动作支持变量（通过 xStr、yStr 等属性）
- 已有 `variableStore` 用于存储变量
- FindImage 和 FindText 可以保存结果到变量
- TextInput、Delay、KeyPress、Screenshot 等动作还不支持变量

## 2. 需要修改的文件

### 2.1 Script.kt
- 为以下动作添加变量支持字段：
  - TextInput: 添加 textStr 字段
  - Delay: 添加 millisecondsStr 字段
  - KeyPress: 添加 keyCodeStr 字段
  - Screenshot: 添加 fileNameStr 字段
  - Comment: 添加 textStr 字段
- 更新 ScriptConverters 的序列化和反序列化逻辑
- 添加新动作类型：SetVariable（设置变量）

### 2.2 AutomationEngine.kt
- 添加通用的变量解析函数（支持字符串、整数等类型）
- 更新 executeAction 函数，为所有动作添加变量解析逻辑
- 实现 SetVariable 动作的执行逻辑

### 2.3 ActionListAdapter.kt
- 更新 getActionInfo 函数，显示变量支持信息

## 3. 实现步骤

### 步骤 1: 更新 Script.kt 数据模型
- 为现有动作添加变量字段
- 添加 SetVariable 动作类型
- 更新 ScriptConverters

### 步骤 2: 更新 AutomationEngine.kt
- 添加 resolveString 函数
- 添加 resolveInt 函数
- 更新各个动作的执行逻辑
- 实现 SetVariable 动作

### 步骤 3: 更新 UI 组件
- 更新 ActionListAdapter 显示变量信息

## 4. 变量使用格式

支持以下格式：
- `${varName}` - 引用变量
- `${varName.property}` - 引用变量的属性（如 FindImage 结果的 x、y）
- 纯数字/字符串直接使用原值
