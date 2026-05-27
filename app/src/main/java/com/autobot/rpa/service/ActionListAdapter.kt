package com.autobot.rpa.service

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.autobot.rpa.R
import com.autobot.rpa.data.model.ScriptAction

class ActionListAdapter(
    private var actions: List<ScriptAction> = emptyList(),
    private var currentActionIndex: Int = -1,
    private var showOnlyCurrentAction: Boolean = false
) : RecyclerView.Adapter<ActionListAdapter.ActionViewHolder>() {

    inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvActionIndex: TextView = itemView.findViewById(R.id.tv_action_index)
        val ivActionIcon: ImageView = itemView.findViewById(R.id.iv_action_icon)
        val tvActionTitle: TextView = itemView.findViewById(R.id.tv_action_title)
        val tvActionDescription: TextView = itemView.findViewById(R.id.tv_action_description)
        val itemRoot: View = itemView.findViewById(R.id.item_root)
        val progressBackground: View = itemView.findViewById(R.id.progress_background)
        val contentLayout: View = itemView.findViewById(R.id.content_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val actualPosition = if (showOnlyCurrentAction) {
            if (currentActionIndex >= 0 && currentActionIndex < actions.size) {
                currentActionIndex
            } else if (actions.isNotEmpty()) {
                0 // 默认显示第一个动作
            } else {
                -1
            }
        } else {
            position
        }
        
        if (actualPosition < 0 || actualPosition >= actions.size) {
            return
        }
        
        val action = actions[actualPosition]
        
        // 设置动作序号
        holder.tvActionIndex.text = (actualPosition + 1).toString()
        
        // 获取动作信息
        val (iconRes, title, description) = getActionInfo(action)
        
        holder.ivActionIcon.setImageResource(iconRes)
        holder.tvActionTitle.text = title
        holder.tvActionDescription.text = description
        
        // 设置高亮和进度条效果
        val isCurrentAction = actualPosition == currentActionIndex
        val isCompletedAction = actualPosition < currentActionIndex
        
        if (isCurrentAction) {
            // 当前动作：高亮显示 + 进度条填满
            holder.contentLayout.isSelected = true
            holder.progressBackground.scaleX = 1f
        } else if (isCompletedAction) {
            // 已完成的动作：显示完整进度条
            holder.contentLayout.isSelected = false
            holder.progressBackground.scaleX = 1f
        } else {
            // 未执行的动作：无进度条
            holder.contentLayout.isSelected = false
            holder.progressBackground.scaleX = 0f
        }
    }

    override fun getItemCount(): Int {
        return if (showOnlyCurrentAction) {
            if (actions.isNotEmpty()) {
                1
            } else {
                0
            }
        } else {
            actions.size
        }
    }

    fun updateActions(newActions: List<ScriptAction>) {
        this.actions = newActions
        notifyDataSetChanged()
    }

    fun setCurrentActionIndex(index: Int) {
        val oldIndex = currentActionIndex
        currentActionIndex = index
        
        if (showOnlyCurrentAction) {
            notifyDataSetChanged()
        } else {
            if (oldIndex >= 0 && oldIndex < actions.size) {
                notifyItemChanged(oldIndex)
            }
            
            if (index >= 0 && index < actions.size) {
                notifyItemChanged(index)
            }
        }
    }
    
    fun setShowOnlyCurrentAction(showOnly: Boolean) {
        this.showOnlyCurrentAction = showOnly
        notifyDataSetChanged()
    }

    private fun getActionInfo(action: ScriptAction): Triple<Int, String, String> {
        return when (action) {
            is ScriptAction.Tap -> Triple(
                android.R.drawable.ic_menu_mylocation,
                "Tap",
                "Position: (${action.x}, ${action.y})"
            )
            is ScriptAction.Swipe -> Triple(
                android.R.drawable.ic_menu_sort_by_size,
                "Swipe",
                "From (${action.startX}, ${action.startY}) to (${action.endX}, ${action.endY})"
            )
            is ScriptAction.LongPress -> Triple(
                android.R.drawable.ic_menu_call,
                "Long Press",
                "Position: (${action.x}, ${action.y}), Duration: ${action.duration}ms"
            )
            is ScriptAction.TextInput -> Triple(
                android.R.drawable.ic_menu_edit,
                "Text Input",
                if (action.text.length > 20) "${action.text.substring(0, 20)}..." else "\"${action.text}\""
            )
            is ScriptAction.KeyPress -> Triple(
                android.R.drawable.ic_menu_preferences,
                "Key Press",
                "KeyCode: ${action.keyCode}"
            )
            is ScriptAction.Delay -> Triple(
                android.R.drawable.ic_menu_recent_history,
                "Delay",
                "${action.milliseconds}ms"
            )
            is ScriptAction.Screenshot -> Triple(
                android.R.drawable.ic_menu_camera,
                "Screenshot",
                action.fileName.ifEmpty { "Capture" }
            )
            is ScriptAction.FindImage -> Triple(
                android.R.drawable.ic_menu_gallery,
                "Find Image",
                "Timeout: ${action.timeout}ms"
            )
            is ScriptAction.FindText -> Triple(
                android.R.drawable.ic_menu_search,
                "Find Text",
                if (action.targetText.length > 20) "${action.targetText.substring(0, 20)}..." else "\"${action.targetText}\""
            )
            is ScriptAction.LoopStart -> Triple(
                android.R.drawable.ic_menu_rotate,
                "Loop Start",
                if (action.infinite) "Infinite loop" else "${action.times} times"
            )
            is ScriptAction.LoopEnd -> Triple(
                android.R.drawable.ic_menu_rotate,
                "Loop End",
                "End of loop"
            )
            is ScriptAction.Condition -> Triple(
                android.R.drawable.ic_menu_info_details,
                "Condition",
                when (action.type) {
                    com.autobot.rpa.data.model.ConditionType.IMAGE_FOUND -> "If image found"
                    com.autobot.rpa.data.model.ConditionType.IMAGE_NOT_FOUND -> "If image not found"
                    com.autobot.rpa.data.model.ConditionType.TEXT_FOUND -> "If text found"
                    com.autobot.rpa.data.model.ConditionType.TEXT_NOT_FOUND -> "If text not found"
                    com.autobot.rpa.data.model.ConditionType.COLOR_MATCH -> "If color matches"
                    com.autobot.rpa.data.model.ConditionType.COLOR_NOT_MATCH -> "If color doesn't match"
                    com.autobot.rpa.data.model.ConditionType.ALWAYS_TRUE -> "Always true"
                    com.autobot.rpa.data.model.ConditionType.ALWAYS_FALSE -> "Always false"
                }
            )
            is ScriptAction.Comment -> Triple(
                android.R.drawable.ic_menu_day,
                "Comment",
                if (action.text.length > 30) "${action.text.substring(0, 30)}..." else action.text
            )
        }
    }
}
