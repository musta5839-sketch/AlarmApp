package com.example.alarmapp

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean = true
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<AlarmEntity>>

    @Insert
    suspend fun insert(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Update
    suspend fun update(alarm: AlarmEntity)
}

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile private var INSTANCE: AlarmDatabase? = null

        fun get(context: android.content.Context): AlarmDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarms.db"
                ).build().also { INSTANCE = it }
            }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AlarmDatabase.get(this).alarmDao()

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val alarms by dao.observeAll().collectAsState(initial = emptyList())
                var time by remember { mutableStateOf(LocalTime.now().plusMinutes(1).withSecond(0).withNano(0)) }
                var label by remember { mutableStateOf("") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("AlarmApp", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.displayLarge
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                time = time.minusMinutes(1)
                            }) { Text("− 1 min") }

                            Button(onClick = {
                                time = time.plusMinutes(1)
                            }) { Text("+ 1 min") }
                        }

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("اسم المنبّه") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    dao.insert(
                                        AlarmEntity(
                                            hour = time.hour,
                                            minute = time.minute,
                                            label = label.ifBlank { "منبّه جديد" }
                                        )
                                    )
                                    label = ""
                                }
                            }
                        ) {
                            Text("حفظ المنبّه")
                        }

                        HorizontalDivider()

                        Text("المنبّهات", style = MaterialTheme.typography.titleLarge)

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(alarms, key = { it.id }) { alarm ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "%02d:%02d".format(alarm.hour, alarm.minute),
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                            Text(alarm.label)
                                        }
                                        Switch(
                                            checked = alarm.enabled,
                                            onCheckedChange = { enabled ->
                                                scope.launch {
                                                    dao.update(alarm.copy(enabled = enabled))
                                                }
                                            }
                                        )
                                        IconButton(onClick = {
                                            scope.launch { dao.delete(alarm) }
                                        }) {
                                            Text("×")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
