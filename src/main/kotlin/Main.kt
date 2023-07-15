import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import java.lang.RuntimeException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async

fun main() {
    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
    }

    val headers = generateHeaders()
    val apiHost = "https://api.switch-bot.com"
    val getDevicesList = "$apiHost/v1.1/devices"
     println("start request")
     runBlocking {
         val responseDeferred = async {
            client.request(getDevicesList) {
                method = HttpMethod.Get
                this.headers.appendAll(headers)
            } 
         }
         val response = responseDeferred.await()
         println(response.body<Response>())

         val deviceId = response.body<Response>().body?.deviceList?.find { it.deviceType == "Bot" }?.deviceId
            ?: throw RuntimeException("Botが見つかりませんでした")
         val sendCommand = "$apiHost/v1.1/devices/$deviceId/commands"
         val sendCommandDeferred = async {
            client.request(sendCommand) {
                method = HttpMethod.Post
                this.headers.appendAll(headers)
                contentType(ContentType.Application.Json)
                setBody(
                    BotCommand(
                        command = "turnOn",
                        parameter = "default",
                        commandType = "command"
                    )
                )
            }
         }
         sendCommandDeferred.await()
         client.close()
         println("end request")
    }
}

private fun generateHeaders(): Headers {
    // 環境変数を取得
    val env = System.getenv()
    val openToken = env["OPEN_TOKEN"]
    val secret = env["SECRET"]
    // 環境変数がセットされていない場合は例外をスロー
    if (openToken == null || secret == null) {
        throw RuntimeException("環境変数をセットしてください")
    }
    // ランダムなnonceを生成
    val nonce = UUID.randomUUID().toString()
    // 現在の時間をミリ秒で取得
    val time = "" + Instant.now().toEpochMilli()
    // データを作成
    val data = openToken + time + nonce
    // シークレットキーを作成
    val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    // Macインスタンスを作成し、シークレットキーで初期化
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secretKeySpec)
    // データをHMAC-SHA256で署名
    val signature = String(Base64.getEncoder().encode(mac.doFinal(data.toByteArray())))
    // ヘッダーを作成して返す
    return headers {
        append(HttpHeaders.Authorization, openToken)
        append("sign", signature)
        append("nonce", nonce)
        append("t", time)
    }
}

@Serializable
data class Response(
    val statusCode: String? = null,
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
