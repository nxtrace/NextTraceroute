/*

NextTraceroute, an Android traceroute app using Nexttrace API
Copyright (C) 2024 surfaceocean
Email: r2qb8uc5@protonmail.com
GitHub: https://github.com/nxtrace/NextTraceroute
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

This project uses the libraries listed below. Detailed information can be found in the LICENSE file of this project.
The "dnsjava" library is licensed under the BSD 3-Clause License.
The "seancfoley/IPAddress" library is licensed under the Apache 2.0 License.
The "square/okhttp" library is licensed under the Apache 2.0 License.
The "gson" library is licensed under the Apache 2.0 License.
The "slf4j-android" library is licensed under the MIT License.
The "androidx" library is licensed under the Apache 2.0 License.

*/




package com.surfaceocean.nexttraceroute

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.surfaceocean.nexttraceroute.ui.theme.ButtonDisabledColor
import com.surfaceocean.nexttraceroute.ui.theme.ButtonEnabledColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColorReverse
import com.surfaceocean.nexttraceroute.ui.theme.NextTracerouteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NextTracerouteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    //color = MaterialTheme.colorScheme.background,
                    color = DefaultBackgroundColor
                ) {
                    val isSearchBarEnabled = remember { mutableStateOf(true) }
                    val currentPage = remember { mutableStateOf("main") }
                    val context = LocalContext.current
                    val currentLanguage =
                        remember { mutableStateOf("Default") } // Default, zh or en
                    val isTraceMapEnabled = remember { mutableStateOf(true) }
                    val maxTraceTTL = remember { mutableIntStateOf(30) }
                    val traceTimeout = remember { mutableStateOf("1") }
                    val traceCount = remember { mutableStateOf("5") }
                    val currentDNSMode = remember { mutableStateOf("udp") }
                    val currentDOHServer = remember { mutableStateOf("https://1.1.1.1/dns-query") }
                    val tracerouteDNSServer = remember { mutableStateOf("1.1.1.1") }
                    val apiHostNamePOW = remember { mutableStateOf("origin-fallback.nxtrace.org") }
                    val apiDNSNamePOW = remember { mutableStateOf("api.nxtrace.org") }
                    val apiHostName = remember { mutableStateOf("origin-fallback.nxtrace.org") }
                    val apiDNSName = remember { mutableStateOf("api.nxtrace.org") }
                    var lastBackPress by remember { mutableLongStateOf(0L) }
                    val listState = rememberLazyListState()
                    val isScrollToFirstLineTriggered = remember { mutableStateOf(false) }

                    //load settings from file
                    LaunchedEffect(Unit) {
                        try {
                            val file = File(context.filesDir, "settings.json")
                            if (file.exists()) {
                                context.openFileInput("settings.json").use { inputStream ->
                                    val size = inputStream.available()
                                    val buffer = ByteArray(size)
                                    inputStream.read(buffer)
                                    val jsonString = String(buffer)
                                    val gson = Gson()
                                    val mapType = object : TypeToken<Map<String, Any>>() {}.type
                                    val settingsMap: Map<String, Any> =
                                        gson.fromJson(jsonString, mapType)
                                    currentLanguage.value = settingsMap["currentLanguage"] as String
                                    isTraceMapEnabled.value =
                                        settingsMap["isTraceMapEnabled"] as Boolean
                                    maxTraceTTL.intValue =
                                        (settingsMap["maxTraceTTL"] as String).toInt()
                                    traceTimeout.value = settingsMap["traceTimeout"] as String
                                    traceCount.value = settingsMap["traceCount"] as String
                                    currentDNSMode.value = settingsMap["currentDNSMode"] as String
                                    tracerouteDNSServer.value =
                                        settingsMap["tracerouteDNSServer"] as String
                                    currentDOHServer.value =
                                        settingsMap["currentDOHServer"] as String
                                    apiHostNamePOW.value = settingsMap["apiHostNamePOW"] as String
                                    apiDNSNamePOW.value = settingsMap["apiDNSNamePOW"] as String
                                    apiHostName.value = settingsMap["apiHostName"] as String
                                    apiDNSName.value = settingsMap["apiDNSName"] as String

                                }

                            }


                        } catch (e: Exception) {
                            Log.e("readSettngsHandler", e.printStackTrace().toString())
                        }
                    }

                    //scroll to first line after run button is pressed
                    LaunchedEffect(Unit) {
                        while (true) {
                            if (isScrollToFirstLineTriggered.value) {
                                isScrollToFirstLineTriggered.value = false
                                listState.animateScrollToItem(index = 0)
                            }
                            delay(timeMillis = 1000)
                        }
                    }

                    BackHandler {
                        if (System.currentTimeMillis() - lastBackPress < 2000) {
                            (context as? Activity)?.finish()
                        } else {
                            Toast.makeText(
                                context,
                                "Press again to exit this program!",
                                Toast.LENGTH_SHORT
                            ).show()
                            lastBackPress = System.currentTimeMillis()
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (currentPage.value) {
                            "settings" -> {
                                SettingsColumn(
                                    context = context,
                                    currentPage = currentPage,
                                    currentLanguage = currentLanguage,
                                    isTraceMapEnabled = isTraceMapEnabled,
                                    maxTraceTTL = maxTraceTTL,
                                    traceTimeout = traceTimeout,
                                    traceCount = traceCount,
                                    tracerouteDNSServer = tracerouteDNSServer,
                                    apiHostNamePOW = apiHostNamePOW,
                                    apiDNSNamePOW = apiDNSNamePOW,
                                    apiHostName = apiHostName,
                                    apiDNSName = apiDNSName,
                                    currentDOHServer = currentDOHServer,
                                    currentDNSMode = currentDNSMode
                                )
                            }

                            "main" -> {
                                MyTopAppBar(
                                    currentPage = currentPage,
                                    isSearchBarEnabled = isSearchBarEnabled
                                )
                                MainColumn(
                                    isSearchBarEnabled = isSearchBarEnabled,
                                    currentLanguage = currentLanguage,
                                    isTraceMapEnabled = isTraceMapEnabled,
                                    maxTraceTTL = maxTraceTTL,
                                    traceTimeout = traceTimeout,
                                    traceCount = traceCount,
                                    tracerouteDNSServer = tracerouteDNSServer,
                                    apiHostNamePOW = apiHostNamePOW,
                                    apiDNSNamePOW = apiDNSNamePOW,
                                    apiHostName = apiHostName,
                                    apiDNSName = apiDNSName,
                                    context = context,
                                    currentDOHServer = currentDOHServer,
                                    currentDNSMode = currentDNSMode,
                                    listState = listState,
                                    isScrollToFirstLineTriggered = isScrollToFirstLineTriggered
                                )


                            }

                            "about" -> {
                                AboutPage(currentPage = currentPage)
                            }
                        }

                    }

                }
            }
        }
    }
}


