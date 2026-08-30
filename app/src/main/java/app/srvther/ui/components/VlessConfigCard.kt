package app.srvther.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.srvther.R
import app.srvther.model.ConnectionProfile
import app.srvther.ui.theme.CardSubSurface
import app.srvther.ui.theme.CardTextDim
import app.srvther.ui.theme.CardTextMuted
import app.srvther.ui.theme.CardTextPrimary
import app.srvther.ui.theme.SrvtherMint

@Composable
fun VlessConfigCard(
    profile: ConnectionProfile,
    onProfileChange: (ConnectionProfile) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showDialog by remember { mutableStateOf(false) }

    val hasVless = profile.vlessConfig.isNotBlank()
    val remark = remember(profile.vlessConfig) { parseVlessRemark(profile.vlessConfig) }

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardSubSurface)
            .drawWithCache {
                val hairline = 1.dp.toPx()
                val inset = hairline / 2f
                val radius = 20.dp.toPx()
                val outline = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(inset, inset, size.width - inset, size.height - inset),
                            cornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                val borderCol = if (hasVless) SrvtherMint.copy(alpha = 0.35f) else Color(0x1F3EDBB0)
                onDrawBehind {
                    drawPath(outline, color = borderCol, style = Stroke(hairline))
                }
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showDialog = true },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (hasVless) SrvtherMint.copy(alpha = 0.15f) else Color(0x154C8DFF),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hasVless) Icons.Rounded.Shield else Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = if (hasVless) SrvtherMint else Color(0xFF4C8DFF),
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.vless_config_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasVless) SrvtherMint else CardTextMuted,
                    )
                    if (hasVless) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SrvtherMint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (hasVless) remark else stringResource(R.string.vless_direct),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (hasVless) FontFamily.Monospace else FontFamily.Default,
                    color = if (hasVless) CardTextPrimary else CardTextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = if (hasVless) TextDirection.Ltr else TextDirection.Content,
                    ),
                )
            }

            Spacer(Modifier.width(8.dp))

            if (hasVless) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showDialog = true },
                        enabled = enabled,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.vless_edit_config),
                            tint = CardTextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            onProfileChange(profile.copy(vlessConfig = ""))
                            Toast.makeText(context, R.string.vless_clear, Toast.LENGTH_SHORT).show()
                        },
                        enabled = enabled,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.vless_clear),
                            tint = Color(0xFFFF5C7A),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        val text = clipboard.getText()?.text?.trim() ?: ""
                        if (text.startsWith("vless://", ignoreCase = true)) {
                            onProfileChange(profile.copy(vlessConfig = text))
                            Toast.makeText(context, R.string.vless_pasted, Toast.LENGTH_SHORT).show()
                        } else {
                            showDialog = true
                        }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SrvtherMint.copy(alpha = 0.18f),
                        contentColor = SrvtherMint,
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.vless_paste),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showDialog) {
        VlessConfigDialog(
            currentConfig = profile.vlessConfig,
            onSave = { newConfig ->
                onProfileChange(profile.copy(vlessConfig = newConfig.trim()))
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
fun VlessConfigDialog(
    currentConfig: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentConfig) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1626),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = SrvtherMint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.vless_config_title),
                    color = CardTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.vless_dialog_desc),
                    color = CardTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            text = "vless://uuid@host:port?security=tls...#Remark",
                            color = CardTextDim,
                            fontSize = 12.sp,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = CardTextPrimary,
                        textDirection = TextDirection.Ltr,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SrvtherMint,
                        unfocusedBorderColor = Color(0x333EDBB0),
                        cursorColor = SrvtherMint,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = CardTextMuted,
                                )
                            }
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val clip = clipboard.getText()?.text?.trim()
                            if (!clip.isNullOrBlank()) {
                                text = clip
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = SrvtherMint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.vless_paste),
                            color = SrvtherMint,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("vless://", ignoreCase = true)) {
                        onSave(trimmed)
                    } else {
                        Toast.makeText(context, R.string.vless_invalid, Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SrvtherMint,
                    contentColor = Color(0xFF0A0E1A),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.vless_save),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.vless_cancel),
                    color = CardTextMuted,
                )
            }
        },
    )
}

private fun parseVlessRemark(raw: String): String {
    if (raw.isBlank()) return ""
    try {
        val hashIdx = raw.indexOf('#')
        if (hashIdx != -1 && hashIdx < raw.length - 1) {
            val decoded = Uri.decode(raw.substring(hashIdx + 1).trim())
            if (decoded.isNotBlank()) return decoded
        }
        val uri = Uri.parse(raw)
        val host = uri.host
        val port = uri.port
        if (!host.isNullOrBlank()) {
            return if (port > 0) "$host:$port" else host
        }
    } catch (_: Exception) {
    }
    return raw.take(28) + (if (raw.length > 28) "…" else "")
}
