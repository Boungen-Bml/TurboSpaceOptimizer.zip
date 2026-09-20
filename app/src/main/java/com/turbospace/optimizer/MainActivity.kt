package com.turbospace.optimizer

import android.app.*
import android.content.*
import android.content.pm.*
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { TurboSpaceHub() } }
}

object TurboColors {
    val Red=Color(0xffff1a1a); val Glow=Color(0xffff4d4d); val Dark=Color(0xff0f0e13)
    val Card=Color(0xff1e1c24); val Border=Color(0xff3d3a46); val Gray=Color(0xffa09daa)
}

object TurboSpaceManager {
    private const val PREFS="TurboSpaceState"
    private const val DELIM="||"
    private const val TIMEOUT=10000L
    private const val COMPILE_TIMEOUT=120000L
    enum class Outcome { PRIMARY_SUCCESS, FALLBACK_SUCCESS, BOTH_FAILED }
    enum class CompileMode(val filter:String,val label:String) { SPACE_SAVING("space","Space Saving"), FULL_SPEED("everything","Compile Everything"), PROFILE_BASED("speed-profile","Usage-Based Compile") }
    data class Saved(val network:Set<String>,val cpu:Set<String>,val gpu:String,val overlay:String,val net:Boolean,val cpuOn:Boolean,val gpuOn:Boolean,val perf:Boolean,val mode:String)
    private fun prefs(c:Context)=c.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    private fun readSet(p:android.content.SharedPreferences,key:String,oldKey:String):Set<String>{
        val raw=p.getString(key,null) ?: p.getString(oldKey,"") ?: ""
        return if(raw.isBlank()) emptySet() else raw.split(DELIM).filter(String::isNotBlank).toSet()
    }
    fun load(c:Context):Saved { val p=prefs(c); return Saved(readSet(p,"NETWORK_APPS","NETWORK_APP"),readSet(p,"CPU_APPS","CPU_APP"),p.getString("GPU_GAME","NO TARGET SELECTED")?:"NO TARGET SELECTED",p.getString("OVERLAY_GAME","NO TARGET SELECTED")?:"NO TARGET SELECTED",p.getBoolean("NET_ACTIVE",false),p.getBoolean("CPU_ACTIVE",false),p.getBoolean("GPU_ACTIVE",false),p.getBoolean("IS_PERFORMANCE_ACTIVE",false),p.getString("COMPILE_MODE",CompileMode.PROFILE_BASED.name)?:CompileMode.PROFILE_BASED.name) }
    fun save(c:Context,network:Set<String>,cpu:Set<String>,gpu:String,overlay:String,net:Boolean,cpuOn:Boolean,gpuOn:Boolean,perf:Boolean,mode:String){prefs(c).edit().putString("NETWORK_APPS",network.joinToString(DELIM)).putString("CPU_APPS",cpu.joinToString(DELIM)).putString("GPU_GAME",gpu).putString("OVERLAY_GAME",overlay).putBoolean("NET_ACTIVE",net).putBoolean("CPU_ACTIVE",cpuOn).putBoolean("GPU_ACTIVE",gpuOn).putBoolean("IS_PERFORMANCE_ACTIVE",perf).putString("COMPILE_MODE",mode).apply()}
    fun granted()=try{Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}catch(_:Exception){false}
    fun requestPermission(){try{if(Shizuku.pingBinder()&&Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)Shizuku.requestPermission(1001)}catch(_:Exception){}}
    fun listener(cb:(Boolean)->Unit):Shizuku.OnRequestPermissionResultListener{val l=Shizuku.OnRequestPermissionResultListener{code,result->if(code==1001)cb(result==PackageManager.PERMISSION_GRANTED)};Shizuku.addRequestPermissionResultListener(l);return l}
    fun remove(l:Shizuku.OnRequestPermissionResultListener)=Shizuku.removeRequestPermissionResultListener(l)
    fun process(cmd:Array<String>):Process {val m=Shizuku::class.java.getDeclaredMethod("newProcess",Array<String>::class.java,Array<String>::class.java,String::class.java);m.isAccessible=true;return m.invoke(null,cmd,null,null) as Process}
    private suspend fun run(cmd:String,timeout:Long=TIMEOUT)=withContext(Dispatchers.IO){var p:Process?=null;try{p=process(arrayOf("sh","-c",cmd));val e=withTimeoutOrNull(timeout){p.waitFor()};if(e==null){p.destroy();false}else e==0}catch(_:Exception){p?.destroy();false}}
    suspend fun exec(primary:String,fallback:String="",timeout:Long=TIMEOUT)=withContext(Dispatchers.IO){if(!granted())return@withContext Outcome.BOTH_FAILED;if(run(primary,timeout))Outcome.PRIMARY_SUCCESS else if(fallback.isNotBlank()&&run(fallback,timeout))Outcome.FALLBACK_SUCCESS else Outcome.BOTH_FAILED}
    fun combine(vararg o:Outcome)=when{ o.any{it==Outcome.BOTH_FAILED}->Outcome.BOTH_FAILED;o.any{it==Outcome.FALLBACK_SUCCESS}->Outcome.FALLBACK_SUCCESS;else->Outcome.PRIMARY_SUCCESS }
    fun toast(c:Context,o:Outcome){Toast.makeText(c,when(o){Outcome.PRIMARY_SUCCESS->"Success";Outcome.FALLBACK_SUCCESS->"Success 2";Outcome.BOTH_FAILED->"Error - Error 2"},Toast.LENGTH_SHORT).show()}
    suspend fun network(pkg:String)=if(pkg.isBlank())Outcome.BOTH_FAILED else exec("cmd netpolicy add restrict-background-whitelist $pkg")
    suspend fun cpu(pkg:String)=if(pkg.isBlank())Outcome.BOTH_FAILED else exec("cmd activity set-inactive $pkg true","cmd appops set $pkg RUN_IN_BACKGROUND deny")
    suspend fun restore(pkg:String)=if(pkg.isBlank())Outcome.BOTH_FAILED else combine(exec("cmd activity set-inactive $pkg false","cmd appops set $pkg RUN_IN_BACKGROUND allow"),exec("cmd netpolicy remove restrict-background-whitelist $pkg","cmd appops set $pkg RUN_IN_BACKGROUND default"))
    suspend fun gpu(pkg:String)=if(pkg=="NO TARGET SELECTED")Outcome.BOTH_FAILED else exec("cmd game downscale $pkg 0.9")
    suspend fun restoreGpu(pkg:String)=if(pkg=="NO TARGET SELECTED")Outcome.BOTH_FAILED else exec("cmd game downscale reset $pkg","cmd game downscale $pkg 1.0")
    suspend fun power(on:Boolean)=exec(if(on)"cmd power set-mode 2" else "cmd power set-mode 0",if(on)"cmd power set-fixed-performance-mode-enabled true" else "cmd power set-fixed-performance-mode-enabled false")
    suspend fun compile(pkg:String,mode:CompileMode)=if(pkg=="NO TARGET SELECTED")Outcome.BOTH_FAILED else exec("cmd package compile -m ${mode.filter} -f $pkg","pm compile -m ${mode.filter} -f $pkg",COMPILE_TIMEOUT)
    suspend fun clearCache()=exec("cmd package trim-caches 512M","pm trim-caches 512M")
}

