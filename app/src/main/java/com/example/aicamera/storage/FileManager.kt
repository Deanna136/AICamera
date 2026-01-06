package com.example.aicamera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器
 * 职责：处理照片保存、相册刷新
 *
 * 特点：
 * - 遵循 Android 10+ Scoped Storage（分区存储）规范
 * - 无需 WRITE_EXTERNAL_STORAGE 权限（仅在 Android 12+ 需要对 Pictures 目录的权限）
 * - 保存后自动刷新相册
 *
 * 扩展点：
 * - 后续可添加文件上传逻辑到后端AI服务
 * - 可添加本地缓存管理（清理过期照片等）
 */
class FileManager(private val context: Context) {

    companion object {
        private const val TAG = "FileManager"
    }

    /**
     * 保存照片到系统相册（使用 MediaStore API）
     *
     * @param bitmap 要保存的位图
     * @return 保存成功返回 Uri，失败返回 null
     *
     * 扩展点：此处可在成功保存后触发后端上传逻辑
     */
    suspend fun saveBitmapToGallery(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentResolver = context.contentResolver
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "IMG_${timeStamp}.jpg"

            // 创建 ContentValues，设置照片元数据
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AiCamera")
                // 在 Android 11+ 上需要设置 IS_PENDING
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            // 插入到 MediaStore
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: run {
                Log.e(TAG, "无法向 MediaStore 插入图像 URI")
                return@withContext null
            }

            Log.d(TAG, "成功创建 MediaStore URI: $uri")

            // 写入位图数据
            val outputStream = contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                outputStream.use { stream ->
                    val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    if (!compressed) {
                        Log.w(TAG, "位图压缩失败")
                        contentResolver.delete(uri, null, null)
                        return@withContext null
                    }
                }
            } else {
                Log.e(TAG, "无法打开输出流")
                contentResolver.delete(uri, null, null)
                return@withContext null
            }

            // 完成写入，更新 IS_PENDING 标志
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                val updated = contentResolver.update(uri, contentValues, null, null)
                if (updated <= 0) {
                    Log.w(TAG, "更新 IS_PENDING 标志失败，但继续处理")
                }
            }

            Log.d(TAG, "照片成功保存到相册: $uri")
            // 返回文件路径或 URI 字符串
            uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "保存照片异常", e)
            null
        }
    }

    /**
     * 保存照片到应用私有目录（仅当相册保存失败时使用）
     * 这是一个备用方案，不建议在正常流程中使用
     *
     * @param bitmap 要保存的位图
     * @return 保存成功返回文件路径，失败返回 null
     */
    suspend fun saveBitmapToAppCache(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheDir = context.cacheDir
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = java.io.File(cacheDir, "IMG_${timeStamp}.jpg")

            file.outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 检查是否有足够空间保存照片
     *
     * @return 有足够空间返回 true
     */
    fun hasEnoughStorage(): Boolean {
        return try {
            val stat = android.os.StatFs(Environment.getExternalStorageDirectory().path)
            val availableBytes = stat.availableBytes
            val requiredBytes = 5 * 1024 * 1024 // 至少需要 5MB
            availableBytes > requiredBytes
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取应用专属缓存目录大小
     * 可用于日志或调试
     *
     * @return 目录大小（字节）
     */
    fun getCacheDirSize(): Long {
        return try {
            val cacheDir = context.cacheDir
            getDirectorySize(cacheDir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 递归计算目录大小
     */
    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirectorySize(file)
                } else {
                    file.length()
                }
            }
        } else {
            size = directory.length()
        }
        return size
    }

    /**
     * 清理应用缓存
     * 可用于释放存储空间
     *
     * @return 清理成功返回 true
     */
    fun clearAppCache(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            deleteRecursively(cacheDir)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 递归删除目录及文件
     */
    private fun deleteRecursively(file: java.io.File): Boolean {
        return if (file.isDirectory) {
            file.listFiles()?.all { deleteRecursively(it) } ?: false && file.delete()
        } else {
            file.delete()
        }
    }
}

