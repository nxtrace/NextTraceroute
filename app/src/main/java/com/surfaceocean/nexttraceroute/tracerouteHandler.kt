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


import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Lookup
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.random.Random


class TracerouteHandler {
    val IPV4IDENTIFIER = "IPv4"
    val IPV6IDENTIFIER = "IPv6"
    val HOSTNAMEIDENTIFIER = "Hostname"
    val ERRORIDENTIFIER = "ERR"


    fun testNativePing(
        v4Status: MutableState<Boolean>,
        v6Status: MutableState<Boolean>,
        errorText: MutableState<String>
    ) {
        try {
            val process = Runtime.getRuntime().exec("ping")
            process.waitFor()
        } catch (e: Exception) {
            Log.e("testNativePing", e.printStackTrace().toString())
            v4Status.value = false
        }
        try {
            val process6 = Runtime.getRuntime().exec("ping6")
            process6.waitFor()

        } catch (e: Exception) {
            Log.e("testNativePing", e.printStackTrace().toString())
            v6Status.value = false
        }
        if (v4Status.value && v6Status.value) {
            errorText.value = ""
        } else if (v4Status.value) {
            errorText.value = "IPv6 native ping failed! Using linux api instead. (Unstable)"
        } else if (v6Status.value) {
            errorText.value = "IPv4 native ping failed! Using linux api instead. (Unstable)"
        } else {
            errorText.value =
                "IPv4 and IPv6 native ping failed! Using linux api instead. (Unstable)"
        }

    }

    private fun extractRttValues(inputString: String): String {
        //val regex = """(?i)rtt[^0-9]*(\d+(\.\d+)?)(/(\d+(\.\d+)?))*""".toRegex()
        val regex = "(?i)^.*rtt.*=\\s*(.*)\$".toRegex(setOf(RegexOption.MULTILINE))
        val matchResult = regex.find(inputString)
        //var finalResult=""
        return if (matchResult != null) {
            matchResult.groupValues[1].trim()
        } else {
            "*"
        }
    }

    private fun nativeGetHopIPv4(inputText: String): String {
        val linePattern = "(?i).*exceeded.*".toRegex()
        val unreachablePattern = "(?i).*unreachable.*".toRegex()
        val fromPattern = "(?i)from\\s+([\\d.]+)".toRegex()
        //val fromPattern = ("(?i)from\\s+([^\\s]*)\\s").toRegex()
        val lines = inputText.lines().filter { linePattern.containsMatchIn(it) }
        for (line in lines) {
            val match1 = fromPattern.find(line)
            if (match1 != null) {
                return match1.groupValues[1].trim()
            }
        }
        //drop unreachable
        val notUnreachableLines =
            inputText.lines().filter { !unreachablePattern.containsMatchIn(it) }
        for (line in notUnreachableLines) {
            val match2 = fromPattern.find(line)
            if (match2 != null) {
                return match2.groupValues[1].trim()
            }
        }
        return "*"

    }

    private fun nativeGetHopIPv6(inputText: String): String {
        val linePattern = "(?i).*exceeded.*".toRegex()
        val unreachablePattern = "(?i).*unreachable.*".toRegex()
        //val fromPattern = ("(?i)from\\s+([0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{0,4}){1,7}|::|([0-9a-fA-F]{1,4}:){1,6}:|:(:[0-9a-fA-F]{1,4}){1,6})").toRegex()
        val fromPattern = ("(?i)from\\s+([^\\s]*)\\s").toRegex()
        val lines = inputText.lines().filter { linePattern.containsMatchIn(it) }
        for (line in lines) {
            val match1 = fromPattern.find(line)
            if (match1 != null) {
                return match1.groupValues[1].trim()
            }
        }
        //drop unreachable
        val notUnreachableLines =
            inputText.lines().filter { !unreachablePattern.containsMatchIn(it) }
        for (line in notUnreachableLines) {
            val match2 = fromPattern.find(line)
            if (match2 != null) {
                var match2Result = match2.groupValues[1].trim()
                if (match2Result.isNotEmpty()) {
                    match2Result = match2Result.substring(0, match2Result.length - 1)
                }
                return match2Result
            }
        }
        return "*"

    }


    private fun rho(challenge: BigInteger): BigInteger {
        val two = BigInteger.valueOf(2L)
        if (challenge.mod(two) == BigInteger.ZERO) {
            return two
        }

        var x = challenge
        var y = challenge
        val c = BigInteger.ONE
        var g = BigInteger.ONE

        while (g == BigInteger.ONE) {
            x = x.multiply(x).add(c).mod(challenge)
            y = y.multiply(y).add(c).mod(challenge)
            y = y.multiply(y).add(c).mod(challenge)
            g = (x.subtract(y)).abs().gcd(challenge)
        }
        return g
    }

    //Pollard's Rho algorithm
    private fun powHandler(challenge: BigInteger): MutableList<BigInteger> {
        val one = BigInteger.ONE
        if (challenge == one) {
            return mutableListOf()
        }
        val factor = rho(challenge)
        if (factor == challenge) {
            return listOf(challenge).toMutableList()
        }
        return (powHandler(factor) + powHandler(challenge.divide(factor))).sorted().toMutableList()
    }


    @Composable
    fun APIDNSHandler(//testAPIText: MutableState<String>,
        threadMutex: Mutex, tracerouteThreadsIntList: MutableList<Int>, scope: CoroutineScope,
        tracerouteDNSServer: MutableState<String>,
        apiHostName: MutableState<String>, apiDNSName: MutableState<String>,
        preferredAPIIp: MutableState<String>, apiDNSList: MutableList<String>,
        apiToken: MutableState<String>, currentDOHServer: MutableState<String>,
        currentDNSMode: MutableState<String>
    ) {

        if (preferredAPIIp.value == "") {
            val apiMutex = Mutex()
            val apiMutexList = mutableListOf(0)
            ResolveHandler(
                threadMutex = apiMutex, tracerouteThreadsIntList = apiMutexList,
                scope = scope,
                name = apiDNSName.value, tracerouteDNSServer = tracerouteDNSServer,
                multipleIps = apiDNSList, multipleIpStateMode = false,
                currentDNSMode = currentDNSMode,
                currentDOHServer = currentDOHServer
            )

            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.IO) {
                    val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                    threadMutex.withLock {
                        tracerouteThreadsIntList.add(uniqueID)
                    }
                    //delay(timeMillis = 1000)
                    //it starts after DNS resolution finished
                    var maxTriesOfAPIToken = 20
                    while (maxTriesOfAPIToken >= 0) {
                        delay(timeMillis = 500)
                        if (apiMutexList.all { it == 0 } && apiToken.value != "") {
                            break
                        }
                        maxTriesOfAPIToken -= 1
                    }

                    if (maxTriesOfAPIToken < 0) {
                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                        }
                        return@launch
                    }
                    var maxTriesOfAPIDNS = 3
                    while (maxTriesOfAPIDNS >= 0) {
                        if (preferredAPIIp.value != "" && apiToken.value != "") {
                            break
                        }
                        if (apiDNSList.size != 0) {
                            if (preferredAPIIp.value != "" && apiToken.value != "") {
                                break
                            }
                            for (i in apiDNSList) {
                                if (preferredAPIIp.value != "" && apiToken.value != "") {
                                    break
                                }
                                try {
                                    //get first usable ip and test ws
                                    var isRequestSuccessful = false
                                    val customDns = object : Dns {
                                        override fun lookup(hostname: String): List<InetAddress> {
                                            //return InetAddress.getAllByName("192.168.3.17").toList()
                                            return InetAddress.getAllByName(i).toList()
                                        }
                                    }
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(10, TimeUnit.SECONDS)
                                        .readTimeout(10, TimeUnit.SECONDS)
                                        .writeTimeout(10, TimeUnit.SECONDS)
                                        .dns(customDns)
                                        .build()
                                    val getAPIHeaders = mapOf(
                                        "Host" to apiHostName.value,
                                        "User-Agent" to "NextTrace v5.1.4/linux/android NextTracerouteAndroid/" + BuildConfig.VERSION_NAME,
                                        "Authorization" to "Bearer " + apiToken.value,
                                    )
                                    val getAPIURL = "wss://" + apiHostName.value + "/v3/ipGeoWs"
                                    val getPOWRequest = Request.Builder()
                                        .url(getAPIURL)
                                    getAPIHeaders.forEach { (key, value) ->
                                        getPOWRequest.addHeader(key, value)
                                    }
                                    val requestBuilder = getPOWRequest.build()
                                    val webSocketListener = object : WebSocketListener() {
                                        override fun onOpen(
                                            webSocket: WebSocket,
                                            response: Response
                                        ) {
                                            webSocket.send("1.1.1.1")
                                        }

                                        override fun onMessage(webSocket: WebSocket, text: String) {
                                            val jsonData =
                                                JsonParser.parseString(text).asJsonObject.getAsJsonPrimitive(
                                                    "ip"
                                                ).asString
                                            if (jsonData != null) {
                                                preferredAPIIp.value = i
                                                isRequestSuccessful = true
                                            }

                                        }

                                        override fun onClosing(
                                            webSocket: WebSocket,
                                            code: Int,
                                            reason: String
                                        ) {
                                            webSocket.close(1000, "oh, I'm coming")
                                        }
                                    }
                                    val webSocketReq =
                                        client.newWebSocket(requestBuilder, webSocketListener)
                                    for (j in 1..5) {
                                        if (isRequestSuccessful) {
                                            webSocketReq.close(1000, "oh, I'm coming")
                                            maxTriesOfAPIDNS = -1
                                            threadMutex.withLock {
                                                tracerouteThreadsIntList.indices.forEach { index ->
                                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                                        tracerouteThreadsIntList[index] = 0
                                                    }
                                                }
                                                tracerouteThreadsIntList.add(0)
                                            }
                                            return@launch
                                        }
                                        delay(500)
                                    }


                                } catch (e: Exception) {
                                    Log.e("APIDNSHandler", e.printStackTrace().toString())
                                    delay(timeMillis = 200)
                                    continue
                                }
                            }
                        } else {
                            maxTriesOfAPIDNS -= 1
                            delay(timeMillis = 200)
                        }
                        delay(timeMillis = 200)
                    }


