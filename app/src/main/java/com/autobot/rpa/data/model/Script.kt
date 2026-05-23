package com.autobot.rpa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "scripts")
@TypeConverters(ScriptConverters::class)
data class Script(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val actions: List<ScriptAction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val runCount: Int = 0,
    val lastRunAt: Long? = null
)

sealed class ScriptAction {
    abstract val id: String
    abstract val order: Int

    data class Tap(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val x: Int,
        val y: Int,
        val duration: Int = 100
    ) : ScriptAction()

    data class Swipe(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val duration: Int = 500
    ) : ScriptAction()

    data class LongPress(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val x: Int,
        val y: Int,
        val duration: Int = 1000
    ) : ScriptAction()

    data class TextInput(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val text: String
    ) : ScriptAction()

    data class KeyPress(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val keyCode: Int
    ) : ScriptAction()

    data class Delay(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val milliseconds: Int
    ) : ScriptAction()

    data class Screenshot(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val fileName: String = ""
    ) : ScriptAction()

    data class FindImage(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val templatePath: String,
        val timeout: Int = 5000,
        val saveResult: Boolean = false,
        val resultVarName: String? = null,
        val matchX: Int? = null,
        val matchY: Int? = null,
        val found: Boolean = false,
        val threshold: Double = 0.7,
        val debugMode: Boolean = false
    ) : ScriptAction()

    data class LoopStart(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val times: Int = -1,
        val infinite: Boolean = false
    ) : ScriptAction()

    data class LoopEnd(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0
    ) : ScriptAction()

    data class Condition(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val type: ConditionType,
        val param1: String = "",
        val param2: String = "",
        val param3: String = "",
        val trueBranch: List<ScriptAction> = emptyList(),
        val falseBranch: List<ScriptAction> = emptyList()
    ) : ScriptAction()

    data class Comment(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val order: Int = 0,
        val text: String
    ) : ScriptAction()
}

enum class ConditionType {
    IMAGE_FOUND,
    IMAGE_NOT_FOUND,
    COLOR_MATCH,
    COLOR_NOT_MATCH,
    ALWAYS_TRUE,
    ALWAYS_FALSE
}

class ScriptConverters {
    @TypeConverter
    fun fromActionList(actions: List<ScriptAction>): String {
        val jsonArray = JSONArray()
        actions.forEach { action ->
            jsonArray.put(actionToJson(action))
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toActionList(json: String): List<ScriptAction> {
        if (json.isEmpty()) return emptyList()
        val jsonArray = JSONArray(json)
        val actions = mutableListOf<ScriptAction>()
        for (i in 0 until jsonArray.length()) {
            actions.add(jsonToAction(jsonArray.getJSONObject(i)))
        }
        return actions
    }

    private fun actionToJson(action: ScriptAction): JSONObject {
        val json = JSONObject()
        when (action) {
            is ScriptAction.Tap -> {
                json.put("type", "Tap")
                json.put("x", action.x)
                json.put("y", action.y)
                json.put("duration", action.duration)
            }
            is ScriptAction.Swipe -> {
                json.put("type", "Swipe")
                json.put("startX", action.startX)
                json.put("startY", action.startY)
                json.put("endX", action.endX)
                json.put("endY", action.endY)
                json.put("duration", action.duration)
            }
            is ScriptAction.LongPress -> {
                json.put("type", "LongPress")
                json.put("x", action.x)
                json.put("y", action.y)
                json.put("duration", action.duration)
            }
            is ScriptAction.TextInput -> {
                json.put("type", "TextInput")
                json.put("text", action.text)
            }
            is ScriptAction.KeyPress -> {
                json.put("type", "KeyPress")
                json.put("keyCode", action.keyCode)
            }
            is ScriptAction.Delay -> {
                json.put("type", "Delay")
                json.put("milliseconds", action.milliseconds)
            }
            is ScriptAction.Screenshot -> {
                json.put("type", "Screenshot")
                json.put("fileName", action.fileName)
            }
            is ScriptAction.FindImage -> {
                json.put("type", "FindImage")
                json.put("templatePath", action.templatePath)
                json.put("timeout", action.timeout)
                json.put("saveResult", action.saveResult)
                json.put("resultVarName", action.resultVarName ?: "")
                json.put("matchX", action.matchX ?: JSONObject.NULL)
                json.put("matchY", action.matchY ?: JSONObject.NULL)
                json.put("found", action.found)
                json.put("threshold", action.threshold)
                json.put("debugMode", action.debugMode)
            }
            is ScriptAction.LoopStart -> {
                json.put("type", "LoopStart")
                json.put("times", action.times)
                json.put("infinite", action.infinite)
            }
            is ScriptAction.LoopEnd -> {
                json.put("type", "LoopEnd")
            }
            is ScriptAction.Condition -> {
                json.put("type", "Condition")
                json.put("conditionType", action.type.name)
                json.put("param1", action.param1)
                json.put("param2", action.param2)
                json.put("param3", action.param3)
            }
            is ScriptAction.Comment -> {
                json.put("type", "Comment")
                json.put("text", action.text)
            }
        }
        json.put("id", action.id)
        json.put("order", action.order)
        return json
    }

    private fun jsonToAction(json: JSONObject): ScriptAction {
        val type = json.getString("type")
        val id = json.getString("id")
        val order = json.getInt("order")
        return when (type) {
            "Tap" -> ScriptAction.Tap(id, order, json.getInt("x"), json.getInt("y"), json.optInt("duration", 100))
            "Swipe" -> ScriptAction.Swipe(id, order, json.getInt("startX"), json.getInt("startY"), json.getInt("endX"), json.getInt("endY"), json.optInt("duration", 500))
            "LongPress" -> ScriptAction.LongPress(id, order, json.getInt("x"), json.getInt("y"), json.optInt("duration", 1000))
            "TextInput" -> ScriptAction.TextInput(id, order, json.getString("text"))
            "KeyPress" -> ScriptAction.KeyPress(id, order, json.getInt("keyCode"))
            "Delay" -> ScriptAction.Delay(id, order, json.getInt("milliseconds"))
            "Screenshot" -> ScriptAction.Screenshot(id, order, json.optString("fileName", ""))
            "FindImage" -> ScriptAction.FindImage(id, order, json.getString("templatePath"), json.optInt("timeout", 5000), json.optBoolean("saveResult", false), json.optString("resultVarName", ""), if (json.isNull("matchX")) null else json.optInt("matchX"), if (json.isNull("matchY")) null else json.optInt("matchY"), json.optBoolean("found", false), json.optDouble("threshold", 0.7), json.optBoolean("debugMode", false))
            "LoopStart" -> ScriptAction.LoopStart(id, order, json.optInt("times", -1), json.optBoolean("infinite", false))
            "LoopEnd" -> ScriptAction.LoopEnd(id, order)
            "Condition" -> ScriptAction.Condition(id, order, ConditionType.valueOf(json.getString("conditionType")), json.optString("param1", ""), json.optString("param2", ""), json.optString("param3", ""))
            "Comment" -> ScriptAction.Comment(id, order, json.getString("text"))
            else -> throw IllegalArgumentException("Unknown action type: $type")
        }
    }
}
