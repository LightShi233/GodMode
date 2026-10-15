package tiiehenry.viewcontroller.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Export
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import tiiehenry.viewcontroller.R
import tiiehenry.viewcontroller.util.CommandUtil
import tiiehenry.viewcontroller.rule.ViewRule
import tiiehenry.viewcontroller.ui.GmViewModel
import java.util.Objects

/**
 * 某应用的全部规则列表（取代 ViewRuleListFragment + RuleDetailAdapter）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    viewModel: GmViewModel,
    packageName: String,
    onBack: () -> Unit,
    onOpenRule: (ViewRule) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rules by viewModel.viewRules.observeAsState(emptyList())
    var appEnabled by remember { mutableStateOf(!viewModel.isAppDisabled()) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ViewRule?>(null) }

    // 回到前台时刷新（对应旧 onResume 的 notifyDataSetChanged）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSelectedPackageView()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_app_rule)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // 直接运行应用
                        context.packageManager.getLaunchIntentForPackage(packageName)
                            ?.let { context.startActivity(it) }
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.menu_title_run_app))
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.menu_title_purge_all_rules))
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_title_stop_and_run_app)) },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                Thread {
                                    runCatching {
                                        val process = CommandUtil.su() ?: return@Thread
                                        CommandUtil.run(process, "am force-stop $packageName\n")
                                        process.waitFor()
                                        process.destroy()
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            context.packageManager.getLaunchIntentForPackage(packageName)
                                                ?.let { context.startActivity(it) }
                                        }
                                    }
                                }.start()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_export_all_rules)) },
                            leadingIcon = { Icon(Icons.Filled.Export, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                viewModel.exportSelectedRules { result ->
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_title_purge_all_rules)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                if (!viewModel.deleteAppRules()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.snack_bar_msg_revert_rule_fail),
                                        )
                                    }
                                } else {
                                    onBack()
                                }
                            },
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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.pref_title_god_mode)) },
                    supportingContent = { Text(packageName) },
                    trailingContent = {
                        Switch(
                            checked = appEnabled,
                            onCheckedChange = { checked ->
                                appEnabled = checked
                                viewModel.setAppEnable(checked)
                            },
                        )
                    },
                )
            }

            if (appEnabled) {
                items(rules, key = { Objects.hashCode(it) }) { rule ->
                    RuleRow(
                        rule = rule,
                        onClick = { onOpenRule(rule) },
                        onLongClick = { pendingDelete = rule },
                    )
                }
            }
        }
    }

    // 长按操作对话框（取代旧 AlertDialog setItems）
    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Operate") },
            text = { Text("Select one action") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule)
                    pendingDelete = null
                }) { Text(stringResource(R.string.menu_delete)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        rule.enable = !rule.enable
                        viewModel.updateRule(rule)
                        pendingDelete = null
                    }) {
                        Text(if (rule.enable) "disable" else "enable")
                    }
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}

/**
 * 单条规则：别名高亮 + 界面/类型摘要（对应旧 SpannableString 逻辑）
 */
@Composable
private fun RuleRow(rule: ViewRule, onClick: () -> Unit, onLongClick: () -> Unit) {
    val aliasColor = MaterialTheme.colorScheme.error
    val activityName = rule.activityClass?.substringAfterLast('.', rule.activityClass ?: "") ?: ""
    ListItem(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (!rule.enable) {
                    Text("*", color = aliasColor)
                }
            }
        },
        headlineContent = {
            if (activityName.isNotEmpty()) {
                Text(stringResource(R.string.field_activity, activityName))
            } else {
                Text(stringResource(R.string.field_view, rule.viewClass ?: ""))
            }
        },
        supportingContent = {
            androidx.compose.foundation.layout.Column {
                if (!rule.alias.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.field_rule_alias, rule.alias),
                        color = aliasColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.field_view, rule.viewClass ?: ""),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
