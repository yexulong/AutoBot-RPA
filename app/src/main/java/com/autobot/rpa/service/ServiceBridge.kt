package com.autobot.rpa.service

/**
 * 简单的桥接类，用于在 FloatingWindowService 中直接访问 AutomationEngine 功能
 */
object ServiceBridge {
    private var automationEngine: AutomationEngine? = null

    fun setAutomationEngine(engine: AutomationEngine) {
        automationEngine = engine
    }

    fun getAutomationEngine(): AutomationEngine? = automationEngine

    fun stopExecution() {
        automationEngine?.stopExecution()
    }

    fun rerunCurrentScript() {
        automationEngine?.rerunCurrentScript()
    }
}
