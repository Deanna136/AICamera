package com.example.aicamera.ui

import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.aicamera.camera.CameraController
import kotlin.math.sqrt
//import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 相机页面 UI
 * 支持手动对焦、长按锁定对焦、双指捏合变焦
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
    val currentZoom = viewModel.currentZoom.collectAsState().value
    val zoomRangeInfo = viewModel.zoomRangeInfo.collectAsState().value
    val focusState = viewModel.focusState.collectAsState().value
    val focusPointX = viewModel.focusPointX.collectAsState().value
    val focusPointY = viewModel.focusPointY.collectAsState().value
    val voiceRecognitionResult = viewModel.voiceRecognitionResult.collectAsState().value
    val isListening = viewModel.isListening.collectAsState().value

    val context = LocalContext.current
    val lifecycleOwnerRef = lifecycleOwner
    val previewViewRef = remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrEmpty()) {
            val rootView = (context as? android.app.Activity)?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
            if (rootView != null) {
                Snackbar.make(rootView, errorMessage, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is CameraUIState.PhotoSaved) {
            kotlinx.coroutines.delay(2000)
            viewModel.resumePreview()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updateZoomRangeInfo()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 相机预览层（支持点击对焦和手势变焦）
        CameraPreviewLayer(
            viewModel = viewModel,
            lifecycleOwner = lifecycleOwner,
            onPreviewViewReady = { previewView ->
                previewViewRef.value = previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // AI 建议显示
        if (aiAdvice.isNotEmpty()) {
            AISuggestionBox(
                advice = aiAdvice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
            )
        }

        // 语音识别结果显示
        if (voiceRecognitionResult.isNotEmpty()) {
            VoiceRecognitionResultBox(
                result = voiceRecognitionResult,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
            )
        }

        // 顶部控制条
        TopControlBar(
            currentZoom = currentZoom,
            onSwitchCamera = {
                previewViewRef.value?.let { previewView ->
                    viewModel.switchCamera(lifecycleOwnerRef, previewView)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // 对焦框显示
        if (focusState != CameraController.FocusState.Idle) {
            FocusIndicator(
                focusPointX = focusPointX,
                focusPointY = focusPointY,
                focusState = focusState,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 拍照按钮和控制区域
        CameraControlsLayer(
            uiState = uiState,
            voiceGuideEnabled = voiceGuideEnabled,
            isListening = isListening,
            currentZoom = currentZoom,
            zoomRangeInfo = zoomRangeInfo,
            onTakePicture = { viewModel.takePicture() },
            onToggleVoiceGuide = { viewModel.toggleVoiceGuide() },
            onStartListening = { viewModel.startListening() },
            onStopListening = { viewModel.stopListening() },
            onSetZoom = { viewModel.setZoom(it, animate = true) },
            onZoomIn = { viewModel.zoomIn() },
            onZoomOut = { viewModel.zoomOut() },
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
 * 相机预览层（支持手势交互）
 */
@Composable
private fun CameraPreviewLayer(
    viewModel: CameraViewModel,
    lifecycleOwner: LifecycleOwner,
    onPreviewViewReady: (androidx.camera.view.PreviewView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).apply {
                viewModel.initializeCamera(lifecycleOwner, this)
                onPreviewViewReady(this)
            }
        },
        update = { previewView ->
            // 设置手势检测器
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // 单点击对焦
                    val x = e.x / previewView.width
                    val y = e.y / previewView.height
                    viewModel.autoFocus(x, y)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    // 长按锁定对焦
                    val x = e.x / previewView.width
                    val y = e.y / previewView.height
                    viewModel.lockFocus(x, y)
                }
            })

            // 设置 OnTouchListener 处理手势
            previewView.setOnTouchListener { _, event ->
                // 处理双指捏合变焦
                when (event.pointerCount) {
                    2 -> handlePinchZoom(event, viewModel)
                    else -> gestureDetector.onTouchEvent(event)
                }
            }
        },
        modifier = modifier
    )
}

/**
 * 处理双指捏合变焦
 */
private fun handlePinchZoom(event: MotionEvent, viewModel: CameraViewModel): Boolean {
    return when (event.action and MotionEvent.ACTION_MASK) {
        MotionEvent.ACTION_POINTER_DOWN -> {
            // 记录初始距离
            lastPinchDistance = calculateDistance(event)
            true
        }
        MotionEvent.ACTION_MOVE -> {
            val currentDistance = calculateDistance(event)
            val distanceDelta = currentDistance - lastPinchDistance

            if (kotlin.math.abs(distanceDelta) > 10) {
                // 根据距离变化调整变焦
                val zoomFactor = if (distanceDelta > 0) 0.05f else -0.05f
                val currentZoom = viewModel.cameraController.getCurrentZoom()
                viewModel.setZoom(currentZoom + zoomFactor, animate = false)
                lastPinchDistance = currentDistance
            }
            true
        }
        else -> false
    }
}

private var lastPinchDistance = 0f

/**
 * 计算双指间距离
 */
private fun calculateDistance(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f

    val x1 = event.getX(0)
    val y1 = event.getY(0)
    val x2 = event.getX(1)
    val y2 = event.getY(1)

    val dx = x2 - x1
    val dy = y2 - y1

    return sqrt(dx * dx + dy * dy)
}

/**
 * 对焦指示器（对焦框）
 */
@Composable
private fun FocusIndicator(
    focusPointX: Float,
    focusPointY: Float,
    focusState: CameraController.FocusState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

        val focusBoxSize = 80.dp
        val xOffset = (focusPointX * screenWidth.value - focusBoxSize.value / 2).dp
        val yOffset = (focusPointY * screenHeight.value - focusBoxSize.value / 2).dp

        Box(
            modifier = Modifier
                .size(focusBoxSize)
                .offset(xOffset, yOffset)
                .border(
                    width = 2.dp,
                    color = when (focusState) {
                        CameraController.FocusState.Focusing -> Color.Yellow
                        CameraController.FocusState.Locked -> Color.Green
                        CameraController.FocusState.Failed -> Color.Red
                        else -> Color.White
                    }
                )
        )
    }
}

/**
 * 相机控制区域
 */
@Composable
private fun CameraControlsLayer(
    uiState: CameraUIState,
    voiceGuideEnabled: Boolean,
    isListening: Boolean,
    currentZoom: Float,
    zoomRangeInfo: Triple<Float, Float, Float>?,
    onTakePicture: () -> Unit,
    onToggleVoiceGuide: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onSetZoom: (Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
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
        // 变焦滑块
        if (zoomRangeInfo != null) {
            ZoomSlider(
                currentZoom = currentZoom,
                minZoom = zoomRangeInfo.first,
                maxZoom = zoomRangeInfo.second,
                onZoomChange = onSetZoom,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 变焦快速按钮和拍照按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomButton(
                label = stringResource(id = R.string.zoom_out),
                onClick = onZoomOut,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            ShutterButton(
                enabled = uiState is CameraUIState.Ready,
                onClick = onTakePicture,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .weight(1f)
            )

            ZoomButton(
                label = stringResource(id = R.string.zoom_in),
                onClick = onZoomIn,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        }

        VoiceGuideToggleButton(
            enabled = voiceGuideEnabled,
            onClick = onToggleVoiceGuide,
            modifier = Modifier.fillMaxWidth()
        )

        // 语音识别控制按钮
        if (voiceGuideEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isListening) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = Color(0xFF2196F3),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onStartListening() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("开始语音识别", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = Color(0xFFF44336),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onStopListening() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("停止语音识别", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 变焦滑块控件
 */
@Composable
private fun ZoomSlider(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = currentZoom,
            onValueChange = onZoomChange,
            valueRange = minZoom..maxZoom,
            steps = (maxZoom - minZoom).toInt() - 1,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(id = R.string.zoom_value, currentZoom),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 变焦按钮
 */
@Composable
private fun ZoomButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFF4CAF50),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(1),
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 顶部控制条（变焦信息、摄像头切换）
 */
@Composable
private fun TopControlBar(
    currentZoom: Float,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 变焦显示
        Text(
            text = String.format("%.1fx", currentZoom),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // 摄像头切换按钮
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Color(0xFF4CAF50),
                    shape = CircleShape
                )
                .clickable { onSwitchCamera() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "📷",
                fontSize = 16.sp
            )
        }
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
 * 语音识别结果显示框
 */
@Composable
private fun VoiceRecognitionResultBox(
    result: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("语音识别结果", color = Color(0xFF4CAF50), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(result, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
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

// 导入缺失的函数
fun Modifier.border(width: androidx.compose.ui.unit.Dp, color: Color) =
    this.then(
        Modifier.background(
            color = color,
//            shape = androidx.compose.foundation.shape.RectangleShape
            shape = RoundedCornerShape(0.dp)
        )
    )

fun Modifier.offset(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) =
    this.then(
        Modifier.padding(start = x, top = y)
    )
