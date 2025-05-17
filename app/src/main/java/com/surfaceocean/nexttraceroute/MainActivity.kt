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

Disclaimer: The NextTrace API (hosted at nxtrace.org) used by default in this program is not managed by the program's developer.
We do not guarantee the performance, accuracy, or any other aspect of the NextTrace API,
nor do we endorse, approve, or guarantee the results returned by the NextTrace API. Users may customize the API server address themselves.

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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import androidx.room.Room
import androidx.room.withTransaction
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NextTracerouteTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .systemBarsPadding(),
                    //color = MaterialTheme.colorScheme.background,
                    color = DefaultBackgroundColor
                ) {
                    org.xbill.DNS.config.AndroidResolverConfigProvider.setContext(this)
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
                    val db = Room.databaseBuilder(
                        context,
                        AppDatabase::class.java, "app-database"
                    ).build()
                    val historyDao = db.historyDao()

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
                            Log.e("readSettingsHandler", e.printStackTrace().toString())
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
                            db.close()
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

                            "history" -> {
                                HistoryPage(
                                    context = context,
                                    currentPage = currentPage,
                                    historyDao = historyDao,
                                    db = db
                                )
                            }

                            "main" -> {
                                MyTopAppBar(
                                    currentPage = currentPage,
                                    isSearchBarEnabled = isSearchBarEnabled,
                                    context = context
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
                                    isScrollToFirstLineTriggered = isScrollToFirstLineTriggered,
                                    historyDao = historyDao,
                                    db = db
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
            .statusBarsPadding()
            .systemBarsPadding()
            .border(1.dp, Color.DarkGray)
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
            text = "NextTraceroute version " +
                    BuildConfig.VERSION_NAME + ", an Android traceroute app using Nexttrace API\n" +
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
                    "Disclaimer: The NextTrace API (hosted at nxtrace.org) used by default in this program is not managed by the program's developer.\n" +
                    "We do not guarantee the performance, accuracy, or any other aspect of the NextTrace API,\n" +
                    "nor do we endorse, approve, or guarantee the results returned by the NextTrace API. Users may customize the API server address themselves.\n\n" +
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
    isSearchBarEnabled: MutableState<Boolean>,
    context: Context
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarColors(
            containerColor = Color.Black,
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
            .border(1.dp, Color.DarkGray)
            .padding(bottom = 1.dp)
            .statusBarsPadding()
            .systemBarsPadding(),
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
                    modifier = Modifier.background(DefaultBackgroundColor),
                    text = {
                        Text(
                            "Settings",
                            color = if (isSearchBarEnabled.value) DefaultBackgroundColorReverse else Color.Gray
                        )
                    },
                    onClick = {
                        showMenu = false
                        currentPage.value = "settings"
                    },
                    enabled = isSearchBarEnabled.value
                )
                DropdownMenuItem(
                    modifier = Modifier.background(DefaultBackgroundColor),
                    text = {
                        Text(
                            "History",
                            color = if (isSearchBarEnabled.value) DefaultBackgroundColorReverse else Color.Gray
                        )
                    },
                    onClick = {
                        showMenu = false
                        currentPage.value = "history"
                    },
                    enabled = isSearchBarEnabled.value
                )
                DropdownMenuItem(
                    modifier = Modifier.background(DefaultBackgroundColor),
                    text = {
                        Text(
                            "About",
                            color = if (isSearchBarEnabled.value) DefaultBackgroundColorReverse else Color.Gray
                        )
                    },
                    onClick = {
                        showMenu = false
                        currentPage.value = "about"
                    },
                    enabled = isSearchBarEnabled.value
                )
                DropdownMenuItem(
                    modifier = Modifier.background(DefaultBackgroundColor),
                    text = {
                        Text(
                            "Privacy Policy",
                            color = if (isSearchBarEnabled.value) DefaultBackgroundColorReverse else Color.Gray
                        )
                    },
                    onClick = {
                        val privacyURL =
                            "https://github.com/nxtrace/NextTraceroute/blob/master/PrivacyPolicy.md"
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                privacyURL.toUri()
                            )
                        )
                    },
                    enabled = isSearchBarEnabled.value
                )
            }
        }
    )

}


