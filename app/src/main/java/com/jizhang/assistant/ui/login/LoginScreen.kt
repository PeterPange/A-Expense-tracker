package com.jizhang.assistant.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.assistant.cloud.CloudApi
import com.jizhang.assistant.util.DataStore
import com.jizhang.assistant.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 登录模式 */
private enum class LoginMode { PASSWORD, EMAIL }

/**
 * 登录界面：账号密码 / 邮箱验证码 双模式 + 跳过。
 */
@Composable
fun LoginScreen(onFinished: () -> Unit) {
    var mode by remember { mutableStateOf(LoginMode.PASSWORD) }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var codeCountdown by remember { mutableIntStateOf(0) }
    var server by remember { mutableStateOf(CloudApi.DEFAULT_SERVER) }
    var showServer by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var sendingCode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 验证码倒计时
    LaunchedEffect(codeCountdown) {
        if (codeCountdown > 0) {
            delay(1000)
            codeCountdown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("¥", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text("记账助手", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "登录账号，数据云端同步",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        // 模式切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(4.dp),
        ) {
            ModeTab("账号密码", mode == LoginMode.PASSWORD, Modifier.weight(1f)) { mode = LoginMode.PASSWORD; error = null }
            ModeTab("邮箱验证码", mode == LoginMode.EMAIL, Modifier.weight(1f)) { mode = LoginMode.EMAIL; error = null }
        }

        Spacer(Modifier.height(22.dp))

        if (mode == LoginMode.PASSWORD) {
            // ── 账号密码模式 ──
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (isRegister) "密码（至少6位）" else "密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(visible = isRegister) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password2,
                        onValueChange = { password2 = it },
                        label = { Text("确认密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            // ── 邮箱验证码模式 ──
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱地址") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = emailCode,
                onValueChange = { emailCode = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (sendingCode || codeCountdown > 0) return@TextButton
                            val to = email.trim()
                            if (!to.contains("@") || !to.contains(".")) {
                                error = "请输入正确的邮箱地址"
                                return@TextButton
                            }
                            error = null
                            sendingCode = true
                            scope.launch(Dispatchers.IO) {
                                DataStore.setCloudServer(server.trim())
                                val r = CloudApi.requestEmailCode(to)
                                withContext(Dispatchers.Main) {
                                    sendingCode = false
                                    if (r.ok) {
                                        codeCountdown = 60
                                    } else {
                                        error = r.error ?: "发送失败"
                                    }
                                }
                            }
                        },
                        enabled = codeCountdown == 0 && !sendingCode,
                    ) {
                        Text(
                            when {
                                sendingCode -> "发送中…"
                                codeCountdown > 0 -> "${codeCountdown}s"
                                else -> "获取验证码"
                            },
                            fontSize = 12.sp,
                            color = if (codeCountdown > 0) MaterialTheme.colorScheme.onSurfaceVariant else Primary,
                            maxLines = 1,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "新邮箱自动注册，无需设置密码",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // 服务器设置（可折叠）
        AnimatedVisibility(visible = showServer) {
            Column {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TextButton(onClick = { showServer = !showServer }) {
            Text(
                if (showServer) "收起服务器设置" else "服务器设置",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 错误提示
        AnimatedVisibility(visible = error != null) {
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 主按钮
        Button(
            onClick = {
                if (busy) return@Button
                error = null
                scope.launch(Dispatchers.IO) {
                    DataStore.setCloudServer(server.trim())
                    val result = withContext(Dispatchers.IO) {
                        if (mode == LoginMode.EMAIL) {
                            // 邮箱登录
                            val to = email.trim()
                            val code = emailCode.trim()
                            when {
                                !to.contains("@") -> AuthErr("请输入邮箱地址")
                                code.length != 6 -> AuthErr("请输入6位验证码")
                                else -> CloudApi.emailLogin(to, code)
                            }
                        } else {
                            // 账号密码
                            val name = username.trim()
                            when {
                                name.length < 2 -> AuthErr("用户名至少2个字符")
                                password.length < 6 -> AuthErr("密码至少6位")
                                isRegister && password != password2 -> AuthErr("两次密码不一致")
                                else -> if (isRegister) CloudApi.register(name, password)
                                        else CloudApi.login(name, password)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        busy = false
                        if (result.ok && result.token != null) {
                            DataStore.setCloudToken(result.token)
                            DataStore.setCloudUserId(result.userId ?: "")
                            DataStore.setCloudUsername(result.username ?: "")
                            onFinished()
                        } else {
                            error = result.error ?: "请求失败"
                        }
                    }
                }
                busy = true
            },
            enabled = !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(
                    if (mode == LoginMode.EMAIL) "登 录"
                    else if (isRegister) "注 册"
                    else "登 录",
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (mode == LoginMode.PASSWORD) {
            TextButton(onClick = { isRegister = !isRegister; error = null }) {
                Text(
                    if (isRegister) "已有账号？去登录" else "没有账号？注册一个",
                    color = Primary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 跳过（本地模式）
        OutlinedButton(
            onClick = { DataStore.setLoginSkipped(true); onFinished() },
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("暂不登录，本地使用", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "登录后记账数据自动云端备份，换机不丢失",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
    }
}

private fun AuthErr(msg: String) = CloudApi.AuthResult(false, null, null, null, msg)

@Composable
private fun ModeTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        modifier = modifier,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
