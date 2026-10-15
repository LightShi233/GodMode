package tiiehenry.viewcontroller.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tiiehenry.viewcontroller.R
import tiiehenry.viewcontroller.rule.ViewRule
import tiiehenry.viewcontroller.ui.GmViewModel
import tiiehenry.viewcontroller.ui.components.InfoRow
import tiiehenry.viewcontroller.ui.components.InputTextDialog
import tiiehenry.viewcontroller.ui.components.RulePreviewDialog
import tiiehenry.viewcontroller.ui.components.SectionHeader
import tiiehenry.viewcontroller.ui.components.SingleChoiceDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 规则详情编辑页（取代 ViewRuleDetailsFragment + pref_rule_details.xml）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(
    viewModel: GmViewModel,
    rule: ViewRule,
    onBack: () -> Unit,
) {
    // 本地可编辑状态，改动即持久化到远端（对应旧 onPreferenceChange -> updateRule）
    var enable by remember { mutableStateOf(rule.enable) }
    var autoClick by remember { mutableStateOf(rule.autoClick) }
    var alias by remember { mutableStateOf(rule.alias ?: "") }
    var alpha by remember { mutableStateOf(rule.alpha.toString()) }
    var targetWidth by remember { mutableStateOf(rule.targetWidth.toString()) }
    var targetHeight by remember { mutableStateOf(rule.targetHeight.toString()) }
    var visibility by remember { mutableStateOf(rule.visibility) }
    var targetParamType by remember { mutableStateOf(rule.targetParamType) }

    var editingField by remember { mutableStateOf<String?>(null) }
    var showVisiblePicker by remember { mutableStateOf(false) }
    var showWhTypePicker by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    // 截图预览加载（取代 Loader/AsyncTaskLoader）
    LaunchedEffect(rule) {
        if (!rule.imagePath.isNullOrEmpty()) {
            viewModel.loadRulePreview(rule) { bmp ->
                previewBitmap = bmp
            }
        }
    }

    fun persist(block: (ViewRule) -> Unit) {
        block(rule)
        viewModel.updateRule(rule)
    }

    val visibleOptions = listOf(
        stringResource(R.string.rule_details_visible),
        stringResource(R.string.rule_details_invisible),
        stringResource(R.string.rule_details_gone),
    )
    val whTypeOptions = listOf(
        stringResource(R.string.rule_details_wh_type_no),
        stringResource(R.string.rule_details_wh_type_w),
        stringResource(R.string.rule_details_wh_type_h),
        stringResource(R.string.rule_details_wh_type_wh),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_rule_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteRule(rule)
                        onBack()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.menu_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 应用信息头部 ----
            ListItem(
                headlineContent = { Text(rule.label ?: "") },
                supportingContent = { Text(rule.packageName ?: "") },
                trailingContent = {
                    Switch(
                        checked = enable,
                        onCheckedChange = { checked ->
                            enable = checked
                            persist { it.enable = checked }
                        },
                    )
                },
            )
            Divider()

            // ---- 可编辑字段 ----
            SectionHeader(stringResource(R.string.rule_details_field_alias))
            EditableRow(
                label = stringResource(R.string.rule_details_set_alias),
                value = alias.ifEmpty { stringResource(R.string.rule_details_set_alias) },
                onClick = { editingField = FIELD_ALIAS },
            )
            EditableRow(
                label = stringResource(R.string.rule_details_field_alpha),
                value = alpha,
                onClick = { editingField = FIELD_ALPHA },
            )
            EditableRow(
                label = stringResource(R.string.rule_details_field_target_width),
                value = targetWidth,
                onClick = { editingField = FIELD_W },
            )
            EditableRow(
                label = stringResource(R.string.rule_details_field_target_height),
                value = targetHeight,
                onClick = { editingField = FIELD_H },
            )
            EditableRow(
                label = stringResource(R.string.rule_details_field_visible),
                value = visibleOptions.getOrElse(visibleValueIndex(visibility)) { visibleOptions.first() },
                onClick = { showVisiblePicker = true },
            )
            EditableRow(
                label = stringResource(R.string.rule_details_field_target_wh),
                value = whTypeOptions.getOrElse(targetParamType) { whTypeOptions.first() },
                onClick = { showWhTypePicker = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.rule_details_rule_auto_click)) },
                trailingContent = {
                    Switch(
                        checked = autoClick,
                        onCheckedChange = { checked ->
                            autoClick = checked
                            persist { it.autoClick = checked }
                        },
                    )
                },
            )
            Divider()

            // ---- 只读信息 ----
            SectionHeader("Info")
            InfoRow(
                stringResource(R.string.rule_details_field_create_time),
                SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(rule.timestamp)),
            )
            InfoRow(
                stringResource(R.string.rule_details_field_generate_version),
                "${rule.label} ${rule.matchVersionName}",
            )
            InfoRow(
                stringResource(R.string.rule_details_field_activity),
                rule.activityClass?.ifEmpty { null } ?: "None",
            )
            InfoRow(
                stringResource(R.string.rule_details_field_view_bounds),
                Rect(rule.x.toFloat(), rule.y.toFloat(), (rule.x + rule.width).toFloat(), (rule.y + rule.height).toFloat()).toString(),
            )
            InfoRow(stringResource(R.string.rule_details_field_view_type), rule.viewClass ?: "")
            InfoRow(
                stringResource(R.string.rule_details_field_view_depth),
                rule.depth?.contentToString() ?: "[]",
            )
            InfoRow(stringResource(R.string.rule_details_field_res_name), rule.resourceName ?: "")
            if (!rule.text.isNullOrEmpty()) {
                InfoRow(stringResource(R.string.rule_details_field_text), rule.text)
            }
            if (!rule.description.isNullOrEmpty()) {
                InfoRow(stringResource(R.string.rule_details_field_description), rule.description)
            }

            // ---- 截图预览入口 ----
            if (!rule.imagePath.isNullOrEmpty()) {
                ListItem(
                    modifier = Modifier.clickable { showPreview = previewBitmap != null },
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.rule_details_field_view_bounds)) },
                )
            }
        }
    }

    // ---- 对话框 ----
    when (editingField) {
        FIELD_ALIAS -> InputTextDialog(
            title = stringResource(R.string.rule_details_set_alias),
            initialValue = alias,
            onDismiss = { editingField = null },
            onConfirm = { v ->
                alias = v
                persist { it.alias = v }
                editingField = null
            },
        )

        FIELD_ALPHA -> InputTextDialog(
            title = stringResource(R.string.rule_details_set_alpha),
            initialValue = alpha,
            numeric = true,
            onDismiss = { editingField = null },
            onConfirm = { v ->
                val num = v.toIntOrNull()
                if (num != null && num in 0..255) {
                    alpha = num.toString()
                    persist { it.alpha = num }
                }
                editingField = null
            },
        )

        FIELD_W -> InputTextDialog(
            title = stringResource(R.string.rule_details_set_target_width),
            initialValue = targetWidth,
            numeric = true,
            onDismiss = { editingField = null },
            onConfirm = { v ->
                val num = v.toIntOrNull()
                if (num != null) {
                    targetWidth = num.toString()
                    persist { it.targetWidth = num }
                }
                editingField = null
            },
        )

        FIELD_H -> InputTextDialog(
            title = stringResource(R.string.rule_details_set_target_height),
            initialValue = targetHeight,
            numeric = true,
            onDismiss = { editingField = null },
            onConfirm = { v ->
                val num = v.toIntOrNull()
                if (num != null) {
                    targetHeight = num.toString()
                    persist { it.targetHeight = num }
                }
                editingField = null
            },
        )
    }

    if (showVisiblePicker) {
        SingleChoiceDialog(
            title = stringResource(R.string.rule_details_field_visible),
            options = visibleOptions,
            selectedIndex = visibleValueIndex(visibility),
            onDismiss = { showVisiblePicker = false },
            onSelect = { index ->
                visibility = visibleValues[index]
                persist { it.visibility = visibleValues[index] }
                showVisiblePicker = false
            },
        )
    }

    if (showWhTypePicker) {
        SingleChoiceDialog(
            title = stringResource(R.string.rule_details_field_target_wh),
            options = whTypeOptions,
            selectedIndex = targetParamType,
            onDismiss = { showWhTypePicker = false },
            onSelect = { index ->
                targetParamType = index
                persist { it.targetParamType = index }
                showWhTypePicker = false
            },
        )
    }

    if (showPreview) {
        RulePreviewDialog(
            bitmap = previewBitmap,
            markBounds = Rect(
                rule.x.toFloat(),
                rule.y.toFloat(),
                (rule.x + rule.width).toFloat(),
                (rule.y + rule.height).toFloat(),
            ),
            onDismiss = { showPreview = false },
        )
    }
}

private const val FIELD_ALIAS = "alias"
private const val FIELD_ALPHA = "alpha"
private const val FIELD_W = "targetWidth"
private const val FIELD_H = "targetHeight"

// View.VISIBLE=0 / INVISIBLE=4 / GONE=8
private val visibleValues = intArrayOf(0, 4, 8)

private fun visibleValueIndex(value: Int): Int =
    visibleValues.indexOfFirst { it == value }.coerceAtLeast(0)

@Composable
private fun EditableRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
