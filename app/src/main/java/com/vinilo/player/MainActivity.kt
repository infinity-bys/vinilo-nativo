package com.vinilo.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vinilo.player.ui.VinyloApp
import com.vinilo.player.ui.VinyloTheme

class MainActivity : ComponentActivity() {

    private lateinit var holder: PlayerHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        holder = PlayerHolder(applicationContext)
        holder.connect()
        setContent {
            VinyloTheme {
                VinyloApp(holder)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        holder.resumeTick++
    }

    override fun onStop() {
        holder.saveLast()
        super.onStop()
    }

    override fun onDestroy() {
        holder.release()
        super.onDestroy()
    }
}
