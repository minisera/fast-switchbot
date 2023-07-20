import kotlinx.serialization.Serializable

class SwitchBotApi {
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
    data class Command(
        val command: String,
        val parameter: String,
        val commandType: String
    )
}
