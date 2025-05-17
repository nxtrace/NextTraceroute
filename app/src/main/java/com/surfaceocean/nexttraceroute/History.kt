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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.surfaceocean.nexttraceroute.ui.theme.ButtonEnabledColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColor
import com.surfaceocean.nexttraceroute.ui.theme.DefaultBackgroundColorReverse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Entity(tableName = "history")
data class HistoryData(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "timestamp") val timeStamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "ip") val ip: String,
    @ColumnInfo(name = "domain") val domain: String,
    @ColumnInfo(name = "history ") val history: String

)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history")
    suspend fun getAll(): List<HistoryData>

    @Query(
        """
    SELECT DISTINCT ip
      FROM history
     WHERE ip LIKE '%' || :inputIP || '%'
  """
    )
    suspend fun findInputIP(inputIP: String): List<String>

    @Query(
        """
    SELECT DISTINCT domain
      FROM history
     WHERE domain LIKE '%' || :inputDomain || '%'
  """
    )
    suspend fun findInputDomain(inputDomain: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(historyData: HistoryData): Long

    @Query("DELETE FROM history WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    @Query("DELETE FROM history")
    suspend fun deleteAll(): Int
}

@Database(entities = [HistoryData::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

@Composable
fun HistoryPage(
    context: Context, currentPage: MutableState<String>,
    historyDao: HistoryDao, db: AppDatabase
) {
    BackHandler {
        currentPage.value = "main"
    }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val isDeleteAllTriggered = remember { mutableStateOf(false) }
    val allData = remember { mutableStateListOf<HistoryData>() }
    //make sure isDatabaseUpdateTriggered is true by default in order to initiate in the first compose
    val isDatabaseUpdateTriggered = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val clearDBToastInfo = remember { mutableStateOf("") }
    val showDeleteAllWarningDialog = remember { mutableStateOf(false) }
    val currentDeletionUUID = remember { mutableStateOf(MAGIC_UUID) }
    val isDatabaseLoadFinished = remember { mutableStateOf(false) }
    if (clearDBToastInfo.value != "") {
        Toast.makeText(
            context,
            clearDBToastInfo.value,
            Toast.LENGTH_SHORT
        ).show()
        clearDBToastInfo.value = ""
    }

    LaunchedEffect(isDeleteAllTriggered.value) {
        scope.launch(Dispatchers.IO) {
            if (isDeleteAllTriggered.value) {
                isDatabaseLoadFinished.value = false
                try {
                    db.withTransaction {
                        historyDao.deleteAll()
                    }
                } catch (e: Exception) {
                    Log.e("databaseHandler", e.printStackTrace().toString())
                }
                clearDBToastInfo.value = "Database Cleared!"
                isDatabaseUpdateTriggered.value = true
                isDeleteAllTriggered.value = false

            }
        }
    }

    LaunchedEffect(isDatabaseUpdateTriggered.value) {
        scope.launch(Dispatchers.IO) {
            if (isDatabaseUpdateTriggered.value) {
                db.withTransaction {
                    isDatabaseLoadFinished.value = false
                    val list = historyDao.getAll()
                    allData.clear()
                    allData.addAll(list)
//                    clearDBToastInfo.value=allData.size.toString()
                    isDatabaseUpdateTriggered.value = false
                    isDatabaseLoadFinished.value = true
                }
            }
        }
    }


    //Item deletion only triggers when currentDeletionUUID is changed
    LaunchedEffect(currentDeletionUUID.value) {
        scope.launch(Dispatchers.IO) {
            if (currentDeletionUUID.value != MAGIC_UUID) {
                isDatabaseLoadFinished.value = false
                try {
                    db.withTransaction {
                        historyDao.deleteByUuid(uuid = currentDeletionUUID.value)
                    }
                } catch (e: Exception) {
                    Log.e("databaseHandler", e.printStackTrace().toString())
                }
                clearDBToastInfo.value = "Item Deleted!"
                currentDeletionUUID.value = MAGIC_UUID
                isDatabaseUpdateTriggered.value = true


            }
        }
    }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .systemBarsPadding()
            .border(1.dp, DefaultBackgroundColorReverse)
            .padding(bottom = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { currentPage.value = "main" }) {
            Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White)
        }
        Button(
            onClick = {
                showDeleteAllWarningDialog.value = true

            },
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonEnabledColor,
                contentColor = DefaultBackgroundColor,
                disabledContainerColor = Color.Gray,
                disabledContentColor = Color.LightGray
            )
        ) {
            Text("Clear")
        }
        if (showDeleteAllWarningDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteAllWarningDialog.value = false
                },
                title = {
                    Text(text = "Warning")
                },
                text = {
                    Text("Are you sure to clear the history database?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleteAllTriggered.value = true
                            showDeleteAllWarningDialog.value = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonEnabledColor,
                            contentColor = DefaultBackgroundColor,
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.LightGray
                        )
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDeleteAllWarningDialog.value = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonEnabledColor,
                            contentColor = DefaultBackgroundColor,
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.LightGray
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    //Remember the scrolling state after deleting an item
    val listState = rememberLazyListState()
    //To prevent recompose when allData is updating
    if (isDatabaseLoadFinished.value) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .border(1.dp, DefaultBackgroundColorReverse)
                .fillMaxWidth()
        ) {
            itemsIndexed(items = allData, key = { _, item -> item.uuid }) { allDataIndex, item ->
                val preShareText = "Date:" + SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date(item.timeStamp)) + "\n"
                Row(
                    modifier = Modifier
                        .border(1.dp, DefaultBackgroundColorReverse)
                        .padding(bottom = 1.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 1.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            color = Color.Green,
                            text = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                            ).format(
                                Date(item.timeStamp)
                            ),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        //val ipText= remember{mutableStateOf(item.ip)}
                        if (item.ip != "") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .pointerInput(item.ip) {
                                        detectTapGestures(
                                            onLongPress = {
                                                clipboardManager.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "simple text",
                                                        item.ip
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Copied!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onTap = {
                                                val tapURL = "https://bgp.tools/search?q=${item.ip}"
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        tapURL.toUri()
                                                    )
                                                )

                                            }
                                        )
                                    },

                                ) {
                                Text(
                                    color = Color.Yellow,
                                    text = item.ip,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                            }
                        }
                        //val domainText=remember{mutableStateOf(item.domain)}
                        if (item.domain != "") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .pointerInput(item.domain) {
                                        detectTapGestures(
                                            onLongPress = {
                                                clipboardManager.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "simple text",
                                                        item.domain
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Copied!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onTap = {
                                                val tapURL =
                                                    "https://bgp.tools/search?q=${item.domain}"
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        tapURL.toUri()
                                                    )
                                                )

                                            }
                                        )
                                    },

                                ) {
                                Text(
                                    color = Color.Cyan,
                                    text = item.domain,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                            }
                        }
                    }
                    val currentItemCursor = remember { mutableIntStateOf(MAGIC_NEGATIVE_INT) }
                    IconButton(
                        onClick = {
                            currentItemCursor.intValue = allDataIndex

                        }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                    }
                    if (currentItemCursor.intValue == allDataIndex) {
                        val scrollState = rememberScrollState()
                        AlertDialog(
                            onDismissRequest = {
                                currentItemCursor.intValue = MAGIC_NEGATIVE_INT
                            },
                            title = {
                                Text(text = item.uuid)
                            },
                            text = {
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    Text(preShareText + item.history)
                                }

                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        clipboardManager.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "simple text",
                                                preShareText + item.history
                                            )
                                        )
                                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT)
                                            .show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButtonEnabledColor,
                                        contentColor = DefaultBackgroundColor,
                                        disabledContainerColor = Color.Gray,
                                        disabledContentColor = Color.LightGray
                                    )
                                ) {
                                    Text("Copy")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = {
                                        currentItemCursor.intValue = MAGIC_NEGATIVE_INT
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButtonEnabledColor,
                                        contentColor = DefaultBackgroundColor,
                                        disabledContainerColor = Color.Gray,
                                        disabledContentColor = Color.LightGray
                                    )
                                ) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, preShareText + item.history)
                            type = "text/plain"
                        }
                        val chooser = Intent.createChooser(shareIntent, "Share to")
                        context.startActivity(chooser)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { currentDeletionUUID.value = item.uuid }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                    }

                }
            }
        }
    }
}