package com.example.symp9

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request read permission if necessary
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SignMonitoringScreen()
            }
        }
    }
}

@Composable
fun SignMonitoringScreen() {
    var heartRate by remember { mutableStateOf("Not Measured") }
    var respiratoryRate by remember { mutableStateOf("Not Measured") }

    val context = LocalContext.current // LocalContext can only be used inside a Composable function
    val scope = rememberCoroutineScope() // Launch Coroutine scope inside Composable

    // UI Layout using Column
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Title
        Text(
            text = "PROJECT-1",
            style = MaterialTheme.typography.titleLarge
        )

        // Display the current heart rate
        Text(text = "Heart Rate: $heartRate")

        // Button to select video and calculate heart rate in the background
        Button(
            onClick = {
                // Launch coroutine for video selection and heart rate calculation
                scope.launch {
                    // Automatically select the video 'heartrate.mp4' from the emulator's storage
                    val videoFile = File("/storage/emulated/0/Movies/Heartrate567.mp4")

                    if (videoFile.exists()) {
                        val videoUri = Uri.fromFile(videoFile)
                        val result = heartRateCalculator(videoUri, context.contentResolver)
                        heartRate = "$result bpm" // Update the heart rate after calculation
                    } else {
                        heartRate = "Video not found"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CALCULATE HEART RATE")
        }

        // Display the current respiratory rate
        Text(text = "Respiratory Rate: $respiratoryRate")
        // Button to measure respiratory rate (simulate)
        Button(
            onClick = { respiratoryRate = "66.86 breaths/min" }, // Simulate respiratory rate measurement
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("MEASURE RESPIRATORY RATE")
        }

        // New button to navigate to Symptoms Activity
        Button(
            onClick = {
                // Navigate to SymptomsActivity
                val intent = Intent(context, SymptomsActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VIEW SYMPTOMS")
        }
    }
}

// Helper function to calculate heart rate from video frames
private suspend fun heartRateCalculator(uri: Uri, contentResolver: ContentResolver): Int {
    return withContext(Dispatchers.IO) {
        val result: Int
        val retriever = MediaMetadataRetriever()
        val frameList = ArrayList<Bitmap>()
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor) // Set data source using file descriptor
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?: return@withContext 0

            val frameDuration = minOf(duration.toInt(), 425)
            var i = 10
            while (i < frameDuration) {
                val bitmap = retriever.getFrameAtIndex(i)
                bitmap?.let { frameList.add(it) }
                i += 15
            }
            if (frameList.isEmpty()) {
                return@withContext 0
            }

        } catch (e: Exception) {
            Log.d("MediaPath", "convertMediaUriToPath: ${e.stackTrace} ")
            return@withContext 0
        } finally {
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
                return@withContext 0
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
        }
        result
    }
}
