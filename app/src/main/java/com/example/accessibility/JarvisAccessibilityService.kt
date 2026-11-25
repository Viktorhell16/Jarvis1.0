package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenNodeSnapshot(
    val index: Int,
    val label: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val viewId: String
)

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strictly on-demand: NO background polling or passive scraping.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun captureOnDemandSnapshot(): List<ScreenNodeSnapshot> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<ScreenNodeSnapshot>()
        traverse(root, result)
        return result
    }

    private fun traverse(node: AccessibilityNodeInfo?, out: MutableList<ScreenNodeSnapshot>) {
        if (node == null || out.size >= 80) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val label = text.ifEmpty { desc }
        if (label.isNotEmpty() || node.isClickable || node.isEditable) {
            out.add(
                ScreenNodeSnapshot(
                    index = out.size,
                    label = label.ifEmpty { node.className?.toString()?.substringAfterLast('.') ?: "Element" },
                    className = node.className?.toString() ?: "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    viewId = node.viewIdResourceName ?: ""
                )
            )
        }
        for (i in 0 until node.childCount) {
            traverse(node.getChild(i), out)
        }
    }

    fun clickNodeByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(targetText)
        for (node in matches) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun inputTextIntoFocusedOrFirstEditable(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val editable = findFirstEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            findFirstEditable(node.getChild(i))?.let { return it }
        }
        if (node.isEditable) return node
        return null
    }

    fun performGlobalNavigation(actionType: String): Boolean {
        return when (actionType) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            else -> false
        }
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