                    threadMutex.withLock {
                        tracerouteThreadsIntList.indices.forEach { index ->
                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                tracerouteThreadsIntList[index] = 0
                            }
                        }
                        tracerouteThreadsIntList.add(0)
                    }
                }
            }


        }
    }


    @Composable
    fun APIPOWHandler(//testAPIText: MutableState<String>,
        threadMutex: Mutex, tracerouteThreadsIntList: MutableList<Int>, scope: CoroutineScope,
        tracerouteDNSServer: MutableState<String>,
        apiHostName: MutableState<String>, apiDNSName: MutableState<String>,
        preferredAPIIp: MutableState<String>, apiDNSList: MutableList<String>,
        apiToken: MutableState<String>, currentDOHServer: MutableState<String>,
        currentDNSMode: MutableState<String>
    ) {

        if (preferredAPIIp.value == "") {
            val powMutex = Mutex()
            val powMutexList = mutableListOf(0)
            ResolveHandler(
                threadMutex = powMutex, tracerouteThreadsIntList = powMutexList,
                scope = scope,
                name = apiDNSName.value, tracerouteDNSServer = tracerouteDNSServer,
                multipleIps = apiDNSList, multipleIpStateMode = false,
                currentDNSMode = currentDNSMode,
                currentDOHServer = currentDOHServer
            )

            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.IO) {
                    val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                    threadMutex.withLock {
                        tracerouteThreadsIntList.add(uniqueID)
                    }
                    delay(timeMillis = 100)
                    //it starts after DNS resolution finished
                    var maxDNSRetries = 20
                    var isPowThreadsDone = false
                    while (maxDNSRetries >= 0) {
                        powMutex.withLock {
                            if (powMutexList.all { it == 0 }) {
                                isPowThreadsDone = true
                            }
                        }
                        if (isPowThreadsDone) {
                            break
                        }
                        delay(timeMillis = 500)
                        maxDNSRetries -= 1
                    }
                    if (maxDNSRetries < 0) {
                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                            return@launch
                        }
                    }
                    var maxTriesOfAPIDNS = 3
                    while (maxTriesOfAPIDNS >= 0) {
                        if (preferredAPIIp.value != "" && apiToken.value != "") {
                            break
                        }
                        if (apiDNSList.size != 0) {
                            if (preferredAPIIp.value != "" && apiToken.value != "") {
                                break
                            }
                            for (i in apiDNSList) {
                                if (preferredAPIIp.value != "" && apiToken.value != "") {
                                    break
                                }
                                try {
                                    //get first usable ip and get pow token
                                    val customDns = object : Dns {
                                        override fun lookup(hostname: String): List<InetAddress> {
                                            //return InetAddress.getAllByName("192.168.3.17").toList()
                                            return InetAddress.getAllByName(i).toList()
                                        }
                                    }
                                    val client = OkHttpClient.Builder()
                                        .dns(customDns)
                                        .connectTimeout(5, TimeUnit.SECONDS)
                                        .readTimeout(5, TimeUnit.SECONDS)
                                        .writeTimeout(5, TimeUnit.SECONDS)
                                        .build()
                                    val receivedPOWChallenge = mutableMapOf(
                                        "challenge" to BigInteger.ZERO,
                                        "request_id" to "",
                                        "request_time" to ""
                                    )
                                    val getPOWHeaders = mapOf(
                                        //"Authorization" to "Bearer ",
                                        "Host" to apiHostName.value,
                                        "User-Agent" to "NextTrace v5.1.4/linux/android NextTracerouteAndroid/" + BuildConfig.VERSION_NAME
                                    )
                                    val getPOWURL =
                                        "https://" + apiHostName.value + "/v3/challenge/request_challenge"
                                    //val getPOWURL="http://192.168.3.17:55000/request_challenge"
                                    val getPOWRequest = Request.Builder()
                                        .url(getPOWURL)

                                    getPOWHeaders.forEach { (key, value) ->
                                        getPOWRequest.addHeader(key, value)
                                    }
                                    val requestBuilder = getPOWRequest.build()
                                    val getPOWCall = client.newCall(requestBuilder).execute()
                                    val getPOWData = getPOWCall.body?.string() ?: ""
                                    if (getPOWCall.isSuccessful) {
                                        if (getPOWData != "") {
                                            try {
                                                val getPOWJsonElement =
                                                    JsonParser.parseString(getPOWData).asJsonObject
                                                receivedPOWChallenge["challenge"] = BigInteger(
                                                    getPOWJsonElement.getAsJsonObject("challenge")
                                                        .getAsJsonPrimitive("challenge").asString
                                                )
                                                receivedPOWChallenge["request_id"] =
                                                    getPOWJsonElement.getAsJsonObject("challenge")
                                                        .getAsJsonPrimitive("request_id").asString
                                                receivedPOWChallenge["request_time"] =
                                                    getPOWJsonElement.getAsJsonPrimitive("request_time").asString
                                                //testAPIText.value=powHandler(BigInteger.valueOf(9)).toString()


                                                val powAnswerList =
                                                    powHandler(receivedPOWChallenge["challenge"] as BigInteger)
                                                if (powAnswerList.size != 2) {
                                                    Log.e(
                                                        "APIPowHandler",
                                                        "powAnswerListError$powAnswerList"
                                                    )
                                                    continue
                                                }
                                                val submitPOWAnswerList = mapOf(
                                                    "challenge" to mapOf(
                                                        "request_id" to receivedPOWChallenge["request_id"],
                                                        "challenge" to receivedPOWChallenge["challenge"].toString(),
                                                        //"challenge" to "",
                                                    ),
                                                    "answer" to listOf(
                                                        powAnswerList[0].toString(),
                                                        powAnswerList[1].toString()
                                                    ),
                                                    "request_time" to receivedPOWChallenge["request_time"]
                                                )
                                                val submitPOWAnswerJson =
                                                    Gson().toJson(submitPOWAnswerList)
                                                val submitPOWMediaType =
                                                    "application/json".toMediaType()
                                                val submitPOWBody =
                                                    submitPOWAnswerJson.toRequestBody(
                                                        submitPOWMediaType
                                                    )
                                                val submitPOWHeaders = mapOf(
                                                    "Host" to apiHostName.value,
                                                    "User-Agent" to "NextTrace v5.1.4/linux/android NextTracerouteAndroid/" + BuildConfig.VERSION_NAME,
                                                    "Content-Length" to submitPOWBody.contentLength()
                                                        .toString(),
                                                    "Content-Type" to "application/json"
                                                )
                                                val submitPOWURL =
                                                    "https://" + apiHostName.value + "/v3/challenge/submit_answer"
                                                //val submitPOWURL="http://192.168.3.17:55000/submit_answer"
                                                val submitPOWRequest = Request.Builder()
                                                    .url(submitPOWURL).post(submitPOWBody)
                                                submitPOWHeaders.forEach { (key, value) ->
                                                    submitPOWRequest.addHeader(key, value)
                                                }
                                                val submitBuilder = submitPOWRequest.build()
                                                val submitPOWCall =
                                                    client.newCall(submitBuilder).execute()
                                                val submitPOWData =
                                                    submitPOWCall.body?.string() ?: ""
                                                //testAPIText.value=submitPOWAnswerJson.toString()
                                                if (submitPOWCall.isSuccessful) {
                                                    if (submitPOWData != "") {
                                                        val submitPOWJsonElement =
                                                            JsonParser.parseString(submitPOWData).asJsonObject
                                                        apiToken.value =
                                                            submitPOWJsonElement.getAsJsonPrimitive(
                                                                "token"
                                                            ).asString
                                                        preferredAPIIp.value = i
                                                        maxTriesOfAPIDNS = -1
                                                        threadMutex.withLock {
                                                            tracerouteThreadsIntList.indices.forEach { index ->
                                                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                                                    tracerouteThreadsIntList[index] =
                                                                        0
                                                                }
                                                            }
                                                            tracerouteThreadsIntList.add(0)
                                                        }
                                                        return@launch
                                                    } else {
                                                        delay(500)
                                                        continue
                                                    }
                                                } else {
                                                    delay(500)
                                                    continue
                                                }
                                            } catch (e2: Exception) {
                                                Log.e(
                                                    "APIPowHandler",
                                                    e2.printStackTrace().toString()
                                                )
                                                continue
                                            }


                                        } else {
                                            delay(500)
                                            continue
                                        }

                                    } else {
                                        delay(500)
                                        continue
                                    }


                                } catch (e: Exception) {
                                    Log.e("APIPowHandler", e.printStackTrace().toString())
                                    delay(200)
                                    continue
                                }
                            }
                        } else {
                            maxTriesOfAPIDNS -= 1
                            delay(timeMillis = 500)
                        }
                        delay(timeMillis = 500)
                    }


                    threadMutex.withLock {
                        tracerouteThreadsIntList.indices.forEach { index ->
                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                tracerouteThreadsIntList[index] = 0
                            }
                        }
                        tracerouteThreadsIntList.add(0)
                    }
                }
            }


        }
    }


    @Composable
    fun InsertHandler(
        threadMutex: Mutex, tracerouteThreadsIntList: MutableList<Int>,
        insertion: MutableState<String>,
        insertErrorText: MutableState<String>,
        gridDataList: MutableList<MutableList<MutableList<MutableState<String>>>>,
        scope: CoroutineScope, tracerouteDNSServer: MutableState<String>,
        count: MutableState<String>, maxTTL: MutableIntState, timeout: MutableState<String>,
        multipleIps: MutableList<MutableState<String>>,
        context: Context,
        isDNSInProgress: MutableState<Boolean>, //testAPIText: MutableState<String>,
        currentDOHServer: MutableState<String>,
        currentDNSMode: MutableState<String>, isTraceMapEnabled: MutableState<Boolean>,
        traceMapURL: MutableState<String>, traceMapMutex: Mutex,
        apiHostName: MutableState<String>,
        preferredAPIIp: MutableState<String>,
        traceMapThreadsIntList: MutableList<Int>,
        traceMapThreadsMapList: MutableList<List<MutableMap<String, Any?>>>,
        isSearchBarEnabled: MutableState<Boolean>

    ) {

        val inputType = remember { mutableStateOf("") }

        inputType.value = identifyInput(insertion.value)
        //pingPlaceText.value= testNativePing()
        val lastHopCursor = remember { mutableIntStateOf(114514) }
        lastHopCursor.intValue = 114514
        var nativeStatus = remember { MutableList(maxTTL.intValue) { mutableIntStateOf(0) } }
        if (inputType.value == IPV4IDENTIFIER) {
            nativeStatus = MutableList(maxTTL.intValue) { mutableIntStateOf(0) }
            for ((index, item) in gridDataList.withIndex()) {
                LaunchedEffect(Unit) {
                    scope.launch(Dispatchers.IO) {
                        val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                        threadMutex.withLock {
                            tracerouteThreadsIntList.add(uniqueID)
                        }

                        val traceroute4Result = nativePingHandler(
                            ip = insertion.value, ttl = (index + 1).toString(),
                            count = count.value, timeout = timeout.value
                        )
                        val traceroute4RegexResult = nativeGetHopIPv4(traceroute4Result)
                        if (traceroute4RegexResult == insertion.value) {
                            if (index < lastHopCursor.intValue) {
                                lastHopCursor.intValue = index
                            }
                        } else {
                            item[0][0].value = (index + 1).toString()
                            item[0][1].value = traceroute4RegexResult
                        }
                        nativeStatus[index].intValue = 1
                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                        }

                    }
                }
                //break
            }


        } else if (inputType.value == IPV6IDENTIFIER) {
            nativeStatus = MutableList(maxTTL.intValue) { mutableIntStateOf(0) }
            for ((index, item) in gridDataList.withIndex()) {
                LaunchedEffect(Unit) {
                    scope.launch(Dispatchers.IO) {
                        val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                        threadMutex.withLock {
                            tracerouteThreadsIntList.add(uniqueID)
                        }
                        val traceroute6Result = nativePingHandler(
                            ip = insertion.value, ttl = (index + 1).toString(),
                            count = count.value, timeout = timeout.value
                        )
                        val traceroute6RegexResult = nativeGetHopIPv6(traceroute6Result)
                        if (traceroute6RegexResult == insertion.value) {
                            if (index < lastHopCursor.intValue) {
                                lastHopCursor.intValue = index
                            }
                        } else {
                            item[0][0].value = (index + 1).toString()
                            item[0][1].value = traceroute6RegexResult
                        }
                        nativeStatus[index].intValue = 1
                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                        }

                    }
                }
                //break
            }


        } else if (inputType.value == HOSTNAMEIDENTIFIER) {
            isDNSInProgress.value = true
            val dnsThreadsList = mutableListOf(0)
            ResolveHandler(
                threadMutex = threadMutex, tracerouteThreadsIntList = dnsThreadsList,
                scope = scope,
                name = insertion.value, tracerouteDNSServer = tracerouteDNSServer,
                multipleIpsState = multipleIps, multipleIpStateMode = true,
                currentDNSMode = currentDNSMode,
                currentDOHServer = currentDOHServer, //testAPIText = testAPIText
            )
            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.IO) {
                    val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                    threadMutex.withLock {
                        tracerouteThreadsIntList.add(uniqueID)
                    }
                    var maxTriesDNS = 20
                    while (maxTriesDNS >= 0) {
                        if (dnsThreadsList.all { it == 0 }) {
                            isDNSInProgress.value = false
                            break
                        }
                        delay(500)
                        maxTriesDNS -= 1
                    }
                    if (multipleIps.size == 0 || isDNSInProgress.value) {
                        isDNSInProgress.value = false
                        insertErrorText.value =
                            "No DNS response yet! Check hostname and DNS setting!"
                        isSearchBarEnabled.value = true
                    }
                    threadMutex.withLock {
                        tracerouteThreadsIntList.indices.forEach { index ->
                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                tracerouteThreadsIntList[index] = 0
                            }
                        }
                        tracerouteThreadsIntList.add(0)
                    }
                }
            }
        } else {
            Toast.makeText(context, "Invalid input! Wait 2 Seconds", Toast.LENGTH_LONG).show()
        }

        if (inputType.value == IPV4IDENTIFIER || inputType.value == IPV6IDENTIFIER) {

            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.IO) {
                    val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                    threadMutex.withLock {
                        tracerouteThreadsIntList.add(uniqueID)
                    }
                    var nativeV4HaveZero = true
                    while (nativeV4HaveZero) {
                        delay(timeMillis = 500)
                        nativeV4HaveZero = false
                        for (i in nativeStatus) {
                            if (i.intValue == 0) {
                                nativeV4HaveZero = true
                            }
                        }
                        delay(timeMillis = 500)
                    }
                    if (lastHopCursor.intValue != 114514) {
                        gridDataList[lastHopCursor.intValue][0][1].value = insertion.value
                        gridDataList[lastHopCursor.intValue][0][0].value =
                            (lastHopCursor.intValue + 1).toString()
                    }
                    threadMutex.withLock {
                        tracerouteThreadsIntList.indices.forEach { index ->
                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                tracerouteThreadsIntList[index] = 0
                            }
                        }
                        tracerouteThreadsIntList.add(0)
                    }

                }
            }
            //tracemap handler
            if (isTraceMapEnabled.value) {
                LaunchedEffect(Unit) {
                    scope.launch(Dispatchers.IO) {
                        val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                        threadMutex.withLock { tracerouteThreadsIntList.add(uniqueID) }

                        delay(timeMillis = 5000)
                        var apiMaxTries = 20
                        while (apiMaxTries >= 0) {
                            var isAPIFinished = false
                            traceMapMutex.withLock {
                                if (traceMapThreadsIntList.all { it == 0 } && preferredAPIIp.value != "") {
                                    isAPIFinished = true
                                }
                            }
                            if (isAPIFinished) {
                                break
                            } else {
                                apiMaxTries -= 1
                            }
                            delay(timeMillis = 500)
                        }
                        if (apiMaxTries < 0) {
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }
                        var maxTries = 20
                        while (traceMapThreadsMapList.size == 0 && maxTries > 0) {
                            delay(timeMillis = 500)
                            maxTries -= 1
                        }
                        if (traceMapThreadsMapList.size == 0) {
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }
                        traceMapThreadsMapList.sortBy { it[0]["TTL"] as? Int ?: 0 }
                        try {
                            val customDns = object : Dns {
                                override fun lookup(hostname: String): List<InetAddress> {
                                    return InetAddress.getAllByName(preferredAPIIp.value).toList()
                                }
                            }
                            val client = OkHttpClient.Builder()
                                .connectTimeout(5, TimeUnit.SECONDS)
                                .readTimeout(5, TimeUnit.SECONDS)
                                .writeTimeout(5, TimeUnit.SECONDS)
                                .dns(customDns)
                                .build()
                            val submitTraceMapList = mapOf(
                                "Hops" to traceMapThreadsMapList,
                                "TraceMapUrl" to ""
                            )
                            val submitTraceMapJson =
                                GsonBuilder().serializeNulls().create().toJson(submitTraceMapList)
                            val submitTraceMapType = "application/json".toMediaType()
                            val submitTraceMapBody =
                                submitTraceMapJson.toRequestBody(submitTraceMapType)
                            val submitTraceMapHeaders = mapOf(
                                "Host" to apiHostName.value,
                                "User-Agent" to "NextTrace v5.1.4/linux/android NextTracerouteAndroid/" + BuildConfig.VERSION_NAME,
                                "Content-Length" to submitTraceMapBody.contentLength().toString(),
                                "Content-Type" to "application/json"
                            )
                            val submitTraceMapURL = "https://" + apiHostName.value + "/tracemap/api"
                            val submitTraceMapRequest = Request.Builder()
                                .url(submitTraceMapURL).post(submitTraceMapBody)
                            submitTraceMapHeaders.forEach { (key, value) ->
                                submitTraceMapRequest.addHeader(key, value)
                            }
                            val submitBuilder = submitTraceMapRequest.build()
                            val submitTraceMapCall = client.newCall(submitBuilder).execute()
                            val receiveTraceMapData = submitTraceMapCall.body?.string() ?: ""
                            //testAPIText.value= submitTraceMapCall.code.toString()
                            if (submitTraceMapCall.isSuccessful) {
                                if (receiveTraceMapData != "") {
                                    traceMapURL.value = receiveTraceMapData
                                    //testAPIText.value=traceMapURL.value
                                }
                            }
                        } catch (e2: Exception) {
                            Log.e("APIPowHandler", e2.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }


                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                        }

                    }
                }

            }


        }

    }

    @Composable
    fun ResolveHandler(
        threadMutex: Mutex, tracerouteThreadsIntList: MutableList<Int>,
        scope: CoroutineScope,
        name: String, tracerouteDNSServer: MutableState<String>,
        multipleIpsState: MutableList<MutableState<String>> = mutableListOf(),
        multipleIps: MutableList<String> = mutableListOf(),
        multipleIpStateMode: Boolean, currentDOHServer: MutableState<String>,
        currentDNSMode: MutableState<String>, //testAPIText: MutableState<String> = mutableStateOf("")
    ) {
        //in case input ip as the server name
        val nameType = identifyInput(name)
        if (nameType == IPV4IDENTIFIER || nameType == IPV6IDENTIFIER) {
            if (multipleIpStateMode) {
                if (multipleIpsState.none { it.value == name }) {
                    multipleIpsState.add(remember { mutableStateOf(name) })
                }
            } else {
                if (multipleIps.none { it == name }) {
                    multipleIps.add(name)
                }
            }
            return
        }

        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                threadMutex.withLock {
                    tracerouteThreadsIntList.add(uniqueID)
                }
                if (currentDNSMode.value == "doh") {
                    var isADOHRequestSuccessful = false
                    var maxADOHTries = 3
                    while (maxADOHTries >= 0) {
                        try {
                            val dohNameA = org.xbill.DNS.Name.fromString("$name.")
                            val dohRecordA =
                                org.xbill.DNS.Record.newRecord(dohNameA, Type.A, DClass.IN)
                            val dohMessageA = org.xbill.DNS.Message.newQuery(dohRecordA)
                            val dohBase64DataA = Base64.encodeToString(
                                dohMessageA.toWire(),
                                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                            )
                            val dohClientA = OkHttpClient.Builder().build()
                            val submitDOHRequestA = Request.Builder()
                                .url(currentDOHServer.value + "?dns=" + dohBase64DataA)
                                .addHeader("Accept", "application/dns-message")
                                .get()
                                .build()
                            val dohRequestA = dohClientA.newCall(submitDOHRequestA).execute()
                            //testAPIText.value=dohResponseA.code.toString()
                            if (dohRequestA.isSuccessful) {
                                val dohResponseBodyA = dohRequestA.body?.bytes()
                                if (dohResponseBodyA != null) {
                                    val dohResponseA = org.xbill.DNS.Message(dohResponseBodyA)
                                        .getSection(org.xbill.DNS.Section.ANSWER)
                                    for (r in dohResponseA) {
                                        if (r is ARecord) {
                                            val aRecordOne = r.address.hostAddress
                                            if (aRecordOne != null) {
                                                isADOHRequestSuccessful = true
                                                if (multipleIpStateMode) {
                                                    if (multipleIpsState.none { it.value == aRecordOne }) {
                                                        multipleIpsState.add(
                                                            mutableStateOf(
                                                                aRecordOne
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    if (multipleIps.none { it == aRecordOne }) {
                                                        multipleIps.add(aRecordOne)
                                                    }
                                                }

                                            }
                                        }
                                    }
                                    dohRequestA.close()
                                }

                            }


                        } catch (e2: Exception) {
                            Log.e("ResolveHandler", e2.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch

                        }
                        if (isADOHRequestSuccessful) {
                            break
                        }
                        delay(timeMillis = 500)
                        maxADOHTries -= 1

                    }
                } else {
                    var maxTriesA = 3
                    var isARequestSuccessful = false
                    while (maxTriesA >= 0) {
                        try {
                            val lookupARecords = Lookup(name, Type.A)
                            val lookupARecordsSimpleResolver =
                                SimpleResolver(tracerouteDNSServer.value)
                            if (currentDNSMode.value == "tcp") {
                                lookupARecordsSimpleResolver.tcp = true
                            } else {
                                lookupARecordsSimpleResolver.tcp = false
                            }
                            lookupARecords.setResolver(lookupARecordsSimpleResolver)


                            val aRecords = lookupARecords.run()

                            if (aRecords != null && aRecords.isNotEmpty()) {
                                for (r in aRecords) {
                                    if (r is ARecord) {
                                        val aRecordOne = r.address.hostAddress
                                        if (aRecordOne != null) {
                                            isARequestSuccessful = true
                                            if (multipleIpStateMode) {
                                                if (multipleIpsState.none { it.value == aRecordOne }) {
                                                    multipleIpsState.add(mutableStateOf(aRecordOne))
                                                }
                                            } else {
                                                if (multipleIps.none { it == aRecordOne }) {
                                                    multipleIps.add(aRecordOne)
                                                }
                                            }

                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ResolveHandler", e.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }
                        if (isARequestSuccessful) {
                            break
                        }
                        maxTriesA -= 1
                        delay(timeMillis = 200)
                    }
                }

                threadMutex.withLock {
                    tracerouteThreadsIntList.indices.forEach { index ->
                        if (tracerouteThreadsIntList[index] == uniqueID) {
                            tracerouteThreadsIntList[index] = 0
                        }
                    }
                    tracerouteThreadsIntList.add(0)
                }
            }
        }

        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                threadMutex.withLock {
                    tracerouteThreadsIntList.add(uniqueID)
                }
                if (currentDNSMode.value == "doh") {
                    var isDOHRequestAAAASuccessful = false
                    var maxAAAADOHTries = 3
                    while (maxAAAADOHTries >= 0) {
                        try {
                            val dohNameAAAA = org.xbill.DNS.Name.fromString("$name.")
                            val dohRecordAAAA =
                                org.xbill.DNS.Record.newRecord(dohNameAAAA, Type.AAAA, DClass.IN)
                            val dohMessageAAAA = org.xbill.DNS.Message.newQuery(dohRecordAAAA)
                            val dohBase64DataAAAA = Base64.encodeToString(
                                dohMessageAAAA.toWire(),
                                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                            )
                            val dohClientAAAA = OkHttpClient.Builder().build()
                            val submitDOHRequestAAAA = Request.Builder()
                                .url(currentDOHServer.value + "?dns=" + dohBase64DataAAAA)
                                .addHeader("Accept", "application/dns-message")
                                .get()
                                .build()
                            val dohRequestAAAA =
                                dohClientAAAA.newCall(submitDOHRequestAAAA).execute()
                            //testAPIText.value=dohResponseAAAA.code.toString()
                            if (dohRequestAAAA.isSuccessful) {
                                val dohResponseBodyAAAA = dohRequestAAAA.body?.bytes()
                                if (dohResponseBodyAAAA != null) {
                                    val dohResponseAAAA = org.xbill.DNS.Message(dohResponseBodyAAAA)
                                        .getSection(org.xbill.DNS.Section.ANSWER)
                                    for (r in dohResponseAAAA) {
                                        if (r is AAAARecord) {
                                            val aaaaRecordOne = r.address.hostAddress
                                            if (aaaaRecordOne != null) {
                                                isDOHRequestAAAASuccessful = true
                                                if (multipleIpStateMode) {
                                                    if (multipleIpsState.none { it.value == aaaaRecordOne }) {
                                                        multipleIpsState.add(
                                                            mutableStateOf(
                                                                aaaaRecordOne
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    if (multipleIps.none { it == aaaaRecordOne }) {
                                                        multipleIps.add(aaaaRecordOne)
                                                    }
                                                }

                                            }
                                        }
                                    }
                                    dohRequestAAAA.close()
                                }

                            }


                        } catch (e2: Exception) {
                            Log.e("ResolveHandler", e2.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch

                        }
                        if (isDOHRequestAAAASuccessful) {
                            break
                        }
                        delay(timeMillis = 500)
                        maxAAAADOHTries -= 1

                    }
                } else {
                    var maxRetriesAAAA = 3
                    var isAAAARequestSuccessful = false
                    while (maxRetriesAAAA >= 0) {
                        try {
                            val lookupAAAARecords = Lookup(name, Type.AAAA)

                            val lookupAAAARecordsSimpleResolver =
                                SimpleResolver(tracerouteDNSServer.value)
                            if (currentDNSMode.value == "tcp") {
                                lookupAAAARecordsSimpleResolver.tcp = true
                            } else {
                                lookupAAAARecordsSimpleResolver.tcp = false
                            }
                            lookupAAAARecords.setResolver(lookupAAAARecordsSimpleResolver)

                            val aAAARecords = lookupAAAARecords.run()
                            if (aAAARecords != null && aAAARecords.isNotEmpty()) {
                                for (r in aAAARecords) {
                                    if (r is AAAARecord) {
                                        val aaaaRecordOne = r.address.hostAddress
                                        if (aaaaRecordOne != null) {
                                            isAAAARequestSuccessful = true
                                            if (multipleIpStateMode) {
                                                if (multipleIpsState.none { it.value == aaaaRecordOne }) {
                                                    multipleIpsState.add(
                                                        mutableStateOf(
                                                            aaaaRecordOne
                                                        )
                                                    )
                                                }
                                            } else {
                                                if (multipleIps.none { it == aaaaRecordOne }) {
                                                    multipleIps.add(aaaaRecordOne)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("eachHopHandler", e.printStackTrace().toString())

                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                                return@launch
                            }

                        }
                        if (isAAAARequestSuccessful) {
                            break
                        }
                        maxRetriesAAAA -= 1
                        delay(timeMillis = 200)
                    }
                }


                threadMutex.withLock {
                    tracerouteThreadsIntList.indices.forEach { index ->
                        if (tracerouteThreadsIntList[index] == uniqueID) {
                            tracerouteThreadsIntList[index] = 0
                        }
                    }
                    tracerouteThreadsIntList.add(0)
                }
            }
        }
        LaunchedEffect(Unit) {
            scope.launch(Dispatchers.IO) {
                val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                threadMutex.withLock {
                    tracerouteThreadsIntList.add(uniqueID)
                }
                if (currentDNSMode.value == "doh") {
                    var isCNAMEDOHRequestSuccessful = false
                    var maxCNAMEDOHTries = 3
                    while (maxCNAMEDOHTries >= 0) {
                        try {
                            val dohNameCNAME = org.xbill.DNS.Name.fromString("$name.")
                            val dohRecordCNAME =
                                org.xbill.DNS.Record.newRecord(dohNameCNAME, Type.CNAME, DClass.IN)
                            val dohMessageCNAME = org.xbill.DNS.Message.newQuery(dohRecordCNAME)
                            val dohBase64DataCNAME = Base64.encodeToString(
                                dohMessageCNAME.toWire(),
                                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                            )
                            val dohClientCNAME = OkHttpClient.Builder().build()
                            val submitDOHRequestCNAME = Request.Builder()
                                .url(currentDOHServer.value + "?dns=" + dohBase64DataCNAME)
                                .addHeader("Accept", "application/dns-message")
                                .get()
                                .build()
                            val dohRequestCNAME =
                                dohClientCNAME.newCall(submitDOHRequestCNAME).execute()
                            //testAPIText.value=dohResponseCNAME.code.toString()
                            if (dohRequestCNAME.isSuccessful) {
                                val dohResponseBodyCNAME = dohRequestCNAME.body?.bytes()
                                if (dohResponseBodyCNAME != null) {
                                    val dohResponseCNAME =
                                        org.xbill.DNS.Message(dohResponseBodyCNAME)
                                            .getSection(org.xbill.DNS.Section.ANSWER)
                                    for (r in dohResponseCNAME) {
                                        if (r is CNAMERecord) {
                                            val cnameRecordOne = r.target
                                            if (cnameRecordOne != null) {
                                                try {
                                                    val dohNameCNAMEA =
                                                        org.xbill.DNS.Name.fromString("$cnameRecordOne")
                                                    val dohRecordCNAMEA =
                                                        org.xbill.DNS.Record.newRecord(
                                                            dohNameCNAMEA,
                                                            Type.A,
                                                            DClass.IN
                                                        )
                                                    val dohMessageCNAMEA =
                                                        org.xbill.DNS.Message.newQuery(
                                                            dohRecordCNAMEA
                                                        )
                                                    val dohBase64DataCNAMEA = Base64.encodeToString(
                                                        dohMessageCNAMEA.toWire(),
                                                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                                    )
                                                    val dohClientCNAMEA =
                                                        OkHttpClient.Builder().build()
                                                    val submitDOHRequestCNAMEA = Request.Builder()
                                                        .url(currentDOHServer.value + "?dns=" + dohBase64DataCNAMEA)
                                                        .addHeader(
                                                            "Accept",
                                                            "application/dns-message"
                                                        )
                                                        .get()
                                                        .build()
                                                    val dohRequestCNAMEA = dohClientCNAMEA.newCall(
                                                        submitDOHRequestCNAMEA
                                                    ).execute()
                                                    if (dohRequestCNAMEA.isSuccessful) {
                                                        val dohResponseBodyCNAMEA =
                                                            dohRequestCNAMEA.body?.bytes()
                                                        if (dohResponseBodyCNAMEA != null) {
                                                            val dohResponseCNAMEA =
                                                                org.xbill.DNS.Message(
                                                                    dohResponseBodyCNAMEA
                                                                )
                                                                    .getSection(org.xbill.DNS.Section.ANSWER)
                                                            for (s in dohResponseCNAMEA) {
                                                                if (s is ARecord) {
                                                                    val dohCNAMEAOne =
                                                                        s.address.hostAddress
                                                                    if (dohCNAMEAOne != null) {
                                                                        isCNAMEDOHRequestSuccessful =
                                                                            true
                                                                        if (multipleIpStateMode) {
                                                                            if (multipleIpsState.none { it.value == dohCNAMEAOne }) {
                                                                                multipleIpsState.add(
                                                                                    mutableStateOf(
                                                                                        dohCNAMEAOne
                                                                                    )
                                                                                )
                                                                            }
                                                                        } else {
                                                                            if (multipleIps.none { it == dohCNAMEAOne }) {
                                                                                multipleIps.add(
                                                                                    dohCNAMEAOne
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                }
                                                            }
                                                            dohRequestCNAMEA.close()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "ResolveHandlerDOHCNAMEA",
                                                        e.printStackTrace().toString()
                                                    )
                                                }
                                                try {
                                                    val dohNameCNAMEAAAA =
                                                        org.xbill.DNS.Name.fromString("$cnameRecordOne")
                                                    val dohRecordCNAMEAAAA =
                                                        org.xbill.DNS.Record.newRecord(
                                                            dohNameCNAMEAAAA,
                                                            Type.AAAA,
                                                            DClass.IN
                                                        )
                                                    val dohMessageCNAMEAAAA =
                                                        org.xbill.DNS.Message.newQuery(
                                                            dohRecordCNAMEAAAA
                                                        )
                                                    val dohBase64DataCNAMEAAAA =
                                                        Base64.encodeToString(
                                                            dohMessageCNAMEAAAA.toWire(),
                                                            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                                        )
                                                    val dohClientCNAMEAAAA =
                                                        OkHttpClient.Builder().build()
                                                    val submitDOHRequestCNAMEAAAA =
                                                        Request.Builder()
                                                            .url(currentDOHServer.value + "?dns=" + dohBase64DataCNAMEAAAA)
                                                            .addHeader(
                                                                "Accept",
                                                                "application/dns-message"
                                                            )
                                                            .get()
                                                            .build()
                                                    val dohRequestCNAMEAAAA =
                                                        dohClientCNAMEAAAA.newCall(
                                                            submitDOHRequestCNAMEAAAA
                                                        ).execute()
                                                    if (dohRequestCNAMEAAAA.isSuccessful) {
                                                        val dohResponseBodyCNAMEAAAA =
                                                            dohRequestCNAMEAAAA.body?.bytes()
                                                        if (dohResponseBodyCNAMEAAAA != null) {
                                                            val dohResponseCNAMEAAAA =
                                                                org.xbill.DNS.Message(
                                                                    dohResponseBodyCNAMEAAAA
                                                                )
                                                                    .getSection(org.xbill.DNS.Section.ANSWER)
                                                            for (t in dohResponseCNAMEAAAA) {
                                                                if (t is AAAARecord) {
                                                                    val dohCNAMEAAAAOne =
                                                                        t.address.hostAddress
                                                                    if (dohCNAMEAAAAOne != null) {
                                                                        isCNAMEDOHRequestSuccessful =
                                                                            true
                                                                        if (multipleIpStateMode) {
                                                                            if (multipleIpsState.none { it.value == dohCNAMEAAAAOne }) {
                                                                                multipleIpsState.add(
                                                                                    mutableStateOf(
                                                                                        dohCNAMEAAAAOne
                                                                                    )
                                                                                )
                                                                            }
                                                                        } else {
                                                                            if (multipleIps.none { it == dohCNAMEAAAAOne }) {
                                                                                multipleIps.add(
                                                                                    dohCNAMEAAAAOne
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                }
                                                            }
                                                            dohRequestCNAMEAAAA.close()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "RHandlerDOHCNAMEAAAA",
                                                        e.printStackTrace().toString()
                                                    )
                                                }


                                            }
                                        }
                                    }
                                    dohRequestCNAME.close()
                                }

                            }


                        } catch (e: Exception) {
                            Log.e("ResolveHandler", e.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch

                        }
                        if (isCNAMEDOHRequestSuccessful) {
                            break
                        }
                        delay(timeMillis = 500)
                        maxCNAMEDOHTries -= 1

                    }

                } else {
                    var maxCNAMERetries = 3
                    var isCNAMERequestSuccessful = false
                    while (maxCNAMERetries >= 0) {
                        try {
                            val lookupCNAMERecords = Lookup(name, Type.CNAME)
                            val lookupCNAMERecordsSimpleResolver =
                                SimpleResolver(tracerouteDNSServer.value)
                            if (currentDNSMode.value == "tcp") {
                                lookupCNAMERecordsSimpleResolver.tcp = true
                            } else {
                                lookupCNAMERecordsSimpleResolver.tcp = false
                            }
                            lookupCNAMERecords.setResolver(lookupCNAMERecordsSimpleResolver)

                            val cNAMERecords = lookupCNAMERecords.run()
                            if (cNAMERecords != null && cNAMERecords.isNotEmpty()) {
                                for (r in cNAMERecords) {
                                    if (r is CNAMERecord) {
                                        val cNAMERecordOne = r.target
                                        if (cNAMERecordOne != null) {
                                            try {
                                                val lookupARecordsCNAME =
                                                    Lookup(cNAMERecordOne, Type.A)

                                                val lookupARecordsCNAMESimpleResolver =
                                                    SimpleResolver(tracerouteDNSServer.value)
                                                if (currentDNSMode.value == "tcp") {
                                                    lookupARecordsCNAMESimpleResolver.tcp = true
                                                } else {
                                                    lookupARecordsCNAMESimpleResolver.tcp = false
                                                }
                                                lookupARecordsCNAME.setResolver(
                                                    lookupARecordsCNAMESimpleResolver
                                                )

                                                val aRecordsCNAME = lookupARecordsCNAME.run()
                                                if (aRecordsCNAME != null && aRecordsCNAME.isNotEmpty()) {
                                                    for (s in aRecordsCNAME) {
                                                        if (s is ARecord) {
                                                            val aRecordOneCNAME =
                                                                s.address.hostAddress
                                                            if (aRecordOneCNAME != null) {
                                                                isCNAMERequestSuccessful = true
                                                                if (multipleIpStateMode) {
                                                                    if (multipleIpsState.none { it.value == aRecordOneCNAME }) {
                                                                        multipleIpsState.add(
                                                                            mutableStateOf(
                                                                                aRecordOneCNAME
                                                                            )
                                                                        )
                                                                    }
                                                                } else {
                                                                    if (multipleIps.none { it == aRecordOneCNAME }) {
                                                                        multipleIps.add(
                                                                            aRecordOneCNAME
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "RHandlerCNAMEA",
                                                    e.printStackTrace().toString()
                                                )
                                            }
                                            try {
                                                val lookupAAAARecordsCNAME =
                                                    Lookup(cNAMERecordOne, Type.AAAA)

                                                val lookupAAAARecordsCNAMESimpleResolver =
                                                    SimpleResolver(tracerouteDNSServer.value)
                                                if (currentDNSMode.value == "tcp") {
                                                    lookupAAAARecordsCNAMESimpleResolver.tcp = true
                                                } else {
                                                    lookupAAAARecordsCNAMESimpleResolver.tcp = false
                                                }
                                                lookupAAAARecordsCNAME.setResolver(
                                                    lookupAAAARecordsCNAMESimpleResolver
                                                )

                                                val aAAARecordsCNAME = lookupAAAARecordsCNAME.run()
                                                if (aAAARecordsCNAME != null && aAAARecordsCNAME.isNotEmpty()) {
                                                    for (t in aAAARecordsCNAME) {
                                                        if (t is AAAARecord) {
                                                            val aaaaRecordOneCNAME =
                                                                t.address.hostAddress
                                                            if (aaaaRecordOneCNAME != null) {
                                                                isCNAMERequestSuccessful = true
                                                                if (multipleIpStateMode) {
                                                                    if (multipleIpsState.none { it.value == aaaaRecordOneCNAME }) {
                                                                        multipleIpsState.add(
                                                                            mutableStateOf(
                                                                                aaaaRecordOneCNAME
                                                                            )
                                                                        )
                                                                    }
                                                                } else {
                                                                    if (multipleIps.none { it == aaaaRecordOneCNAME }) {
                                                                        multipleIps.add(
                                                                            aaaaRecordOneCNAME
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                            } catch (e: Exception) {
                                                Log.e(
                                                    "RHandlerCNAMEAAAA",
                                                    e.printStackTrace().toString()
                                                )
                                            }

                                        }
                                    }
                                }
                            }


                        } catch (e: Exception) {
                            Log.e("eachHopHandler", e.printStackTrace().toString())
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }
                        if (isCNAMERequestSuccessful) {
                            break
                        }
                        maxCNAMERetries -= 1
                        delay(timeMillis = 200)


                    }
                }


                threadMutex.withLock {
                    tracerouteThreadsIntList.indices.forEach { index ->
                        if (tracerouteThreadsIntList[index] == uniqueID) {
                            tracerouteThreadsIntList[index] = 0
                        }
                    }
                    tracerouteThreadsIntList.add(0)
                }
            }
        }

    }


    @Composable
    fun EachHopHandler(
        threadMutex: Mutex, tracerouteThreadsIntList: MutableList<Int>,
        singleHopCursor: MutableList<MutableState<String>>,
        gridDataList: MutableList<MutableList<MutableList<MutableState<String>>>>,
        scope: CoroutineScope, tracerouteDNSServer: MutableState<String>,
        count: MutableState<String>, timeout: MutableState<String>,
        //testAPIText: MutableState<String>,
        apiHostName: MutableState<String>,
        preferredAPIIp: MutableState<String>,
        apiToken: MutableState<String>, currentLanguage: MutableState<String>,
        traceMapThreadsIntList: MutableList<Int>, traceMapMutex: Mutex,
        traceMapThreadsMapList: MutableList<List<MutableMap<String, Any?>>>,
        currentDOHServer: MutableState<String>,
        currentDNSMode: MutableState<String>

    ) {
        for ((index, item) in gridDataList.withIndex()) {
            if ((identifyInput(item[0][1].value) == "IPv4" || identifyInput(item[0][1].value) == "IPv6") && item[0][1].value != singleHopCursor[index].value) {
                //prevent duplicate recompose
                singleHopCursor[index].value = item[0][1].value
                if (identifyInput(item[0][1].value) == "IPv4") {
                    LaunchedEffect(Unit) {
                        scope.launch(Dispatchers.IO) {
                            val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                            threadMutex.withLock {
                                tracerouteThreadsIntList.add(uniqueID)
                            }
                            //ping each hop
                            //Can't set ttl here: ping can't set unicast time-to-live invalid argument
                            val ping4Result = nativePingHandler(
                                ip = item[0][1].value, ttl = "",
                                count = count.value, timeout = timeout.value
                            )
                            val ping4RegexResult = extractRttValues(ping4Result)
                            item[2][1].value = ping4RegexResult
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }

                        }
                    }
                    //RDNS
                    LaunchedEffect(Unit) {
                        scope.launch(Dispatchers.IO) {
                            val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                            threadMutex.withLock {
                                tracerouteThreadsIntList.add(uniqueID)
                            }
                            var noResult = true
                            if (currentDNSMode.value == "doh") {
                                var isPTR4DOHRequestSuccessful = false
                                var maxPTR4DOHTries = 3
                                while (maxPTR4DOHTries >= 0) {
                                    try {
                                        val rDNSOriginalIp4DoH =
                                            InetAddress.getByName(item[0][1].value)
                                        val rDNSOriginalIp4Bytes = rDNSOriginalIp4DoH.address
                                        val query4 = StringBuilder()
                                        for (b in rDNSOriginalIp4Bytes.reversed()) {
                                            query4.append((b.toInt() and 0xFF).toString())
                                            query4.append(".")
                                        }
                                        query4.append("in-addr.arpa.")
                                        val dohNamePTR4 =
                                            org.xbill.DNS.Name.fromString(query4.toString())
                                        val dohRecordPTR4 = org.xbill.DNS.Record.newRecord(
                                            dohNamePTR4,
                                            Type.PTR,
                                            DClass.IN
                                        )
                                        val dohMessagePTR4 =
                                            org.xbill.DNS.Message.newQuery(dohRecordPTR4)
                                        val dohBase64DataPTR4 = Base64.encodeToString(
                                            dohMessagePTR4.toWire(),
                                            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                        )
                                        val dohClientPTR4 = OkHttpClient.Builder().build()
                                        val submitDOHRequestPTR4 = Request.Builder()
                                            .url(currentDOHServer.value + "?dns=" + dohBase64DataPTR4)
                                            .addHeader("Accept", "application/dns-message")
                                            .get()
                                            .build()
                                        val dohRequestPTR4 =
                                            dohClientPTR4.newCall(submitDOHRequestPTR4).execute()
                                        if (dohRequestPTR4.isSuccessful) {
                                            val dohResponseBodyPTR4 = dohRequestPTR4.body?.bytes()
                                            if (dohResponseBodyPTR4 != null) {
                                                val dohResponsePTR4 =
                                                    org.xbill.DNS.Message(dohResponseBodyPTR4)
                                                        .getSection(org.xbill.DNS.Section.ANSWER)
                                                for (r in dohResponsePTR4) {
                                                    if (r is PTRRecord) {
                                                        isPTR4DOHRequestSuccessful = true
                                                        item[2][0].value = r.target.toString()
                                                        noResult = false
                                                    }
                                                }
                                                dohRequestPTR4.close()
                                            }

                                        }


                                    } catch (e2: Exception) {
                                        Log.e("eachHopHandler", e2.printStackTrace().toString())
                                        threadMutex.withLock {
                                            tracerouteThreadsIntList.indices.forEach { index ->
                                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                                    tracerouteThreadsIntList[index] = 0
                                                }
                                            }
                                            tracerouteThreadsIntList.add(0)
                                        }
                                        return@launch

                                    }
                                    if (isPTR4DOHRequestSuccessful) {
                                        break
                                    }
                                    delay(timeMillis = 500)
                                    maxPTR4DOHTries -= 1

                                }
                            } else {
                                try {
                                    val rDNSOriginalIp = InetAddress.getByName(item[0][1].value)
                                    val rDNSOriginalIpBytes = rDNSOriginalIp.address
                                    val query = StringBuilder()
                                    for (b in rDNSOriginalIpBytes.reversed()) {
                                        query.append((b.toInt() and 0xFF).toString())
                                        query.append(".")
                                    }
                                    query.append("in-addr.arpa.")

                                    val lookup = Lookup(query.toString(), Type.PTR)
                                    val lookupSimpleResolver =
                                        SimpleResolver(tracerouteDNSServer.value)
                                    if (currentDNSMode.value == "tcp") {
                                        lookupSimpleResolver.tcp = true
                                    } else {
                                        lookupSimpleResolver.tcp = false
                                    }
                                    lookup.setResolver(lookupSimpleResolver)

                                    val records = lookup.run()
                                    //item[2][0].value=query.toString()
                                    if (records != null && records.isNotEmpty()) {
                                        for (r in records) {
                                            if (r is PTRRecord) {
                                                item[2][0].value = r.target.toString()
                                                noResult = false

                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("eachHopHandler", e.printStackTrace().toString())
                                    item[2][0].value = "*"
                                    threadMutex.withLock {
                                        tracerouteThreadsIntList.indices.forEach { index ->
                                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                                tracerouteThreadsIntList[index] = 0
                                            }
                                        }
                                        tracerouteThreadsIntList.add(0)
                                    }
                                    return@launch
                                }
                            }
                            if (noResult) {
                                item[2][0].value = "*"
                            }
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }


                        }
                    }
//                    //prevent duplicate recompose
//                    singleHopCursor[index].value=item[0][1].value
                }
                if (identifyInput(item[0][1].value) == "IPv6") {
                    LaunchedEffect(Unit) {
                        scope.launch(Dispatchers.IO) {
                            val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                            threadMutex.withLock {
                                tracerouteThreadsIntList.add(uniqueID)
                            }
                            //ping each hop
                            //Can't set ttl here: ping can't set unicast time-to-live invalid argument
                            val ping6Result = nativePingHandler(
                                ip = item[0][1].value, ttl = "",
                                count = count.value, timeout = timeout.value
                            )
                            val ping6RegexResult = extractRttValues(ping6Result)
                            item[2][1].value = ping6RegexResult
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }


                        }
                    }
                    //RDNS6
                    LaunchedEffect(Unit) {
                        scope.launch(Dispatchers.IO) {
                            val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                            threadMutex.withLock {
                                tracerouteThreadsIntList.add(uniqueID)
                            }

                            var noResult6 = true
                            if (currentDNSMode.value == "doh") {
                                var isPTR6DOHRequestSuccessful = false
                                var maxPTR6DOHTries = 3
                                while (maxPTR6DOHTries >= 0) {
                                    try {
                                        val ip6HexPartsDOH =
                                            IPAddressString(item[0][1].value).getAddress()
                                                .toFullString().replace(":", "")
                                        val query6 = StringBuilder()
                                        for (d in ip6HexPartsDOH.reversed()) {
                                            query6.append(d)
                                            query6.append('.')
                                        }
                                        query6.append("ip6.arpa.")
                                        val dohNamePTR6 =
                                            org.xbill.DNS.Name.fromString(query6.toString())
                                        val dohRecordPTR6 = org.xbill.DNS.Record.newRecord(
                                            dohNamePTR6,
                                            Type.PTR,
                                            DClass.IN
                                        )
                                        val dohMessagePTR6 =
                                            org.xbill.DNS.Message.newQuery(dohRecordPTR6)
                                        val dohBase64DataPTR6 = Base64.encodeToString(
                                            dohMessagePTR6.toWire(),
                                            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                        )
                                        val dohClientPTR6 = OkHttpClient.Builder().build()
                                        val submitDOHRequestPTR6 = Request.Builder()
                                            .url(currentDOHServer.value + "?dns=" + dohBase64DataPTR6)
                                            .addHeader("Accept", "application/dns-message")
                                            .get()
                                            .build()
                                        val dohRequestPTR6 =
                                            dohClientPTR6.newCall(submitDOHRequestPTR6).execute()
                                        if (dohRequestPTR6.isSuccessful) {
                                            val dohResponseBodyPTR6 = dohRequestPTR6.body?.bytes()
                                            if (dohResponseBodyPTR6 != null) {
                                                val dohResponsePTR6 =
                                                    org.xbill.DNS.Message(dohResponseBodyPTR6)
                                                        .getSection(org.xbill.DNS.Section.ANSWER)
                                                for (r in dohResponsePTR6) {
                                                    if (r is PTRRecord) {
                                                        isPTR6DOHRequestSuccessful = true
                                                        item[2][0].value = r.target.toString()
                                                        noResult6 = false
                                                    }
                                                }
                                                dohRequestPTR6.close()
                                            }

                                        }


                                    } catch (e2: Exception) {
                                        Log.e("eachHopHandler", e2.printStackTrace().toString())
                                        threadMutex.withLock {
                                            tracerouteThreadsIntList.indices.forEach { index ->
                                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                                    tracerouteThreadsIntList[index] = 0
                                                }
                                            }
                                            tracerouteThreadsIntList.add(0)
                                        }
                                        return@launch

                                    }
                                    if (isPTR6DOHRequestSuccessful) {
                                        break
                                    }
                                    delay(timeMillis = 500)
                                    maxPTR6DOHTries -= 1

                                }
                            } else {
                                try {
                                    val ip6HexParts = IPAddressString(item[0][1].value).getAddress()
                                        .toFullString().replace(":", "")
                                    val query6 = StringBuilder()
                                    for (d in ip6HexParts.reversed()) {
                                        query6.append(d)
                                        query6.append('.')
                                    }
                                    query6.append("ip6.arpa.")
                                    val lookup6 = Lookup(query6.toString(), Type.PTR)

                                    val lookup6SimpleResolver =
                                        SimpleResolver(tracerouteDNSServer.value)
                                    if (currentDNSMode.value == "tcp") {
                                        lookup6SimpleResolver.tcp = true
                                    } else {
                                        lookup6SimpleResolver.tcp = false
                                    }
                                    lookup6.setResolver(lookup6SimpleResolver)

                                    val records6 = lookup6.run()
                                    if (records6 != null && records6.isNotEmpty()) {
                                        for (r in records6) {
                                            if (r is PTRRecord) {
                                                item[2][0].value = r.target.toString()
                                                noResult6 = false

                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("eachHopHandler", e.printStackTrace().toString())
                                    item[2][0].value = "*"
                                    threadMutex.withLock {
                                        tracerouteThreadsIntList.indices.forEach { index ->
                                            if (tracerouteThreadsIntList[index] == uniqueID) {
                                                tracerouteThreadsIntList[index] = 0
                                            }
                                        }
                                        tracerouteThreadsIntList.add(0)
                                        return@launch
                                    }
                                }
                            }




                            if (noResult6) {
                                item[2][0].value = "*"
                            }
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }


                        }
                    }


//                    //prevent duplicate recompose
//                    singleHopCursor[index].value=item[0][1].value
                }
                //NextTrace API:POW
                LaunchedEffect(Unit) {
                    scope.launch(Dispatchers.IO) {
                        val uniqueID = Random.nextInt(1, Int.MAX_VALUE)
                        threadMutex.withLock {
                            tracerouteThreadsIntList.add(uniqueID)
                        }
                        //start map trace after the api query finished
                        val uniqueID2 = Random.nextInt(1, Int.MAX_VALUE)
                        traceMapMutex.withLock {
                            traceMapThreadsIntList.add(uniqueID2)
                        }


                        //delay(timeMillis = 1000)
                        //it starts after API test finished
                        var maxTriesOfAPIToken = 40
                        while (maxTriesOfAPIToken >= 0) {
                            delay(timeMillis = 500)
                            if (preferredAPIIp.value != "" && apiToken.value != "") {
                                break
                            }
                            maxTriesOfAPIToken -= 1
                        }
                        if (maxTriesOfAPIToken < 0) {
                            threadMutex.withLock {
                                tracerouteThreadsIntList.indices.forEach { index ->
                                    if (tracerouteThreadsIntList[index] == uniqueID) {
                                        tracerouteThreadsIntList[index] = 0
                                    }
                                }
                                tracerouteThreadsIntList.add(0)
                            }
                            return@launch
                        }
                        var maxTriesOfAPIReq = 3
                        while (maxTriesOfAPIReq >= 0) {
                            try {
                                val currentLocale = Locale.getDefault()
                                val isChinese = currentLocale.language.startsWith("zh")
                                //get first usable ip and test ws
                                var isRequestSuccessful = false
                                var jsonData = JsonObject()
                                val customDns = object : Dns {
                                    override fun lookup(hostname: String): List<InetAddress> {
                                        return InetAddress.getAllByName(preferredAPIIp.value)
                                            .toList()
                                    }
                                }
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(5, TimeUnit.SECONDS)
                                    .readTimeout(5, TimeUnit.SECONDS)
                                    .writeTimeout(5, TimeUnit.SECONDS)
                                    .dns(customDns)
                                    .build()
                                val mapTraceSingleData = mutableMapOf(
                                    "Success" to false,
                                    "Address" to mapOf("IP" to item[0][1].value, "zone" to ""),
                                    "Hostname" to "",
                                    "TTL" to index + 1,
                                    //"RTT" to 114514,
                                    "Error" to null,
                                    "Geo" to mutableMapOf(
                                        "ip" to "",
                                        "asnumber" to "",
                                        "country" to "",
                                        "country_en" to "",
                                        "prov" to "",
                                        "prov_en" to "",
                                        "city" to "",
                                        "city_en" to "",
                                        "district" to "",
                                        "owner" to "",
                                        "isp" to "",
                                        "domain" to "",
                                        "whois" to "",
                                        "lat" to 0,
                                        "lng" to 0,
                                        "prefix" to "",
                                        "router" to mapOf<Any, Any>(),
                                        "source" to ""
                                    ),
                                    "Lang" to "cn",
                                    "MPLS" to null
                                )
                                val getAPIHeaders = mapOf(
                                    "Host" to apiHostName.value,
                                    "User-Agent" to "NextTrace v5.1.4/linux/android NextTracerouteAndroid/" + BuildConfig.VERSION_NAME,
                                    "Authorization" to "Bearer " + apiToken.value,
                                )
                                val getAPIURL = "wss://" + apiHostName.value + "/v3/ipGeoWs"
                                val getPOWRequest = Request.Builder()
                                    .url(getAPIURL)
                                getAPIHeaders.forEach { (key, value) ->
                                    getPOWRequest.addHeader(key, value)
                                }
                                val requestBuilder = getPOWRequest.build()
                                val webSocketListener = object : WebSocketListener() {
                                    override fun onOpen(webSocket: WebSocket, response: Response) {
                                        webSocket.send(item[0][1].value)
                                    }

                                    override fun onMessage(webSocket: WebSocket, text: String) {
                                        if (text != "") {
                                            jsonData = JsonParser.parseString(text).asJsonObject
                                            if ((currentLanguage.value == "Default" && isChinese) || currentLanguage.value == "zh") {
                                                val asNumberData =
                                                    jsonData.getAsJsonPrimitive("asnumber").asString
                                                item[0][2].value =
                                                    if (asNumberData.isNullOrEmpty()) "*" else "AS$asNumberData"
                                                item[0][3].value =
                                                    jsonData.getAsJsonPrimitive("whois").asString.takeUnless { it.isNullOrEmpty() }
                                                        ?: "*"
                                                item[1][0].value =
                                                    (jsonData.getAsJsonPrimitive("country").asString + " " +
                                                            jsonData.getAsJsonPrimitive("prov").asString + " " +
                                                            jsonData.getAsJsonPrimitive("city").asString + " " +
                                                            jsonData.getAsJsonPrimitive("domain").asString).takeUnless { it.isEmpty() }
                                                        ?: "*"

                                            } else {
                                                val asNumberData =
                                                    jsonData.getAsJsonPrimitive("asnumber").asString
                                                item[0][2].value =
                                                    if (asNumberData.isNullOrEmpty()) "*" else "AS$asNumberData"
                                                item[0][3].value =
                                                    jsonData.getAsJsonPrimitive("whois").asString.takeUnless { it.isNullOrEmpty() }
                                                        ?: "*"
                                                item[1][0].value =
                                                    (jsonData.getAsJsonPrimitive("country_en").asString + " " +
                                                            jsonData.getAsJsonPrimitive("prov_en").asString + " " +
                                                            jsonData.getAsJsonPrimitive("city_en").asString + " " +
                                                            jsonData.getAsJsonPrimitive("domain").asString).takeUnless { it.isEmpty() }
                                                        ?: "*"
                                            }

                                            isRequestSuccessful = true
                                        }

                                    }

                                    override fun onClosing(
                                        webSocket: WebSocket,
                                        code: Int,
                                        reason: String
                                    ) {
                                        webSocket.close(1000, "oh, I'm coming")
                                    }
                                }
                                val webSocketReq =
                                    client.newWebSocket(requestBuilder, webSocketListener)
                                for (i in 1..20) {
                                    //testAPIText.value=isRequestSuccessful.toString()
                                    if (isRequestSuccessful) {
                                        webSocketReq.close(1000, "oh, I'm coming")
                                        mapTraceSingleData["Success"] = true
                                        @Suppress("UNCHECKED_CAST")
                                        val geoMap =
                                            mapTraceSingleData["Geo"] as MutableMap<String, Any>
                                        geoMap["ip"] = ""
                                        geoMap["asnumber"] =
                                            jsonData.getAsJsonPrimitive("asnumber").asString
                                        geoMap["country"] =
                                            jsonData.getAsJsonPrimitive("country").asString
                                        geoMap["country_en"] =
                                            jsonData.getAsJsonPrimitive("country_en").asString
                                        geoMap["prov"] =
                                            jsonData.getAsJsonPrimitive("prov").asString
                                        geoMap["prov_en"] =
                                            jsonData.getAsJsonPrimitive("prov_en").asString
                                        geoMap["city"] =
                                            jsonData.getAsJsonPrimitive("city").asString
                                        geoMap["city_en"] =
                                            jsonData.getAsJsonPrimitive("city_en").asString
                                        geoMap["owner"] =
                                            jsonData.getAsJsonPrimitive("owner").asString
                                        geoMap["isp"] = jsonData.getAsJsonPrimitive("isp").asString
                                        geoMap["domain"] =
                                            jsonData.getAsJsonPrimitive("domain").asString
                                        geoMap["whois"] =
                                            jsonData.getAsJsonPrimitive("whois").asString
                                        geoMap["country_en"] =
                                            jsonData.getAsJsonPrimitive("country_en").asString
                                        geoMap["lat"] = jsonData.getAsJsonPrimitive("lat").asDouble
                                        geoMap["lng"] = jsonData.getAsJsonPrimitive("lng").asDouble
                                        if (geoMap["lat"] == 0.0) {
                                            geoMap["lat"] = 0
                                        }
                                        if (geoMap["lng"] == 0.0) {
                                            geoMap["lng"] = 0
                                        }
                                        val mapTraceSingleDataList =
                                            listOf(mapTraceSingleData)////,mapTraceSingleData,mapTraceSingleData)



                                        traceMapMutex.withLock {
                                            traceMapThreadsMapList.add(mapTraceSingleDataList)
                                        }


                                        threadMutex.withLock {
                                            tracerouteThreadsIntList.indices.forEach { index ->
                                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                                    tracerouteThreadsIntList[index] = 0
                                                }
                                            }
                                            tracerouteThreadsIntList.add(0)
                                        }
                                        traceMapMutex.withLock {
                                            traceMapThreadsIntList.indices.forEach { index ->
                                                if (traceMapThreadsIntList[index] == uniqueID2) {
                                                    traceMapThreadsIntList[index] = 0
                                                }
                                            }
                                            traceMapThreadsIntList.add(0)
                                        }
                                        return@launch
                                    }
                                    delay(100)
                                }
                                if (!isRequestSuccessful) {
                                    continue
                                }


                            } catch (e: Exception) {
                                Log.e("MapHandler", e.printStackTrace().toString())
                                delay(timeMillis = 200)
                                continue
                            }

                            maxTriesOfAPIReq -= 1
                            delay(timeMillis = 200)
                        }









                        threadMutex.withLock {
                            tracerouteThreadsIntList.indices.forEach { index ->
                                if (tracerouteThreadsIntList[index] == uniqueID) {
                                    tracerouteThreadsIntList[index] = 0
                                }
                            }
                            tracerouteThreadsIntList.add(0)
                        }
                        traceMapMutex.withLock {
                            traceMapThreadsIntList.indices.forEach { index ->
                                if (traceMapThreadsIntList[index] == uniqueID2) {
                                    traceMapThreadsIntList[index] = 0
                                }
                            }
                            traceMapThreadsIntList.add(0)
                        }
                    }
                }


            }
        }


    }


    private fun nativePingHandler(ip: String, count: String, ttl: String, timeout: String): String {

        try {
            var command: String
            val ipType = identifyInput(ip)
            if (ipType == IPV4IDENTIFIER) {
                command = "ping -n -c $count -W $timeout -t $ttl $ip"
                if (ttl == "") {
                    command = "ping -n -c $count -W $timeout $ip"
                }
            } else if (ipType == IPV6IDENTIFIER) {
                command = "ping6 -n -c $count -W $timeout -t $ttl $ip"
                if (ttl == "") {
                    command = "ping6 -n -c $count -W $timeout $ip"
                }
            } else {
                return ERRORIDENTIFIER
            }
            val process = Runtime.getRuntime().exec(command)
            process.waitFor()
            val stdReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val stdOutput = StringBuilder()
            val errOutput = StringBuilder()

            var lineStd: String?
            while (stdReader.readLine().also { lineStd = it } != null) {
                stdOutput.append(lineStd + "\n")
            }
            var lineErr: String?
            while (errReader.readLine().also { lineErr = it } != null) {
                errOutput.append(lineErr + "\n")
            }
            stdOutput.append(errOutput)

            return stdOutput.toString()
        } catch (e: Exception) {
            Log.e("nativePingHandler", e.printStackTrace().toString())
            return ERRORIDENTIFIER
        }


    }

    fun identifyInput(input: String): String {
        val ipv4Pattern = Pattern.compile(
            "^(?!255\\.255\\.255\\.255\$)([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}\$"
        )
        val ipv6Pattern1 = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\$"
        )
        val ipv6Pattern2 = Pattern.compile(
            "^(([0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4})*)?)::((([0-9A-Fa-f]{1,4}:)*[0-9A-Fa-f]{1,4})?)\$"
        )
        val hostnamePattern = Pattern.compile(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z][A-Za-z0-9]*)\$"

        )
        if (ipv4Pattern.matcher(input).matches()) {
            return IPV4IDENTIFIER
        }

        if (ipv6Pattern1.matcher(input).matches()) {
            return IPV6IDENTIFIER
        }
        if (ipv6Pattern2.matcher(input).matches()) {
            return IPV6IDENTIFIER
        }
        if (hostnamePattern.matcher(input).matches()) {
            return HOSTNAMEIDENTIFIER
        }
        return ERRORIDENTIFIER

    }


}