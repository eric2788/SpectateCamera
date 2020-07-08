package com.ericlam.mc.spectate.camera

import com.comphenix.packetwrapper.WrapperPlayClientUseEntity
import com.comphenix.packetwrapper.WrapperPlayServerEntityHeadRotation
import com.comphenix.packetwrapper.WrapperPlayServerEntityLook
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.ericlam.mc.kotlib.bukkit.BukkitPlugin

class CameraListener(
        private val p: BukkitPlugin
) : PacketAdapter(p,
        PacketType.Play.Client.USE_ENTITY
) {

    private val filterMap: Map<PacketType, (PacketContainer) -> Int>

    init {
        filterMap = mapOf(
                PacketType.Play.Server.ENTITY_LOOK to { packet -> WrapperPlayServerEntityLook(packet).entityID },
                PacketType.Play.Server.ENTITY_HEAD_ROTATION to { packet -> WrapperPlayServerEntityHeadRotation(packet).entityID }
        )
    }

    private val PacketEvent.entityId: Int
        get() = filterMap[this.packetType]?.invoke(this.packet) ?: -1


    override fun onPacketReceiving(event: PacketEvent?) {
        event ?: return
        p.debug("receiving packet ${event.packetType} to ${event.player.name}")
        if (event.packetType == PacketType.Play.Client.USE_ENTITY) {
            val useEntity = WrapperPlayClientUseEntity(event.packet)
            if (event.player.entityId == useEntity.targetID) {
                p.debug("${event.player.name} is interacting itself, cancelling")
                event.isCancelled = true
            }
        }

    }

    override fun onPacketSending(event: PacketEvent?) {
        event ?: return
        p.debug("sending packet ${event.packetType} to ${event.player.name}")
    }
}