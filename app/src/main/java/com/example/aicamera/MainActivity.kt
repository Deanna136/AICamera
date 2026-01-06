package com.example.aicamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.aicamera.permission.PermissionManager
import com.example.aicamera.ui.CameraScreen
import com.example.aicamera.ui.CameraViewModel

/**
 * 主 Activity
 * 职责：管理权限、初始化 ViewModel、显示 UI
 *
 * 权限流程：
 * 1. 应用启动时检查相机权限
 * 2. 若未授予，请求用户授权
 * 3. 获得权限后初始化相机
 * 4. 如果用户拒绝，显示错误提示
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: CameraViewModel
    private lateinit var permissionManager: PermissionManager

    // 权限请求启动器
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 相机权限已授予，初始化相机
            viewModel.initializeCamera(this, androidx.camera.view.PreviewView(this))
        } else {
            // 相机权限被拒绝
            viewModel.clearError()
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 相册权限处理（当前暂不使用）
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化 ViewModel 和权限管理器
        viewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        permissionManager = PermissionManager(this)

        // 注册权限请求启动器
        permissionManager.registerCameraPermissionLauncher(cameraPermissionLauncher)
        permissionManager.registerGalleryPermissionLauncher(galleryPermissionLauncher)

        // 使用 Compose 设置 UI
        setContent {
            CameraScreen(
                viewModel = viewModel,
                lifecycleOwner = this
            )
        }

        // 检查权限
        checkAndRequestPermissions()
    }

    /**
     * 检查和请求必需的权限
     */
    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        // 检查是否所有权限都已授予
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            // 所有权限已授予，初始化相机
            // (注：需要在 UI 初始化后调用，这里通过 ViewModel 中的初始化逻辑处理)
        } else {
            // 请求相机权限（其他权限可按需添加）
            if (!permissionManager.hasCameraPermission()) {
                permissionManager.requestCameraPermission()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releaseCamera(this)
    }
}