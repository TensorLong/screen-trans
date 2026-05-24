package com.yiqun.translator.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yiqun.translator.R
import com.yiqun.translator.data.local.secure.ApiKeyInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiApiSettingsDialog(
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit,
    onCleared: () -> Unit,
    onFetchModels: suspend () -> List<String>,
    onTestConnection: suspend () -> String,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialKey = remember { ApiKeyInfo.getApiKeyChatgpt(context).orEmpty() }
    val initialBaseUrl = remember { ApiKeyInfo.getApiBaseUrlChatgpt(context).orEmpty() }
    val initialModel = remember { ApiKeyInfo.getApiModelChatgpt(context).orEmpty() }
    val initialCachedModels = remember { ApiKeyInfo.getCachedModelsChatgpt(context).orEmpty() }
    var keyText by remember { mutableStateOf(initialKey) }
    var baseUrlText by remember { mutableStateOf(initialBaseUrl) }
    var modelText by remember { mutableStateOf(initialModel) }
    var models by remember { mutableStateOf<List<String>>(initialCachedModels) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val modelScrollState = rememberScrollState()
    val menuItemHeightPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    fun persistInputs() {
        val trimmedKey = keyText.trim()
        ApiKeyInfo.setApiBaseUrlChatgpt(context, baseUrlText.trim())
        ApiKeyInfo.setApiModelChatgpt(context, modelText.trim())
        ApiKeyInfo.setApiKeyChatgpt(context, trimmedKey)
        if (trimmedKey.isNotEmpty()) {
            ApiKeyInfo.setApiKeyVersionChatgpt(context, 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(id = R.string.settings_menu_ai_api_settings))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.settings_menu_ai_api_key_label)) },
                    placeholder = { Text(text = stringResource(id = R.string.settings_menu_ai_api_key_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    visualTransformation = VisualTransformation.None,
                )
                OutlinedTextField(
                    value = baseUrlText,
                    onValueChange = { baseUrlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.settings_menu_ai_api_base_url)) },
                    placeholder = { Text(text = stringResource(id = R.string.settings_menu_ai_api_base_url_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    visualTransformation = VisualTransformation.None,
                )
                Text(
                    text = stringResource(id = R.string.settings_menu_ai_api_base_url_helper),
                    style = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { if (models.isNotEmpty()) modelDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = { modelText = it },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(id = R.string.settings_menu_ai_api_model)) },
                        placeholder = { Text(text = stringResource(id = R.string.settings_menu_ai_api_model_hint)) },
                        trailingIcon = {
                            if (models.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        visualTransformation = VisualTransformation.None,
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false },
                    ) {
                        LaunchedEffect(modelDropdownExpanded, models, modelText) {
                            if (modelDropdownExpanded && models.isNotEmpty()) {
                                val idx = models.indexOf(modelText.trim())
                                if (idx >= 0) {
                                    modelScrollState.scrollTo(idx * menuItemHeightPx)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(modelScrollState),
                        ) {
                            models.forEach { model ->
                                val isSelected = model == modelText.trim()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = model,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        modelText = model
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.settings_menu_ai_api_model_helper),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            persistInputs()
                            loading = true
                            statusText = context.getString(R.string.settings_menu_ai_api_fetching_models)
                            coroutineScope.launch {
                                runCatching { onFetchModels() }
                                    .onSuccess { fetchedModels ->
                                        models = fetchedModels
                                        if (fetchedModels.isNotEmpty()) {
                                            ApiKeyInfo.setCachedModelsChatgpt(context, fetchedModels)
                                        }
                                        statusText = if (fetchedModels.isEmpty()) {
                                            context.getString(R.string.settings_menu_ai_api_no_models)
                                        } else {
                                            context.getString(R.string.settings_menu_ai_api_models_loaded, fetchedModels.size)
                                        }
                                    }
                                    .onFailure { throwable ->
                                        statusText = throwable.localizedMessage ?: context.getString(R.string.settings_menu_ai_api_models_failed)
                                    }
                                loading = false
                            }
                        }
                    ) {
                        Text(text = stringResource(id = R.string.settings_menu_ai_api_fetch_models))
                    }
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            persistInputs()
                            loading = true
                            statusText = context.getString(R.string.settings_menu_ai_api_testing)
                            coroutineScope.launch {
                                runCatching { onTestConnection() }
                                    .onSuccess { response ->
                                        statusText = context.getString(
                                            R.string.settings_menu_ai_api_test_succeeded,
                                            response.ifBlank { "OK" }
                                        )
                                    }
                                    .onFailure { throwable ->
                                        statusText = throwable.localizedMessage ?: context.getString(R.string.settings_menu_ai_api_test_failed)
                                    }
                                loading = false
                            }
                        }
                    ) {
                        Text(text = stringResource(id = R.string.settings_menu_ai_api_test))
                    }
                }
                if (statusText.isNotBlank()) {
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.settings_menu_ai_api_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                persistInputs()
                if (keyText.trim().isEmpty() || baseUrlText.trim().isEmpty()) {
                    onCleared()
                } else {
                    onSaved()
                }
            }) {
                Text(text = stringResource(id = R.string.settings_menu_ai_api_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.settings_menu_ai_api_cancel))
            }
        },
    )
}
