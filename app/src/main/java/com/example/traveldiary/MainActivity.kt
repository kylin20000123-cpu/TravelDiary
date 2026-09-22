package com.example.traveldiary

import android.app.Application
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.traveldiary.data.*
import kotlinx.coroutines.flow.collectAsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{TravelDiaryApp()}}
}

class TravelVM(app:Application):AndroidViewModel(app){
    private val dao=Db.get(app).dao()
    val trips=dao.trips()
    fun createTrip(name:String,start:String,end:String)=viewModelScope.launch{
        dao.addTrip(Trip(name=name,start=start,end=end))
    }
    fun add(id:Long,kind:String,title:String,detail:String="",amount:Double=0.0)=viewModelScope.launch{
        dao.addRecord(Record(tripId=id,kind=kind,title=title,detail=detail,amount=amount,date=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())))
    }
    fun records(id:Long)=dao.records(id)
}

@Composable
fun TravelDiaryApp(vm:TravelVM=viewModel()){
    var selected by remember{mutableStateOf<Long?>(null)}
    var showCreate by remember{mutableStateOf(false)}
    val trips by vm.trips.collectAsState(emptyList())
    if(selected==null) Home(trips,{showCreate=true}){selected=it}
    else TripDetail(selected!!,vm){selected=null}
    if(showCreate) CreateTrip(
        save={n,s,e->vm.createTrip(n,s,e);showCreate=false},
        cancel={showCreate=false}
    )
}

@Composable
fun Home(trips:List<Trip>,create:()->Unit,open:(Long)->Unit){
    Scaffold(floatingActionButton={FloatingActionButton(create){Text("+")}}){pad->
        LazyColumn(
            Modifier.padding(pad).padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)
        ){
            item{
                Text("旅行日记",style=MaterialTheme.typography.headlineLarge)
                Text("把旅程、同行、费用和回忆放在一起")
            }
            if(trips.isEmpty()) item {
                Card(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(20.dp)){
                        Text("还没有旅行记录",style=MaterialTheme.typography.titleMedium)
                        Text("点击右下角 + 开始创建你的第一段旅行")
                    }
                }
            }
            items(trips){t->
                Card(Modifier.fillMaxWidth().clickable{open(t.id)}){
                    Column(Modifier.padding(18.dp)){
                        Text(t.name,style=MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("${t.start}  →  ${t.end}")
                        Spacer(Modifier.height(4.dp))
                        Text("点击进入旅行详情")
                    }
                }
            }
        }
    }
}

@Composable
fun CreateTrip(save:(String,String,String)->Unit,cancel:()->Unit){
    var name by remember{mutableStateOf("")}
    var start by remember{mutableStateOf("")}
    var end by remember{mutableStateOf("")}
    AlertDialog(
        onDismissRequest=cancel,
        title={Text("新建旅行")},
        text={
            Column{
                TextField(name,{name=it},label={Text("旅行名称")})
                TextField(start,{start=it},label={Text("开始日期")})
                TextField(end,{end=it},label={Text("结束日期")})
            }
        },
        confirmButton={Button({save(name,start,end)},enabled=name.isNotBlank()){Text("创建")}},
        dismissButton={TextButton(cancel){Text("取消")}}
    )
}

@Composable
fun TripDetail(id:Long,vm:TravelVM,back:()->Unit){
    val records by vm.records(id).collectAsState(emptyList())
    var tab by remember{mutableIntStateOf(0)}
    var add by remember{mutableStateOf(false)}
    val total=records.filter{it.kind=="expense"}.sumOf{it.amount}

    Scaffold(
        topBar={TopAppBar(
            title={Text("旅行详情")},
            navigationIcon={TextButton(back){Text("返回")}}
        )},
        floatingActionButton={FloatingActionButton({add=true}){Text("+")}}
    ){pad->
        Column(Modifier.padding(pad)){
            Row(Modifier.horizontalScroll(rememberScrollState())){
                listOf("概览","同行","交通","费用","穿搭","相册","日记").forEachIndexed{i,label->
                    TextButton({tab=i}){Text(label)}
                }
            }
            when(tab){
                0->Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("旅行概览",style=MaterialTheme.typography.headlineSmall)
                    Text("全部记录：${records.size} 项")
                    Text("旅行费用：¥${"%.2f".format(total)}")
                    Text("照片记录：${records.count{it.kind=="photo"}}")
                    Text("经典照片：可在照片模块标记")
                }
                1->RecordList(records.filter{it.kind=="people"}.map{it.title})
                2->RecordList(records.filter{it.kind=="transport"}.map{"${it.title}  ${it.detail}"})
                3->RecordList(records.filter{it.kind=="expense"}.map{"${it.title}   ¥${"%.2f".format(it.amount)}   ${it.detail}"})
                4->RecordList(records.filter{it.kind=="outfit"}.map{"${it.date}   ${it.title}"})
                5->RecordList(records.filter{it.kind=="photo"}.map{it.title})
                else->RecordList(records.filter{it.kind=="diary"}.map{"${it.date}   ${it.title}: ${it.detail}"})
            }
        }
    }

    if(add) AddRecord(tab,{title,amount,detail->
        add=false
        val kind=listOf("overview","people","transport","expense","outfit","photo","diary")[tab]
        vm.add(id,kind,title,detail,amount)
    }){add=false}
}

@Composable
fun RecordList(items:List<String>){
    LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        items(items){text->
            Card(Modifier.fillMaxWidth()){Text(text,Modifier.padding(16.dp))}
        }
    }
}

@Composable
fun AddRecord(tab:Int,save:(String,Double,String)->Unit,cancel:()->Unit){
    var title by remember{mutableStateOf("")}
    var amount by remember{mutableStateOf("")}
    var detail by remember{mutableStateOf("")}
    val labels=listOf("概览记录","同行人员","交通工具","费用","穿搭计划","照片记录","旅行日记")
    AlertDialog(
        onDismissRequest=cancel,
        title={Text("添加${labels[tab]}")},
        text={
            Column{
                TextField(title,{title=it},label={Text("标题")})
                if(tab==3) TextField(amount,{amount=it},label={Text("金额")})
                TextField(detail,{detail=it},label={Text("备注 / 详情")})
            }
        },
        confirmButton={Button({save(title,amount.toDoubleOrNull()?:0.0,detail)},enabled=title.isNotBlank()){Text("保存")}},
        dismissButton={TextButton(cancel){Text("取消")}}
    )
}
