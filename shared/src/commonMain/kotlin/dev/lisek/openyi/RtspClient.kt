package dev.lisek.openyi

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

data class RtspEndpoint(
    val host: String,
    val port: Int = 554,
    val path: String = "/live"
)

data class RtpPacket(
    val payload: ByteArray,
    val sequenceNumber: Int,
    val timestamp: Long,
    val marker: Boolean
)

/**
 * Opens an RTSP session whose media is transported as RTP over client UDP ports.
 *
 * This class deliberately stops at RTP packets. Decoding and rendering H.264/H.265
 * belongs to a platform media component; the negotiated transport is reusable by it.
 */
@OptIn(DelicateCoroutinesApi::class)
class RtspClient(
    private val endpoint: RtspEndpoint,
    private val selector: SelectorManager
) {
    private var controlRead: ByteReadChannel? = null
    private var controlWrite: ByteWriteChannel? = null
    private var controlSocket: io.ktor.network.sockets.Socket? = null
    private var rtpSocket: io.ktor.network.sockets.BoundDatagramSocket? = null
    private var sessionId: String? = null
    private var cSeq = 0

    suspend fun start(): ReceiveChannel<RtpPacket> {
        val control = runStage("connect to ${endpoint.host}:${endpoint.port}") {
            aSocket(selector).tcp().connect(endpoint.host, endpoint.port)
        }
        controlSocket = control
        controlRead = control.openReadChannel()
        controlWrite = control.openWriteChannel(autoFlush = true)

        val describe = runStage("DESCRIBE ${uri()}") {
            request(
                method = "DESCRIBE",
                uri = uri(),
                headers = mapOf("Accept" to "application/sdp")
            ).also { response ->
                check(response.code in 200..299) {
                    "RTSP DESCRIBE returned ${response.code}"
                }
            }
        }
        val track = parseTrackUri(
            body = describe.body,
            baseUri = describe.headers["Content-Base"] ?: uri()
        )
        val rtp = runStage("bind local UDP socket") {
            aSocket(selector).udp().bind(InetSocketAddress("0.0.0.0", 0))
        }
        rtpSocket = rtp
        val localPort = (rtp.localAddress as InetSocketAddress).port

        runStage("SETUP $track") {
            request(
                method = "SETUP",
                uri = track,
                headers = mapOf(
                    "Transport" to "RTP/AVP/UDP;unicast;client_port=$localPort-${localPort + 1}"
                )
            ).also { response ->
                check(response.code in 200..299) {
                    "RTSP SETUP returned ${response.code}"
                }
                sessionId = response.headers["Session"]?.substringBefore(';')
            }
        }
        runStage("PLAY ${uri()}") {
            request("PLAY", uri()).also { response ->
                check(response.code in 200..299) {
                    "RTSP PLAY returned ${response.code}"
                }
            }
        }

        return receivePackets(rtp)
    }

    private suspend fun <T> runStage(name: String, action: suspend () -> T): T {
        return try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val detail = exception.message?.takeIf { it.isNotBlank() } ?: exception::class.simpleName
            throw IllegalStateException("RTSP $name failed: $detail", exception)
        }
    }

    suspend fun stop() {
        runCatching {
            if (sessionId != null) request("TEARDOWN", uri())
        }
        controlSocket?.close()
        rtpSocket?.close()
        controlSocket = null
        rtpSocket = null
        sessionId = null
    }

    private suspend fun receivePackets(
        socket: io.ktor.network.sockets.BoundDatagramSocket
    ): ReceiveChannel<RtpPacket> {
        val packets = Channel<RtpPacket>(capacity = 32)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                while (!packets.isClosedForSend) {
                    val datagram = socket.receive()
                    @Suppress("DEPRECATION")
                    val bytes = datagram.packet.readBytes()
                    parseRtp(bytes)?.let { packets.send(it) }
                }
            } finally {
                packets.close()
            }
        }
        return packets
    }

    private suspend fun request(
        method: String,
        uri: String,
        headers: Map<String, String> = emptyMap()
    ): RtspResponse {
        val write = requireNotNull(controlWrite)
        val read = requireNotNull(controlRead)
        val request = buildString {
            append("$method $uri RTSP/1.0\r\n")
            append("CSeq: ${++cSeq}\r\n")
            sessionId?.let { append("Session: $it\r\n") }
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }.encodeToByteArray()
        write.writeFully(request)
        return readResponse(read)
    }

    private suspend fun readResponse(read: ByteReadChannel): RtspResponse {
        val bytes = mutableListOf<Byte>()
        val buffer = ByteArray(1024)
        while (true) {
            val count = read.readAvailable(buffer)
            if (count == -1) error("RTSP control connection closed")
            bytes += buffer.take(count)
            if (bytes.windowed(4).any { it.toByteArray().contentEquals("\r\n\r\n".encodeToByteArray()) }) {
                break
            }
        }
        val text = bytes.toByteArray().decodeToString()
        val headerEnd = text.indexOf("\r\n\r\n")
        val headerLines = text.substring(0, headerEnd).lines()
        val code = headerLines.first().split(' ').getOrNull(1)?.toIntOrNull()
            ?: error("Invalid RTSP response")
        val headers = headerLines.drop(1).mapNotNull {
            it.split(':', limit = 2).takeIf { parts -> parts.size == 2 }
        }.associate { it[0].trim() to it[1].trim() }
        val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
        val bodyStart = headerEnd + 4
        val bodyBytes = text.substring(bodyStart).encodeToByteArray().toMutableList()
        while (bodyBytes.size < contentLength) {
            val count = read.readAvailable(buffer)
            if (count == -1) break
            bodyBytes += buffer.take(count)
        }
        val body = bodyBytes.take(contentLength).toByteArray().decodeToString()
        return RtspResponse(code, headers, body)
    }

    private fun uri() = "rtsp://${endpoint.host}:${endpoint.port}${endpoint.path}"

    private fun parseTrackUri(body: String, baseUri: String): String {
        val normalizedBase = normalizeRtspUri(baseUri)
        var inMediaSection = false
        var mediaControl: String? = null
        for (line in body.lineSequence().map { it.trim() }) {
            if (line.startsWith("m=")) {
                inMediaSection = true
            } else if (inMediaSection && line.startsWith("a=control:")) {
                val control = line.removePrefix("a=control:").trim()
                if (control.isNotEmpty() && control != "*") {
                    mediaControl = control
                    break
                }
            }
        }
        if (mediaControl == null) return normalizedBase

        val normalizedControl = normalizeRtspUri(mediaControl)
        if (normalizedControl.startsWith("rtsp://")) return normalizedControl
        return "${normalizedBase.trimEnd('/')}/${normalizedControl.trimStart('/')}"
    }

    private fun normalizeRtspUri(uri: String): String {
        val normalized = uri.trim()
        if (normalized.startsWith("rtsp:///")) {
            return "rtsp://${endpoint.host}:${endpoint.port}/${
                normalized.removePrefix("rtsp:///").trimStart('/')
            }"
        }
        return normalized
    }

    private fun parseRtp(bytes: ByteArray): RtpPacket? {
        if (bytes.size < 12 || (bytes[0].toInt() and 0xc0) != 0x80) return null
        val csrcCount = bytes[0].toInt() and 0x0f
        val hasExtension = bytes[0].toInt() and 0x10 != 0
        var offset = 12 + csrcCount * 4
        if (bytes.size < offset) return null
        if (hasExtension) {
            if (bytes.size < offset + 4) return null
            val extensionLength = ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
            offset += 4 + extensionLength * 4
        }
        if (offset > bytes.size) return null
        return RtpPacket(
            payload = bytes.copyOfRange(offset, bytes.size),
            sequenceNumber = ((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff),
            timestamp = ((bytes[4].toLong() and 0xff) shl 24) or
                ((bytes[5].toLong() and 0xff) shl 16) or
                ((bytes[6].toLong() and 0xff) shl 8) or
                (bytes[7].toLong() and 0xff),
            marker = bytes[1].toInt() and 0x80 != 0
        )
    }
}

private data class RtspResponse(
    val code: Int,
    val headers: Map<String, String>,
    val body: String
)
