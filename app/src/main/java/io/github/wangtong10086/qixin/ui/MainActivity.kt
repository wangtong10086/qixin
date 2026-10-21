package io.github.wangtong10086.qixin.ui

import android.os.Bundle
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.wangtong10086.qixin.R
import io.github.wangtong10086.qixin.service.Wire
import io.github.wangtong10086.qixin.core.Policy
import kotlinx.coroutines.*
import org.json.*
import java.text.DateFormat
import java.util.Date

class MainActivity:ComponentActivity(){
 override fun onCreate(state:Bundle?){super.onCreate(state);androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=true
  setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF6750A4),onPrimary=Color.White,primaryContainer=Color(0xFFEADDFF),surface=Color(0xFFFFFBFE),surfaceVariant=Color(0xFFF3EDF7),outline=Color(0xFF79747E))){App(intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: intent.dataString.orEmpty())}}
 }
}
private fun JSONArray.rows()=(0 until length()).map{getJSONObject(it)}
private fun stamp(time:Long)=if(time==0L)"尚未记录" else DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(Date(time))
private fun state(value:String)=mapOf("connected" to "已连接", "connecting" to "连接中", "startup-notification-cleanup" to "启动通知清理", "preserved-background-notifications" to "已保留后台通知", "native-connection-recovery" to "原生连接恢复", "hook-ready" to "钩子状态", "service" to "后台服务", "task" to "持续任务", "message" to "消息", "legacy" to "旧版未分类", "notification-enqueue" to "通知发布失败", "click-request" to "系统通知点击", "system-ui-click" to "用户已点击通知", "click-route" to "点击转发", "click-marker-restored" to "已保留点击标记并临时放行", "dispatch-result" to "服务投递结果", "service-started" to "服务启动请求已接受", "no-component" to "服务未启动", "business-token-upload" to "业务服务器注册", "business-token-batch" to "批量注册上报", "server-accepted" to "服务器已接受", "no-server-ack-observed" to "未观察到服务器确认", "business-token-cache" to "业务注册缓存", "business-channel-support" to "服务端通道配置", "supported" to "允许此通道", "not-supported" to "未允许此通道", "present" to "存在缓存", "absent" to "无缓存", "native-method-returned" to "原生调用已返回，尚未确认上报", "registered" to "已有小米注册记录","not-registered" to "尚未注册","unregistered" to "已注销","imported-send-message" to "小米消息记录","imported-registration" to "小米注册请求","imported-registration-result" to "小米注册结果","imported-ack" to "小米回执记录","imported-notification" to "小米通知记录","ok" to "成功","denied-disabled" to "服务禁用拦截","denied-user" to "用户策略拦截","authenticated" to "已连接并认证","disconnected" to "已断开","no-network" to "无可用网络","unknown" to "尚未确认","cached-registration" to "发现注册缓存","imported-record" to "已导入注册记录","dispatch" to "请求投递","notification" to "通知发布","click" to "通知点击","registration-callback" to "注册回调","recovery" to "连接恢复","accepted" to "系统已接受","rejected" to "系统拒绝","requested" to "已请求","observe" to "仅观察","manage" to "正在管理")[value]?:value
@Composable private fun AppIcon(pkg:String){
 val context=LocalContext.current
 var bitmap by remember(pkg){mutableStateOf<Bitmap?>(null)}
 LaunchedEffect(pkg){bitmap=withContext(Dispatchers.IO){try{context.packageManager.getApplicationIcon(pkg).toBitmap(96,96)}catch(_:android.content.pm.PackageManager.NameNotFoundException){null}}}
 if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.size(40.dp))else Icon(painterResource(R.drawable.ic_qixin),null,Modifier.size(40.dp))
}
@Composable private fun Entry(title:String,detail:String,click:(()->Unit)?=null,pkg:String=""){
 ListItem(headlineContent={Text(title)},supportingContent={Text(detail)},leadingContent=if(pkg.isEmpty())null else {{AppIcon(pkg)}},modifier=if(click==null)Modifier else Modifier.clickable(onClick=click))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(initialPackage:String=""){
 var tab by rememberSaveable{mutableStateOf("概览")};var pkg by rememberSaveable{mutableStateOf(initialPackage)};var event by remember{mutableStateOf<JSONObject?>(null)}
 var status by remember{mutableStateOf(JSONObject())};var apps by remember{mutableStateOf(emptyList<JSONObject>())};var registrations by remember{mutableStateOf(emptyList<JSONObject>())};var events by remember{mutableStateOf(emptyList<JSONObject>())}
 var config by remember{mutableStateOf(JSONObject())};var draft by rememberSaveable{mutableStateOf("")};var original by rememberSaveable{mutableStateOf("")};var revision by rememberSaveable{mutableStateOf(0L)}
 var historyKind by rememberSaveable{mutableStateOf("message")};var states by remember{mutableStateOf(emptyList<JSONObject>())}
 var query by rememberSaveable{mutableStateOf("")};var channel by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf("")}
 val context=LocalContext.current
 val coroutine=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current.lifecycle
 var active by remember{mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,_->active=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 suspend fun rpc(value:JSONObject)=withContext(Dispatchers.IO){Wire.call(value)}
 fun execute(value:JSONObject,done:(JSONObject)->Unit={}){coroutine.launch{busy=true;try{done(rpc(value));error=""}catch(e:java.io.IOException){error=e.message?:"本地服务未连接"}finally{busy=false}}}
 fun back(){if(event!=null){event=null;return};if(draft!=original){confirm="discard";return};if(pkg.isNotEmpty()){pkg="";return};if(tab!="概览")tab="概览"}
 BackHandler(pkg.isNotEmpty()||event!=null||tab!="概览"){back()}
 LaunchedEffect(active){if(active){
  try{val index=rpc(JSONObject().put("command","apps"));apps=index.getJSONArray("apps").rows();registrations=index.getJSONArray("registrations").rows();config=rpc(JSONObject().put("command","config.get"))}catch(e:java.io.IOException){error=e.message?:"连接失败"}
  while(true){try{status=rpc(JSONObject().put("command","status"));error=""}catch(e:java.io.IOException){error=e.message?:"连接失败"};delay(5000)}
 }}
 LaunchedEffect(pkg,config.toString()){if(draft==original){draft=if(pkg.isEmpty())config.toString() else if(config.has("apps"))Policy.app(config,pkg).toString() else "{}";original=draft;revision=config.optLong("revision")}}
 LaunchedEffect(tab,pkg,channel,historyKind,active){if(active&&(tab=="历史"||pkg.isNotEmpty())){
  while(true){try{if(pkg.isEmpty()&&historyKind=="states")states=rpc(JSONObject().put("command","states")).getJSONArray("states").rows() else events=rpc(JSONObject().put("command","events").put("kind",if(pkg.isEmpty())historyKind else "").put("package",pkg).put("channel",channel).put("limit",100).put("order","time")).getJSONArray("events").rows()}catch(e:java.io.IOException){error=e.message?:"历史读取失败"};delay(5000)}
 }}
 val label=apps.firstOrNull{it.getString("package")==pkg}?.getString("label")?:pkg
 fun save(){val next=JSONObject(config.toString());if(pkg.isNotEmpty())next.getJSONObject("apps").put(pkg,JSONObject(draft))else{val changed=JSONObject(draft);changed.keys().forEach{next.put(it,changed.get(it))}}
  execute(JSONObject().put("command","config.replace").put("config",next).put("expectedRevision",revision)){config=it.getJSONObject("config");original=draft;revision=config.getLong("revision");message="已保存"}
 }
 Scaffold(topBar={TopAppBar(title={Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){if(pkg.isNotEmpty())AppIcon(pkg);Text(if(event!=null)"消息详情" else if(pkg.isNotEmpty())label else "栖信")}},navigationIcon={if(pkg.isNotEmpty()||event!=null)TextButton(onClick={back()}){Text("返回")}})},bottomBar={if(pkg.isEmpty()&&event==null)NavigationBar{listOf("概览","应用","历史","设置").forEach{item->NavigationBarItem(tab==item,{if(draft!=original)confirm="discard" else tab=item},icon={Icon(painterResource(when(item){"概览"->R.drawable.ic_layout_dashboard;"应用"->R.drawable.ic_grid_2x2;"历史"->R.drawable.ic_clock_3;else->R.drawable.ic_settings_2}),null,Modifier.size(24.dp))},label={Text(item)})}}}){padding->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
   if(error.isNotEmpty())item{Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick={execute(JSONObject().put("command","config.get")){config=it;draft=if(pkg.isEmpty())it.toString() else Policy.app(it,pkg).toString();original=draft;revision=it.getLong("revision")}}){Text("重新连接并加载配置")}}
   if(message.isNotEmpty())item{Text(message)}
   if(event!=null){
    val fields=linkedMapOf("time" to "记录时间","package" to "应用","channel" to "通道","stage" to "阶段","source" to "来源","result" to "结果","reason" to "原因","component" to "目标组件","action" to "系统动作","priority" to "投递优先级","originalPriority" to "原始优先级","ttl" to "有效期（秒）","surface" to "点击入口","clickMarker" to "SDK 点击标记","sdkChannel" to "SDK 通道编号","messageKey" to "消息指纹","tokenFingerprint" to "注册指纹","title" to "标题","body" to "正文")
    items(fields.keys.filter{event!!.has(it)}){key->Entry(fields.getValue(key),if(key=="time")stamp(event!!.getLong(key))else state(event!!.get(key).toString()))}
   }else if(pkg.isNotEmpty()){
    item{Text("注册与投递",style=MaterialTheme.typography.titleLarge)}
    val evidence=io.github.wangtong10086.qixin.core.Evidence.summarize(JSONArray(events))
    item{listOf("businessUpload" to "业务服务器注册", "delivery" to "系统投递", "notification" to "通知发布", "clickRoute" to "点击转发").forEach{(key,title)->val e=evidence.getJSONObject(key);Entry(title,(when(e.optString("channel")){"mipush"->"小米 · ";"fcm"->"FCM · ";else->if(e.has("sdkChannel")) "SDK 通道 ${e.optInt("sdkChannel")} · " else ""})+state(e.optString("result"))+" · "+stamp(e.optLong("time")))};Text("转发成功不代表已进入目标页面；实际页面需要单独验证。",style=MaterialTheme.typography.bodySmall)}
    val found=registrations.filter{it.getString("package")==pkg}
    if(found.isEmpty())item{Text("尚未发现注册证据；不代表应用不支持推送。")}
    items(found){r->Entry(if(r.getString("channel")=="fcm")"FCM" else "小米推送",state(r.getString("state"))+" · "+stamp(r.optLong("observedAt")));if(r.has("tokenFingerprint"))Text("注册指纹 ${r.getString("tokenFingerprint").take(16)}",style=MaterialTheme.typography.bodySmall);Text("业务服务器通道选择：尚未确认",style=MaterialTheme.typography.bodySmall)}
    if(draft.isNotEmpty()&&config.has("apps"))item{
     HorizontalDivider();Text("应用配置",style=MaterialTheme.typography.titleLarge)
     listOf("island" to "在通知岛显示", "recordBody" to "记录此应用的标题和正文","deliveryLease" to "投递期间临时放行").forEach{(key,text)->Row(Modifier.fillMaxWidth()){Text(text,Modifier.weight(1f));Switch(JSONObject(draft).optBoolean(key),{draft=JSONObject(draft).put(key,it).toString()},enabled=key=="recordBody"||key=="deliveryLease"||key=="island")}}
     Text("投递豁免需在设置中启用，并使用支持此功能的栖序。声音和振动由系统通知渠道管理。",style=MaterialTheme.typography.bodySmall)
     Button(onClick={save()},enabled=!busy&&draft!=original){Text("保存更改")}
    }
    item{RuntimeApplicationPanel(pkg)}
    if(pkg=="com.ss.android.lark")item{OutlinedButton(onClick={execute(JSONObject().put("command","registration.retry").put("package",pkg)){message="请求已排队，请打开飞书并等待约 40 秒"}}){Text("重新请求小米注册")};Text("调用飞书自身 SDK 注册和上报；不会发送聊天消息。",style=MaterialTheme.typography.bodySmall)}
    if(pkg=="com.ss.android.lark")item{
     val keep=status.optJSONObject("keepalive")?:JSONObject()
     HorizontalDivider();Text("后台连接保活",style=MaterialTheme.typography.titleLarge)
     Text("每 30 秒检查飞书主进程，退出后启动其原生后台服务；半小时最多尝试 3 次。依赖应用自身连接，不代表小米或 FCM 离线通道可用。",style=MaterialTheme.typography.bodySmall)
     Text("启用会在当前栖序方案中允许飞书自启动和后台联网并保留进程；关闭恢复原有规则。不会恢复被你强行停止的应用。",style=MaterialTheme.typography.bodySmall)
     Row(Modifier.fillMaxWidth()){Text("启用保活",Modifier.weight(1f));Switch(keep.optBoolean("enabled"),{enabled->execute(JSONObject().put("command","keepalive.set").put("enabled",enabled).put("expectedRevision",keep.optLong("revision"))){status=JSONObject(status.toString()).put("keepalive",it);message=if(enabled) "已启用后台连接保活" else "已关闭并恢复原策略"}},enabled=!busy)}
     if(keep.optBoolean("restorePending")&&!keep.optBoolean("enabled"))OutlinedButton(onClick={execute(JSONObject().put("command","keepalive.set").put("enabled",false).put("expectedRevision",keep.optLong("revision"))){status=JSONObject(status.toString()).put("keepalive",it)}}){Text("恢复原策略")}
     if(keep.optString("error").isNotEmpty())Text(keep.getString("error"),color=MaterialTheme.colorScheme.error)
    }
    item{HorizontalDivider();Text("最近消息",style=MaterialTheme.typography.titleLarge)}
    items(events){e->Entry(state(e.getString("stage")),stamp(e.getLong("time"))+" · "+state(e.optString("result")),{event=e})}
   }else when(tab){
    "概览"->{
     item{Text(if(status.optBoolean("connected"))"本地服务已连接 · ${state(status.optString("mode"))}" else "等待本地服务",style=MaterialTheme.typography.titleMedium)}
     if(status.has("userUnlocked")&&!status.getBoolean("userUnlocked"))item{Text("首次解锁后会自动刷新应用注册记录。",style=MaterialTheme.typography.bodyMedium)}
     listOf("fcm" to "FCM","mipush" to "小米推送").forEach{(key,title)->item{
      val v=status.optJSONObject("channels")?.optJSONObject(key)
      if(key=="mipush")RuntimeOverview(active) else Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.headlineSmall);Text(state(v?.optString("state")?:"unknown"));Text(stamp(v?.optLong("sampledAt")?:0),style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={execute(JSONObject().put("command","channel.reconnect").put("channel",key)){message="已请求重连，等待通道确认"}},enabled=!busy){Text("请求重连")}}}
     }}
     item{Entry("消息历史","${status.optLong("messages")} 条消息",{tab="历史"});Entry("注册记录","${registrations.size} 条记录",{tab="应用"})}
     for(key in listOf("maintenanceError","importError"))if(status.optString(key).isNotEmpty())item{Text(status.getString(key),color=MaterialTheme.colorScheme.error)}
    }
    "应用"->{
     item{OutlinedTextField(query,{query=it},label={Text("搜索应用名称或包名")},singleLine=true,modifier=Modifier.fillMaxWidth());Row{listOf("" to "全部","fcm" to "FCM","mipush" to "小米推送").forEach{(key,title)->FilterChip(channel==key,{channel=key},label={Text(title)})}}}
     items(apps.filter{(it.getString("label").contains(query,true)||it.getString("package").contains(query,true))&&(channel.isEmpty()||registrations.any{r->r.getString("package")==it.getString("package")&&r.getString("channel")==channel})},key={it.getString("package")}){a->val name=a.getString("package");val registered=registrations.filter{it.getString("package")==name}.map{if(it.getString("channel")=="fcm")"FCM" else "小米"}.distinct();Entry(a.getString("label"),if(registered.isEmpty())"尚无注册记录" else registered.joinToString(" / "),{pkg=name},name)}
    }
    "历史"->{
     item{Row(Modifier.horizontalScroll(rememberScrollState())){listOf("message" to "消息","states" to "运行状态","diagnostic" to "诊断","legacy" to "旧版未分类").forEach{(key,title)->FilterChip(historyKind==key,{historyKind=key},label={Text(title)})}}}
     item{Row{listOf("" to "全部","fcm" to "FCM","mipush" to "小米推送").forEach{(key,title)->FilterChip(channel==key,{channel=key},label={Text(title)})}}}
     val historyRows=if(historyKind=="states")states else events
     if(historyRows.isEmpty())item{Text("此范围尚无记录")}
     items(historyRows){e->val name=e.optString("package");Entry(apps.firstOrNull{it.getString("package")==name}?.getString("label")?:name,stamp(e.getLong("time"))+" · "+state(e.getString("stage")),{event=e},name)}
    }
    "设置"->{if(config.has("mode")&&draft.isNotEmpty()){
     item{
      Text("通知岛",style=MaterialTheme.typography.titleLarge)
      Text("持续任务常驻，普通消息短显；VPN 等后台服务状态不上岛。锁屏时使用系统原通知。",style=MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth()){Text("启用通知岛",Modifier.weight(1f));Switch(Policy.island(JSONObject(draft)).getBoolean("enabled"),{val next=JSONObject(draft);next.put("island",Policy.island(next).put("enabled",it));draft=next.toString()})}
      Text("消息显示 ${Policy.island(JSONObject(draft)).getInt("timeoutSeconds")} 秒")
      Slider(value=Policy.island(JSONObject(draft)).getInt("timeoutSeconds").toFloat(),onValueChange={val next=JSONObject(draft);next.put("island",Policy.island(next).put("timeoutSeconds",it.toInt()));draft=next.toString()},valueRange=1f..30f,steps=28)
      OutlinedButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}){Text("通知读取权限")}
      OutlinedButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+context.packageName)))}){Text("悬浮显示权限")}
      HorizontalDivider()
     }
     item{Text("运行策略",style=MaterialTheme.typography.titleLarge);Row{listOf("observe" to "仅观察","manage" to "管理").forEach{(key,title)->FilterChip(JSONObject(draft).optString("mode")==key,{draft=JSONObject(draft).put("mode",key).toString()},label={Text(title)})}}}
     item{Row{Text("飞书小米注册兼容",Modifier.weight(1f));Switch(JSONObject(draft).optJSONObject("takeover")?.optBoolean("registration")==true,{val next=JSONObject(draft);next.getJSONObject("takeover").put("registration",it);draft=next.toString()})};Text("让飞书按已安装的 XMSF 判断小米推送可用性；不修改全局机型。",style=MaterialTheme.typography.bodySmall)}
     item{Row{Text("推送投递临时放行",Modifier.weight(1f));Switch(JSONObject(draft).optJSONObject("takeover")?.optBoolean("delivery")==true,{val next=JSONObject(draft);next.getJSONObject("takeover").put("delivery",it);draft=next.toString()})};Text("经系统确认的推送到达后，为目标应用放行 30 秒；需要栖序支持。",style=MaterialTheme.typography.bodySmall)}
     item{Row{Text("小米通知点击修复",Modifier.weight(1f));Switch(JSONObject(draft).optJSONObject("takeover")?.optBoolean("clicks")==true,{val next=JSONObject(draft);next.getJSONObject("takeover").put("clicks",it);draft=next.toString()})};Text("仅对用户点击的原始小米消息保留点击标记，并临时放行目标服务 30 秒。",style=MaterialTheme.typography.bodySmall)}
     item{Row{Text("温和自动恢复",Modifier.weight(1f));Switch(JSONObject(draft).optString("recovery")=="soft",{draft=JSONObject(draft).put("recovery",if(it)"soft" else "manual").toString()})};Text("自动恢复不重启整个 GMS／XMSF，不清除注册数据。",style=MaterialTheme.typography.bodySmall);Button(onClick={save()},enabled=!busy&&draft!=original){Text("保存设置")}}
     item{HorizontalDivider();Entry("连接恢复接管",if(config.optJSONObject("takeover")?.optBoolean("connection")==true)"由栖信管理；原连接守护已停用" else "尚未接管，保留现有连接守护");OutlinedButton(onClick={confirm="takeover"},enabled=!busy&&config.optJSONObject("takeover")?.optBoolean("connection")!=true){Text("接管连接恢复")}}
     item{HorizontalDivider();Entry("历史保存","七天 · 默认仅元数据，正文按应用开启");OutlinedButton(onClick={confirm="clear"}){Text("清空本地历史")};Entry("CLI","通过 root 执行 qixin；GUI 与 CLI 使用同一配置。");TextButton(onClick={execute(JSONObject().put("command","refresh")){message="已重新读取注册和通道状态"}}){Text("刷新注册与诊断")}}
    }}
   }
  }
 }
 if(confirm.isNotEmpty())AlertDialog(onDismissRequest={confirm=""},title={Text(if(confirm=="takeover")"接管连接恢复？" else if(confirm=="clear")"清空七天历史？" else "放弃未保存的更改？")},text={Text(if(confirm=="takeover")"将备份并停用旧 FCM 连接守护，使用栖信的温和重连；注册桥接和通知组件保持启用。" else if(confirm=="clear")"不会删除注册信息或改变推送配置。" else "已保存的配置保持不变。")},dismissButton={TextButton(onClick={confirm=""}){Text("取消")}},confirmButton={TextButton(onClick={val action=confirm;confirm="";if(action=="takeover")execute(JSONObject().put("command","connection.takeover")){execute(JSONObject().put("command","config.get")){config=it;draft=it.toString();original=draft;revision=it.getLong("revision");message="连接恢复已接管"}}else if(action=="clear")execute(JSONObject().put("command","history.clear")){events=emptyList();message="历史已清空"}else{draft=original;pkg="";tab="概览"}}){Text("确认")}})
}
