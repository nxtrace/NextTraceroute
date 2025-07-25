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

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.surfaceocean.nexttraceroute.ui.theme.ButtonEnabledColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColorReverse
import kotlin.math.roundToInt


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
        "https://doh.pub/dns-query",
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
            .border(1.dp, Color.DarkGray)
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
                if (trHandler.identifyInput(dnsServerText.value) == IPV4_IDENTIFIER ||
                    trHandler.identifyInput(dnsServerText.value) == IPV6_IDENTIFIER
                ) {
                    tracerouteDNSServer.value = dnsServerText.value
                } else {
                    errorText += "Invalid UDP/TCP DNS server, "
                }
                currentDOHServer.value = dohServersOptions[dohServersSelectedIndex.intValue]
                if (trHandler.identifyInput(powHostNameText.value) == HOSTNAME_IDENTIFIER) {
                    apiHostNamePOW.value = powHostNameText.value
                } else {
                    errorText += "Invalid Hostname for POW server, "
                }
                if (trHandler.identifyInput(powDNSNameText.value) == IPV4_IDENTIFIER ||
                    trHandler.identifyInput(powDNSNameText.value) == IPV6_IDENTIFIER ||
                    trHandler.identifyInput(powDNSNameText.value) == HOSTNAME_IDENTIFIER
                ) {
                    apiDNSNamePOW.value = powDNSNameText.value
                } else {
                    errorText += "Invalid DNS Hostname for POW server, "
                }

                if (trHandler.identifyInput(apiHostNameText.value) == HOSTNAME_IDENTIFIER) {
                    apiHostName.value = apiHostNameText.value
                } else {
                    errorText += "Invalid Hostname for API server, "
                }

                if (trHandler.identifyInput(apiDNSNameText.value) == IPV4_IDENTIFIER ||
                    trHandler.identifyInput(apiDNSNameText.value) == IPV6_IDENTIFIER ||
                    trHandler.identifyInput(apiDNSNameText.value) == HOSTNAME_IDENTIFIER
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("POW HostName:", color = DefaultBackgroundColorReverse)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("POW DNSName:", color = DefaultBackgroundColorReverse)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("API HostName:", color = DefaultBackgroundColorReverse)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("API DNSName:", color = DefaultBackgroundColorReverse)
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
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)


    }
}