object TurboSpaceRepository {
    private val _network=MutableStateFlow<Set<String>>(emptySet()); val network=_network.asStateFlow()
    private val _cpu=MutableStateFlow<Set<String>>(emptySet()); val cpu=_cpu.asStateFlow()
    private val _gpu=MutableStateFlow("NO TARGET SELECTED"); val gpu=_gpu.asStateFlow()
    private val _overlay=MutableStateFlow("NO TARGET SELECTED"); val overlay=_overlay.asStateFlow()
    val netActive=MutableStateFlow(false); val cpuActive=MutableStateFlow(false); val gpuActive=MutableStateFlow(false); val performance=MutableStateFlow(false); val service=MutableStateFlow(false)
    val netLoading=MutableStateFlow(false); val cpuLoading=MutableStateFlow(false); val gpuLoading=MutableStateFlow(false); val perfLoading=MutableStateFlow(false); val clearing=MutableStateFlow(false); val resetting=MutableStateFlow(false); val compiling=MutableStateFlow(false)
    val compileMode=MutableStateFlow(TurboSpaceManager.CompileMode.PROFILE_BASED)
    fun restore(c:Context){val s=TurboSpaceManager.load(c);_network.value=s.network;_cpu.value=s.cpu;_gpu.value=s.gpu;_overlay.value=s.overlay;netActive.value=s.net;cpuActive.value=s.cpuOn;gpuActive.value=s.gpuOn;performance.value=s.perf;compileMode.value=runCatching{TurboSpaceManager.CompileMode.valueOf(s.mode)}.getOrDefault(TurboSpaceManager.CompileMode.PROFILE_BASED)}
    private fun save(c:Context)=TurboSpaceManager.save(c,_network.value,_cpu.value,_gpu.value,_overlay.value,netActive.value,cpuActive.value,gpuActive.value,performance.value,compileMode.value.name)
    fun setNetwork(c:Context,v:Set<String>){_network.value=v;save(c)}; fun setCpu(c:Context,v:Set<String>){_cpu.value=v;save(c)}; fun setGpu(c:Context,v:String){_gpu.value=v;save(c)}; fun setOverlay(c:Context,v:String){_overlay.value=v;save(c)}
    suspend fun startNetwork(c:Context){netLoading.value=true;try{val o=TurboSpaceManager.combine(*_network.value.map{TurboSpaceManager.network(it)}.toTypedArray());TurboSpaceManager.toast(c,o);netActive.value=o!=TurboSpaceManager.Outcome.BOTH_FAILED;save(c)}finally{netLoading.value=false}}
    suspend fun startCpu(c:Context){cpuLoading.value=true;try{val o=TurboSpaceManager.combine(*_cpu.value.map{TurboSpaceManager.cpu(it)}.toTypedArray());TurboSpaceManager.toast(c,o);cpuActive.value=o!=TurboSpaceManager.Outcome.BOTH_FAILED;save(c)}finally{cpuLoading.value=false}}
    suspend fun startGpu(c:Context){gpuLoading.value=true;try{val o=TurboSpaceManager.gpu(_gpu.value);TurboSpaceManager.toast(c,o);gpuActive.value=o!=TurboSpaceManager.Outcome.BOTH_FAILED;save(c)}finally{gpuLoading.value=false}}
    suspend fun setPower(c:Context,on:Boolean){perfLoading.value=true;try{val o=TurboSpaceManager.power(on);TurboSpaceManager.toast(c,o);if(o!=TurboSpaceManager.Outcome.BOTH_FAILED)performance.value=on;save(c)}finally{perfLoading.value=false}}
    suspend fun clearCache(c:Context){clearing.value=true;try{TurboSpaceManager.toast(c,TurboSpaceManager.clearCache())}finally{clearing.value=false}}
    suspend fun reset(c:Context){resetting.value=true;try{val a=(_network.value+_cpu.value).map{TurboSpaceManager.restore(it)};val o=TurboSpaceManager.combine(*(a+listOf(TurboSpaceManager.restoreGpu(_gpu.value),TurboSpaceManager.power(false))).toTypedArray());TurboSpaceManager.toast(c,o);netActive.value=false;cpuActive.value=false;gpuActive.value=false;performance.value=false;save(c)}finally{resetting.value=false}}
}

