package com.iris.assistant.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.assistant.agent.OpenAiIntentModel
import com.iris.assistant.config.ApiKeyStore
import com.iris.assistant.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ApiSettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(ApiKeyStore.getApiKey(ctx) ?: "") }
    var model by remember { mutableStateOf(ApiKeyStore.getModel(ctx)) }
    var baseUrl by remember { mutableStateOf(ApiKeyStore.getBaseUrl(ctx)) }
    var showKey by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun save() {
        ApiKeyStore.setApiKey(ctx, apiKey)
        ApiKeyStore.setModel(ctx, model.ifBlank { ApiKeyStore.DEFAULT_MODEL })
        ApiKeyStore.setBaseUrl(ctx, baseUrl.ifBlank { ApiKeyStore.DEFAULT_BASE_URL })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("تنظیمات هوش مصنوعی", color = Color.White, fontSize = 20.sp)
        Text(
            "کلید و آدرس فقط رمزنگاری‌شده روی خود گوشی ذخیره می‌شن — به‌جز مستقیم به همون سرویس، جای دیگه‌ای فرستاده نمی‌شن.",
            color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp)
        )

        // Quick presets
        Text("انتخاب سریع سرویس", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row {
            OutlinedButton(onClick = {
                baseUrl = "https://api.openai.com/v1"
                model = "gpt-4o-mini"
                statusMessage = null
            }) { Text("OpenAI") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                statusMessage = "آدرس Cloudflare رو شامل accountId هست؛ خودت پرش کن (مثال زیر رو ببین)."
                statusIsError = false
            }) { Text("Cloudflare Workers AI") }
        }
        Text(
            "مثال آدرس Cloudflare: https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/ai/v1  —  مدل مثال: @cf/meta/llama-3.2-3b-instruct",
            color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        Text("آدرس پایه (Base URL)", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; statusMessage = null },
            placeholder = { Text("https://api.openai.com/v1", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))
        Text("کلید API", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; statusMessage = null },
            placeholder = { Text("sk-... یا cfut_...", color = TextSecondary) },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TextButton(onClick = { showKey = !showKey }) {
            Text(if (showKey) "پنهان کردن کلید" else "نمایش کلید", color = IrisTeal, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("مدل", color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            placeholder = { Text("gpt-4o-mini", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))
        Row {
            Button(
                onClick = {
                    save()
                    statusMessage = "ذخیره شد ✅"
                    statusIsError = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = IrisTeal)
            ) { Text("ذخیره", color = Color.Black) }

            Spacer(Modifier.width(12.dp))

            OutlinedButton(
                enabled = !testing,
                onClick = {
                    save()
                    testing = true
                    statusMessage = null
                    scope.launch {
                        val result = OpenAiIntentModel(ctx).testConnection()
                        testing = false
                        result.onSuccess {
                            statusMessage = it
                            statusIsError = false
                        }.onFailure {
                            statusMessage = it.message ?: "خطای نامشخص"
                            statusIsError = true
                        }
                    }
                }
            ) { Text(if (testing) "در حال تست..." else "تست اتصال") }
        }

        statusMessage?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = if (statusIsError) Color(0xFFFF5050) else IrisTeal, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "اگه این سرویس در دسترس نباشه (بدون اعتبار، بدون اینترنت، خطای سرور)، برنامه خودکار می‌ره روی موتور آفلاین داخلی تا مشکل حل بشه.",
            color = TextSecondary, fontSize = 11.sp
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            ApiKeyStore.clearApiKey(ctx)
            apiKey = ""
            statusMessage = "کلید حذف شد."
            statusIsError = false
        }) {
            Text("حذف کلید ذخیره‌شده", color = Color(0xFFFF5050), fontSize = 13.sp)
        }
    }
}
