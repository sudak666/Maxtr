package ua.zminka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ua.zminka.app.ui.navigation.ZminkaNavHost
import ua.zminka.app.ui.theme.ZminkaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZminkaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZminkaNavHost()
                }
            }
        }
    }
}
