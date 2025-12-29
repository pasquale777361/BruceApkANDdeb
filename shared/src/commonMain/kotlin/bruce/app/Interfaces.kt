package bruce.app

expect suspend fun downloadUrl(url: String): ByteArray
expect fun saveFile(fileName: String, content: ByteArray): String


interface SerialCommunicator {
    fun connect()
    fun disconnect()
    fun sendCommand(command: String)
    fun setBaudRate(baudRate: Int)
    fun setOutputListener(listener: (String) -> Unit)
}

interface FirmwareFlasher {
    fun uploadFirmware(
        arguments: String,
        onStatusChange: (String) -> Unit
    ): String
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val category: String
)

data class CustomSerialCommand(
    val id: String,
    val name: String,
    val command: String
)

interface CommandStore {
    fun insertCommand(command: CustomSerialCommand)
    fun getAllCommands(): List<CustomSerialCommand>
    fun deleteCommand(id: String)
}
