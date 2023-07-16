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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.lang.RuntimeException
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val API_HOST = "https://api.switch-bot.com"
val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json()
    }
}

fun main() {
    runBlocking {
        val deviceList = getDeviceList().deviceList
        println(deviceList)
        val botDeviceId = deviceList.find { it.deviceType == "Bot" }?.deviceId
            ?: throw RuntimeException("Botが見つかりませんでした")
        val standLightMainDeviceId = deviceList.find { it.deviceName == "スタンドライトメイン" }?.deviceId
            ?: throw RuntimeException("スタンドライトメインが見つかりませんでした")
        val standLightSubDeviceId = deviceList.find { it.deviceName == "スタンドライトサブ" }?.deviceId
            ?: throw RuntimeException("スタンドライトサブが見つかりませんでした")
        // スタンドライトメインをオンにする
        onLight(standLightMainDeviceId)
        // スタンドライトサブをオンにする
        onLight(standLightSubDeviceId)
        // Botをオンにする
        onLight(botDeviceId)
        client.close()
    }
}

private suspend fun getDeviceList(): DeviceList {
    val response = client.request("$API_HOST/v1.1/devices") {
        method = HttpMethod.Get
        this.headers.appendAll(generateHeaders())
    }
    return response.body<Response>().body ?: throw RuntimeException("デバイス一覧の取得に失敗しました")
}

private suspend fun onLight(deviceId: String) {
    client.request("$API_HOST/v1.1/devices/$deviceId/commands") {
        method = HttpMethod.Post
        this.headers.appendAll(generateHeaders())
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
