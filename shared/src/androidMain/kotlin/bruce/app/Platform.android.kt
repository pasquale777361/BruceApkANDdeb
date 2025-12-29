package bruce.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

actual suspend fun downloadUrl(url: String): ByteArray = withContext(Dispatchers.IO) {
    URL(url).readBytes()
}

actual fun saveFile(fileName: String, content: ByteArray): String {
    val tempFile = File.createTempFile(fileName, null)
    tempFile.writeBytes(content)
    return tempFile.absolutePath
}
