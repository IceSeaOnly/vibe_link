package com.vibelink.client

object ControlButtonDefaults {
    fun defaultButtons(): List<ControlButton> = listOf(
        ControlButton("send_text", "Send Text", "sendText"),
        ControlButton("backspace", "Backspace", "backspace"),
        ControlButton("keyboard", "Keyboard", "keyboard"),
        ControlButton("select_all", "Select All", "selectAll"),
        ControlButton("enter", "Ent", "enter"),
        ControlButton("cmd_enter", "Cmd+Ent", "cmdEnter"),
        ControlButton("copy", "Copy", "copy"),
        ControlButton("paste", "Paste", "paste"),
        ControlButton("escape", "ESC", "escape"),
        ControlButton("interrupt", "Break", "interrupt"),
        ControlButton("undo", "Undo", "undo"),
        ControlButton("close", "Close", "close")
    )
}
