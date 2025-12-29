import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import bruce.app.*
import com.fazecast.jSerialComm.SerialPort
import java.io.File
import java.util.concurrent.Executors

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Bruce App") {
        val serial = remember { DesktopSerialCommunicator() }
        val flasher = remember { DesktopFirmwareFlasher() }
        val store = remember { DesktopCommandStore() }
        
        App(serial, flasher, store)
    }
}

class DesktopSerialCommunicator : SerialCommunicator {
    private var port: SerialPort? = null
    private var listener: ((String) -> Unit)? = null
    private var baudRate = 115200

    override fun connect() {
        val ports = SerialPort.getCommPorts()
        listener?.invoke("Available ports: ${ports.joinToString { it.systemPortName }}")
        
        // Simple auto-connect logic: pick the last one (often the USB one) or search for specific
        // For now, just pick the last one if available
        if (ports.isNotEmpty()) {
            port = ports.last()
            port?.let {
                it.baudRate = baudRate
                if (it.openPort()) {
                    listener?.invoke("Connected to ${it.systemPortName} at $baudRate")
                    
                    // Start reading thread
                    Thread {
                        try {
                            val buffer = ByteArray(1024)
                            while (it.isOpen) {
                                val bytesRead = it.readBytes(buffer, buffer.size.toLong())
                                if (bytesRead > 0) {
                                    val msg = String(buffer, 0, bytesRead.toInt())
                                    listener?.invoke(msg)
                                }
                                Thread.sleep(100)
                            }
                        } catch (e: Exception) {
                            listener?.invoke("Read error: ${e.message}")
                        }
                    }.start()
                } else {
                    listener?.invoke("Failed to open ${it.systemPortName}")
                }
            }
        } else {
            listener?.invoke("No serial ports found")
        }
    }

    override fun disconnect() {
        port?.closePort()
        port = null
        listener?.invoke("Disconnected")
    }

    override fun sendCommand(command: String) {
        port?.let {
            if (it.isOpen) {
                val data = "$command\n".toByteArray()
                it.writeBytes(data, data.size.toLong())
                listener?.invoke("Sent: $command")
            } else {
                listener?.invoke("Port closed")
            }
        } ?: listener?.invoke("No port connected")
    }

    override fun setBaudRate(baudRate: Int) {
        this.baudRate = baudRate
        port?.baudRate = baudRate
        listener?.invoke("Baud rate set to $baudRate")
    }

    override fun setOutputListener(listener: (String) -> Unit) {
        this.listener = listener
    }
}

class DesktopFirmwareFlasher : FirmwareFlasher {
    override fun uploadFirmware(arguments: String, onStatusChange: (String) -> Unit): String {
        return try {
            // Arguments comes in like: --chip esp32s3 --baud ...
            // We need to split it
            val argsList = arguments.split(" ").filter { it.isNotBlank() }
            val command = listOf("esptool") + argsList
            
            onStatusChange("Executing: ${command.joinToString(" ")}")
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                onStatusChange(line!!)
            }
            
            val exitCode = process.waitFor()
            if (exitCode == 0) "Success" else "Failed with exit code $exitCode"
        } catch (e: Exception) {
            "Error: ${e.message} (Make sure 'esptool' is in PATH)"
        }
    }
}

class DesktopCommandStore : CommandStore {
    // Simple in-memory store for now, or file based
    private val file = File("custom_commands.json") // in working dir
    private var commands = mutableListOf<CustomSerialCommand>()

    init {
        // Mock load
        // In real app, read JSON from file
    }

    override fun insertCommand(command: CustomSerialCommand) {
        commands.add(command)
        // Save to file
    }

    override fun getAllCommands(): List<CustomSerialCommand> {
        return commands
    }

    override fun deleteCommand(id: String) {
        commands.removeIf { it.id == id }
        // Save to file
    }
}

// Helper to remember generic classes in Compose
import androidx.compose.runtime.remember
