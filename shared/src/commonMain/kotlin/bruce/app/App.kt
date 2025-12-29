package bruce.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bruce.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi

// Helper to replace JSONObject
fun parseManifest(jsonString: String): List<DeviceInfo> {
    // ultra-simple json parser or regex for now to avoid dragging in serialization lib yet
    // Structure: { "Category": [ { "id": "...", "name": "..." }, ... ], ... }
    val devices = mutableListOf<DeviceInfo>()
    
    // This is a rough heuristic parser for the specific manifest format
    // Real implementation should use kotlinx.serialization
    val cleanJson = jsonString.replace("\n", "").replace("\r", "")
    
    // Find all objects
    val objectRegex = Regex("""\{"id":"([^"]+)","name":"([^"]+)"\}""")
    objectRegex.findAll(cleanJson).forEach { match ->
         devices.add(DeviceInfo(match.groupValues[1], match.groupValues[2], "Device"))
    }
    return devices
}

@Composable
fun App(
    serialCommunicator: SerialCommunicator,
    firmwareFlasher: FirmwareFlasher,
    commandStore: CommandStore
) {
    FirmwareFlasherTheme {
        MainScreen(serialCommunicator, firmwareFlasher, commandStore)
    }
}

@Composable
fun MainScreen(
    serialCommunicator: SerialCommunicator,
    firmwareFlasher: FirmwareFlasher,
    commandStore: CommandStore
) {
    // Context removal: No LocalContext
    // Configuration: Simple layout logic
    val isLandscape = false // Simplified for desktop default
    val isTablet = true    // Simplified
    
    var terminalOutput by remember { mutableStateOf(listOf("Terminal ready...")) }
    var showDocDialog by remember { mutableStateOf(false) }
    var showWebView by remember { mutableStateOf(false) } // WebView Placeholder
    var showWebViewCredentials by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var serialCommand by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf("m5stack-cardputer") }
    var deviceList by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var isTerminalMaximized by remember { mutableStateOf(false) }
    var baudRate by remember { mutableStateOf("115200") }
    var showBaudRateDialog by remember { mutableStateOf(false) }
    var showSerialCmdDialog by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var showInstallationCompleteDialog by remember { mutableStateOf(false) }
    var showAddCustomCmdDialog by remember { mutableStateOf(false) }
    var customCommands by remember { mutableStateOf(listOf<CustomSerialCommand>()) }
    val terminalListState = rememberLazyListState()
    
    val scope = rememberCoroutineScope()

    // Initialize serial communication and database
    LaunchedEffect(Unit) {
        serialCommunicator.setOutputListener { message ->
            terminalOutput = terminalOutput + message
        }
        // Automatically try to connect when app starts
        try {
            serialCommunicator.connect()
        } catch (e: Exception) {
            terminalOutput = terminalOutput + "Connection error: ${e.message}"
        }
        
        // Initialize database and load custom commands
        customCommands = commandStore.getAllCommands()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Upload Firmware button
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = {
                    showDeviceDialog = true
                    if (deviceList.isEmpty()) {
                        scope.launch(Dispatchers.IO) {
                             try {
                                val jsonUrl = "https://raw.githubusercontent.com/pr3y/Bruce/refs/heads/WebPage/src/lib/data/manifests.json"
                                val jsonString = try {
                                    downloadUrl(jsonUrl).decodeToString()
                                } catch (e: Exception) { "{}" }
                                val devices = parseManifest(jsonString)
                                withContext(Dispatchers.Main) {
                                    deviceList = devices
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                     terminalOutput = terminalOutput + "Error loading manifest: ${e.message}"
                                }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PurpleAccent,
                    contentColor = White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    text = "Upload Firmware",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showWebViewCredentials = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurpleGrey80,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Bruce WebView")
                }
                
                Button(
                    onClick = { showSerialCmdDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PurpleGrey80,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Serial CMD")
                }
            }

            // Loading indicator below action buttons
            if (isUploading) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PurpleAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Please wait...",
                        color = White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Top right corner buttons
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { showBaudRateDialog = true },
                modifier = Modifier
                    .background(
                        PurpleAccent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Configuration",
                    tint = White
                )
            }
            
            IconButton(
                onClick = { showDocDialog = true },
                modifier = Modifier
                    .background(
                        PurpleAccent,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Documentation",
                    tint = White
                )
            }
        }

        // Device Selection Dialog
        if (showDeviceDialog) {
            Dialog(onDismissRequest = { showDeviceDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(600.dp)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGray)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Select Device",
                            color = White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (deviceList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Loading devices...", color = White)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                items(deviceList) { device ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (device.id == selectedDevice) PurpleAccent else LightGray
                                        )
                                    ) {
                                        TextButton(
                                            onClick = {
                                                selectedDevice = device.id
                                                terminalOutput = terminalOutput + "> Device selected: ${device.name} (${device.id})"
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = device.name,
                                                    color = White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = device.category,
                                                    color = White.copy(alpha = 0.7f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Selected: $selectedDevice",
                                color = White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Button(
                                onClick = {
                                    showDeviceDialog = false
                                    terminalOutput = terminalOutput + "> Selected device: $selectedDevice"
                                    
                                    // Firmware Upload Logic
                                    isUploading = true
                                    terminalOutput = terminalOutput + "Starting firmware download..."
                                    
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            withContext(Dispatchers.Main) { terminalOutput = terminalOutput + "Downloading..." }
                                            
                                            val firmwareUrl = "https://github.com/pr3y/Bruce/releases/download/1.11/Bruce-$selectedDevice.bin"
                                            val firmwareData = downloadUrl(firmwareUrl)
                                            val firmwarePath = saveFile("bruce_firmware.bin", firmwareData)
                                            
                                            withContext(Dispatchers.Main) { 
                                                 terminalOutput = terminalOutput + "Downloaded to $firmwarePath"
                                                 terminalOutput = terminalOutput + "Flashing..."
                                            }
                                            
                                            val result = firmwareFlasher.uploadFirmware(
                                                /* arguments = */ "--chip esp32s3 --baud $baudRate --before default_reset --after hard_reset --no-stub write_flash -z 0x0 $firmwarePath",
                                                onStatusChange = { msg ->
                                                     // We need to run this on main thread if we modify state
                                                     // But callback might be from background
                                                     // Ideally use a channel or state updates
                                                     // For now, assume this might be called from bg, so we can't update state directly comfortably 
                                                     // without jumping to main.
                                                     // BUT, we can't jump to main here easily without scope.
                                                     // Let's assume onStatusChange handles concurrency or we act safe.
                                                     // Actually we updated interface to be just a callback.
                                                }
                                            )
                                            
                                            withContext(Dispatchers.Main) {
                                                terminalOutput = terminalOutput + result
                                                if(result.contains("Success", true)) {
                                                    showInstallationCompleteDialog = true
                                                }
                                                isUploading = false
                                            }
                                        } catch(e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                terminalOutput = terminalOutput + "Error: ${e.message}"
                                                isUploading = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PurpleAccent,
                                    contentColor = White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "INSTALL",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Cancel button
                        Button(
                            onClick = { showDeviceDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurpleGrey80,
                                contentColor = White
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Terminal section at bottom
        if (isTerminalMaximized) {
            // Maximized terminal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                 // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            DarkGray,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .border(
                            2.dp,
                            PurpleAccent,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terminal Output",
                            color = White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Button(
                            onClick = { isTerminalMaximized = false },
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PurpleAccent,
                                contentColor = White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Terminal display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            DarkGray,
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .border(
                            2.dp,
                            PurpleAccent,
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        state = terminalListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(terminalOutput) { line ->
                            Text(
                                text = line,
                                color = White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                     LaunchedEffect(terminalOutput.size) {
                        if (terminalOutput.isNotEmpty()) {
                            terminalListState.animateScrollToItem(terminalOutput.size - 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Serial command input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serialCommand,
                        onValueChange = { serialCommand = it },
                        label = { Text("Serial Command", color = White) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = LightGray,
                            focusedLabelColor = PurpleAccent,
                            unfocusedLabelColor = White
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (serialCommand.isNotEmpty()) {
                                terminalOutput = terminalOutput + "> $serialCommand"
                                serialCommunicator.sendCommand(serialCommand)
                                serialCommand = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent,
                            contentColor = White
                        )
                    ) {
                        Text("Send")
                    }
                }
            }
        } else {
            // Normal terminal at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Terminal controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terminal Output:",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = { isTerminalMaximized = true },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent,
                            contentColor = White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = "+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // Terminal display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(DarkGray, RoundedCornerShape(8.dp))
                        .border(1.dp, PurpleAccent, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(state = terminalListState) {
                        items(terminalOutput) { line ->
                            Text(
                                text = line,
                                color = White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                    LaunchedEffect(terminalOutput.size) {
                        if (terminalOutput.isNotEmpty()) {
                            terminalListState.animateScrollToItem(terminalOutput.size - 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Serial command input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serialCommand,
                        onValueChange = { serialCommand = it },
                        label = { Text("Serial Command", color = White) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = LightGray,
                            focusedLabelColor = PurpleAccent,
                            unfocusedLabelColor = White
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (serialCommand.isNotEmpty()) {
                                terminalOutput = terminalOutput + "> $serialCommand"
                                serialCommunicator.sendCommand(serialCommand)
                                serialCommand = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleAccent,
                            contentColor = White
                        )
                    ) {
                        Text("Send")
                    }
                }
            }
        }

        // WebView Placeholder
        if (showWebView) {
            Dialog(onDismissRequest = { showWebView = false }) {
                 Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGray)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                         Text("WebView is not yet supported on Desktop in this version.", color = White)
                         Spacer(modifier = Modifier.height(16.dp))
                         val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                         Button(onClick = { 
                             uriHandler.openUri("http://bruce.local")
                             showWebView = false
                         }) {
                             Text("Open in Browser")
                         }
                    }
                }
            }
        }
        
        // Serial Commands Dialog
        if (showSerialCmdDialog) {
            Dialog(onDismissRequest = { showSerialCmdDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(600.dp)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGray)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom Serial Commands", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // List
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(customCommands) { cmd ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical=4.dp),
                                    colors = CardDefaults.cardColors(containerColor = LightGray)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                         Column(modifier = Modifier.weight(1f)) {
                                             Text(cmd.name, color = PurpleAccent, fontWeight = FontWeight.Bold)
                                             Text(cmd.command, color = White, fontSize = 12.sp)
                                         }
                                         Button(onClick = {
                                             terminalOutput = terminalOutput + "> ${cmd.command}"
                                             serialCommunicator.sendCommand(cmd.command)
                                         }) { Text("Run") }
                                         IconButton(onClick = {
                                             commandStore.deleteCommand(cmd.id)
                                             customCommands = commandStore.getAllCommands()
                                         }) { Icon(Icons.Default.Close, "Delete", tint = White) }
                                    }
                                }
                            }
                        }
                        
                        Button(onClick = { showAddCustomCmdDialog = true }) {
                            Text("Add Command")
                        }
                        Button(onClick = { showSerialCmdDialog = false }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
        
        // Add Custom Command
        if(showAddCustomCmdDialog) {
            Dialog(onDismissRequest = { showAddCustomCmdDialog = false }) {
                 Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkGray)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add Command", color = White, fontWeight = FontWeight.Bold)
                        var name by remember { mutableStateOf("") }
                        var cmd by remember { mutableStateOf("") }
                        OutlinedTextField(value = name, onValueChange = { name = it}, label = { Text("Name") })
                        OutlinedTextField(value = cmd, onValueChange = { cmd = it}, label = { Text("Command") })
                        Row {
                            Button(onClick = { showAddCustomCmdDialog = false }) { Text("Cancel") }
                            Button(onClick = {
                                 commandStore.insertCommand(CustomSerialCommand(System.currentTimeMillis().toString(), name, cmd))
                                 customCommands = commandStore.getAllCommands()
                                 showAddCustomCmdDialog = false
                            }) { Text("Add") }
                        }
                    }
                }
            }
        }
    }
}