class GameSpaceOverlayService:LifecycleService(),SavedStateRegistryOwner,ViewModelStoreOwner{
    private lateinit var wm:WindowManager; private lateinit var view:ComposeView; private val saved=SavedStateRegistryController.create(this); private val store=ViewModelStore()
    override val savedStateRegistry get()=saved.savedStateRegistry; override val viewModelStore get()=store
    override fun onCreate(){super.onCreate();saved.performRestore(null);startForegroundNotification();TurboSpaceRepository.service.value=true;wm=getSystemService(WINDOW_SERVICE) as WindowManager;view=ComposeView(this).apply{setViewTreeLifecycleOwner(this@GameSpaceOverlayService);setViewTreeViewModelStoreOwner(this@GameSpaceOverlayService);setViewTreeSavedStateRegistryOwner(this@GameSpaceOverlayService);setContent{GameSpaceSidebar()}};val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE;val p=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.START or Gravity.CENTER_VERTICAL};runCatching{wm.addView(view,p)}}
    private fun startForegroundNotification(){val id="turbo_overlay_channel";val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager;if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(id,"Turbo Overlay Service",NotificationManager.IMPORTANCE_LOW));val n=NotificationCompat.Builder(this,id).setContentTitle("Turbo Engine Active").setContentText("Game overlay is running").setSmallIcon(android.R.drawable.ic_dialog_info).setPriority(NotificationCompat.PRIORITY_LOW).build();if(Build.VERSION.SDK_INT>=34)startForeground(1001,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)else startForeground(1001,n)}
    override fun onDestroy(){TurboSpaceRepository.service.value=false;runCatching{wm.removeView(view)};store.clear();super.onDestroy()}
}

