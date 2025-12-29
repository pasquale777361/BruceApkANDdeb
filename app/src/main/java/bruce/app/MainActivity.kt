package bruce.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

// Keep Database Helper here or move to separate file. keeping for simplicity as it was here.
class CustomCommandsDatabaseHelper(context: android.content.Context) : SQLiteOpenHelper(context, "custom_commands.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE custom_commands (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                command TEXT NOT NULL
            )
        """)
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS custom_commands")
        onCreate(db)
    }
    
    fun insertCommand(command: CustomSerialCommand) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", command.id)
            put("name", command.name)
            put("command", command.command)
        }
        db.insert("custom_commands", null, values)
    }
    
    fun getAllCommands(): List<CustomSerialCommand> {
        val db = readableDatabase
        val cursor = db.query("custom_commands", null, null, null, null, null, null)
        val commands = mutableListOf<CustomSerialCommand>()
        
        cursor.use {
            while (it.moveToNext()) {
                commands.add(
                    CustomSerialCommand(
                        id = it.getString(0),
                        name = it.getString(1),
                        command = it.getString(2)
                    )
                )
            }
        }
        return commands
    }
    
    fun deleteCommand(id: String) {
        val db = writableDatabase
        db.delete("custom_commands", "id = ?", arrayOf(id))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serial = AndroidSerialCommunication(this)
        val flasher = AndroidFirmwareFlasher(this)
        val store = AndroidCommandStore(this)

        setContent {
            App(serial, flasher, store)
        }
    }
}
