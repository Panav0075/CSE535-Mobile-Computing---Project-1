package com.example.hr3

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.hr3.ui.theme.HR3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val REQUEST_EXTERNAL_STORAGE = 1
    private val PERMISSIONS_STORAGE = arrayOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                "android.permission.MANAGE_EXTERNAL_STORAGE"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf("android.permission.MANAGE_EXTERNAL_STORAGE"), REQUEST_EXTERNAL_STORAGE
            )
        }

        setContent {
            HR3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    HeartRateCalculator()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Your code here
            }
        }
    }
}

@Composable
fun HeartRateCalculator() {
    val uri = Uri.parse("file:///sdcard/Heart Rate.mp4") // Update the URI to point to the video file
    val contentResolver = LocalContext.current.contentResolver
    var heartRate by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        heartRate = heartRateCalculator(uri, contentResolver)
    }

    Column {
        Text(text = "heartrate=97hrt")
        if (heartRate == -1) {
            // Handle the error case
            Text(text = "Error: Unable to read video file")
        } else {
            Text(text = "Calculated Heart Rate: $heartRate")
        }
    }
}

private suspend fun heartRateCalculator(uri: Uri, contentResolver: ContentResolver): Int {
    return withContext(Dispatchers.IO) {
        try {
            val result: Int
            val retriever = MediaMetadataRetriever()
            val frameList = ArrayList<Bitmap>()
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor) // Set data source using file descriptor
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?: return@withContext -1

            val frameDuration = kotlin.math.min(duration.toInt(), 425)
            var i = 10
            while (i < frameDuration) {
                val bitmap = retriever.getFrameAtIndex(i)
                if (bitmap != null) {
                    frameList.add(bitmap)
                } else {
                    Log.e("MediaMetadataRetriever", "video frame at index $i is a NULL pointer")
                }
                i += 15
            }
            if (frameList.isEmpty()) {
                return@withContext -1
            }

            retriever.release()
            var redBucket: Long
            var pixelCount: Long = 0
            val a = mutableListOf<Long>()
            for (frame in frameList) {
                redBucket = 0
                for (y in 350 until 450) { // Specify the area of the frame to analyze
                    for (x in 350 until 450) {
                        val pixel = frame.getPixel(x, y)
                        pixelCount++
                        redBucket += android.graphics.Color.red(pixel) + android.graphics.Color.blue(pixel) + android.graphics.Color.green(pixel)
                    }
                }
                a.add(redBucket)
            }
            val b = mutableListOf<Long>()
            for (i in 0 until a.size - 5) {
                val temp = (a[i] + a[i + 1] + a[i + 2] + a[i + 3] + a[i + 4]) / 4
                b.add(temp)
            }
            if (b.isEmpty()) {
                return@withContext -1
            }
            var x = b[0]
            var count = 0
            for (i in 1 until b.size) {
                val currentValue = b[i]
                if ((currentValue - x) > 3500) { // Threshold for detecting a heartbeat
                    count++
                }
                x = currentValue
            }
            val rate = ((count.toFloat()) * 60).toInt()
            result = (rate / 4)
            result
        } catch (e: Exception) {
            // Log the exception and return an error value
            return@withContext -1
        }
    }
}
    @Preview(showBackground = true)
    @Composable
    fun HeartRateCalculatorPreview() {
        HR3Theme {
            HeartRateCalculator()
        }
    }