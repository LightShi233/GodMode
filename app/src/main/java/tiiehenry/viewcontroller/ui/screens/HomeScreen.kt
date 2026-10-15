package tiiehenry.viewcontroller.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Donate
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Export
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tiiehenry.viewcontroller.CrashHandler
import tiiehenry.viewcontroller.GodModeApplication
import tiiehenry.viewcontroller.R
import tiiehenry.viewcontroller.injection.bridge.GodModeManager
import tiiehenry.viewcontroller.rule.AppRules
import tiiehenry.viewcontroller.ui.GmViewModel
import tiiehenry.viewcontroller.ui.components.LoadingDialog
import tiiehenry.viewcontroller.ui.components.SingleChoiceDialog
import tiiehenry.viewcontroller.util.Clipboard
import tiiehenry.viewcontroller.util.DonateHelper
import tiiehenry.viewcontroller.util.XposedEnvironment

/**
 * 主页：设置项 + 已生成规则的应用列表（取代 GeneralPreferenceFragment）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GmViewModel,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val appRules by viewModel.appRules.observeAsState(AppRules())
    var editorEnabled by remember { mutableStateOf(GodModeManager.getDefault().isInEditMode) }
    var iconHidden by remember { mutableStateOf(viewModel.isIconHidden()) }

    // 对话框状态
    var showCrashDialog by remember { mutableStateOf(false) }
    var crashInfo by remember { mutableStateOf<String?>(null) }
    var showEnableModuleDialog by remember { mutableStateOf(false) }
    var showUpdatePolicyDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showGroupLoading by remember { mutableStateOf(false) }
    var groupEntries by remember { mutableStateOf<List<tiiehenry.viewcontroller.ui.GroupEntry>?>(null) }
    var backupLoading by remember { mutableStateOf(false) }
    var importLoading by remember { mutableStateOf(false) }

    // SAF 文件选择器（取代已废弃的 GetContent + 存储权限）
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importLoading = true
            viewModel.importExternalRules(uri) { result ->
                importLoading = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            if (result.isSuccess) R.string.import_success else R.string.import_failed,
                        ),
                    )
                }
            }
        }
    }

    // 首次进入：崩溃检查 / 版本更新提示 / 模块激活检查（对应旧 onCreatePreferences 逻辑）
    LaunchedEffect(Unit) {
        val info = CrashHandler.getLastCrashInfo(GodModeApplication.getApplication())
        if (info != null) {
            crashInfo = info
            showCrashDialog = true
        } else {
            val sp = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val previous = sp.getInt("version_code", 0)
            if (previous != tiiehenry.viewcontroller.BuildConfig.VERSION_CODE) {
                sp.edit().putInt("version_code", tiiehenry.viewcontroller.BuildConfig.VERSION_CODE).apply()
                showUpdatePolicyDialog = true
            } else if (!XposedEnvironment.XposedType.UNKNOWN.isModuleActive(context)) {
                showEnableModuleDialog = true
            }
        }
        viewModel.reloadRules()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_title_god_mode)) },
                actions = {
                    // 原 menu_general：导入规则 / 图标显隐
                    IconButton(onClick = { restoreLauncher.launch(arrayOf("application/zip")) }) {
                        Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.menu_title_import_view_rule))
                    }
                    IconButton(onClick = {
                        iconHidden = !iconHidden
                        viewModel.setIconHidden(iconHidden)
                    }) {
                        Icon(
                            if (iconHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(
                                if (iconHidden) R.string.menu_icon_switch_show else R.string.menu_icon_switch_hide,
                            ),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            item {
                SwitchPreferenceRow(
                    title = stringResource(R.string.pref_title_god_mode),
                    summary = stringResource(R.string.pref_description_god_mode),
                    checked = editorEnabled,
                    onCheckedChange = { checked ->
                        if (!XposedEnvironment.XposedType.UNKNOWN.isModuleActive(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.not_active_module))
                            }
                            return@SwitchPreferenceRow
                        }
                        editorEnabled = checked
                        GodModeManager.getDefault().setEditMode(checked)
                    },
                )
            }
            item {
                ActionPreferenceRow(
                    icon = { Icon(Icons.Filled.Export, contentDescription = null) },
                    title = stringResource(R.string.pref_title_backup),
                    summary = stringResource(R.string.pref_description_backup),
                    onClick = {
                        backupLoading = true
                        viewModel.exportAllRules { result ->
                            backupLoading = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    result.getOrNull()?.let {
                                        context.getString(R.string.export_successful, it)
                                    } ?: context.getString(R.string.export_failed),
                                )
                            }
                        }
                    },
                )
            }
            item {
                ActionPreferenceRow(
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    title = stringResource(R.string.pref_title_restore),
                    summary = stringResource(R.string.pref_description_restore),
                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) },
                )
            }
            item {
                ActionPreferenceRow(
                    icon = { Icon(Icons.Filled.Group, contentDescription = null) },
                    title = stringResource(R.string.pref_title_add_group),
                    summary = stringResource(R.string.pref_description_add_group),
                    onClick = {
                        showGroupLoading = true
                        viewModel.fetchGroupInfo { result ->
                            showGroupLoading = false
                            result.onSuccess {
                                groupEntries = it
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "获取群组信息失败: ${it.message}",
                                    )
                                }
                            }
                        }
                    },
                )
            }
            item {
                ActionPreferenceRow(
                    icon = { Icon(Icons.Filled.Donate, contentDescription = null) },
                    title = stringResource(R.string.pref_title_donate_me),
                    summary = stringResource(R.string.pref_description_donate_me),
                    onClick = { showDonateDialog = true },
                )
            }

            items(appRules.keys.sorted(), key = { it }) { packageName ->
                AppRuleRow(packageName = packageName) {
                    viewModel.buildAppEntries()
                        .firstOrNull { it.packageName == packageName }
                        ?.let { entry ->
                            viewModel.selectApp(entry)
                            onOpenApp(packageName)
                        }
                }
            }
        }
    }

    // ---------------- 对话框 ----------------

    if (backupLoading) {
        LoadingDialog(stringResource(R.string.pref_description_backup))
    }
    if (importLoading) {
        LoadingDialog(stringResource(R.string.dialog_message_importing))
    }
    if (showGroupLoading) {
        LoadingDialog(stringResource(R.string.dialog_message_query_community))
    }

    crashInfo?.let { info ->
        if (showCrashDialog) {
            AlertDialog(
                onDismissRequest = { showCrashDialog = false },
                title = { Text(stringResource(R.string.hey_guy)) },
                text = {
                    Column {
                        Text(stringResource(R.string.crash_tip))
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        Clipboard.putContent(context, info)
                        showCrashDialog = false
                    }) { Text(stringResource(R.string.dialog_btn_copy)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCrashDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }

    if (showEnableModuleDialog) {
        AlertDialog(
            onDismissRequest = { showEnableModuleDialog = false },
            title = { Text(stringResource(R.string.hey_guy)) },
            text = { Text(stringResource(R.string.not_active_module)) },
            confirmButton = {
                TextButton(onClick = {
                    val xposedType = XposedEnvironment.checkXposedType(context)
                    if (xposedType != XposedEnvironment.XposedType.UNKNOWN) {
                        context.packageManager.getLaunchIntentForPackage(xposedType.PACKAGE_NAME)
                            ?.let { context.startActivity(it) }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.not_found_xp_installer))
                        }
                    }
                    showEnableModuleDialog = false
                }) { Text(stringResource(R.string.active)) }
            },
            dismissButton = {
                TextButton(onClick = { showEnableModuleDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showUpdatePolicyDialog) {
        AlertDialog(
            onDismissRequest = { showUpdatePolicyDialog = false },
            title = { Text(stringResource(R.string.welcome_title)) },
            text = { Text(stringResource(R.string.update_tips)) },
            confirmButton = {
                TextButton(onClick = {
                    DonateHelper.startAliPayDonate(context)
                    showUpdatePolicyDialog = false
                }) { Text(stringResource(R.string.dialog_btn_alipay)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    DonateHelper.startWxPayDonate(context)
                    showUpdatePolicyDialog = false
                }) { Text(stringResource(R.string.dialog_btn_wxpay)) }
            },
        )
    }

    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = { Text(stringResource(R.string.thanks)) },
            text = { Text(stringResource(R.string.donate_thanks)) },
            confirmButton = {
                TextButton(onClick = {
                    DonateHelper.startAliPayDonate(context)
                    showDonateDialog = false
                }) { Text(stringResource(R.string.dialog_btn_alipay)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    DonateHelper.startWxPayDonate(context)
                    showDonateDialog = false
                }) { Text(stringResource(R.string.dialog_btn_wxpay)) }
            },
        )
    }

    groupEntries?.let { entries ->
        SingleChoiceDialog(
            title = stringResource(R.string.pref_title_add_group),
            options = entries.map { it.name },
            selectedIndex = -1,
            onDismiss = { groupEntries = null },
            onSelect = { index ->
                groupEntries = null
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entries[index].link))
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
        )
    }
}

// ---------------- 通用行组件（模仿 Preference 外观，Material 3 实现） ----------------

@Composable
private fun SwitchPreferenceRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun ActionPreferenceRow(
    icon: @Composable () -> Unit,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = icon,
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
    )
}

/**
 * 应用规则条目：显示应用图标、名称与包名
 */
@Composable
private fun AppRuleRow(packageName: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val entry = remember(packageName) {
        runCatching {
            val pm = context.packageManager
            val aInfo = pm.getApplicationInfo(packageName, 0)
            Triple(aInfo.loadLabel(pm).toString(), aInfo.loadIcon(pm), packageName)
        }.getOrElse {
            Triple(
                packageName,
                androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_god),
                packageName,
            )
        }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            entry.second?.let { drawable ->
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        with(drawable) {
                            setBounds(0, 0, size.width.toInt(), size.height.toInt())
                            drawIntoCanvas { canvas -> draw(canvas.nativeCanvas) }
                        }
                    }
                }
            }
        },
        headlineContent = { Text(entry.first) },
        supportingContent = {
            Text(
                entry.third,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
