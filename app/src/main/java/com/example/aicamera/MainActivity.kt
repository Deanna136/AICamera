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
            // 相机权限已授予，继续请求其他权限
            checkAndRequestPermissions()
        } else {
            // 相机权限被拒绝
            viewModel.clearError()
        }
    }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 相册读取权限处理
        if (isGranted) {
            checkAndRequestPermissions()
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 存储写入权限处理（Android 12+）
        if (isGranted) {
            checkAndRequestPermissions()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 录音权限处理
        if (isGranted) {
            checkAndRequestPermissions()
        }
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
        permissionManager.registerStoragePermissionLauncher(storagePermissionLauncher)
        permissionManager.registerAudioPermissionLauncher(audioPermissionLauncher)

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
        // 检查是否所有权限都已授予
        val allPermissionsGranted = permissionManager.hasCameraPermission() &&
            permissionManager.hasGalleryPermission() &&
            permissionManager.hasStoragePermission() &&
            permissionManager.hasAudioPermission()

        if (allPermissionsGranted) {
            // 所有权限已授予，初始化相机
            viewModel.initializeCamera(this, androidx.camera.view.PreviewView(this))
        } else {
            // 按顺序请求缺失的权限
            if (!permissionManager.hasCameraPermission()) {
                permissionManager.requestCameraPermission()
            } else if (!permissionManager.hasGalleryPermission()) {
                permissionManager.requestGalleryPermission()
            } else if (!permissionManager.hasStoragePermission()) {
                permissionManager.requestStoragePermission()
            } else if (!permissionManager.hasAudioPermission()) {
                permissionManager.requestAudioPermission()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releaseCamera(this)
    }
}