@Composable
fun AboutPage(currentPage: MutableState<String>) {
    BackHandler {
        currentPage.value = "main"
    }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DefaultBackgroundColorReverse)
            .padding(bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { currentPage.value = "main" }) {
            Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Text(
            color = DefaultBackgroundColorReverse,
            text = "NextTraceroute, an Android traceroute app using Nexttrace API\n" +
                    "Copyright (C) 2024 surfaceocean\n" +
                    "Email: r2qb8uc5@protonmail.com\n" +
                    "GitHub: https://github.com/nxtrace/NextTraceroute\n" +
                    "This program is free software: you can redistribute it and/or modify\n" +
                    "it under the terms of the GNU General Public License as published by\n" +
                    "the Free Software Foundation, either version 3 of the License, or\n" +
                    "any later version.\n" +
                    "\n" +
                    "This program is distributed in the hope that it will be useful,\n" +
                    "but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
                    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the\n" +
                    "GNU General Public License for more details.\n" +
                    "\n" +
                    "You should have received a copy of the GNU General Public License\n" +
                    "along with this program in the LICENSE file.  If not, see <https://www.gnu.org/licenses/>.\n" +
                    "\n" +
                    "This project uses the libraries listed below. Detailed information can be found in the LICENSE file of this project.\n" +
                    "The \"dnsjava\" library is licensed under the BSD 3-Clause License.\n" +
                    "The \"seancfoley/IPAddress\" library is licensed under the Apache 2.0 License.\n" +
                    "The \"square/okhttp\" library is licensed under the Apache 2.0 License.\n" +
                    "The \"gson\" library is licensed under the Apache 2.0 License.\n" +
                    "The \"slf4j-android\" library is licensed under the MIT License.\n" +
                    "The \"androidx\" library is licensed under the Apache 2.0 License.\n" +
                    "\n",
            modifier = Modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopAppBar(
    modifier: Modifier = Modifier,
    currentPage: MutableState<String>,
    isSearchBarEnabled: MutableState<Boolean>
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarColors(
            containerColor = DefaultBackgroundColor,
            scrolledContainerColor = DefaultBackgroundColorReverse,
            navigationIconContentColor = DefaultBackgroundColorReverse,
            titleContentColor = DefaultBackgroundColorReverse,
            actionIconContentColor = DefaultBackgroundColorReverse
        ),
        title = {
            Text(
                "NextTraceroute",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )
        },
        modifier = modifier
            .border(1.dp, DefaultBackgroundColorReverse)
            .padding(bottom = 1.dp),
        actions = {
            IconButton(onClick = { showMenu = !showMenu }
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        showMenu = false
                        currentPage.value = "settings"
                    },
                    enabled = isSearchBarEnabled.value
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = {
                        showMenu = false
                        currentPage.value = "about"
                    },
                    enabled = isSearchBarEnabled.value
                )
            }
        }
    )

}

