package com.ericlam.mc.spectate.camera

import com.comphenix.packetwrapper.*
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.*
import com.comphenix.protocol.wrappers.nbt.NbtFactory
import com.ericlam.mc.kotlib.bukkit.BukkitPlugin
import com.ericlam.mc.kotlib.config.Prefix
import com.ericlam.mc.kotlib.config.Resource
import com.ericlam.mc.kotlib.config.dto.ConfigFile
import com.ericlam.mc.kotlib.config.dto.MessageFile
import com.ericlam.mc.kotlib.msgFormat
import com.ericlam.mc.kotlib.textOf
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.md_5.bungee.api.ChatMessageType
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Rotatable
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.set


object CameraManager {

    @Resource(locate = "cameras.yml")
    data class CameraConfig(
            val cameras: MutableMap<String, Camera> = mutableMapOf()
    ) : ConfigFile() {
        data class Camera(
                val owner: UUID,
                val entity: UUID,
                val camera: Location,
                val public: Boolean = false
        )
    }

    @Resource(locate = "buttons.yml")
    data class ButtonConfig(
            val buttons: MutableMap<String, String> = mutableMapOf()
    ) : ConfigFile()


    @Prefix(path = "prefix")
    @Resource(locate = "lang.yml")
    class Lang : MessageFile()

    private val spectating: MutableSet<Player> = HashSet()

    private const val CAMERA_SKIN: String = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWE3ZDJhN2ZiYjRkMzdiNGQ1M2ZlODc3NTcxMjhlNWVmNjZlYzIzZDdmZjRmZTk5NDQ1NDZkYmM4Y2U3NzcifX19"

    fun generateCamera(player: OfflinePlayer, name: String, public: Boolean = false): ItemStack {
        val camera = BukkitPlugin.plugin.itemStack(
                material = Material.PLAYER_HEAD,
                display = SpectateCamera.PROPERTIES.Config.LANG.getPure("show-name").msgFormat(name)
        ).apply {
            val skullMeta = itemMeta as SkullMeta
            val gp = GameProfile(UUID.randomUUID(), "Camera")
            gp.properties.put("textures", Property("textures", CAMERA_SKIN))
            try{
                val f = skullMeta.javaClass.getDeclaredField("profile").also { it.isAccessible = true }
                f.set(skullMeta, gp)
            }catch (e: Exception){
                SpectateCamera.instance.warning(e.message)
            }
            itemMeta = skullMeta
        }
        val craftItem = MinecraftReflection.getBukkitItemStack(camera)
        if (craftItem == null || craftItem.type == Material.AIR) throw IllegalStateException("craftItem is null or air, skipped")
        val wrapper = NbtFactory.fromItemTag(craftItem)
        val compound = NbtFactory.asCompound(wrapper)
        compound.put("camera.owner", player.uniqueId.toString())
        compound.put("camera.name", name)
        compound.put("camera.public", public.toByte())
        NbtFactory.setItemTag(craftItem, compound)
        return craftItem
    }


    fun asCameraButton(item: ItemStack, name: String): ItemStack {
        if (!item.type.toString().toLowerCase().endsWith("button")) throw IllegalStateException("${item.type} is not a button")
        val craftItem = MinecraftReflection.getBukkitItemStack(item)
        val nbtwrapper = NbtFactory.fromItemTag(craftItem)
        val nbtCompound = NbtFactory.asCompound(nbtwrapper)
        nbtCompound.put("camera.to", name)
        NbtFactory.setItemTag(craftItem, nbtCompound)
        return craftItem
    }

    fun hasCamera(name: String): Boolean = SpectateCamera.PROPERTIES.Config.CAMERA.cameras.containsKey(name)

    fun isSpectating(p: Player): Boolean = spectating.contains(p)

    fun getCamera(b: Block): String? = SpectateCamera.PROPERTIES.Config.CAMERA.cameras.entries.find { (_, c) -> c.camera == b.location }?.key

