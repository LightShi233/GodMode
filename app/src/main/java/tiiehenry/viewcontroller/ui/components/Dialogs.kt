package tiiehenry.viewcontroller.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * 通用进度对话框（取代已废弃的 android.app.ProgressDialog）
 */
@Composable
fun LoadingDialog(message: String, onDismiss: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                Spacer(Modifier.width(20.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 单行文本输入对话框（取代 EditTextPreference 弹窗）
 */
@Composable
fun InputTextDialog(
    title: String,
    initialValue: String,
    hint: String = "",
    numeric: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = if (numeric) newValue.filter { it.isDigit() || it == '-' } else newValue
                },
                singleLine = true,
                placeholder = { Text(hint) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * 单选下拉对话框（取代 DropDownPreference）
 */
@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                options.forEachIndexed { index, option ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        label = { Text(option) },
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * 规则截图预览 + 红色标记框（取代 ImageViewPreference + Loader）
 */
@Composable
fun RulePreviewDialog(
    bitmap: Bitmap?,
    markBounds: Rect?,
    onDismiss: () -> Unit,
) {
    val bmp = bitmap ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = aspectHeightOf(bmp),
                )
                markBounds?.let { r ->
                    Canvas(modifier = aspectHeightOf(bmp)) {
                        val scale = size.width / bmp.width
                        drawRect(
                            color = Color.Red.copy(alpha = 0.4f),
                            topLeft = Offset(r.left * scale, r.top * scale),
                            size = Size(r.width * scale, r.height * scale),
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
    }
}

private fun aspectHeightOf(bitmap: Bitmap): Modifier {
    val ratio = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1)
    return Modifier.fillMaxWidth().height((280f * ratio).coerceAtMost(480f).dp)
}

/**
 * 信息条目行（标题 + 摘要），用于详情页只读字段
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/**
 * 分区标题（取代 PreferenceCategory）
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp),
    )
}
