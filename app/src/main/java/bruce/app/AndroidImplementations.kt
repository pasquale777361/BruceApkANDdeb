package bruce.app

import android.content.Context
import bruce.app.Main // esptool-android Main

class AndroidFirmwareFlasher(private val context: Context) : FirmwareFlasher {
    override fun uploadFirmware(arguments: String, onStatusChange: (String) -> Unit): String {
        onStatusChange("Starting upload...")
        // Original logic was blocking
        val result = Main().uploadFirmware(context, arguments)
        // Split result into lines for status updates
        result.lines().forEach { onStatusChange(it) }
        return result
    }
}

class AndroidCommandStore(context: Context) : CommandStore {
    private val helper = CustomCommandsDatabaseHelper(context)

    override fun insertCommand(command: CustomSerialCommand) {
        helper.insertCommand(command)
    }

    override fun getAllCommands(): List<CustomSerialCommand> {
        return helper.getAllCommands()
    }

    override fun deleteCommand(id: String) {
        helper.deleteCommand(id)
    }
}
