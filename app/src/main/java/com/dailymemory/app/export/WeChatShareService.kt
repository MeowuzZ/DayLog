package com.dailymemory.app.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.util.UUID

object WeChatShareService {
    fun share(context: Context, fileName: String, mimeType: String, write: (OutputStream) -> Unit) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            // Text exports are attachments, so avoid WeChat's plain-message share route.
            type = if (mimeType.startsWith("text/")) "application/octet-stream" else mimeType
            setPackage("com.tencent.mm")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (shareIntent.resolveActivity(context.packageManager) == null) {
            shareIntent.type = "application/octet-stream"
        }
        require(shareIntent.resolveActivity(context.packageManager) != null) {
            "未找到可用的微信分享入口，请安装或更新微信后再试"
        }

        // Keep each export separate while WeChat may still be reading an earlier file.
        val directory = File(context.cacheDir, "shared_exports/${UUID.randomUUID()}")
        check(directory.mkdirs()) { "无法创建导出文件，请检查可用存储空间" }
        try {
            val file = File(directory, fileName)
            file.outputStream().use(write)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.putExtra(Intent.EXTRA_TITLE, file.name)
            shareIntent.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            context.startActivity(shareIntent)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
}
