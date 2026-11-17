package com.picacompose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.picacompose.data.initDownloadNotifier
import com.picacompose.data.initExportNotifier
import com.picacompose.data.initDownloadStorage
import com.picacompose.data.initPicaStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 「记住账号」和本子缓存都要落盘，先把 applicationContext 交给共享层
        initPicaStorage(applicationContext)
        initDownloadStorage(applicationContext)
        initDownloadNotifier(applicationContext)
        initExportNotifier(applicationContext)
        // 缓存进度挂在通知栏上，Android 13 起要先问一句；不给也不影响下载，只是没有通知。
        // 只问第一次：拒了之后每次启动都弹一遍太烦。
        val notifyPrefs = getSharedPreferences("download_notify", MODE_PRIVATE)
        if (!notifyPrefs.getBoolean("asked", false) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPrefs.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
