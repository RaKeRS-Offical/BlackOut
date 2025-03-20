package com.example.blackoutx

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.blackoutx.ui.theme.BlackOutXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlackOutXTheme {
                BlackoutScreen()
            }
        }
    }
}

@Composable
fun BlackoutScreen() {
    var isBlackoutActive by remember { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val irCommands = remember { loadAllIRCommandsFromJson(context, "Xiaomi_TV.json") }
    var irProgress by remember { mutableFloatStateOf(0f) }
    var isSendingIR by remember { mutableStateOf(false) }

    LaunchedEffect(isBlackoutActive) {
        if (isBlackoutActive && !isSendingIR) {
            while (isBlackoutActive) {
                offsetX.animateTo(
                    targetValue = (100..300).random().toFloat(),
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
                offsetY.animateTo(
                    targetValue = (50..150).random().toFloat(),
                    animationSpec = tween(durationMillis = 50, easing = LinearEasing)
                )
                offsetX.animateTo(0f, animationSpec = tween(durationMillis = 50, easing = LinearEasing))
                offsetY.animateTo(0f, animationSpec = tween(durationMillis = 50, easing = LinearEasing))
                delay(1000)
            }
        } else {
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.black),
                contentDescription = "Blackout Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .offset { IntOffset(offsetX.value.toInt(), offsetY.value.toInt()) },
                colorFilter = if (isBlackoutActive) ColorFilter.tint(Color.Red) else null
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        if (!isSendingIR) {
                            isBlackoutActive = !isBlackoutActive
                            if (isBlackoutActive) {
                                sendIRCommands(context, irCommands, { progress ->
                                    irProgress = progress
                                }, { newState ->
                                    isBlackoutActive = newState
                                    isSendingIR = false
                                })
                                isSendingIR = true
                                irProgress = 0f
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isSendingIR) "Sending..." else if (isBlackoutActive) "Stop" else "BlackOut",
                    color = Color.Black
                )
            }
            if (isSendingIR) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    progress = { irProgress }
                )
            }
        }
    }
}

fun loadAllIRCommandsFromJson(context: Context, fileName: String): List<Pair<Int, IntArray>> {
    val commands = mutableListOf<Pair<Int, IntArray>>()
    try {
        val inputStream: InputStream = context.assets.open(fileName)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = org.json.JSONObject(jsonString)
        val jsonArray = jsonObject.getJSONArray("commands")

        for (i in 0 until jsonArray.length()) {
            val commandObject = jsonArray.getJSONObject(i)
            val frequency = commandObject.getInt("frequency")
            val patternArray = commandObject.getJSONArray("pattern")
            val pattern = IntArray(patternArray.length()) { index -> patternArray.getInt(index) }
            commands.add(Pair(frequency, pattern))
        }
    } catch (e: IOException) {
        Log.e("IRCommand", "JSON dosyası okuma hatası", e)
        showToast(context, "IR komutları yüklenirken dosya okuma hatası oluştu.")
    } catch (e: JSONException) {
        Log.e("IRCommand", "JSON format hatası", e)
        showToast(context, "IR komutları yüklenirken JSON format hatası oluştu.")
    } catch (e: Exception) {
        Log.e("IRCommand", "JSON yükleme hatası", e)
        showToast(context, "IR komutları yüklenirken hata oluştu.")
    }
    return commands
}

fun sendIRCommands(
    context: Context,
    commands: List<Pair<Int, IntArray>>,
    onProgressUpdate: (Float) -> Unit,
    onBlackoutStateChange: (Boolean) -> Unit
) {
    val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager?
    if (irManager != null && irManager.hasIrEmitter()) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                for ((index, command) in commands.withIndex()) {
                    val frequency = command.first
                    val pattern = command.second
                    irManager.transmit(frequency, pattern)
                    delay(0.5.toLong()) // Gecikme süresi 0.5 ms
                    onProgressUpdate((index + 1).toFloat() / commands.size)
                }
                onBlackoutStateChange(false)
            } catch (e: Exception) {
                Log.e("IRCommand", "IR command gönderme hatası", e)
                e.printStackTrace()
                showToast(context, "IR komutları gönderilirken hata oluştu: ${e.message}")
                onBlackoutStateChange(false)
            }
        }
    } else {
        showToast(context, "Cihazda IR Blaster bulunamadı!")
        onBlackoutStateChange(false)
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}