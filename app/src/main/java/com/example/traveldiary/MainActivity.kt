package com.example.traveldiary

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Trip(
    val id: Long,
    val name: String,
    val start: String,
    val end: String,
    val note: String = ""
)

data class Record(
    val id: Long,
    val tripId: Long,
    val kind: String,
    val title: String,
    val detail: String = "",
    val amount: Double = 0.0,
    val date: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            MaterialTheme {
                TravelDiaryApp()
            }
        }
    }
}

class TravelVM(app: Application) : AndroidViewModel(app) {

    private val prefs =
        app.getSharedPreferences("travel_diary", Context.MODE_PRIVATE)

    private val _trips = MutableStateFlow(loadTrips())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    val allRecords: StateFlow<List<Record>> = _records.asStateFlow()

    fun createTrip(name: String, start: String, end: String) =
        viewModelScope.launch {
            val newTrip = Trip(
                id = System.currentTimeMillis(),
                name = name,
                start = start,
                end = end
            )

            _trips.value = listOf(newTrip) + _trips.value
            saveTrips()
        }

    fun add(
        tripId: Long,
        kind: String,
        title: String,
        detail: String = "",
        amount: Double = 0.0
    ) = viewModelScope.launch {

        val date = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())

        val record = Record(
            id = System.currentTimeMillis(),
            tripId = tripId,
            kind = kind,
            title = title,
            detail = detail,
            amount = amount,
            date = date
        )

