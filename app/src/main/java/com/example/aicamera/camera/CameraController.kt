package com.example.aicamera.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * 相机控制器
 * 职责：管理相机预览、拍照、实时帧获取、对焦、变焦等功能
 *
 * 特点：
 * - 使用 CameraX 库管理相机生命周期
 * - 预览竖屏适配，无拉伸
 * - 支持手动对焦和对焦锁定
 * - 支持平滑变焦动画
 * - 自动适配设备最大变焦倍数
 */
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "CameraController"
        private const val ZOOM_MIN = 1f
        private const val ZOOM_STEP = 0.5f
        // 对焦自动取消时间（毫秒）
        private const val FOCUS_CANCEL_DELAY_MS = 5000L
        // 对焦锁定状态保持时间（毫秒）
        private const val FOCUS_LOCK_DURATION_MS = 3000L
        // 变焦动画时长（毫秒）
        private const val ZOOM_ANIMATION_DURATION_MS = 300L
    }

    // 相机相关成员
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null

    // 当前使用的相机镜头（0 = 后置，1 = 前置）
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var currentZoom = 1f
    private var targetZoom = 1f

    // 变焦动画器
    private var zoomAnimator: ValueAnimator? = null

    // 对焦相关
    private var focusLocked = false
    private var focusCancelRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var previewView: PreviewView

    // 相机回调
    var onCameraReady: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null
    var onFocusStateChanged: ((FocusState) -> Unit)? = null
    var onZoomRangeUpdated: ((Float, Float) -> Unit)? = null

    var onFrameAvailable: ((Bitmap) -> Unit)? = null

    /**
     * 初始化相机
     */
    fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        this.previewView = previewView
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindPreviewAndCapture(lifecycleOwner, previewView)
                setupZoomStateListener()
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

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(currentLensFacing)
            .build()

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            currentZoom = 1f
            targetZoom = 1f
            focusLocked = false
            setupZoomStateListener()
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机用例失败", e)
            onCameraError?.invoke("相机配置失败：${e.message}")
        }
    }

    /**
     * 监听变焦状态，自动适配最大变焦倍数
     */
    private fun setupZoomStateListener() {
        try {
            camera?.cameraInfo?.zoomState?.observe((context as? androidx.lifecycle.LifecycleOwner) ?: return) { zoomState ->
                val minZoom = zoomState.minZoomRatio
                val maxZoom = zoomState.maxZoomRatio
                Log.d(TAG, "变焦范围已更新: $minZoom - $maxZoom")
                onZoomRangeUpdated?.invoke(minZoom, maxZoom)
            }
        } catch (e: Exception) {
            Log.w(TAG, "设置变焦状态监听失败", e)
        }
    }

    /**
     * 拍照
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

        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
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
     */
    private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap {
        val planes = image.planes
        val width = image.width
        val height = image.height

        val buffer = planes[0].buffer
        buffer.rewind()

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = try {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "BitmapFactory 解码失败，使用备用方案", e)
            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        }

        val rotationDegrees = image.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees)
        } else {
            bitmap
        }
    }

    /**
     * 旋转 Bitmap
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
     */
    fun releaseCamera() {
        try {
            cancelFocusAnimation()
            mainHandler.removeCallbacks(focusCancelRunnable ?: return)
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "释放相机资源失败", e)
        }
    }

    /**
     * 切换前后置摄像头
     */
    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: androidx.camera.view.PreviewView
    ): Int? {
        return try {
            currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindPreviewAndCapture(lifecycleOwner, previewView)
            Log.d(TAG, "成功切换到${if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) "前置" else "后置"}摄像头")
            currentLensFacing
        } catch (e: Exception) {
            Log.e(TAG, "切换摄像头失败", e)
            onCameraError?.invoke("切换摄像头失败：${e.message}")
            null
        }
    }

    /**
     * 获取当前镜头朝向
     */
    fun getCurrentLensFacing(): Int = currentLensFacing

    /**
     * 设置变焦（支持平滑动画）
     * @param zoomFactor 目标变焦倍数
     * @param animate 是否使用动画（true = 平滑变焦，false = 立即应用）
     * @return 实际应用的变焦比例
     */
    fun setZoom(zoomFactor: Float, animate: Boolean = true): Float {
        return try {
            val cameraControl = camera?.cameraControl ?: return currentZoom
            val cameraInfo = camera?.cameraInfo ?: return currentZoom

            // 获取设备的变焦范围
            val zoomState = cameraInfo.zoomState?.value
            val minZoom = zoomState?.minZoomRatio ?: 1f
            val maxZoom = zoomState?.maxZoomRatio ?: 4f

            // 限制变焦倍数在合理范围内
            targetZoom = zoomFactor.coerceIn(minZoom, maxZoom)

            if (animate) {
                // 使用 ValueAnimator 实现平滑变焦
                startZoomAnimation(currentZoom, targetZoom)
            } else {
                // 立即应用变焦
                currentZoom = targetZoom
                cameraControl.setZoomRatio(currentZoom)
            }

            Log.d(TAG, "变焦操作完成，当前变焦比例: $currentZoom，目标: $targetZoom")
            currentZoom
        } catch (e: Exception) {
            Log.e(TAG, "变焦操作失败", e)
            onCameraError?.invoke("变焦操作失败：${e.message}")
            currentZoom
        }
    }

    /**
     * 启动变焦动画，实现平滑变焦效果
     */
    private fun startZoomAnimation(fromZoom: Float, toZoom: Float) {
        // 取消之前的动画
        zoomAnimator?.cancel()

        zoomAnimator = ValueAnimator.ofFloat(fromZoom, toZoom).apply {
            duration = ZOOM_ANIMATION_DURATION_MS
            addUpdateListener { animator ->
                currentZoom = animator.animatedValue as Float
                try {
                    camera?.cameraControl?.setZoomRatio(currentZoom)
                } catch (e: Exception) {
                    Log.e(TAG, "应用变焦失败", e)
                }
            }
            start()
        }
    }

    /**
     * 取消变焦动画
     */
    private fun cancelFocusAnimation() {
        zoomAnimator?.cancel()
        zoomAnimator = null
    }

    /**
     * 获取当前变焦比例
     */
    fun getCurrentZoom(): Float = currentZoom

    /**
     * 获取变焦范围信息
     * @return Triple(minZoom, maxZoom, currentZoom)
     */
    fun getZoomRangeInfo(): Triple<Float, Float, Float>? {
        return try {
            val zoomState = camera?.cameraInfo?.zoomState?.value ?: return null
            Triple(zoomState.minZoomRatio, zoomState.maxZoomRatio, currentZoom)
        } catch (e: Exception) {
            Log.e(TAG, "获取变焦范围失败", e)
            null
        }
    }

    /**
     * 自动对焦指定点
     * @param x 对焦点 X 坐标（0-1 相对坐标）
     * @param y 对焦点 Y 坐标（0-1 相对坐标）
     * @return 成功返回 true
     */
    fun autoFocus(x: Float, y: Float): Boolean {
        return try {
            if (camera == null) {
                Log.w(TAG, "相机未初始化，无法对焦")
                return false
            }

            val cameraInfo = camera?.cameraInfo ?: return false

            // 创建测光点
            val meteringPoint = previewView.meteringPointFactory.createPoint(x, y)

            // 创建对焦行为（对焦 + 自动曝光）
            val focusMeteringAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(FOCUS_CANCEL_DELAY_MS, TimeUnit.MILLISECONDS)
                .build()

            // 执行对焦
            camera?.cameraControl?.startFocusAndMetering(focusMeteringAction)

            onFocusStateChanged?.invoke(FocusState.Focusing)

            Log.d(TAG, "自动对焦发起，位置: ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "自动对焦失败", e)
            onCameraError?.invoke("对焦失败：${e.message}")
            false
        }
    }

    /**
     * 锁定对焦和曝光（长按触发）
     * @param x 对焦点 X 坐标（0-1 相对坐标）
     * @param y 对焦点 Y 坐标（0-1 相对坐标）
     * @return 成功返回 true
     */
    fun lockFocus(x: Float, y: Float): Boolean {
        return try {
            if (camera == null) {
                Log.w(TAG, "相机未初始化，无法锁定对焦")
                return false
            }

            val cameraInfo = camera?.cameraInfo ?: return false

            // 创建测光点
            val meteringPoint = previewView.meteringPointFactory.createPoint(x, y)

            // 创建对焦行为（对焦 + 自动曝光）
            // 不自动取消，保持锁定状态
            val focusMeteringAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(FOCUS_LOCK_DURATION_MS, TimeUnit.MILLISECONDS)
                .build()

            // 执行对焦
            camera?.cameraControl?.startFocusAndMetering(focusMeteringAction)

            focusLocked = true
            onFocusStateChanged?.invoke(FocusState.Locked)

            Log.d(TAG, "对焦已锁定，位置: ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "锁定对焦失败", e)
            onCameraError?.invoke("对焦锁定失败：${e.message}")
            false
        }
    }

    /**
     * 重置对焦（取消手动对焦，恢复自动对焦）
     */
    fun resetFocus(): Boolean {
        return try {
            camera?.cameraControl?.cancelFocusAndMetering()
            focusLocked = false
            onFocusStateChanged?.invoke(FocusState.Idle)
            Log.d(TAG, "对焦已重置")
            true
        } catch (e: Exception) {
            Log.e(TAG, "重置对焦失败", e)
            false
        }
    }

    /**
     * 快速变焦（放大）
     */
    fun zoomIn(): Float {
        return setZoom(currentZoom + ZOOM_STEP, animate = true)
    }

    /**
     * 快速变焦（缩小）
     */
    fun zoomOut(): Float {
        return setZoom(currentZoom - ZOOM_STEP, animate = true)
    }

    /**
     * 重置变焦到 1x
     */
    fun resetZoom(): Float {
        return setZoom(ZOOM_MIN, animate = true)
    }

    /**
     * 检查设备是否有摄像头
     */
    fun hasCameraDevice(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * 获取实时帧（预留接口）
     */
    fun getRealtimeFrame() {
        // TODO: 实现实时帧获取逻辑
    }

    /**
     * 对焦状态定义
     */
    enum class FocusState {
        Idle,       // 空闲状态
        Focusing,   // 对焦中
        Locked,     // 对焦锁定
        Failed      // 对焦失败
    }
}
