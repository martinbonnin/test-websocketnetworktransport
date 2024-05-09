import app.cash.turbine.test
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.annotations.ApolloInternal
import com.apollographql.apollo3.api.json.jsonReader
import com.apollographql.apollo3.api.json.readAny
import com.apollographql.apollo3.mockserver.MockServer
import com.apollographql.apollo3.mockserver.TextMessage
import com.apollographql.apollo3.mockserver.awaitWebSocketRequest
import com.apollographql.apollo3.mockserver.enqueueWebSocket
import com.apollographql.apollo3.network.websocket.WebSocketNetworkTransport
import com.example.FooSubscription
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MainTest {
    @OptIn(ApolloExperimental::class)
    @Test
    fun test() = runBlocking {
        MockServer().use { mockServer ->
            ApolloClient.Builder()
                .serverUrl(mockServer.url())
                .subscriptionNetworkTransport(
                    WebSocketNetworkTransport.Builder()
                        .serverUrl(mockServer.url())
                        .webSocketEngine(KtorWebSocketEngine())
                        .build()
                )
                .build()
                .use { apolloClient ->

                    val responseBody = mockServer.enqueueWebSocket()
                    apolloClient.subscription(FooSubscription())
                        .toFlow()
                        .test {
                            val request = mockServer.awaitWebSocketRequest()

                            request.awaitMessage().apply {
                                assertIs<TextMessage>(this)
                                assertEquals("{\"type\":\"connection_init\"}", this.text)
                            }
                            responseBody.enqueueMessage(TextMessage("{\"type\":\"connection_ack\"}"))

                            var operationId: String
                            request.awaitMessage().apply {
                                assertIs<TextMessage>(this)
                                text.parseJson().apply {
                                    assertEquals("subscribe", get("type"))
                                    operationId = get("id") as String
                                }
                            }

                            responseBody.enqueueMessage(TextMessage("{\"type\":  \"next\", \"id\": \"$operationId\", \"payload\":  { \"data\":  { \"foo\" :  42 }}}"))

                            awaitItem().apply {
                                assertEquals(42, data?.foo)
                            }
                        }
                }
        }
    }
}

@OptIn(ApolloInternal::class)
private fun String.parseJson(): Map<String, Any?> {
    return Buffer().writeUtf8(this).jsonReader().readAny() as Map<String, Any?>
}