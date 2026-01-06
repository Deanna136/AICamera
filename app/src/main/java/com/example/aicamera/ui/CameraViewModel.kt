package com.example.aicamera.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicamera.camera.CameraController
import com.example.aicamera.storage.FileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 相机页面 ViewModel
 * 职责：管理相机页面的状态和业务逻辑
 *
 * 特点：
 * - 与 Activity/Fragment 生命周期绑定
 * - 使用 Coroutine Flow 管理状态
 * - 预留 AI 相关数据存储和回调
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    // 依赖注入
    val cameraController = CameraController(application)
    val fileManager = FileManager(application)

    // UI 状态管理
    private val _uiState = MutableStateFlow<CameraUIState>(CameraUIState.Idle)
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * AI 相关数据存储（预留接口）
     *
     * 扩展点：
     * - aiAdvice 用于存储后端 AI 返回的建议（构图、姿势等）
     * - 在接收到 AI 响应时更新此字段
     * - UI 监听此字段变化，实时显示建议
     */
    private val _aiAdvice = MutableStateFlow<String>("")
    val aiAdvice: StateFlow<String> = _aiAdvice.asStateFlow()

    /**
     * 语音播报开关状态（预留接口）
     *
     * 扩展点：
     * - 用于控制是否启用语音播报
     * - 当此值为 true 且有新的 AI 建议时，触发 playVoiceAdvice()
     */
    private val _voiceGuideEnabled = MutableStateFlow<Boolean>(false)
    val voiceGuideEnabled: StateFlow<Boolean> = _voiceGuideEnabled.asStateFlow()

    // 最后拍摄的照片
    private val _lastPhoto = MutableStateFlow<Bitmap?>(null)
    val lastPhoto: StateFlow<Bitmap?> = _lastPhoto.asStateFlow()

    /**
     * 初始化相机
     *
     * @param lifecycleOwner Activity/Fragment 生命周期持有者
     * @param previewView 相机预览 View
     */
    fun initializeCamera(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        _uiState.value = CameraUIState.Initializing

        // 检查设备是否有摄像头
        if (!cameraController.hasCameraDevice()) {
            _errorMessage.value = "设备不支持相机功能"
            _uiState.value = CameraUIState.Error
            return
        }

        // 设置相机回调
        cameraController.onCameraReady = {
            _uiState.value = CameraUIState.Ready
        }

        cameraController.onCameraError = { error ->
            _errorMessage.value = error
            _uiState.value = CameraUIState.Error
        }

        // 设置实时帧回调（预留接口）
        cameraController.onFrameAvailable = { frame ->
            handleFrameAvailable(frame)
        }

        // 初始化相机
        cameraController.initializeCamera(lifecycleOwner, previewView)
    }

    /**
     * 拍照
     */
    fun takePicture() {
        if (_uiState.value != CameraUIState.Ready) {
            _errorMessage.value = "相机未就绪"
            return
        }

        _uiState.value = CameraUIState.Taking

        cameraController.takePicture(
            androidx.core.content.ContextCompat.getMainExecutor(getApplication())
        ) { bitmap ->
            if (bitmap != null) {
                _lastPhoto.value = bitmap
                savePicture(bitmap)
            } else {
                _errorMessage.value = "拍照失败"
                _uiState.value = CameraUIState.Ready
            }
        }
    }

    /**
     * 保存照片到相册
     *
     * @param bitmap 照片位图
     */
    private fun savePicture(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = CameraUIState.Saving

            try {
                Log.d(TAG, "开始保存照片，Bitmap 尺寸: ${bitmap.width}x${bitmap.height}")

                // 检查存储空间
                if (!fileManager.hasEnoughStorage()) {
                    Log.e(TAG, "存储空间不足")
                    _errorMessage.value = "存储空间不足"
                    _uiState.value = CameraUIState.Ready
                    return@launch
                }

                Log.d(TAG, "存储空间充足，开始保存到相册...")

                // 保存到相册
                val result = fileManager.saveBitmapToGallery(bitmap)
                if (result != null) {
                    Log.d(TAG, "照片成功保存，URI: $result")
                    _uiState.value = CameraUIState.PhotoSaved
                } else {
                    Log.e(TAG, "照片保存失败")
                    _errorMessage.value = "照片保存失败"
                    _uiState.value = CameraUIState.Ready
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存异常", e)
                _errorMessage.value = "保存异常：${e.message}"
                _uiState.value = CameraUIState.Ready
            }
        }
    }

    /**
     * 处理实时帧数据回调（预留接口）
     *
     * 扩展点：
     * - 在此处接收相机实时帧
     * - 将帧发送给后端 AI 服务
     * - 接收 AI 建议并更新 _aiAdvice 字段
     * - 如果启用了语音播报，触发 playVoiceAdvice()
     *
     * @param frame 相机实时帧 Bitmap
     */
    private fun handleFrameAvailable(frame: Bitmap) {
        // TODO: 实现实时帧处理逻辑
        // 当前暂不实现，预留给后续 AI 功能
        // 1. 将 frame 发送给后端 AI 服务
        // 2. 接收 AI 返回的建议
        // 3. 更新 _aiAdvice 字段
        // 4. 如果 _voiceGuideEnabled 为 true，调用 playVoiceAdvice()
    }

    /**
     * 更新 AI 建议（供后端响应调用）
     *
     * 扩展点：当后端返回 AI 建议时调用此方法
     *
     * @param advice AI 建议文本
     */
    fun updateAIAdvice(advice: String) {
        _aiAdvice.value = advice

        // 如果启用了语音播报，自动播报建议
        if (_voiceGuideEnabled.value) {
            playVoiceAdvice(advice)
        }
    }

    /**
     * 语音播报 AI 建议（预留接口）
     *
     * 扩展点：
     * - 使用文本转语音（TTS）API 进行语音播报
     * - 可集成第三方语音 AI 服务（如百度、阿里等）
     * - 当前暂不实现，仅预留接口
     *
     * @param advice 要播报的建议文本
     */
    fun playVoiceAdvice(advice: String) {
        // TODO: 实现语音播报逻辑
        // 当前暂不实现，预留给后续语音 AI 功能
        // 1. 初始化 TextToSpeech（TTS）
        // 2. 调用 speak() 方法播报文本
        // 或者集成第三方语音 AI 服务进行播报
    }

    /**
     * 切换语音播报开关
     */
    fun toggleVoiceGuide() {
        _voiceGuideEnabled.value = !_voiceGuideEnabled.value
    }

    /**
     * 重新开始拍照
     */
    fun resumePreview() {
        _uiState.value = CameraUIState.Ready
        _errorMessage.value = null
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 释放资源
     * 应在 Activity 销毁时调用
     */
    override fun onCleared() {
        super.onCleared()
        cameraController.releaseCamera()
    }

    fun releaseCamera(context: android.content.Context) {
        val cameraController = CameraController(context)
        cameraController.releaseCamera()
    }
}

/**
 * 相机 UI 状态定义
 */
sealed class CameraUIState {
    object Idle : CameraUIState()
    object Initializing : CameraUIState()
    object Ready : CameraUIState()
    object Taking : CameraUIState()
    object Saving : CameraUIState()
    object PhotoSaved : CameraUIState()
    object Error : CameraUIState()
}

