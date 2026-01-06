package com.example.aicamera.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 权限管理器
 * 职责：统一管理相机、录音等权限申请
 *
 * 扩展点：
 * - 后续可添加其他权限（如位置、通知等）
 * - 可与后端结合上传权限状态日志
 */
class PermissionManager(private val context: Context) {

    // 权限检查回调
    var onCameraPermissionResult: ((Boolean) -> Unit)? = null
    var onAudioPermissionResult: ((Boolean) -> Unit)? = null

    /**
     * 检查是否已获得相机权限
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否已获得录音权限
     * 预留接口，当前暂不使用
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否已获得相册读取权限
     */
    fun hasGalleryPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 注册权限请求回调
     * 在Activity中调用 registerForActivityResult 后，传入对应的ActivityResultLauncher
     */
    fun registerCameraPermissionLauncher(launcher: ActivityResultLauncher<String>) {
        this.cameraPermissionLauncher = launcher
    }

    fun registerAudioPermissionLauncher(launcher: ActivityResultLauncher<String>) {
        this.audioPermissionLauncher = launcher
    }

    fun registerGalleryPermissionLauncher(launcher: ActivityResultLauncher<String>) {
        this.galleryPermissionLauncher = launcher
    }

    /**
     * 请求相机权限
     */
    fun requestCameraPermission() {
        if (::cameraPermissionLauncher.isInitialized) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 请求录音权限
     * 预留接口，当前暂不使用
     */
    fun requestAudioPermission() {
        if (::audioPermissionLauncher.isInitialized) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * 请求相册权限
     * 用于保存照片到相册
     */
    fun requestGalleryPermission() {
        if (::galleryPermissionLauncher.isInitialized) {
            galleryPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    /**
     * 请求所有必需权限
     * 包括：相机、相册读取权限
     * 预留：录音权限（当前暂不使用）
     */
    fun requestAllRequiredPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
        if (!hasGalleryPermission()) {
            requestGalleryPermission()
        }
    }

    // 权限请求启动器
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var galleryPermissionLauncher: ActivityResultLauncher<String>
}

