import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun main() {
    val env = System.getenv()
    val openToken = env["OPEN_TOKEN"]
    val secret = env["SECRET"]
    if (openToken == null || secret == null) {
        println("環境変数をセットしてください")
        return
    }

    val nonce = UUID.randomUUID().toString()
    val time = "" + Instant.now().toEpochMilli()
    val data = openToken + time + nonce
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secretKeySpec)

    val signature = String(Base64.getEncoder().encode(mac.doFinal(data.toByteArray())))

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
    }

    val apiHost = "https://api.switch-bot.com"
    val getDevicesList = "$apiHost/v1.1/devices"

    println("start request")
    runBlocking {
        // 持っているデバイス一覧を取得
        val response = client.request(getDevicesList) {
            method = HttpMethod.Get
            headers {
                append(HttpHeaders.Authorization, openToken)
                append("sign", signature)
                append("nonce", nonce)
                append("t", time)
            }
        }

        println(response.body<Response>())

        // deviceIdを取得する
        val deviceId = response.body<Response>().body?.deviceList?.find { it.deviceType == "Bot" }?.deviceId
        if (deviceId == null) {
            println("Botが見つかりませんでした")
            return@runBlocking
        }

        // Botをオンにする
        val sendCommand = "$apiHost/v1.1/devices/$deviceId/commands"
        client.request(sendCommand) {
            method = HttpMethod.Post
            headers {
                append(HttpHeaders.Authorization, openToken)
                append("sign", signature)
                append("nonce", nonce)
                append("t", time)
            }
            contentType(ContentType.Application.Json)
            setBody(
                BotCommand(
                    command = "turnOn",
                    parameter = "default",
                    commandType = "command"
                )
            )
        }
        client.close()
    }
}

@Serializable
data class Response(
    val statusCode: String,
    val body: DeviceList? = null,
    val message: String
)

@Serializable
data class DeviceList(
    val deviceList: List<DeviceListItem>,
    val infraredRemoteList: List<InfraredRemoteListItem>
)

@Serializable
data class DeviceListItem(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val enableCloudService: String? = null,
    val hubDeviceId: String,
    val lockDeviceId: String? = null,
    val keyList: List<KeyListItem>? = null
)

@Serializable
data class InfraredRemoteListItem(
    val deviceId: String,
    val deviceName: String,
    val remoteType: String,
    val hubDeviceId: String
)

@Serializable
data class KeyListItem(
    val id: Long,
    val name: String,
    val type: String,
    val status: String,
    val createTime: Long,
    val password: String,
    val iv: String
)

@Serializable
data class BotCommand(
    val command: String,
    val parameter: String,
    val commandType: String
)