@Composable fun TurboSpaceHub(){val c=LocalContext.current;val scope=rememberCoroutineScope();var ready by remember{mutableStateOf(TurboSpaceManager.granted())};var picker by remember{mutableStateOf<String?>(null)};var compilePicker by remember{mutableStateOf(false)};var compileMode by remember{mutableStateOf(false)};var target by remember{mutableStateOf("NO TARGET SELECTED")};LaunchedEffect(Unit){TurboSpaceRepository.restore(c)};DisposableEffect(Unit){val l=TurboSpaceManager.listener{ready=it};onDispose{TurboSpaceManager.remove(l)}};val network by TurboSpaceRepository.network.collectAsState();val cpu by TurboSpaceRepository.cpu.collectAsState();val gpu by TurboSpaceRepository.gpu.collectAsState();val overlay by TurboSpaceRepository.overlay.collectAsState();val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ready=TurboSpaceManager.granted()};Column(Modifier.fillMaxSize().background(TurboColors.Dark).padding(16.dp)){Text("TURBO SPACE",color=TurboColors.Red,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(if(ready)"Shizuku: Connected" else "Shizuku: Not Connected",color=TurboColors.Gray);if(!ready)Button({TurboSpaceManager.requestPermission()}){Text("Grant Shizuku Permission")};Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxSize()){Column(Modifier.weight(.35f)){IconButton({picker="overlay"},Modifier.size(78.dp)){if(overlay=="NO TARGET SELECTED")Icon(Icons.Default.Add,null,tint=Color.White)else AppIcon(overlay)};Button({if(Settings.canDrawOverlays(c)){c.startActivity(c.packageManager.getLaunchIntentForPackage(overlay));ContextCompat.startForegroundService(c,Intent(c,GameSpaceOverlayService::class.java))}else permission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}")))},enabled=ready&&overlay!="NO TARGET SELECTED"&& !TurboSpaceRepository.service.collectAsState().value){Text("Start Game Overlay")};OutlinedButton({c.stopService(Intent(c,GameSpaceOverlayService::class.java))}){Text("Stop Overlay")}};Spacer(Modifier.width(12.dp));LazyColumn(Modifier.weight(.65f),verticalArrangement=Arrangement.spacedBy(8.dp)){item{MultiCard("Network Optimization",network,TurboSpaceRepository.netActive.collectAsState().value,TurboSpaceRepository.netLoading.collectAsState().value){picker="network"}{scope.launch{TurboSpaceRepository.startNetwork(c)}}};item{MultiCard("CPU Optimization",cpu,TurboSpaceRepository.cpuActive.collectAsState().value,TurboSpaceRepository.cpuLoading.collectAsState().value){picker="cpu"}{scope.launch{TurboSpaceRepository.startCpu(c)}}};item{SingleCard("GPU Optimization",gpu,TurboSpaceRepository.gpuActive.collectAsState().value,TurboSpaceRepository.gpuLoading.collectAsState().value){picker="gpu"}{scope.launch{TurboSpaceRepository.startGpu(c)}}};item{Button({compilePicker=true},enabled=ready&&!TurboSpaceRepository.compiling.collectAsState().value){Text("Game Compile (ART AOT)")}};item{Button({scope.launch{TurboSpaceRepository.reset(c)}},enabled=!TurboSpaceRepository.resetting.collectAsState().value){Text("Reset All")}}}}};if(picker!=null){if(picker=="network"||picker=="cpu")MultiPicker(picker=="network",if(picker=="network")network else cpu,{picker=null}){v->if(picker=="network")TurboSpaceRepository.setNetwork(c,v)else TurboSpaceRepository.setCpu(c,v);picker=null}}else if(picker=="gpu"||picker=="overlay")SinglePicker(true,if(picker=="gpu")gpu else overlay,{picker=null}){v->if(picker=="gpu")TurboSpaceRepository.setGpu(c,v)else TurboSpaceRepository.setOverlay(c,v);picker=null}};if(compilePicker)SinglePicker(true,"NO TARGET SELECTED",{compilePicker=false}){target=it;compilePicker=false;compileMode=true};if(compileMode)CompileDialog(target,TurboSpaceRepository.compileMode.collectAsState().value,{TurboSpaceRepository.compileMode.value=it},{compileMode=false;scope.launch{TurboSpaceRepository.compiling.value=true;TurboSpaceManager.toast(c,TurboSpaceManager.compile(target,TurboSpaceRepository.compileMode.value));TurboSpaceRepository.compiling.value=false}},{compileMode=false})}
}

@Composable fun AppIcon(pkg:String){val c=LocalContext.current;val b=remember(pkg){runCatching{c.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()}.getOrNull()};if(b!=null)Image(b,null,Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))else Icon(Icons.Default.SportsEsports,null,tint=Color.White)}
@Composable fun label(pkg:String)=remember(pkg){runCatching{LocalContext.current.packageManager.getApplicationLabel(LocalContext.current.packageManager.getApplicationInfo(pkg,0)).toString()}.getOrDefault(pkg)}
@Composable fun MultiCard(title:String,apps:Set<String>,active:Boolean,loading:Boolean,pick:()->Unit,start:()->Unit){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(active)Color(0xff4a0000) else TurboColors.Card)){Column(Modifier.padding(12.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(if(apps.isEmpty())"Select apps..." else "${apps.size} apps selected",color=TurboColors.Gray,modifier=Modifier.clickable{pick()}.padding(vertical=10.dp));Button(start,enabled=apps.isNotEmpty()&&!loading){Text(if(loading)"Starting..." else "Start")}}}}
@Composable fun SingleCard(title:String,pkg:String,active:Boolean,loading:Boolean,pick:()->Unit,start:()->Unit){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(active)Color(0xff4a0000) else TurboColors.Card)){Column(Modifier.padding(12.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(if(pkg=="NO TARGET SELECTED")"Select game..." else label(pkg),color=Color.White,modifier=Modifier.clickable{pick()}.padding(vertical=10.dp));Button(start,enabled=pkg!="NO TARGET SELECTED"&&!loading){Text(if(loading)"Starting..." else "Start")}}}}

