package dev.legendsayantan.extendroid.lib


import android.graphics.BitmapFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.math.ceil

/**
 * @author legendsayantan
 */

class UdpServer(private val callback: (Int, Type, ByteArray) -> Unit) {
    private val idToPort = mutableMapOf<Int,Int>()
    private val sockets = mutableMapOf<Int, DatagramSocket>()
    private val clientInfo = mutableMapOf<Int,Pair<InetAddress,Int>>()

    fun create(id:Int): Int {
        val socket = DatagramSocket(0)
        val port = socket.localPort
        idToPort[id] = port
        sockets[port] = socket

        Thread {
            val buffer = ByteArray(MAX_PACKET_SIZE)
            try {
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = packet.data
                    when(data[0]){
                        Type.CONNECTION.ordinal.toByte() -> clientInfo[id] = Pair(packet.address,packet.port)
                        Type.DISCONNECTION.ordinal.toByte() -> clientInfo.remove(id)
                        else -> {callback(id, Type.entries[data[0].toInt()], data.copyOfRange(1,data.size))}
                    }
                }
            }catch (_:Exception){}
        }.start()

        return port
    }

    fun send(id: Int, byteArray: ByteArray) {
        idToPort[id]?.let { port->
            val socket = sockets[port]
            val client = clientInfo[id]
            if (socket != null) {
                if(client!=null){
                    val packet = DatagramPacket(byteArray, byteArray.size.coerceAtMost(MAX_PACKET_SIZE), client.first, client.second)
                    socket.send(packet)
                }
            } else {
                throw IllegalArgumentException("No socket found for port $port")
            }
        }
    }

    fun close(id: Int) {
        idToPort.remove(id)?.let { port->
            val socket = sockets.remove(port)
            socket?.close()
        }
        clientInfo.remove(id)
    }

    fun closeAll() {
        sockets.values.forEach { it.close() }
        sockets.clear()
    }

    enum class Type{
        CONNECTION, MOTIONEVENT, KEYEVENT, DISCONNECTION
    }

    companion object{
        const val MAX_PACKET_SIZE = 65507
    }
}
