package com.example.aicamera.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.aicamera.R
import com.google.android.material.snackbar.Snackbar
import android.view.ViewGroup

/**
 * 相机页面 UI
 * 职责：显示相机预览和拍照界面
 *
 * 布局结构：
 * - 上层：相机预览区域（全屏）
 * - 中层：AI 建议显示区域（预留占位，当前隐藏）
 * - 下层：拍照按钮 + 语音播报开关（预留占位，当前隐藏）
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsState().value
    val errorMessage = viewModel.errorMessage.collectAsState().value
    val aiAdvice = viewModel.aiAdvice.collectAsState().value
    val voiceGuideEnabled = viewModel.voiceGuideEnabled.collectAsState().value
    val context = LocalContext.current

    // 处理错误消息
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrEmpty()) {
            // 显示 Snackbar 错误提示
            val rootView = (context as? android.app.Activity)?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
            if (rootView != null) {
                Snackbar.make(rootView, errorMessage, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // 处理拍照后保存完成
    LaunchedEffect(uiState) {
        if (uiState is CameraUIState.PhotoSaved) {
            // 延迟 2 秒后恢复预览
            kotlinx.coroutines.delay(2000)
            viewModel.resumePreview()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 相机预览层
        CameraPreviewLayer(
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
            modifier = Modifier.fillMaxSize()
        )

        // AI 建议显示区域（预留占位，当前隐藏）
        if (aiAdvice.isNotEmpty()) {
            AISuggestionBox(
                advice = aiAdvice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
            )
        }

        // 拍照按钮和控制区域
        CameraControlsLayer(
            uiState = uiState,
            voiceGuideEnabled = voiceGuideEnabled,
            onTakePicture = { viewModel.takePicture() },
            onToggleVoiceGuide = { viewModel.toggleVoiceGuide() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )

        // 错误提示覆盖层
        if (uiState is CameraUIState.Error && !errorMessage.isNullOrEmpty()) {
            ErrorOverlay(
                message = errorMessage,
                onRetry = { /* 权限处理在 MainActivity 中进行 */ },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 加载状态提示
        if (uiState is CameraUIState.Initializing || uiState is CameraUIState.Taking || uiState is CameraUIState.Saving) {
            LoadingOverlay(
                status = when (uiState) {
                    CameraUIState.Initializing -> "初始化相机..."
                    CameraUIState.Taking -> "拍照中..."
                    CameraUIState.Saving -> "保存照片..."
                    else -> ""
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 照片保存成功提示
        if (uiState is CameraUIState.PhotoSaved) {
            SaveSuccessOverlay(
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 相机预览层
 */
@Composable
private fun CameraPreviewLayer(
    viewModel: CameraViewModel,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // 初始化相机
                viewModel.initializeCamera(lifecycleOwner, this)
            }
        },
        modifier = modifier
    )
}

/**
 * 相机控制区域（拍照按钮、语音播报开关）
 */
@Composable
private fun CameraControlsLayer(
    uiState: CameraUIState,
    voiceGuideEnabled: Boolean,
    onTakePicture: () -> Unit,
    onToggleVoiceGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.5f)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 拍照按钮
        ShutterButton(
            enabled = uiState is CameraUIState.Ready,
            onClick = onTakePicture,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 语音播报开关（预留占位）
        VoiceGuideToggleButton(
            enabled = voiceGuideEnabled,
            onClick = onToggleVoiceGuide,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 拍照按钮
 */
@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (enabled) Color.White else Color.Gray,
                shape = CircleShape
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = R.string.take_photo),
            fontSize = 12.sp,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 语音播报开关按钮（预留占位）
 */
@Composable
private fun VoiceGuideToggleButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = if (enabled) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.voice_guide_toggle),
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

/**
 * AI 建议显示框（预留占位，当前隐藏）
 */
@Composable
private fun AISuggestionBox(
    advice: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.ai_suggestion),
                color = Color(0xFFFFC107),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = advice,
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 加载状态覆盖层
 */
@Composable
private fun LoadingOverlay(
    status: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = status,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * 保存成功提示
 */
@Composable
private fun SaveSuccessOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.photo_taken),
                color = Color.Green,
                fontSize = 18.sp
            )
        }
    }
}

/**
 * 错误提示覆盖层
 */
@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = Color(0xFF1F1F1F),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.error),
                color = Color.Red,
                fontSize = 18.sp
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = Color(0xFF4CAF50),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        )
                        .clickable { onRetry() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.retry),
                        color = Color.White
                    )
                }
            }
        }
    }
}

