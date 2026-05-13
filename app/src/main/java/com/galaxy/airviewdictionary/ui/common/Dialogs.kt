package com.galaxy.airviewdictionary.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.galaxy.airviewdictionary.R


@Composable
fun MyDialog(
    isGlobalAlerts: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    icon: ImageVector? = null,
    painterResource: Int? = null,
    dialogTitle: String? = null,
    dialogText: String,
    onConfirmLabel: String? = null,
    onDismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalPadding)
                .background(
                    color = Color(0xFFEFEFF2),
                    shape = RoundedCornerShape(32.dp)
                ),
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center)
                    .padding(24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "$dialogText Icon",
                        modifier = Modifier.size(36.dp)
                    )
                }
                if (painterResource != null) {
                    Image(
                        modifier = Modifier.size(36.dp),
                        painter = painterResource(id = painterResource),
                        contentDescription = "$dialogText Image",
                        contentScale = ContentScale.Fit,
                    )
                }

                dialogTitle?.let {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = dialogTitle,
                        style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFF1C1B1F)),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = dialogText,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF49454F)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onDismiss != null) {
                        TextButton(
                            onClick = { onDismiss() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF454545)),
                        ) {
                            Text(
                                text = onDismissLabel ?: "Dismiss",
                                color = Color(0xFF044b8a),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.widthIn(max = 80.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = { onConfirm() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF454545)),
                    ) {
                        Text(
                            text = onConfirmLabel ?: "Confirm",
                            color = Color(0xFF044b8a),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.widthIn(max = 80.dp)
                        )
                    }
                }
            }

            if (isGlobalAlerts) {
                Row(
                    modifier = Modifier
                        .wrapContentHeight()
                        .padding(start = 14.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.outline_translate_white_24),
                        contentDescription = "ic_launcher",
                        colorFilter = ColorFilter.tint(Color(0xFF454545)),
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        color = Color(0xFF454545),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), //
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }

}