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
     * @param onBitmapReady 拍照完成回调，返回照片 Bitmap
     *
     * 扩展点：此处可在获得 Bitmap 后发送给后端 AI 服务进行实时分析
     */
    fun takePicture(
        executor: Executor,
        onBitmapReady: (Bitmap?) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            Log.w(TAG, "ImageCapture 未初始化")
            onBitmapReady(null)
            return
        }

        // 使用内存中的 ImageCapture，而不是文件
        // 这样可以直接获取 Bitmap，避免文件读取问题
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        // 将 ImageProxy 转换为 Bitmap
                        val bitmap = imageProxyToBitmap(image)
                        image.close() // 立即释放 ImageProxy 资源
                        onBitmapReady(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "转换图像失败", e)
                        image.close()
                        onBitmapReady(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败", exception)
                    onCameraError?.invoke("拍照失败：${exception.message}")
                    onBitmapReady(null)
                }
            }
        )
    }

    /**
     * 将 ImageProxy 转换为 Bitmap
     *
     * @param image ImageProxy 对象
     * @return 转换后的 Bitmap
     */
    private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap {
        val planes = image.planes
        val width = image.width
        val height = image.height

        // 处理 NV21 格式的图像数据
        val buffer = planes[0].buffer
        buffer.rewind()

        // 创建字节数组并复制缓冲区数据
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // 使用 BitmapFactory 解码字节数据
        val bitmap = try {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "BitmapFactory 解码失败，使用备用方案", e)
            // 备用方案：直接创建位图
            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        }

        // 处理相机旋转（竖屏适配）
        val rotationDegrees = image.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees)
        } else {
            bitmap
        }
    }

    /**
     * 旋转 Bitmap
     *
     * @param bitmap 原始位图
     * @param degrees 旋转角度
     * @return 旋转后的位图
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        return android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
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

