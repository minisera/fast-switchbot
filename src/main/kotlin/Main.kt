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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.RuntimeException

const val API_HOST = "https://api.switch-bot.com"
val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json()
    }
}

fun main() {
    runBlocking {
        val path = Paths.get("${File("").absolutePath}/workflow/device-list.json")

        val fileText = try {
            val file = path.toFile()
            val time = file.lastModified()
            val now = Date().time

            // deviceListをJSONファイルにから読み込む
            // 以下の条件でダンプファイルを更新する
            // - ダンプファイルが空の場合
            // - ダンプファイルの更新日時が1日以上前の場合
            file.readText().let {
                if (it.isEmpty() || time < now - 24 * 60 * 60 * 1_000) {
                    val deviceList = fetchDeviceList().getOrThrow().deviceList
                    val deviceListJson = Json.encodeToString(deviceList)
                    path.toFile().writeText(deviceListJson)
                    deviceListJson
                } else {
                    it
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("device-list.jsonの読み込みに失敗しました")
        }

        val deviceList = try {
            Json.decodeFromString<List<SwitchBotApi.DeviceListItem>>(fileText)
        } catch (e: Exception) {
            throw RuntimeException("device-list.jsonの形式が不正です。")
        }

        val itemList = deviceList.map {
            Item(
                title = it.deviceName,
                subtitle = it.deviceType,
                arg = it.deviceId,
                uid = it.deviceId
            )
        }

        val json = Json.encodeToString(Items(items = itemList))
        // 操作したい家電一覧を表示する
        // TODO 引数によって操作したい家電を絞り込む
        println(json)

        // TODO 家電の実行は別スクリプトで行う
//        println(deviceList)
//        val botDeviceId = findDeviceId(deviceList, "Bot", "deviceType").getOrThrow()
//        val standLightMainDeviceId = findDeviceId(deviceList, "スタンドライトメイン", "deviceName").getOrThrow()
//        val standLightSubDeviceId = findDeviceId(deviceList, "スタンドライトサブ", "deviceName").getOrThrow()

//        turnOnLight(standLightMainDeviceId)
//        turnOnLight(standLightSubDeviceId)
//        turnOnLight(botDeviceId)
        client.close()
    }
}

private fun findDeviceId(
    deviceList: List<SwitchBotApi.DeviceListItem>,
    query: String,
    searchType: String = "deviceType"
): Result<String> {
    return when (searchType) {
        "deviceName" -> deviceList.find { it.deviceName == query }?.deviceId?.let { Result.success(it) }
            ?: Result.failure(RuntimeException("$query が見つかりませんでした"))
        "deviceType" -> deviceList.find { it.deviceType == query }?.deviceId?.let { Result.success(it) }
            ?: Result.failure(RuntimeException("$query が見つかりませんでした"))
        else -> Result.failure(RuntimeException("検索タイプが不正です"))
    }
}

private suspend fun fetchDeviceList(): Result<SwitchBotApi.DeviceList> {
    val response = try {
        client.request("$API_HOST/v1.1/devices") {
            method = HttpMethod.Get
            this.headers.appendAll(generateHeaders())
        }
    } catch (e: Exception) {
        return Result.failure(RuntimeException("デバイス一覧の取得に失敗しました"))
    }

    return response.body<SwitchBotApi.Response>().body?.let { Result.success(it) }
        ?: Result.failure(RuntimeException("デバイスの情報が０件です"))
}

private suspend fun turnOnLight(deviceId: String): Result<Unit> {
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
        return Result.failure(RuntimeException("デバイスの操作に失敗しました"))
    }

    return Result.success(Unit)
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
data class Item(
    val title: String,
    val subtitle: String = "",
    val arg: String = "",
    val uid: String = ""
)

@Serializable
data class Items(
    val items: List<Item>
)
