package com.ericlam.mc.spectate.camera

import com.ericlam.mc.kotlib.textOf
import net.md_5.bungee.api.ChatMessageType
import org.bukkit.entity.Player

fun Player.sendActionBar(msg: String) {
    this.spigot().sendMessage(ChatMessageType.ACTION_BAR, textOf(msg))
}