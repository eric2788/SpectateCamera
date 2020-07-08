package com.ericlam.mc.spectate.camera

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.nbt.NbtFactory
import com.ericlam.mc.kotlib.KotLib
import com.ericlam.mc.kotlib.bukkit.BukkitPlugin
import com.ericlam.mc.kotlib.config.ConfigManager
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.util.BlockVector

class SpectateCamera : BukkitPlugin() {

    companion object PROPERTIES {

        lateinit var debug: (String) -> Unit
        lateinit var instance: SpectateCamera

        private lateinit var manager: ConfigManager

        object Config {
            val CAMERA: CameraManager.CameraConfig
                get() = manager.getConfig(CameraManager.CameraConfig::class)
            val LANG: CameraManager.Lang
                get() = manager.getConfig(CameraManager.Lang::class)
            val BUTTON: CameraManager.ButtonConfig
                get() = manager.getConfig(CameraManager.ButtonConfig::class)
        }
    }

    override fun enable() {
        instance = this
        debug = ::debug
        manager = KotLib.getConfigFactory(this)
                .register(CameraManager.CameraConfig::class)
                .register(CameraManager.Lang::class)
                .register(CameraManager.ButtonConfig::class)
                .dump()

        registerCmd(CameraCommand)

        ProtocolLibrary.getProtocolManager().addPacketListener(CameraListener(this))

        listen<BlockPlaceEvent> {
            CameraManager.onPlaceCamera(it)
            if (it.block.type.name.endsWith("BUTTON")) {
                val craftItem = MinecraftReflection.getBukkitItemStack(it.itemInHand)
                val nbtwrapper = NbtFactory.fromItemTag(craftItem)
                val nbtCompound = NbtFactory.asCompound(nbtwrapper)
                if (!nbtCompound.containsKey("camera.to")) {
                    debug("the button directed camera name is null")
                    return@listen
                }
                val name = nbtCompound.getString("camera.to")
                Config.BUTTON.buttons[it.block.location.toVector().toBlockVector().toId()] = name
                Config.BUTTON.save()

            }else{
                debug("the placed block is not a button")
            }

        }

        listen<BlockBreakEvent> {
            if (CameraManager.getCamera(it.block) != null) {
                it.isCancelled = true
            }
            Config.BUTTON.buttons.remove(it.block.location.toVector().toBlockVector().toId())?.also { name ->
                Config.BUTTON.save()
                it.isDropItems = false
                it.player.inventory.addItem(CameraManager.asCameraButton(it.block.drops.first(), name))

            }
        }

        listen<PlayerToggleSneakEvent> {
            debug("sneaking for ${it.player.name}: ${it.isSneaking}")
            if (it.isSneaking && CameraManager.isSpectating(it.player)) {
                debug("unwatching for ${it.player.name}")
                CameraManager.unwatchCamera(it.player)
            }
        }

        listen<EntityDamageEvent> {
            val p = it.entity as? Player ?: return@listen
            if (CameraManager.isSpectating(p)) {
                CameraManager.unwatchCamera(p)
            }

        }

        listen<PlayerInteractEvent> {
            if (CameraManager.isSpectating(it.player) && it.clickedBlock != null) {
                it.isCancelled = true
                it.player.sendActionBar(Config.LANG.getPure("no-interact"))
            } else if (it.clickedBlock != null && it.action == Action.RIGHT_CLICK_BLOCK) {
                if (it.clickedBlock?.type?.name?.endsWith("BUTTON") != true) {
                    debug("the clicked block is not a button")
                    return@listen
                }
                Config.BUTTON.buttons[it.clickedBlock!!.location.toVector().toBlockVector().toId()]?.also { s ->
                    CameraManager.watchCamera(it.player, s)
                }

            }
        }

        listen<PlayerInteractEntityEvent> {
            if (CameraManager.isSpectating(it.player)) {
                it.isCancelled = true
                it.player.sendActionBar(Config.LANG.getPure("no-interact"))
            }
        }

        listen<PlayerCommandPreprocessEvent> {
            if (CameraManager.isSpectating(it.player)) {
                it.isCancelled = true
                it.player.sendActionBar(Config.LANG.getPure("no-command"))
            }
        }


    }

    private fun BlockVector.toId(): String {
        return this.toString().replace(".", "-")
    }

    private fun String.toVector(): BlockVector {
        val parse = this.replace("-", ".").split(",").map { s -> s.toDouble() }
        return BlockVector(parse[0], parse[1], parse[2])
    }

}