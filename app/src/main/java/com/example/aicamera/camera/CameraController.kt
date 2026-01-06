package com.example.aicamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/**
 * 相机控制器
 * 职责：管理相机预览、拍照、实时帧获取
 *
 * 特点：
 * - 使用 CameraX 库管理相机生命周期
 * - 预览竖屏适配，无拉伸
 * - 支持实时帧回调（预留接口）
 *
 * 扩展点：
 * - onFrameAvailable() 接口用于后续 AI 构图/姿势指导
 * - 可添加滤镜、美颜等处理
 * - 可添加不同分辨率的拍照模式选择
 */
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "CameraController"
    }

    // 相机相关成员
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // 相机回调
    var onCameraReady: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null

    /**
     * 实时帧数据回调接口
     *
     * 当相机捕获新的预览帧时调用
     * 暂不实现，预留给后续 AI 功能使用
     *
     * 扩展点：可在此回调中：
     * 1. 获取实时帧数据（Bitmap）
     * 2. 发送给后端 AI 服务进行构图/姿势分析
     * 3. 接收 AI 返回的建议并在 UI 上显示
     */
    var onFrameAvailable: ((Bitmap) -> Unit)? = null

    /**
     * 初始化相机
     *
     * @param lifecycleOwner Activity/Fragment 生命周期持有者
     * @param previewView PreviewView 用于显示相机预览
     */
    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreviewAndCapture(lifecycleOwner, previewView)
                onCameraReady?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "相机初始化失败", e)
                onCameraError?.invoke("相机初始化失败：${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 绑定预览和拍照功能
     */
    private fun bindPreviewAndCapture(
        lifecycleOwner: LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ) {
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机用例失败", e)
            onCameraError?.invoke("相机配置失败：${e.message}")
        }
    }

    /**
     * 拍照
     *
     * @param executor 执行拍照操作的线程（通常使用 MainExecutor）
     * @param callback 拍照完成回调，返回照片 Bitmap
     */
    fun takePicture(
        executor: Executor,
        callback: (Bitmap?) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            Log.w(TAG, "ImageCapture 未初始化")
            callback(null)
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.cacheDir
        ).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val savedUri = outputFileResults.savedUri
                        if (savedUri != null) {
                            val bitmap = loadBitmapFromFile(savedUri.path ?: return)
                            callback(bitmap)
                        } else {
                            callback(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理拍照结果失败", e)
                        callback(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exception)
                    onCameraError?.invoke("拍照失败：${exception.message}")
                    callback(null)
                }
            }
        )
    }

    /**
     * 从文件路径加载 Bitmap
     *
     * @param filePath 文件路径
     * @return 加载的 Bitmap，失败返回 null
     */
    private fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(filePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载图片失败", e)
            null
        }
    }

    /**
     * 释放相机资源
     * 应在 Activity 销毁时调用
     */
    fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "释放相机资源失败", e)
        }
    }

    /**
     * 检查设备是否有摄像头
     *
     * @return 有摄像头返回 true
     */
    fun hasCameraDevice(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * 获取实时帧（预留接口）
     *
     * 当前暂不实现，预留给后续 AI 功能
     * 可用于获取相机实时预览帧进行 AI 分析
     *
     * 扩展点：
     * - 获取实时预览帧
     * - 将帧发送给后端 AI 服务
     * - 接收 AI 建议并触发回调
     */
    fun getRealtimeFrame() {
        // TODO: 实现实时帧获取逻辑
        // 此处预留给后续与后端AI联合开发
        // 1. 从预览中截取实时帧
        // 2. 转换为 Bitmap
        // 3. 触发 onFrameAvailable 回调
    }
}

