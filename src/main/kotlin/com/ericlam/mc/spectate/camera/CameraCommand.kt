package com.ericlam.mc.spectate.camera

import com.ericlam.mc.kotlib.command.BukkitCommand
import com.ericlam.mc.kotlib.msgFormat
import org.bukkit.entity.Player

object CameraCommand : BukkitCommand(
        name = "camera",
        description = "camera command",
        child = arrayOf(
                BukkitCommand(
                        name = "add",
                        description = "add camera",
                        placeholders = arrayOf("name"),
                        permission = "camera.create",
                        optionalPlaceholders = arrayOf("public")
                ) { sender, args ->
                    val player = (sender as? Player) ?: run {
                        sender.sendMessage("not player")
                        return@BukkitCommand
                    }
                    val name = args[0].toLowerCase()
                    if (CameraManager.hasCamera(name)) {
                        player.sendMessage(SpectateCamera.PROPERTIES.Config.LANG["contained"].msgFormat(name))
                        return@BukkitCommand
                    }
                    val public = args.getOrNull(1)?.toBoolean() ?: false
                    player.inventory.addItem(CameraManager.generateCamera(player, name, public))
                    player.sendMessage(SpectateCamera.PROPERTIES.Config.LANG["created"].msgFormat(name))
                },
                BukkitCommand(
                        name = "watch",
                        description = "watch the camera",
                        placeholders = arrayOf("name")
                ) { sender, args ->
                    val player = (sender as? Player) ?: run {
                        sender.sendMessage("not player")
                        return@BukkitCommand
                    }
                    CameraManager.watchCamera(player, args[0].toLowerCase())

                },
                BukkitCommand(
                        name = "remove",
                        description = "watch the camera",
                        placeholders = arrayOf("name")
                ) { sender, args ->
                    val name = args[0].toLowerCase()
                    val player = (sender as? Player) ?: run {
                        sender.sendMessage("not player")
                        return@BukkitCommand
                    }
                    CameraManager.removeCamera(player, name)
                },
                BukkitCommand(
                        name = "button",
                        description = "set hand button as camera switch",
                        placeholders = arrayOf("name")
                ) { sender, args ->
                    val name = args[0].toLowerCase()
                    val player = (sender as? Player) ?: run {
                        sender.sendMessage("not player")
                        return@BukkitCommand
                    }
                    val item = player.inventory.itemInMainHand
                    if (!item.type.toString().toLowerCase().endsWith("button")) {
                        player.sendMessage(SpectateCamera.PROPERTIES.Config.LANG["not-button"])
                        return@BukkitCommand
                    }
                    CameraManager.asCameraButton(item, name)
                    player.sendMessage(SpectateCamera.PROPERTIES.Config.LANG["button-set"].msgFormat(name))
                }
        )
)