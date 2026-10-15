package tiiehenry.viewcontroller.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import androidx.compose.runtime.Immutable
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import tiiehenry.io.Filej
import tiiehenry.viewcontroller.IObserver
import tiiehenry.viewcontroller.R
import tiiehenry.viewcontroller.backup.RuleExporter
import tiiehenry.viewcontroller.backup.RuleImporter
import tiiehenry.viewcontroller.injection.bridge.GodModeManager
import tiiehenry.viewcontroller.repository.RemoteRepository
import tiiehenry.viewcontroller.rule.ActRules
import tiiehenry.viewcontroller.rule.AppRules
import tiiehenry.viewcontroller.rule.ViewRule
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

/**
 * 应用信息条目（Compose 用不可变数据）
 */
@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * 群组信息条目
 */
@Immutable
data class GroupEntry(
    val name: String,
    val link: String,
)

/**
 * 现代化 ViewModel：以 StateFlow/LiveData + suspend/回调混合方式驱动 Compose UI。
 * 取代旧的 SharedViewModel（LiveData + ExecutorService）。
 */
class GmViewModel(application: Application) : AndroidViewModel(application) {

    private val _appRules = MutableLiveData<AppRules>(AppRules())
    val appRules: LiveData<AppRules> = _appRules

    private val _viewRules = MutableLiveData<List<ViewRule>>(emptyList())
    val viewRules: LiveData<List<ViewRule>> = _viewRules

    var selectedPackage: String? = null
        private set

    /** 选中应用的展示信息（图标 / 名称） */
    var selectedAppEntry: AppEntry? = null
        private set

    private val observer = object : IObserver.Stub() {
        override fun onEditModeChanged(enable: Boolean) {}

        override fun onAppStatusChanged(enable: Boolean) {}

        @Throws(android.os.RemoteException::class)
        override fun onViewRuleChanged(packageName: String?, actRules: ActRules?) {
            reloadRules()
            if (TextUtils.equals(packageName, selectedPackage)) {
                refreshSelectedPackageView()
            }
        }
    }

    init {
        runCatching { GodModeManager.getDefault().addObserver("*", observer) }
        reloadRules()
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { GodModeManager.getDefault().removeObserver("*", observer) }
    }

