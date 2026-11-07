package com.picacompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.picacompose.data.initPicaStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 「记住账号」要落盘，先把 applicationContext 交给共享层
        initPicaStorage(applicationContext)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
