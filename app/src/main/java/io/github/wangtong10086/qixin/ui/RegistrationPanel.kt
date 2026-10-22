package io.github.wangtong10086.qixin.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.wangtong10086.qixin.service.Wire
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun OpenRegistrationButton(name: String) {
 val context=LocalContext.current
 var error by remember(name){mutableStateOf("")}
 TextButton(onClick={
  val intent=context.packageManager.getLaunchIntentForPackage(name)
  if(intent==null)error="此应用没有可打开的入口" else try{context.startActivity(intent);error=""}catch(_:ActivityNotFoundException){error="应用入口已变更，请刷新应用列表"}
 }){Text("手动打开注册")}
 if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
}

@Composable internal fun RegistrationPanel(active:Boolean) {
 var job by remember{mutableStateOf(JSONObject())}
 var error by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)}
 var details by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 fun command(name:String){scope.launch{busy=true;try{job=withContext(Dispatchers.IO){Wire.call(JSONObject().put("command",name))};error=""}catch(e:java.io.IOException){error=e.message?:"操作失败"}finally{busy=false}}}
 LaunchedEffect(active){if(active)while(true){try{job=withContext(Dispatchers.IO){Wire.call(JSONObject().put("command","registration.status"))};error=""}catch(e:java.io.IOException){error=e.message?:"服务未连接"};delay(2000)}}
 val rows=job.optJSONArray("items")?.let{a->(0 until a.length()).map{a.getJSONObject(it)}}?:emptyList()
 Text("批量注册",style=MaterialTheme.typography.titleMedium)
 Text("只发送无需打开界面的请求。FCM 及无后台入口的应用需手动打开；已有注册记录不代表服务器会使用该通道。",style=MaterialTheme.typography.bodySmall)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Button(onClick={command("registration.batch")},enabled=!busy&&!job.optBoolean("running")){Text("一键注册")}
  if(job.optBoolean("running"))OutlinedButton(onClick={command("registration.cancel")},enabled=!busy){Text("取消剩余")}
 }
 if(rows.isNotEmpty()){
  Text("${rows.count{it.optString("state")=="request_sent"}} 项已请求 · ${rows.count{it.optString("state")=="manual_required"}} 项需手动 · ${rows.count{it.optString("state")=="failed"}} 项失败")
  TextButton(onClick={details=true}){Text("查看 ${rows.size} 项结果")}
 }
 if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
 if(details)AlertDialog(onDismissRequest={details=false},title={Text("注册请求结果")},text={
  LazyColumn(Modifier.heightIn(max=460.dp)){
   items(rows,key={it.getString("package")+it.getString("channel")}){row->
    Text(row.getString("label")+" · "+if(row.getString("channel")=="fcm")"FCM" else "小米",style=MaterialTheme.typography.titleSmall)
    Text(row.getString("reason"),style=MaterialTheme.typography.bodySmall)
    if(row.getString("state")=="manual_required")OpenRegistrationButton(row.getString("package"))
    HorizontalDivider(Modifier.padding(vertical=12.dp))
   }
  }
 },confirmButton={TextButton(onClick={details=false}){Text("关闭")}})
}
