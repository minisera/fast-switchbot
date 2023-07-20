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
        val deviceList = fetchDeviceList().deviceList
        println(deviceList)
        val botDeviceId = findDeviceId(deviceList, "Bot", "deviceType")
        val standLightMainDeviceId = findDeviceId(deviceList, "スタンドライトメイン", "deviceName")
        val standLightSubDeviceId = findDeviceId(deviceList, "スタンドライトサブ", "deviceName")

        turnOnLight(standLightMainDeviceId)
        turnOnLight(standLightSubDeviceId)
        turnOnLight(botDeviceId)

        client.close()
    }
}

private fun findDeviceId(deviceList: List<SwitchBotApi.DeviceListItem>, query: String, searchType: String = "deviceType"): String {
    return when (searchType) {
        "deviceName" -> deviceList.find { it.deviceName == query }?.deviceId
            ?: throw RuntimeException("$query が見つかりませんでした")
        "deviceType" -> deviceList.find { it.deviceType == query }?.deviceId
            ?: throw RuntimeException("$query が見つかりませんでした")
        else -> throw RuntimeException("検索タイプが不正です")
    }
}

private suspend fun fetchDeviceList(): SwitchBotApi.DeviceList {
    val response = try {
        client.request("$API_HOST/v1.1/devices") {
            method = HttpMethod.Get
            this.headers.appendAll(generateHeaders())
        }
    } catch (e: Exception) {
        throw RuntimeException("デバイス一覧の取得に失敗しました")
    }

    return response.body<SwitchBotApi.Response>().body ?: throw RuntimeException("デバイス情報がありません")
}

private suspend fun turnOnLight(deviceId: String) {
    try {
        client.request("$API_HOST/v1.1/devices/$deviceId/commands") {
            method = HttpMethod.Post
            this.headers.appendAll(generateHeaders())
            contentType(ContentType.Application.Json)
            setBody(
                SwitchBotApi.Command(
                    command = "turnOn",
                    parameter = "default",
                    commandType = "command"
                )
            )
        }
    } catch (e: Exception) {
        throw RuntimeException("ライトのONに失敗しました")
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
