package dev.legendsayantan.extendroid.echo


import dev.legendsayantan.extendroid.echo.EchoNetworkUtils.Companion.mappings
import dev.legendsayantan.extendroid.lib.MediaCore
import dev.legendsayantan.extendroid.services.IRootService
import org.webrtc.DataChannel
import kotlin.collections.forEach
import kotlin.collections.plus
import kotlin.collections.set

/**
 * @author legendsayantan
 */
class RemoteSessionHandler {
    companion object{

        fun createDataChannelPacket(string: String,type: PacketType): DataChannel.Buffer{
            val combined = (mappings.entries.find { it.value==type.typename }?.key?: type.typename) +string
            return DataChannel.Buffer(java.nio.ByteBuffer.wrap(combined.toByteArray()),false)
        }

        enum class PacketType(val typename: String) {
            InstalledApps("InstalledApps"),
            RunningApps("RunningApps"),
            RunApp("RunApp")
        }

        fun processDataMessage(connectionId: String, message: String, mediaCore: MediaCore, svc: IRootService) {
            val type = mappings[message[0].toString()]?.let { PacketType.valueOf(it) }
            if(type==null) return
            val content = message.substring(1)
            when(type){
                PacketType.RunApp -> {
                    //content is packageName here
                    mediaCore.echoDisplayIds[connectionId]?.let { displayId ->
                        svc.launchAppOnDisplay(content, displayId)
                        mediaCore.appRemoteAccessHistory[connectionId]?.let {
                            if(!it.contains(content)) {
                                mediaCore.appRemoteAccessHistory[connectionId] = it + content
                            }
                        } ?: run {
                            mediaCore.appRemoteAccessHistory[connectionId] = listOf(content)
                        }
                    }
                }
                else -> {}
            }
        }

        fun shutDownRemoteSession(
            connectionId: String,
            mediaCore: MediaCore,
            svc: IRootService
        ) {
            mediaCore.appRemoteAccessHistory[connectionId]?.forEach { appPackage->
                svc.exitTasks(appPackage)
            }
            mediaCore.appRemoteAccessHistory = hashMapOf()
        }
    }
}