@Composable
fun CheckThreadsStatus(
    scope: CoroutineScope,
    mutex: Mutex,
    tracerouteThreadsIntList: MutableList<Int>,
    isSearchBarEnabled: MutableState<Boolean>,
    multipleIps: MutableList<MutableState<String>>,
    isDNSInProgress: MutableState<Boolean>,
    currentDomain: MutableState<String>,
    searchText: MutableState<String>,
    copyHistory: MutableState<String>,
    historyDao: HistoryDao,
    db: AppDatabase
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
                //Add history after all threads are finished
                val historyData = HistoryData(
                    ip = searchText.value,
                    domain = currentDomain.value,
                    history = copyHistory.value
                )
                db.withTransaction {
                    historyDao.insertHistory(historyData)
                }
                currentDomain.value = ""
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
    traceMapThreadsMapList: MutableList<List<MutableMap<String, Any?>>>,
    traceMapURL: MutableState<String>, isAPIFinished: MutableState<Boolean>,
    copyHistory: MutableState<String>
) {
    copyHistory.value = ""
    isAPIFinished.value = false
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
    historyDao: HistoryDao, db: AppDatabase
) {

    val threadMutex = Mutex()
    val tracerouteThreadsIntList = remember { mutableStateListOf<Int>() }
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
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val keyboardController = LocalSoftwareKeyboardController.current
    val trHandler = remember { TracerouteHandler() }


    val preferredAPIIpPOW = remember { mutableStateOf("") }
    val apiDNSListPOW = remember { mutableListOf<String>() }
    val apiToken = remember { mutableStateOf("") }
    val preferredAPIIp = remember { mutableStateOf("") }
    val apiDNSList = remember { mutableListOf<String>() }
    val isAPIFinished = remember { mutableStateOf(false) }

    val currentDomain = remember { mutableStateOf("") }

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
    val copyHistory = remember { mutableStateOf("") }




    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        //Top Bar

        Spacer(modifier = Modifier.height(8.dp))
        //Run button
        if (isButtonClicked.value) {
            isButtonClicked.value = false
            isSearchBarEnabled.value = false
            keyboardController?.hide()
            clearData(
                multipleIps = multipleIps,
                nativePingCheckErrorText = nativePingCheckErrorText,
                singleHopCursor = singleHopCursor,
                gridDataList = gridDataList, insertErrorText = insertErrorText,
                testAPIText = testText, preferredAPIIp = preferredAPIIp,
                apiDNSList = apiDNSList, preferredAPIIpPOW = preferredAPIIpPOW,
                apiDNSListPOW = apiDNSListPOW,
                apiToken = apiToken,
                traceMapThreadsMapList = traceMapThreadsMapList,
                traceMapURL = traceMapURL, isAPIFinished = isAPIFinished,
                copyHistory = copyHistory
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
                testAPIText = testText,
                currentDOHServer = currentDOHServer,
                currentDNSMode = currentDNSMode,
                isTraceMapEnabled = isTraceMapEnabled,
                traceMapURL = traceMapURL,
                preferredAPIIp = preferredAPIIp,
                apiHostName = apiHostName,
                traceMapThreadsMapList = traceMapThreadsMapList,
                isSearchBarEnabled = isSearchBarEnabled,
                isAPIFinished = isAPIFinished,
                apiToken = apiToken,
                currentLanguage = currentLanguage,
                apiDNSList = apiDNSList,
                apiDNSListPOW = apiDNSListPOW,
                apiDNSName = apiDNSName,
                apiDNSNamePOW = apiDNSNamePOW,
                apiHostNamePOW = apiHostNamePOW,
                preferredAPIIpPOW = preferredAPIIpPOW
            )
            CheckThreadsStatus(
                scope = coroutineScope,
                mutex = threadMutex,
                tracerouteThreadsIntList = tracerouteThreadsIntList,
                isDNSInProgress = isDNSInProgress,
                isSearchBarEnabled = isSearchBarEnabled,
                multipleIps = multipleIps,
                currentDomain = currentDomain,
//                insertErrorText = insertErrorText,
                searchText = searchText,
                copyHistory = copyHistory,
                historyDao = historyDao,
                db = db
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
            val searchDatabaseResultList = remember { mutableStateListOf<String>() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(searchText.value) {
                scope.launch(Dispatchers.IO) {
                    if (isSearchBarEnabled.value && searchText.value != "") {
                        try {
                            db.withTransaction {
                                searchDatabaseResultList.clear()
                                val searchDatabaseReturn =
                                    (historyDao.findInputIP(searchText.value) + historyDao.findInputDomain(
                                        searchText.value
                                    )).distinct()
                                //only add if it's not perfectly matched
                                if (!(searchDatabaseReturn.size == 1 && searchDatabaseReturn[0] == searchText.value)
                                ) {
                                    searchDatabaseResultList.addAll(searchDatabaseReturn)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("databaseHandler", e.printStackTrace().toString())
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = isSearchBarEnabled.value && searchDatabaseResultList.size != 0,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                ),
                onDismissRequest = { }
            ) {
                searchDatabaseResultList.forEach { text ->
                    DropdownMenuItem(onClick = {
                        searchText.value = text
                        searchDatabaseResultList.clear()
                    }, text = { Text(text = text) })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier = Modifier.alignByBaseline(),
                enabled = isSearchBarEnabled.value,
                onClick = { isButtonClicked.value = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSearchBarEnabled.value) ButtonEnabledColor else ButtonDisabledColor,
                    contentColor = DefaultBackgroundColor,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray
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
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (insertErrorText.value != "") {
            Text(text = insertErrorText.value, color = DefaultBackgroundColorReverse)
        }
        if (testText.value != "") {
            Text(text = testText.value, color = DefaultBackgroundColorReverse)
//            clipboardManager.setPrimaryClip(ClipData.newPlainText("simple text",testText.value))
        }
        if (nativePingCheckErrorText.value != "") {
            Toast.makeText(context, nativePingCheckErrorText.value, Toast.LENGTH_LONG).show()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (traceMapURL.value != "" && Patterns.WEB_URL.matcher(traceMapURL.value).matches()) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                traceMapURL.value.toUri()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonEnabledColor,
                        contentColor = DefaultBackgroundColor,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.DarkGray
                    )
                ) {
                    Text("Map")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (tracerouteThreadsIntList.all { it == 0 }) {
                copyHistory.value = gridDataList.joinToString(
                    separator = "\n",
                    prefix = "Traceroute Result:\n" + "IP:" + searchText.value + "\n" + "Domain:" + currentDomain.value + "\n"
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
                Button(
                    onClick = {
                        clipboardManager.setPrimaryClip(
                            ClipData.newPlainText("simple text", copyHistory.value)
                        )
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()

                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonEnabledColor,
                        contentColor = DefaultBackgroundColor,
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.DarkGray
                    )
                ) {
                    Text("Copy Result")
                }
            }
        }
        //test text

//        val testtest: MutableList<Int> = tracerouteThreadsIntList.toMutableStateList()
//        Text(text = testtest.toList().toString(), color = DefaultBackgroundColorReverse)
        //test button
//        Button(onClick = {
//            gridDataList[0][0][0].value = "1"
//            gridDataList[0][0][1].value = "114.51.41.91"
//            gridDataList[0][0][2].value = "AS114514"
//            gridDataList[0][0][3].value = "[EXAMPLE-peers]"
//            gridDataList[0][1][0].value = "United States example.com"
//            gridDataList[0][2][0].value = "123.example.com"
//            gridDataList[0][2][1].value = " 123.45 ms / 234.56 ms / 345.67 ms"
//
//        }) {
//            Text("Update")
//        }
        //Select a ip and change

        if (multipleIps.size != 0 && tracerouteThreadsIntList.none { it != 0 }) {
            LazyColumn(
                modifier = Modifier
                    .border(1.dp, Color.DarkGray)
                    .fillMaxWidth()
            ) {
                itemsIndexed(
                    items = multipleIps,
                    key = { _, item -> item.value }) { _, multipleIPItem ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = {
                                currentDomain.value = searchText.value
                                searchText.value = multipleIPItem.value
                                multipleIps.clear()
                                isButtonClicked.value = true

                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonEnabledColor,
                                contentColor = DefaultBackgroundColor,
                                disabledContainerColor = Color.Gray,
                                disabledContentColor = Color.DarkGray
                            )
                        ) {
                            Text(multipleIPItem.value)
                        }
                    }
                }
            }
        }
        //LazyVerticalGrid(columns = GridCells.Fixed(gridRows),
        LazyColumn(
            state = listState,
            modifier = Modifier
                .border(1.dp, Color.DarkGray)
            //.padding(bottom = 1.dp)
        ) {
            itemsIndexed(items = gridDataList) { _, gridDataListItem ->
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
                                                clipboardManager.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "simple text",
                                                        item.value
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Copied!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onTap = {
                                                if (item.value != "*" && item.value != "") {
                                                    if (!(gridDataIndex == 0 && colIndex == 0) && !(gridDataIndex == 2 && colIndex == 1)) {
                                                        val tapURL =
                                                            "https://bgp.tools/search?q=" + item.value
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                tapURL.toUri()
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
                            color = Color.DarkGray
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
        textStyle = TextStyle(color = DefaultBackgroundColorReverse),
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
            unfocusedTextColor = DefaultBackgroundColorReverse,
            unfocusedIndicatorColor = Color.DarkGray
        ),
        placeholder = {
            Text("Insert Host, IPv4 or IPv6", color = DefaultBackgroundColorReverse)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    )

}




