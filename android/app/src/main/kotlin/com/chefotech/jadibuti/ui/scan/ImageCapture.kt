package com.chefotech.jadibuti.ui.scan

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.chefotech.jadibuti.ui.components.BigOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies/downscales a picked image into the cache as JPEG (max 1600px, quality 85). */
suspend fun prepareImage(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: error("Could not read image")
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val out = File(dir, "img-${UUID.randomUUID()}.jpg")
    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    bitmap.recycle()
    out
}

/** "Take photo" + "Choose image" buttons wired to camera (with permission) and the photo picker. */
@Composable
fun ImagePickerButtons(onImage: (File) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { u -> scope.launch { onImage(prepareImage(context, u)) } } }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val f = File(dir, "camera-${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri = uri
            takePicture.launch(uri)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { u -> scope.launch { onImage(prepareImage(context, u)) } } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigOutlinedButton("Take photo", icon = Icons.Default.CameraAlt, modifier = Modifier.weight(1f), onClick = { cameraPermission.launch(Manifest.permission.CAMERA) })
        BigOutlinedButton("Choose image", icon = Icons.Default.Image, modifier = Modifier.weight(1f), onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
}