@Composable
fun SettingsColumn(
    modifier: Modifier = Modifier, context: Context,
    currentPage: MutableState<String>,
    currentLanguage: MutableState<String>, isTraceMapEnabled: MutableState<Boolean>,
    maxTraceTTL: MutableIntState, traceTimeout: MutableState<String>,
    traceCount: MutableState<String>, currentDNSMode: MutableState<String>,
    tracerouteDNSServer: MutableState<String>, currentDOHServer: MutableState<String>,
    apiHostNamePOW: MutableState<String>, apiDNSNamePOW: MutableState<String>,
    apiHostName: MutableState<String>, apiDNSName: MutableState<String>,

    ) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val trHandler = TracerouteHandler()
    val scrollState = rememberScrollState()
    val languageExpanded = remember { mutableStateOf(false) }
    val languageOptions = listOf("Default", "zh", "en")
    val languageSelectedIndex = remember { mutableIntStateOf(0) }
    val enableTraceMapCheckedState = remember { mutableStateOf(true) }
    val maxHopSliderValue = remember { mutableFloatStateOf(30f) }
    val maxTimeoutSliderValue = remember { mutableFloatStateOf(1f) }
    val maxPacketCountSliderValue = remember { mutableFloatStateOf(5f) }
    val dnsModeExpanded = remember { mutableStateOf(false) }
    val dnsModeOptions = listOf("udp", "tcp", "doh")
    val dnsModeSelectedIndex = remember { mutableIntStateOf(0) }
    val dnsServerText = remember { mutableStateOf("") }
    val dohServersExpanded = remember { mutableStateOf(false) }
    val dohServersSelectedIndex = remember { mutableIntStateOf(0) }
    val dohServersOptions = listOf(
        "https://1.1.1.1/dns-query",
        "https://[2606:4700:4700::1111]/dns-query",
        "https://8.8.8.8/dns-query",
        "https://[2001:4860:4860::8888]/dns-query",
        "https://223.5.5.5/dns-query",
        "https://dns.cloudflare.com/dns-query",
        "https://dns.adguard-dns.com/dns-query",
        "https://doh.opendns.com/dns-query",
        "https://dns.google/dns-query",
        "https://ordns.he.net/dns-query",
        "https://dns.quad9.net/dns-query"
    )
    val powHostNameText = remember { mutableStateOf("") }
    val powDNSNameText = remember { mutableStateOf("") }
    val apiHostNameText = remember { mutableStateOf("") }
    val apiDNSNameText = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        languageSelectedIndex.intValue = languageOptions.indexOf(currentLanguage.value)
        enableTraceMapCheckedState.value = isTraceMapEnabled.value
        maxHopSliderValue.floatValue = maxTraceTTL.intValue.toFloat()
        maxTimeoutSliderValue.floatValue = traceTimeout.value.toFloat()
        maxPacketCountSliderValue.floatValue = traceCount.value.toFloat()
        dnsModeSelectedIndex.intValue = dnsModeOptions.indexOf(currentDNSMode.value)
        dnsServerText.value = tracerouteDNSServer.value
        dohServersSelectedIndex.intValue = dohServersOptions.indexOf(currentDOHServer.value)
        powHostNameText.value = apiHostNamePOW.value
        powDNSNameText.value = apiDNSNamePOW.value
        apiHostNameText.value = apiHostName.value
        apiDNSNameText.value = apiDNSName.value

    }
    BackHandler {
        currentPage.value = "main"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DefaultBackgroundColorReverse)
            .padding(bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { currentPage.value = "main" }) {
            Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White)
        }
        Button(
            modifier = Modifier.alignByBaseline(),
            onClick = {
                var errorText = ""
                currentLanguage.value = languageOptions[languageSelectedIndex.intValue]
                isTraceMapEnabled.value = enableTraceMapCheckedState.value
                maxTraceTTL.intValue = maxHopSliderValue.floatValue.toInt()
                traceTimeout.value = maxTimeoutSliderValue.floatValue.toInt().toString()
                traceCount.value = maxPacketCountSliderValue.floatValue.toInt().toString()
                currentDNSMode.value = dnsModeOptions[dnsModeSelectedIndex.intValue]
                if (trHandler.identifyInput(dnsServerText.value) == trHandler.IPV4IDENTIFIER ||
                    trHandler.identifyInput(dnsServerText.value) == trHandler.IPV6IDENTIFIER
                ) {
                    tracerouteDNSServer.value = dnsServerText.value
                } else {
                    errorText += "Invalid UDP/TCP DNS server, "
                }
                currentDOHServer.value = dohServersOptions[dohServersSelectedIndex.intValue]
                if (trHandler.identifyInput(powHostNameText.value) == trHandler.HOSTNAMEIDENTIFIER) {
                    apiHostNamePOW.value = powHostNameText.value
                } else {
                    errorText += "Invalid Hostname for POW server, "
                }
                if (trHandler.identifyInput(powDNSNameText.value) == trHandler.IPV4IDENTIFIER ||
                    trHandler.identifyInput(powDNSNameText.value) == trHandler.IPV6IDENTIFIER ||
                    trHandler.identifyInput(powDNSNameText.value) == trHandler.HOSTNAMEIDENTIFIER
                ) {
                    apiDNSNamePOW.value = powDNSNameText.value
                } else {
                    errorText += "Invalid DNS Hostname for POW server, "
                }

                if (trHandler.identifyInput(apiHostNameText.value) == trHandler.HOSTNAMEIDENTIFIER) {
                    apiHostName.value = apiHostNameText.value
                } else {
                    errorText += "Invalid Hostname for API server, "
                }

                if (trHandler.identifyInput(apiDNSNameText.value) == trHandler.IPV4IDENTIFIER ||
                    trHandler.identifyInput(apiDNSNameText.value) == trHandler.IPV6IDENTIFIER ||
                    trHandler.identifyInput(apiDNSNameText.value) == trHandler.HOSTNAMEIDENTIFIER
                ) {
                    apiDNSName.value = apiDNSNameText.value
                } else {
                    errorText += "Invalid DNS Hostname for API server, "
                }

                if (errorText != "") {
                    Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                } else {
                    val savingSettingsMap = mapOf(
                        "currentLanguage" to currentLanguage.value,
                        "isTraceMapEnabled" to isTraceMapEnabled.value,
                        "maxTraceTTL" to maxTraceTTL.intValue.toString(),
                        "traceTimeout" to traceTimeout.value,
                        "traceCount" to traceCount.value,
                        "currentDNSMode" to currentDNSMode.value,
                        "tracerouteDNSServer" to tracerouteDNSServer.value,
                        "currentDOHServer" to currentDOHServer.value,
                        "apiHostNamePOW" to apiHostNamePOW.value,
                        "apiDNSNamePOW" to apiDNSNamePOW.value,
                        "apiHostName" to apiHostName.value,
                        "apiDNSName" to apiDNSName.value
                    )
                    try {
                        val gson = Gson()
                        val jsonString = gson.toJson(savingSettingsMap)
                        context.openFileOutput("settings.json", Context.MODE_PRIVATE)
                            .use { outputStream ->
                                outputStream.write(jsonString.toByteArray())
                            }
                    } catch (e: Exception) {
                        Log.e("SettingSaveHandler", e.printStackTrace().toString())
                    }
                    Toast.makeText(context, "Change Saved!", Toast.LENGTH_LONG).show()
                }

            },
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonEnabledColor,
                contentColor = DefaultBackgroundColor,
                disabledContainerColor = Color.Gray,
                disabledContentColor = Color.LightGray
            )

        ) {
            Text("Save")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                //.border(1.dp, DefaultBackgroundColorReverse)
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("API Language   ", color = DefaultBackgroundColorReverse)
            TextButton(
                onClick = { languageExpanded.value = true },
                border = BorderStroke(2.dp, DefaultBackgroundColorReverse),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = languageOptions[languageSelectedIndex.intValue],
                    color = DefaultBackgroundColorReverse
                )
            }
            DropdownMenu(
                expanded = languageExpanded.value,
                onDismissRequest = { languageExpanded.value = false }
            ) {
                languageOptions.forEachIndexed { index, text ->
                    DropdownMenuItem(onClick = {
                        languageSelectedIndex.intValue = index
                        languageExpanded.value = false
                    }, text = { Text(text = text) })
                }
            }
        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enable TraceMap", color = DefaultBackgroundColorReverse)
            Checkbox(
                checked = enableTraceMapCheckedState.value,
                onCheckedChange = { enableTraceMapCheckedState.value = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Green,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White
                )
            )

        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("TTL:", color = DefaultBackgroundColorReverse)
            Text(
                maxHopSliderValue.floatValue.toInt().toString(),
                color = DefaultBackgroundColorReverse
            )
            Slider(
                value = maxHopSliderValue.floatValue,
                onValueChange = { newValue: Float ->
                    maxHopSliderValue.floatValue = newValue.roundToInt().toFloat()
                },
                valueRange = 1f..255f,
                steps = 254
            )

        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("Single Packet Timeout (Sec):", color = DefaultBackgroundColorReverse)
            Text(
                maxTimeoutSliderValue.floatValue.toInt().toString(),
                color = DefaultBackgroundColorReverse
            )
            Slider(
                value = maxTimeoutSliderValue.floatValue,
                onValueChange = { newValue: Float ->
                    maxTimeoutSliderValue.floatValue = newValue.roundToInt().toFloat()
                },
                valueRange = 1f..10f,
                steps = 10
            )

        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("Packet Count:", color = DefaultBackgroundColorReverse)
            Text(
                maxPacketCountSliderValue.floatValue.toInt().toString(),
                color = DefaultBackgroundColorReverse
            )
            Slider(
                value = maxPacketCountSliderValue.floatValue,
                onValueChange = { newValue: Float ->
                    maxPacketCountSliderValue.floatValue = newValue.roundToInt().toFloat()
                },
                valueRange = 1f..10f,
                steps = 10
            )

        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                //.border(1.dp, DefaultBackgroundColorReverse)
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DNS Mode   ", color = DefaultBackgroundColorReverse)
            TextButton(
                onClick = { dnsModeExpanded.value = true },
                border = BorderStroke(2.dp, DefaultBackgroundColorReverse),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = dnsModeOptions[dnsModeSelectedIndex.intValue],
                    color = DefaultBackgroundColorReverse
                )
            }
            DropdownMenu(
                expanded = dnsModeExpanded.value,
                onDismissRequest = { dnsModeExpanded.value = false }
            ) {
                dnsModeOptions.forEachIndexed { index, text ->
                    DropdownMenuItem(onClick = {
                        dnsModeSelectedIndex.intValue = index
                        dnsModeExpanded.value = false
                    }, text = { Text(text = text) })
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("UDP/TCP DNS Server:", color = DefaultBackgroundColorReverse)
            TextField(
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                value = dnsServerText.value,
                onValueChange = {
                    dnsServerText.value = it
                    dnsServerText.value = dnsServerText.value.replace("\n", "").trim()
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = DefaultBackgroundColor,
                    focusedContainerColor = DefaultBackgroundColor,
                    focusedTextColor = DefaultBackgroundColorReverse,
                    unfocusedTextColor = DefaultBackgroundColorReverse
                ),
                placeholder = {
                    Text("Insert IPv4 or IPv6", color = DefaultBackgroundColorReverse)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
            )

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                //.border(1.dp, DefaultBackgroundColorReverse)
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("Doh Server   ", color = DefaultBackgroundColorReverse)
            TextButton(
                onClick = { dohServersExpanded.value = true },
                border = BorderStroke(2.dp, DefaultBackgroundColorReverse),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = dohServersOptions[dohServersSelectedIndex.intValue],
                    color = DefaultBackgroundColorReverse
                )
            }
            DropdownMenu(
                expanded = dohServersExpanded.value,
                onDismissRequest = { dohServersExpanded.value = false }
            ) {
                dohServersOptions.forEachIndexed { index, text ->
                    DropdownMenuItem(onClick = {
                        dohServersSelectedIndex.intValue = index
                        dohServersExpanded.value = false
                    }, text = { Text(text = text) })
                }
            }
        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("POW Hostname:", color = DefaultBackgroundColorReverse)
            TextField(
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                value = powHostNameText.value,
                onValueChange = {
                    powHostNameText.value = it
                    powHostNameText.value = powHostNameText.value.replace("\n", "").trim()
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = DefaultBackgroundColor,
                    focusedContainerColor = DefaultBackgroundColor,
                    focusedTextColor = DefaultBackgroundColorReverse,
                    unfocusedTextColor = DefaultBackgroundColorReverse
                ),
                placeholder = {
                    Text("Insert Hostname", color = DefaultBackgroundColorReverse)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
            )

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("POW DNS Name:", color = DefaultBackgroundColorReverse)
            TextField(
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                value = powDNSNameText.value,
                onValueChange = {
                    powDNSNameText.value = it
                    powDNSNameText.value = powDNSNameText.value.replace("\n", "").trim()
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = DefaultBackgroundColor,
                    focusedContainerColor = DefaultBackgroundColor,
                    focusedTextColor = DefaultBackgroundColorReverse,
                    unfocusedTextColor = DefaultBackgroundColorReverse
                ),
                placeholder = {
                    Text("Insert Hostname, IPv4 or IPv6", color = DefaultBackgroundColorReverse)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
            )

        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("API Host Name:", color = DefaultBackgroundColorReverse)
            TextField(
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                value = apiHostNameText.value,
                onValueChange = {
                    apiHostNameText.value = it
                    apiHostNameText.value = apiHostNameText.value.replace("\n", "").trim()
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = DefaultBackgroundColor,
                    focusedContainerColor = DefaultBackgroundColor,
                    focusedTextColor = DefaultBackgroundColorReverse,
                    unfocusedTextColor = DefaultBackgroundColorReverse
                ),
                placeholder = {
                    Text("Insert Hostname", color = DefaultBackgroundColorReverse)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
            )

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("API DNS Name:", color = DefaultBackgroundColorReverse)
            TextField(
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
                value = apiDNSNameText.value,
                onValueChange = {
                    apiDNSNameText.value = it
                    apiDNSNameText.value = apiDNSNameText.value.replace("\n", "").trim()
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = DefaultBackgroundColor,
                    focusedContainerColor = DefaultBackgroundColor,
                    focusedTextColor = DefaultBackgroundColorReverse,
                    unfocusedTextColor = DefaultBackgroundColorReverse
                ),
                placeholder = {
                    Text("Insert Hostname, IPv4 or IPv6", color = DefaultBackgroundColorReverse)
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
            )

        }
        HorizontalDivider(color = Color.Yellow, thickness = 1.dp)


    }
}


@Composable
fun CheckThreadsStatus(
    scope: CoroutineScope,
    mutex: Mutex,
    tracerouteThreadsIntList: MutableList<Int>,
    isSearchBarEnabled: MutableState<Boolean>,
    multipleIps: MutableList<MutableState<String>>,
    isDNSInProgress: MutableState<Boolean>
) {
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            delay(timeMillis = 200)
            while (isDNSInProgress.value) {
                return@launch
            }
            var allDone = true
            delay(timeMillis = 10000)
            while (allDone) {
                delay(timeMillis = 1000)
                if (tracerouteThreadsIntList.all { it == 0 }) {
                    allDone = false
                }

            }
            mutex.withLock {
                tracerouteThreadsIntList.removeAll { it == 0 }
            }
            //tracerouteThreadsIntList.clear()
            if (multipleIps.size == 0) {
                isSearchBarEnabled.value = true
            }
        }
    }

}


fun clearData(
    multipleIps: MutableList<MutableState<String>>,
    insertErrorText: MutableState<String>,
    nativePingCheckErrorText: MutableState<String>,
    singleHopCursor: MutableList<MutableState<String>>,
    gridDataList: MutableList<MutableList<MutableList<MutableState<String>>>>,
    testAPIText: MutableState<String>,
    preferredAPIIp: MutableState<String>,
    apiDNSList: MutableList<String>,
    apiToken: MutableState<String>,
    preferredAPIIpPOW: MutableState<String>,
    apiDNSListPOW: MutableList<String>,
    traceMapThreadsIntList: MutableList<Int>,
    traceMapThreadsMapList: MutableList<List<MutableMap<String, Any?>>>,
    traceMapURL: MutableState<String>
) {
    traceMapURL.value = ""
    testAPIText.value = ""
    preferredAPIIp.value = ""
    apiDNSList.clear()
    apiDNSListPOW.clear()
    preferredAPIIpPOW.value = ""
    apiToken.value = ""
    multipleIps.clear()
    nativePingCheckErrorText.value = ""
    insertErrorText.value = ""
    traceMapThreadsIntList.clear()
    traceMapThreadsMapList.clear()
    singleHopCursor.forEach { i ->
        i.value = ""
    }
    for (gridRow in gridDataList) {
        for (gridColumn in gridRow) {
            for (i in gridColumn) {
                i.value = ""
            }
        }
    }

}

//@Preview(showBackground = true)
@Composable
fun MainColumn(
    currentLanguage: MutableState<String>, isTraceMapEnabled: MutableState<Boolean>,
    maxTraceTTL: MutableIntState, traceTimeout: MutableState<String>,
    traceCount: MutableState<String>, tracerouteDNSServer: MutableState<String>,
    apiHostNamePOW: MutableState<String>, apiDNSNamePOW: MutableState<String>,
    apiHostName: MutableState<String>, apiDNSName: MutableState<String>,
    context: Context, isSearchBarEnabled: MutableState<Boolean>,
    currentDOHServer: MutableState<String>,
    currentDNSMode: MutableState<String>, listState: LazyListState,
    isScrollToFirstLineTriggered: MutableState<Boolean>,
) {

    val threadMutex = Mutex()
    val traceMapMutex = Mutex()
    val tracerouteThreadsIntList = remember { mutableStateListOf<Int>() }
    val traceMapThreadsIntList = remember { mutableStateListOf<Int>() }
    val traceMapThreadsMapList = remember { mutableListOf<List<MutableMap<String, Any?>>>() }
    val traceMapURL = remember { mutableStateOf("") }
    val multipleIps = remember { mutableStateListOf<MutableState<String>>() }
    val isDNSInProgress = remember { mutableStateOf(false) }
    val isNativePing4Available = remember { mutableStateOf(true) }
    val isNativePing6Available = remember { mutableStateOf(true) }
    val nativePingCheckErrorText = remember { mutableStateOf("") }
    val isButtonClicked = remember { mutableStateOf(false) }
    val singleHopCursor = remember(maxTraceTTL.intValue) {
        MutableList(maxTraceTTL.intValue) { mutableStateOf("") }
    }
    val insertErrorText = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val searchText = remember { mutableStateOf("") }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val trHandler = remember { TracerouteHandler() }


    val preferredAPIIpPOW = remember { mutableStateOf("") }
    val apiDNSListPOW = remember { mutableListOf<String>() }
    val apiToken = remember { mutableStateOf("") }
    val preferredAPIIp = remember { mutableStateOf("") }
    val apiDNSList = remember { mutableListOf<String>() }
    //val gridRows=remember{ mutableIntStateOf(255) }
    val basicGridData = remember {
        mutableStateListOf(
            mutableStateListOf(
                mutableStateOf(""), mutableStateOf(""),
                mutableStateOf(""), mutableStateOf("")
            ),
            mutableStateListOf(mutableStateOf("")),
            mutableStateListOf(mutableStateOf(""), mutableStateOf(""))
        )
    }
    val gridDataList = remember(maxTraceTTL.intValue) {
        mutableStateListOf<MutableList<MutableList<MutableState<String>>>>().apply {
            repeat(maxTraceTTL.intValue) {
                add(basicGridData.map { row ->
                    row.map { item ->
                        mutableStateOf(item.value)
                    }.toMutableList()
                }.toMutableList())
            }
        }
    }
    val testText = remember { mutableStateOf("") }





    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        //Top Bar

        Spacer(modifier = Modifier.height(8.dp))
        //Run button
        if (isButtonClicked.value) {
            isButtonClicked.value = false
            isSearchBarEnabled.value = false
            clearData(
                multipleIps = multipleIps,
                nativePingCheckErrorText = nativePingCheckErrorText,
                singleHopCursor = singleHopCursor,
                gridDataList = gridDataList, insertErrorText = insertErrorText,
                testAPIText = testText, preferredAPIIp = preferredAPIIp,
                apiDNSList = apiDNSList, preferredAPIIpPOW = preferredAPIIpPOW,
                apiDNSListPOW = apiDNSListPOW,
                apiToken = apiToken, traceMapThreadsIntList = traceMapThreadsIntList,
                traceMapThreadsMapList = traceMapThreadsMapList,
                traceMapURL = traceMapURL
            )
            trHandler.APIPOWHandler(
                scope = coroutineScope, threadMutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreadsIntList,
                tracerouteDNSServer = tracerouteDNSServer,
                preferredAPIIp = preferredAPIIpPOW, //testAPIText = testText,
                apiHostName = apiHostNamePOW,
                apiDNSName = apiDNSNamePOW,
                apiDNSList = apiDNSListPOW,
                apiToken = apiToken,
                currentDOHServer = currentDOHServer,
                currentDNSMode = currentDNSMode
            )
            trHandler.APIDNSHandler(
                scope = coroutineScope, threadMutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreadsIntList,
                tracerouteDNSServer = tracerouteDNSServer,
                preferredAPIIp = preferredAPIIp, //testAPIText = testText,
                apiHostName = apiHostName,
                apiDNSName = apiDNSName,
                apiDNSList = apiDNSList,
                apiToken = apiToken,
                currentDNSMode = currentDNSMode,
                currentDOHServer = currentDOHServer
            )
            trHandler.testNativePing(
                v4Status = isNativePing4Available,
                v6Status = isNativePing6Available, errorText = nativePingCheckErrorText
            )
            trHandler.InsertHandler(
                threadMutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreadsIntList,
                insertion = searchText,
                insertErrorText = insertErrorText,
                gridDataList = gridDataList,
                scope = coroutineScope,
                maxTTL = maxTraceTTL, count = traceCount, timeout = traceTimeout,
                multipleIps = multipleIps,
                tracerouteDNSServer = tracerouteDNSServer, context = context,
                isDNSInProgress = isDNSInProgress,
                //testAPIText = testText,
                currentDOHServer = currentDOHServer,
                currentDNSMode = currentDNSMode, traceMapMutex = traceMapMutex,
                isTraceMapEnabled = isTraceMapEnabled,
                traceMapURL = traceMapURL,
                preferredAPIIp = preferredAPIIp,
                apiHostName = apiHostName,
                traceMapThreadsIntList = traceMapThreadsIntList,
                traceMapThreadsMapList = traceMapThreadsMapList,
                isSearchBarEnabled = isSearchBarEnabled
            )
            CheckThreadsStatus(
                scope = coroutineScope,
                mutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreadsIntList,
                isDNSInProgress = isDNSInProgress,
                isSearchBarEnabled = isSearchBarEnabled,
                multipleIps = multipleIps
            )
            isScrollToFirstLineTriggered.value = true
        }
        //Compare singleHopCursor with current value

        trHandler.EachHopHandler(
            threadMutex = threadMutex,
            tracerouteThreadsIntList = tracerouteThreadsIntList,
            singleHopCursor = singleHopCursor,
            gridDataList = gridDataList,
            scope = coroutineScope,
            count = traceCount,

            timeout = traceTimeout,
            tracerouteDNSServer = tracerouteDNSServer,
            //testAPIText = testText,
            preferredAPIIp = preferredAPIIp,
            apiHostName = apiHostName,

            apiToken = apiToken,
            currentLanguage = currentLanguage,
            traceMapThreadsIntList = traceMapThreadsIntList,
            traceMapThreadsMapList = traceMapThreadsMapList,
            traceMapMutex = traceMapMutex,
            currentDNSMode = currentDNSMode,
            currentDOHServer = currentDOHServer
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SearchBar(
                onSearchResults = searchText,
                modifier = Modifier.weight(1f),
                isButtonClicked = isButtonClicked,
                isSearchBarEnabled = isSearchBarEnabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier = Modifier.alignByBaseline(),
                enabled = isSearchBarEnabled.value,
                onClick = { isButtonClicked.value = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSearchBarEnabled.value) ButtonEnabledColor else ButtonDisabledColor,
                    contentColor = DefaultBackgroundColor,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.LightGray
                )

            ) {
                Text("Run")
            }

        }
        if (!isSearchBarEnabled.value && tracerouteThreadsIntList.any { it != 0 }) {
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(
                color = DefaultBackgroundColorReverse,
                strokeWidth = 4.dp,
                modifier = Modifier.size(50.dp)
            )
        }

        if (insertErrorText.value != "") {
            Text(text = insertErrorText.value, color = DefaultBackgroundColorReverse)
        }
        if (testText.value != "") {
            Text(text = testText.value, color = DefaultBackgroundColorReverse)
        }
        if (nativePingCheckErrorText.value != "") {
            Toast.makeText(context, nativePingCheckErrorText.value, Toast.LENGTH_LONG).show()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (traceMapURL.value != "") {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(traceMapURL.value)
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonEnabledColor,
                        contentColor = DefaultBackgroundColor,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.LightGray
                    )
                ) {
                    Text("Map")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (tracerouteThreadsIntList.all { it == 0 }) {
                Button(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(
                                gridDataList.joinToString(
                                    separator = "\n",
                                    prefix = "Traceroute Result:\n"
                                ) { layer ->
                                    if (layer[0][0].value != "") {
                                        layer.joinToString(separator = "\n") { row ->
                                            row.joinToString(separator = ", ") { cell ->
                                                cell.value
                                            }
                                        }
                                    } else {
                                        ""
                                    }
                                }.trim()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonEnabledColor,
                        contentColor = DefaultBackgroundColor,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.LightGray
                    )
                ) {
                    Text("Copy Result")
                }
            }
        }
        //test text
//        var testtest: MutableList<Int> = tracerouteThreadsIntList.toMutableStateList()
//        Text(text = testtest.toList().toString(), color = DefaultBackgroundColorReverse)
        //test button
//        Button(onClick = {
//            gridDataList[0][0][0].value = "1"
//            gridDataList[0][0][1].value = "114.114.514.81"
//            gridDataList[0][0][2].value = "AS114514"
//            gridDataList[0][0][3].value = "[CDN77-peerlinks]"
//            gridDataList[0][1][0].value = "中国 香港   cloudflare.com"
//            gridDataList[0][2][0].value = "4.example.com"
//            gridDataList[0][2][1].value = " 358.48 ms / 362.66 ms / 362.60 ms"
//
//        }) {
//            Text("Update")
//        }
        //Select a ip and change

        if (multipleIps.size != 0 && tracerouteThreadsIntList.none { it != 0 }) {
            for (i in multipleIps) {
                Button(
                    onClick = {
                        searchText.value = i.value
                        multipleIps.clear()
                        isButtonClicked.value = true

                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonEnabledColor,
                        contentColor = DefaultBackgroundColor,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.LightGray
                    )
                ) {
                    Text(i.value)
                }
            }
        }
        //LazyVerticalGrid(columns = GridCells.Fixed(gridRows),
        LazyColumn(
            state = listState,
            modifier = Modifier
                .border(1.dp, DefaultBackgroundColorReverse)
            //.padding(bottom = 1.dp)
        ) {
            items(gridDataList.size) { gridDataListItemIndex ->
                val gridDataListItem = gridDataList[gridDataListItemIndex]
                //for ((gridDataListItemIndex, gridDataListItem) in gridDataList.withIndex()) {
                for ((gridDataIndex, gridDataItem) in gridDataListItem.withIndex()) {
                    val arrangementForOneColumn =
                        remember { mutableStateOf(Arrangement.SpaceBetween) }
                    if (gridDataItem.size == 1) {
                        arrangementForOneColumn.value = Arrangement.Center
                    }
                    //Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = arrangementForOneColumn.value,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for ((colIndex, item) in gridDataItem.withIndex()) {
//                            var uGridIndex = remember { mutableIntStateOf(
//                                gridDataListItemIndex*100+gridDataIndex*10+colIndex) }
                            val colorForSpecialUse = remember {
                                mutableStateOf(Color.White)
                            }
                            if (gridDataIndex == 0 && colIndex == 0) {
                                colorForSpecialUse.value = Color.Yellow
                            }
                            if (gridDataIndex == 0 && (colIndex == 2 || colIndex == 3)) {
                                colorForSpecialUse.value = Color.Green
                            }
                            if (gridDataIndex == 2 && (colIndex == 1)) {
                                colorForSpecialUse.value = Color(0xFF00FFFF)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(8.dp)
                                    //.border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                    .pointerInput(item) {
                                        detectTapGestures(
                                            onLongPress = {
                                                clipboardManager.setText(AnnotatedString(item.value))
                                            },
                                            onTap = {
                                                if (item.value != "*" && item.value != "") {
                                                    if (!(gridDataIndex == 0 && colIndex == 0) && !(gridDataIndex == 2 && colIndex == 1)) {
                                                        val tapURL =
                                                            "https://bgp.tools/search?q=" + item.value
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse(tapURL)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        )
                                    },

                                ) {
                                Text(
                                    text = item.value,
                                    style = TextStyle(color = colorForSpecialUse.value)
                                )

                            }
                        }
                    }
                    if (gridDataIndex == 2) {
                        HorizontalDivider(
                            modifier = Modifier,
                            thickness = 1.dp,
                            color = Color.Yellow
                        )
                    }

                }
            }


        }


    }


}


@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onSearchResults: MutableState<String>,
    isSearchBarEnabled: MutableState<Boolean>,
    isButtonClicked: MutableState<Boolean>
) {
    // var searchText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    TextField(
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                if (isSearchBarEnabled.value) {
                    isButtonClicked.value = true
                }
                keyboardController?.hide()
            }
        ),
        singleLine = true,
        value = onSearchResults.value,
        //search bar can only clicked again if everything is done
        onValueChange = {
            if (isSearchBarEnabled.value) {
                onSearchResults.value = it
                val regex = """.*://?([^/]+)""".toRegex()
                val matchResult = regex.find(onSearchResults.value)
                onSearchResults.value = matchResult?.groups?.get(1)?.value ?: onSearchResults.value
                onSearchResults.value = onSearchResults.value.replace("\n", "").trim()

            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = DefaultBackgroundColor,
            focusedContainerColor = DefaultBackgroundColor,
            focusedTextColor = DefaultBackgroundColorReverse,
            unfocusedTextColor = DefaultBackgroundColorReverse
        ),
        placeholder = {
            Text("Insert Host, IPv4 or IPv6", color = DefaultBackgroundColorReverse)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    )

}