        _records.value = _records.value + record
        saveRecords()
    }

    fun records(tripId: Long): Flow<List<Record>> {
        return allRecords.map { records ->
            records.filter { it.tripId == tripId }
        }
    }

    private fun loadTrips(): List<Trip> {
        return try {
            val text = prefs.getString("trips", "[]") ?: "[]"
            val array = JSONArray(text)

            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)

                    add(
                        Trip(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            start = o.optString("start"),
                            end = o.optString("end"),
                            note = o.optString("note")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadRecords(): List<Record> {
        return try {
            val text = prefs.getString("records", "[]") ?: "[]"
            val array = JSONArray(text)

            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)

                    add(
                        Record(
                            id = o.optLong("id"),
                            tripId = o.optLong("tripId"),
                            kind = o.optString("kind"),
                            title = o.optString("title"),
                            detail = o.optString("detail"),
                            amount = o.optDouble("amount"),
                            date = o.optString("date")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveTrips() {
        val array = JSONArray()

        _trips.value.forEach { trip ->
            array.put(
                JSONObject().apply {
                    put("id", trip.id)
                    put("name", trip.name)
                    put("start", trip.start)
                    put("end", trip.end)
                    put("note", trip.note)
                }
            )
        }

        prefs.edit()
            .putString("trips", array.toString())
            .apply()
    }

    private fun saveRecords() {
        val array = JSONArray()

        _records.value.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("tripId", record.tripId)
                    put("kind", record.kind)
                    put("title", record.title)
                    put("detail", record.detail)
                    put("amount", record.amount)
                    put("date", record.date)
                }
            )
        }

        prefs.edit()
            .putString("records", array.toString())
            .apply()
    }
}

@Composable
fun TravelDiaryApp(
    vm: TravelVM = viewModel()
) {
    var selected by remember { mutableStateOf<Long?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    val trips by vm.trips.collectAsState()

    if (selected == null) {
        Home(
            trips = trips,
            create = { showCreate = true },
            open = { selected = it }
        )
    } else {
        TripDetail(
            id = selected!!,
            vm = vm,
            back = { selected = null }
        )
    }

    if (showCreate) {
        CreateTrip(
            save = { name, start, end ->
                vm.createTrip(name, start, end)
                showCreate = false
            },
            cancel = {
                showCreate = false
            }
        )
    }
}

@Composable
fun Home(
    trips: List<Trip>,
    create: () -> Unit,
    open: (Long) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = create) {
                Text("+")
            }
        }
    ) { pad ->

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                Text(
                    "旅行日记",
                    style = MaterialTheme.typography.headlineLarge
                )

                Text("把旅程、同行、费用和回忆放在一起")
            }

            if (trips.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(20.dp)
                        ) {
                            Text(
                                "还没有旅行记录",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                "点击右下角 + 开始创建你的第一段旅行"
                            )
                        }
                    }
                }
            }

            items(
                items = trips,
                key = { it.id }
            ) { trip ->

                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            open(trip.id)
                        }
                ) {
                    Column(
                        Modifier.padding(18.dp)
                    ) {
                        Text(
                            trip.name,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(6.dp))

                        Text("${trip.start}  →  ${trip.end}")

                        Spacer(Modifier.height(4.dp))

                        Text("点击进入旅行详情")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateTrip(
    save: (String, String, String) -> Unit,
    cancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = cancel,

        title = {
            Text("新建旅行")
        },

        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("旅行名称") }
                )

                TextField(
                    value = start,
                    onValueChange = { start = it },
                    label = { Text("开始日期") }
                )

                TextField(
                    value = end,
                    onValueChange = { end = it },
                    label = { Text("结束日期") }
                )
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    save(name, start, end)
                },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },

        dismissButton = {
            TextButton(
                onClick = cancel
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TripDetail(
    id: Long,
    vm: TravelVM,
    back: () -> Unit
) {
    val records by vm
        .records(id)
        .collectAsState(emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }

    val total =
        records
            .filter { it.kind == "expense" }
            .sumOf { it.amount }

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text("旅行详情")
                },

                navigationIcon = {
                    TextButton(
                        onClick = back
                    ) {
                        Text("返回")
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    add = true
                }
            ) {
                Text("+")
            }
        }

    ) { pad ->

        Column(
            Modifier.padding(pad)
        ) {

            Row(
                Modifier
                    .horizontalScroll(
                        rememberScrollState()
                    )
            ) {

                listOf(
                    "概览",
                    "同行",
                    "交通",
                    "费用",
                    "穿搭",
                    "相册",
                    "日记"
                ).forEachIndexed { i, label ->

                    TextButton(
                        onClick = {
                            tab = i
                        }
                    ) {
                        Text(label)
                    }
                }
            }

            when (tab) {

                0 -> Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "旅行概览",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text("全部记录：${records.size} 项")

                    Text(
                        "旅行费用：¥${
                            "%.2f".format(total)
                        }"
                    )

                    Text(
                        "照片记录：${
                            records.count {
                                it.kind == "photo"
                            }
                        }"
                    )

                    Text("经典照片：后续可在照片模块标记")
                }

                1 -> RecordList(
                    records
                        .filter { it.kind == "people" }
                        .map { it.title }
                )

                2 -> RecordList(
                    records
                        .filter { it.kind == "transport" }
                        .map {
                            "${it.title}  ${it.detail}"
                        }
                )

                3 -> RecordList(
                    records
                        .filter { it.kind == "expense" }
                        .map {
                            "${it.title}   ¥${
                                "%.2f".format(it.amount)
                            }   ${it.detail}"
                        }
                )

                4 -> RecordList(
                    records
                        .filter { it.kind == "outfit" }
                        .map {
                            "${it.date}   ${it.title}"
                        }
                )

                5 -> RecordList(
                    records
                        .filter { it.kind == "photo" }
                        .map {
                            it.title
                        }
                )

                else -> RecordList(
                    records
                        .filter { it.kind == "diary" }
                        .map {
                            "${it.date}   ${it.title}: ${it.detail}"
                        }
                )
            }
        }
    }

    if (add) {
        AddRecord(
            tab = tab,

            save = { title, amount, detail ->

                add = false

                val kind = listOf(
                    "overview",
                    "people",
                    "transport",
                    "expense",
                    "outfit",
                    "photo",
                    "diary"
                )[tab]

                vm.add(
                    id,
                    kind,
                    title,
                    detail,
                    amount
                )
            },

            cancel = {
                add = false
            }
        )
    }
}

@Composable
fun RecordList(
    items: List<String>
) {
    LazyColumn(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        items(items) { text ->

            Card(
                Modifier.fillMaxWidth()
            ) {
                Text(
                    text,
                    Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun AddRecord(
    tab: Int,
    save: (String, Double, String) -> Unit,
    cancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }

    val labels = listOf(
        "概览记录",
        "同行人员",
        "交通工具",
        "费用",
        "穿搭计划",
        "照片记录",
        "旅行日记"
    )

    AlertDialog(
        onDismissRequest = cancel,

        title = {
            Text("添加${labels[tab]}")
        },

        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                TextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text("标题")
                    }
                )

                if (tab == 3) {
                    TextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                        },
                        label = {
                            Text("金额")
                        }
                    )
                }

                TextField(
                    value = detail,
                    onValueChange = {
                        detail = it
                    },
                    label = {
                        Text("备注 / 详情")
                    }
                )
            }
        },

        confirmButton = {
            Button(
                onClick = {
                    save(
                        title,
                        amount.toDoubleOrNull() ?: 0.0,
                        detail
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },

        dismissButton = {
            TextButton(
                onClick = cancel
            ) {
                Text("取消")
            }
        }
    )
}