    fun reloadRules() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = runCatching { GodModeManager.getDefault().allRules }.getOrNull() ?: AppRules()
            _appRules.postValue(rules)
        }
    }

    // ---------------- 应用列表 ----------------

    fun buildAppEntries(): List<AppEntry> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val rules = _appRules.value ?: AppRules()
        return rules.entries.map { (packageName, _) ->
            val entry = runCatching {
                val aInfo = pm.getApplicationInfo(packageName, 0)
                AppEntry(packageName, aInfo.loadLabel(pm).toString(), aInfo.loadIcon(pm))
            }.getOrElse {
                AppEntry(
                    packageName,
                    packageName,
                    ResourcesCompat.getDrawable(context.resources, R.mipmap.ic_god, context.theme),
                )
            }
            entry
        }
    }

    fun selectApp(entry: AppEntry) {
        selectedPackage = entry.packageName
        selectedAppEntry = entry
        refreshSelectedPackageView()
    }

    fun refreshSelectedPackageView() {
        val pkg = selectedPackage ?: return
        val appRules = _appRules.value ?: return
        val actRules = appRules[pkg]
        val list = ArrayList<ViewRule>()
        actRules?.values?.forEach { list.addAll(it) }
        // 按生成时间排序
        Collections.sort(list) { o1, o2 -> (o1.timestamp - o2.timestamp).toInt() }
        _viewRules.postValue(list)
    }

    // ---------------- 规则操作 ----------------

    fun isAppDisabled(): Boolean =
        selectedPackage?.let { GodModeManager.getDefault().isAppDisabled(it) } ?: false

    fun setAppEnable(enable: Boolean) {
        selectedPackage?.let { GodModeManager.getDefault().setAppEnable(it, enable) }
        refreshSelectedPackageView()
    }

    fun updateRule(rule: ViewRule): Boolean =
        GodModeManager.getDefault().updateRule(rule.packageName, rule)

    fun deleteRule(rule: ViewRule): Boolean =
        GodModeManager.getDefault().deleteRule(rule.packageName, rule)

    fun deleteAppRules(): Boolean {
        val pkg = selectedPackage ?: return false
        return GodModeManager.getDefault().deleteRules(pkg)
    }

    // ---------------- 备份 / 恢复 ----------------

    fun exportAllRules(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val result = runCatching {
                val dir = RuleExporter.getExportDir(context)
                val file = RuleExporter.exportAllRules(dir.path, _appRules.value ?: AppRules())
                ShareUtilForVm.share(context, file)
                dir.path
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun exportSelectedRules(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val rules = _viewRules.value ?: emptyList()
            val result = runCatching {
                val dir = RuleExporter.getExportDir(context)
                RuleExporter.exportRules(dir.path, rules)
                dir.path
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun importExternalRules(uri: Uri, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val file = File(context.cacheDir, "app.gm")
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        Filej.copyStream(input, output)
                        output.flush()
                    }
                }
                check(RuleImporter.importRules(file)) { "import fail" }
            }
            file.delete()
            if (result.isSuccess) reloadRules()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    // ---------------- 群组信息 ----------------

    fun fetchGroupInfo(onResult: (Result<List<GroupEntry>>) -> Unit) {
        RemoteRepository.fetchGroupInfo(object : Callback<Array<Map<String, String>>> {
            override fun onResponse(
                call: Call<Array<Map<String, String>>>,
                response: Response<Array<Map<String, String>>>,
            ) {
                if (!response.isSuccessful) {
                    onResult(Result.failure(Exception("not successful ${response.code()}")))
                    return
                }
                val body = response.body() ?: arrayOf()
                onResult(Result.success(body.map {
                    GroupEntry(it["group_name"] ?: "", it["group_link"] ?: "")
                }))
            }

            override fun onFailure(call: Call<Array<Map<String, String>>>, t: Throwable) {
                onResult(Result.failure(t))
            }
        })
    }

    // ---------------- 桌面图标显隐 ----------------

    fun setIconHidden(hidden: Boolean) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val cmp = ComponentName(context.packageName, ALIAS_ACTIVITY)
        pm.setComponentEnabledSetting(
            cmp,
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun isIconHidden(): Boolean {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val cmp = ComponentName(context.packageName, ALIAS_ACTIVITY)
        return pm.getComponentEnabledSetting(cmp) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    // ---------------- 规则截图预览 ----------------

    fun loadRulePreview(rule: ViewRule, onLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                GodModeManager.getDefault().openImageFileDescriptor(rule.imagePath)?.use { pfd: ParcelFileDescriptor ->
                    BitmapFactoryDecode.decode(pfd)?.let { src ->
                        val copy = src.copy(Bitmap.Config.ARGB_8888, true) ?: src
                        val canvas = Canvas(copy)
                        val paint = Paint().apply {
                            color = Color.RED
                            alpha = 100
                        }
                        canvas.drawRect(
                            Rect(rule.x, rule.y, rule.x + rule.width, rule.y + rule.height), paint,
                        )
                        copy
                    }
                }
            }.getOrNull()
            withContext(Dispatchers.Main) { onLoaded(bitmap) }
        }
    }

    companion object {
        private const val ALIAS_ACTIVITY = "tiiehenry.viewcontroller.SettingsAliasActivity"
    }
}

private object BitmapFactoryDecode {
    fun decode(pfd: ParcelFileDescriptor): Bitmap? =
        android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
}

private object ShareUtilForVm {
    fun share(context: Context, file: File) {
        runCatching { tiiehenry.viewcontroller.util.ShareUtil.share(context, file) }
    }
}
