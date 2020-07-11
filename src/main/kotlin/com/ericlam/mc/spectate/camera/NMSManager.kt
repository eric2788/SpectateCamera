package com.ericlam.mc.spectate.camera

import com.comphenix.protocol.wrappers.WrappedDataWatcher
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


object NMSManager {

    fun <T> getDataWatcherSerializer(name: String, type: Class<T>): WrappedDataWatcher.Serializer {
        val registry = getNMSClass("DataWatcherRegistry")
        val field = registry.getDeclaredField(name).also { field -> field.isAccessible = true }
        val handle = field.get(null)
        return WrappedDataWatcher.Serializer(type, handle, false)
    }

    fun getTrackingRange(w: World): Int {
        if (Bukkit.spigot().config.contains("world-settings.${w.name}")){
            return Bukkit.spigot().config.getInt("world-settings.${w.name}.entity-tracking-range.other")
        }
        return Bukkit.spigot().config.getInt("world-settings.default.entity-tracking-range.other")
    }


    private val version
        get() = Bukkit.getServer().javaClass.getPackage().name.replace(".", ",").split(",").toTypedArray()[3] + "."


    @Throws(ClassNotFoundException::class)
    fun getNMSClass(nmsClassString: String): Class<*> {
        val name = "net.minecraft.server.$version$nmsClassString"
        return Class.forName(name)
    }

    @Throws(ClassNotFoundException::class)
    fun getOBCClass(nmsClassString: String): Class<*> {
        val name = "org.bukkit.craftbukkit.$version$nmsClassString"
        return Class.forName(name)
    }


    @Throws(SecurityException::class, NoSuchMethodException::class, NoSuchFieldException::class, IllegalArgumentException::class, IllegalAccessException::class, InvocationTargetException::class)
    fun getConnection(player: Player): Any {
        val getHandle: Method = player::class.java.getMethod("getHandle")
        val nmsPlayer: Any = getHandle.invoke(player)
        val conField: Field = nmsPlayer.javaClass.getField("playerConnection")
        return conField.get(nmsPlayer)
    }
}