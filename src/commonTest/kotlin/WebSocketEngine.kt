import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.http.HttpHeader
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.network.websocket.WebSocket
import com.apollographql.apollo3.network.websocket.WebSocketEngine
import com.apollographql.apollo3.network.websocket.WebSocketListener
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@ApolloExperimental
class KtorWebSocketEngine(
    private val client: HttpClient,
) : WebSocketEngine {

    constructor() : this(
        HttpClient {
            install(WebSockets)
        }
    )

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun close() {

    }

    override fun newWebSocket(url: String, headers: List<HttpHeader>, listener: WebSocketListener): WebSocket {
        val newUrl = URLBuilder(url).apply {
            when (this.protocol) {
                URLProtocol.HTTPS -> {
                    protocol = URLProtocol.WSS
                }

                URLProtocol.HTTP -> {
                    protocol = URLProtocol.WS
                }

                URLProtocol.WS, URLProtocol.WSS -> Unit
                /* URLProtocol.SOCKS */else -> throw UnsupportedOperationException("'$protocol' is not a supported protocol")
            }
        }.build()

        val sendFrameChannel = Channel<Frame>(Channel.UNLIMITED)

        coroutineScope.launch {
            client.webSocket(
                request = {
                    headers {
                        headers.forEach {
                            append(it.name, it.value)
                        }
                    }
                    url(newUrl)
                },
            ) {
                launch {
                    listener.onOpen()

                    while (true) {
                        val frame = sendFrameChannel.receive()

                        try {
                            send(frame)
                        } catch (e: Exception) {
                            listener.onError(ApolloNetworkException("Error while sending frame", e))
                            this@webSocket.cancel()
                            break
                        }
                    }
                }
                while (true) {
                    val frame = try {
                        incoming.receive()
                    } catch (e: Exception) {
                        listener.onError(ApolloNetworkException("Error while receiving frame", e))
                        this@webSocket.cancel()
                        break
                    }

                    when (frame) {
                        is Frame.Text -> {
                            listener.onMessage(frame.readText())
                        }

                        is Frame.Binary -> {
                            listener.onMessage(frame.data)
                        }

                        is Frame.Close -> {
                            val closeReason = frame.readReason()
                            if (closeReason != null) {
                                listener.onClosed(closeReason.code.toInt(), closeReason.message)
                            } else {
                                listener.onClosed(1001, "going away")
                            }
                            this@webSocket.cancel()
                        }

                        else -> error("Unhandled frame type '$frame'")
                    }
                }
            }
        }

        return object : WebSocket {
            override fun send(data: ByteArray) {
                sendFrameChannel.trySend(Frame.Binary(true, data))
            }

            override fun send(text: String) {
                sendFrameChannel.trySend(Frame.Text(text))
            }

            override fun close(code: Int, reason: String) {
                sendFrameChannel.trySend(Frame.Close(CloseReason(code.toShort(), reason)))
            }
        }
    }
}