    fun isSpectating(entityId: Int): Boolean = spectating.any { p -> p.entityId == entityId }

    fun unwatchCamera(p: Player) {

        val deletePlayerPacket = WrapperPlayServerEntityDestroy()
        deletePlayerPacket.setEntityIds(IntArray(1) { p.toUniqueId() })

        val cameraPacket = WrapperPlayServerCamera()
        cameraPacket.cameraId = p.entityId

        cameraPacket.sendPacket(p)
        deletePlayerPacket.sendPacket(p)

        spectating.remove(p)
    }

    fun watchCamera(p: Player, name: String) {
        with(SpectateCamera.PROPERTIES.Config) {
            val camera = CAMERA.cameras[name] ?: let {
                p.sendMessage(LANG["not-found"])
                return
            }
            if (camera.owner != p.uniqueId && !camera.public && !p.hasPermission("camera.use.other")) {
                p.sendMessage(LANG["not-owner"])
                return
            }
            SpectateCamera.debug("client view distance: ${p.clientViewDistance}, checking: ${NMSManager.getTrackingRange(p.world)}")
            val armorStand = Bukkit.getEntity(camera.entity)?.takeIf { e -> e.location.distance(p.location) <= NMSManager.getTrackingRange(p.world) } ?: let {
                p.sendMessage(LANG["error"])
                return
            }

            val invisiblePacket = WrapperPlayServerEntityMetadata()
            invisiblePacket.entityID = p.entityId
            val dataWatcher = WrappedDataWatcher()
            val serializer = NMSManager.getDataWatcherSerializer("a", Byte::class.java)
            dataWatcher.setObject(0, serializer, 0x20.toByte())
            invisiblePacket.metadata = dataWatcher.watchableObjects

            val fakePlayerPacket = WrapperPlayServerPlayerInfo()
            fakePlayerPacket.data = listOf(p.toInfoData())
            fakePlayerPacket.action = EnumWrappers.PlayerInfoAction.ADD_PLAYER

            val spawnFakePlayerPacket = WrapperPlayServerNamedEntitySpawn()
            spawnFakePlayerPacket.entityID = p.toUniqueId()
            spawnFakePlayerPacket.playerUUID = p.uniqueId
            spawnFakePlayerPacket.x = p.location.x
            spawnFakePlayerPacket.y = p.location.y
            spawnFakePlayerPacket.z = p.location.z
            spawnFakePlayerPacket.pitch = p.location.pitch
            spawnFakePlayerPacket.yaw = p.location.yaw
            spawnFakePlayerPacket.position = p.location.toVector()

            val cameraPacket = WrapperPlayServerCamera()
            cameraPacket.cameraId = armorStand.entityId

            val entityPacket = WrapperPlayServerEntity()
            entityPacket.entityID = armorStand.entityId

            entityPacket.sendPacket(p)
            invisiblePacket.sendPacket(p)
            cameraPacket.sendPacket(p)
            fakePlayerPacket.sendPacket(p)
            spawnFakePlayerPacket.sendPacket(p)

            spectating.add(p)
            p.sendMessage(LANG["entered"].msgFormat(name))
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, textOf(LANG.getPure("leave-hint")))
        }

    }

    fun removeCamera(p: Player, name: String): Boolean {
        with(SpectateCamera.PROPERTIES.Config) {
            val camera = CAMERA.cameras[name] ?: let {
                p.sendMessage(LANG["not-found"])
                return false
            }
            if (camera.owner != p.uniqueId && !p.hasPermission("camera.remove.other")) {
                p.sendMessage(LANG["not-owner"])
                return false
            }
            val armorStand = p.world.getEntitiesByClass(ArmorStand::class.java).find { it.uniqueId == camera.entity } ?: let {
                p.sendMessage(LANG["error"])
                return false
            }
            armorStand.remove()
            camera.camera.block.type = Material.AIR
            val result = CAMERA.cameras.remove(name) != null
            p.inventory.addItem(generateCamera(p, name))
            CAMERA.save()
            p.sendMessage(LANG["deleted"].msgFormat(name))
            return result
        }


    }

    fun onPlaceCamera(e: BlockPlaceEvent) {
        val item = e.itemInHand
        val b = e.block
        val bFace = when (val data = b.blockData) {
            is Rotatable -> data.rotation
            is Directional -> data.facing
            else -> {
                SpectateCamera.debug("this block data is not a skull, skipped")
                return
            }
        }

        val craftItem = MinecraftReflection.getBukkitItemStack(item)
        if (craftItem == null || craftItem.type == Material.AIR) throw IllegalStateException("craftItem is null or air, skipped")
        val wrapper = NbtFactory.fromItemOptional(craftItem)
        if (!wrapper.isPresent) {
            SpectateCamera.debug("item has no nbt, skipped")
            return
        }
        val compound = NbtFactory.asCompound(wrapper.get())
        if (!compound.containsKey("camera.owner")) {
            SpectateCamera.debug("camera owner not exist, skipped")
            return
        }
        val name = compound.getString("camera.name") ?: let {
            SpectateCamera.debug("camera name is not exist, skipped")
            return
        }
        val public = compound.getByte("camera.public").toBoolean()
        val uuid = UUID.fromString(compound.getString("camera.owner"))
        val player = e.player
        val stand = b.world.spawnEntity(b.location, EntityType.ARMOR_STAND) as ArmorStand
        stand.isInvulnerable = true
        stand.isVisible = false
        stand.isCustomNameVisible = true
        stand.setGravity(false)
        stand.customName = SpectateCamera.PROPERTIES.Config.LANG.getPure("show-name").msgFormat(name)
        stand.removeWhenFarAway = false
        stand.setRotation(bFace.toYaw(b.blockData is Rotatable), 0f)
        with(SpectateCamera.PROPERTIES.Config) {
            CAMERA.cameras[name] = CameraConfig.Camera(uuid, stand.uniqueId, b.location, public)
            CAMERA.save()
            player.sendMessage(LANG["created"].msgFormat(name))
        }
        player.inventory.removeItem(item)
    }

    private fun BlockFace.toYaw(rotatable: Boolean): Float = (when (if (rotatable) this.oppositeFace else this) {
        BlockFace.SOUTH, BlockFace.SELF, BlockFace.UP, BlockFace.DOWN -> 0.0
        BlockFace.SOUTH_SOUTH_WEST -> 0.5
        BlockFace.SOUTH_WEST -> 1.0
        BlockFace.WEST_SOUTH_WEST -> 1.5
        BlockFace.WEST -> 2.0
        BlockFace.WEST_NORTH_WEST -> 2.5
        BlockFace.NORTH_WEST -> 3.0
        BlockFace.NORTH_NORTH_WEST -> 3.5
        BlockFace.NORTH -> 4.0
        BlockFace.NORTH_NORTH_EAST -> 4.5
        BlockFace.NORTH_EAST -> 5.0
        BlockFace.EAST_NORTH_EAST -> 5.5
        BlockFace.EAST -> 6.0
        BlockFace.EAST_SOUTH_EAST -> 6.5
        BlockFace.SOUTH_EAST -> 7.0
        BlockFace.SOUTH_SOUTH_EAST -> 7.5
    } * 45).toFloat()

    private fun Player.toInfoData(): PlayerInfoData {
        return PlayerInfoData(
                WrappedGameProfile.fromPlayer(this),
                5,
                EnumWrappers.NativeGameMode.fromBukkit(this.gameMode),
                WrappedChatComponent.fromText(this.displayName)
        )
    }

    private fun Player.toUniqueId(): Int {
        return this.uniqueId.hashCode() + this.name.hashCode()
    }

    private fun Boolean.toByte(): Byte = if (this) 1 else 0

    private fun Byte.toBoolean(): Boolean = this == 1.toByte()

}