fun installedApps(c:Context,games:Boolean):List<ApplicationInfo>{val pm=c.packageManager;return pm.getInstalledApplications(PackageManager.GET_META_DATA).filter{it.flags and ApplicationInfo.FLAG_SYSTEM==0}.filter{!games||it.category==ApplicationInfo.CATEGORY_GAME||(it.flags and ApplicationInfo.FLAG_IS_GAME)!=0}.sortedBy{pm.getApplicationLabel(it).toString()}}
@Composable fun MultiPicker(network:Boolean,selected:Set<String>,dismiss:()->Unit,done:(Set<String>)->Unit){val c=LocalContext.current;var q by remember{mutableStateOf("")};var values by remember{mutableStateOf(selected)};val apps=remember(network){installedApps(c,!network)};AlertDialog(onDismissRequest=dismiss,title={Text(if(network)"Select Apps" else "Select Games")},confirmButton={TextButton({done(values)}){Text("Apply")}},dismissButton={TextButton(dismiss){Text("Cancel")}},text={Column{OutlinedTextField(q,{q=it},placeholder={Text("Search...")},singleLine=true);LazyColumn(Modifier.heightIn(max=360.dp)){items(apps.filter{c.packageManager.getApplicationLabel(it).toString().contains(q,true)}){a->Row(Modifier.fillMaxWidth().clickable{values=if(a.packageName in values)values-a.packageName else values+a.packageName}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(a.packageName in values,null);AppIcon(a.packageName);Spacer(Modifier.width(8.dp));Text(c.packageManager.getApplicationLabel(a).toString())}}}}})}
@Composable fun SinglePicker(games:Boolean,selected:String,dismiss:()->Unit,done:(String)->Unit){val c=LocalContext.current;val apps=remember{installedApps(c,games)};AlertDialog(onDismissRequest=dismiss,title={Text("Select Game")},confirmButton={TextButton(dismiss){Text("Cancel")}},text={LazyColumn(Modifier.heightIn(max=400.dp)){items(apps){a->Row(Modifier.fillMaxWidth().clickable{done(a.packageName)}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){AppIcon(a.packageName);Spacer(Modifier.width(8.dp));Text(c.packageManager.getApplicationLabel(a).toString())}}}})}
@Composable fun CompileDialog(pkg:String,mode:TurboSpaceManager.CompileMode,select:(TurboSpaceManager.CompileMode)->Unit,ok:()->Unit,cancel:()->Unit){AlertDialog(onDismissRequest=cancel,title={Text("Compile Game")},text={Column{TurboSpaceManager.CompileMode.values().forEach{m->Row(Modifier.fillMaxWidth().clickable{select(m)}.padding(6.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(m==mode){select(m)};Text(m.label)}}}},confirmButton={TextButton(ok){Text("OK")}},dismissButton={TextButton(cancel){Text("Cancel")}})}

@Composable fun GameSpaceSidebar(){val c=LocalContext.current;var open by remember{mutableStateOf(false)};var menu by remember{mutableStateOf(false)};val service by TurboSpaceRepository.service.collectAsState();if(!service)return;Box(Modifier.padding(8.dp)){if(!open){IconButton({open=true},Modifier.size(72.dp).background(Color(0xee1e1c24),RoundedCornerShape(18.dp))){val pkg=TurboSpaceRepository.overlay.collectAsState().value;if(pkg=="NO TARGET SELECTED")Icon(Icons.Default.FlashOn,"Open Game Space",tint=TurboColors.Glow)else AppIcon(pkg)}}else{Card(Modifier.width(190.dp),colors=CardDefaults.cardColors(containerColor=Color(0xee17151d)),border=BorderStroke(1.dp,TurboColors.Red)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("TURBO SPACE",color=TurboColors.Glow,fontWeight=FontWeight.Bold);IconButton({open=false}){Icon(Icons.Default.Close,null,tint=Color.White)}};Text("Balance / Performance",color=Color.White);Button({menu=true},Modifier.fillMaxWidth()){Icon(Icons.Default.RocketLaunch,null);Spacer(Modifier.width(6.dp));Text(if(TurboSpaceRepository.performance.collectAsState().value)"Performance" else "Balance")};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Balance Mode")},{menu=false;CoroutineScope(Dispatchers.Main).launch{TurboSpaceRepository.setPower(c,false)}});DropdownMenuItem({Text("Performance Mode")},{menu=false;CoroutineScope(Dispatchers.Main).launch{TurboSpaceRepository.setPower(c,true)}})};Divider(Modifier.padding(vertical=8.dp));Text("FPS  --",color=Color.White);Text("CPU  --",color=Color.White);Text("RAM  --",color=Color.White);Spacer(Modifier.height(8.dp));Button({CoroutineScope(Dispatchers.Main).launch{TurboSpaceRepository.clearCache(c)}},Modifier.fillMaxWidth()){Icon(Icons.Default.FlashOn,null);Spacer(Modifier.width(4.dp));Text("ล้างแคช")};IconButton({open=false}){Icon(Icons.Default.Refresh,"Close Game Space",tint=TurboColors.Glow)}}}}}}
