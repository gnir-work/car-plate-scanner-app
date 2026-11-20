package com.lpscanner.app.ui

import kotlinx.coroutines.delay
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lpscanner.app.ui.theme.AppIcons
import java.util.concurrent.Executors

private val CameraBg = Color(0xFF1B2230)

/**
 * Live camera preview with a shutter and a Gallery button (design: "Scanner – camera").
 * Pinch to zoom and tap to focus come with CameraX's controller.
 *
 * @param onCaptured called (off the main thread) with the photo, rotated upright.
 */
@Composable
fun CameraPanel(
    busy: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onCaptureFailed: (String) -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    // After a second "deny", Android stops showing the prompt; then only Settings can turn it on.
    var blocked by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        val activity = context.findActivity()
        blocked = !ok && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            request.launch(Manifest.permission.CAMERA)
        }
    }
    // Back from Settings with the permission switched on.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose { }
    }

    Box(modifier.clipToBounds().background(CameraBg)) {
        if (granted) {
            CameraPreview(busy, onCaptured, onCaptureFailed)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(AppIcons.Camera, contentDescription = null, tint = Color(0xFFC9D1DC), modifier = Modifier.size(32.dp))
                Text(
                    stringResource(R.string.scan_camera_needed),
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = {
                    if (blocked) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                        )
                    } else {
                        request.launch(Manifest.permission.CAMERA)
                    }
                }) { Text(stringResource(if (blocked) R.string.scan_open_settings else R.string.scan_allow_camera)) }
            }
        }
        Text(
            stringResource(R.string.scan_title),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 32.dp).height(48.dp)
                .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .clickable(onClick = onGallery, enabled = !busy, role = Role.Button)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(AppIcons.Gallery, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.scan_gallery), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CameraPreview(
    busy: Boolean,
    onCaptured: (Bitmap) -> Unit,
    onCaptureFailed: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            // Enough pixels for a plate across the street, without the full 12+ MP the OCR would scale down anyway.
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(2560, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                )
                .build()
        }
    }
    // Known once the camera is open; phones without a flash (and the emulator) get no button.
    var hasFlash by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    LaunchedEffect(controller) {
        // cameraInfo stays null until the camera has actually opened, a moment after binding.
        while (controller.cameraInfo == null) delay(100)
        hasFlash = controller.cameraInfo?.hasFlashUnit() == true
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(flashOn) {
        controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    var capturing by remember { mutableStateOf(false) } // tap → photo delivered; blocks double taps
    val takePhoto = stringResource(R.string.scan_take_photo)

    AndroidView(
        factory = {
            PreviewView(it).apply {
                // TextureView-based: clips and resizes with the Compose layout (a SurfaceView draws outside it).
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    PlateGuide(Modifier.align(Alignment.Center))

    if (hasFlash) {
        FlashToggle(flashOn, onToggle = { flashOn = it }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }

    // Shutter: white ring with a white disc, as in the design.
    Box(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(72.dp)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = !busy && !capturing, role = Role.Button) {
                capturing = true
                controller.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val photo = image.use { it.toUprightBitmap() }
                            capturing = false
                            onCaptured(photo)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            onCaptureFailed(exception.message ?: "camera error ${exception.imageCaptureError}")
                        }
                    },
                )
            }
            .semantics { contentDescription = takePhoto },
        contentAlignment = Alignment.Center,
    ) {
        if (busy || capturing) {
            CircularProgressIndicator(Modifier.size(54.dp), color = Color.White, strokeWidth = 3.dp)
        } else {
            Box(Modifier.size(54.dp).background(Color.White, CircleShape))
        }
    }
}

/**
 * Where to put the plate: corner brackets the shape of an Israeli plate (52 × 11 cm), 40% of
 * the preview's width and a little below its middle, which is about a plate's size from 1–2 m once zoomed in a little. Only a
 * guide: the whole photo is still read. Draws nothing touchable, so pinch-to-zoom and
 * tap-to-focus reach the preview underneath.
 */
@Composable
private fun PlateGuide(modifier: Modifier = Modifier) {
    // A bit below the middle: where a plate naturally sits when holding the phone at chest height.
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = BiasAlignment(0f, GUIDE_VERTICAL_BIAS)) {
        val width = maxWidth * GUIDE_WIDTH
        val height = width / PLATE_ASPECT
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Same height above as the hint below, so the frame itself sits in the middle.
            Spacer(Modifier.height(HINT_SPACE))
            Canvas(Modifier.size(width + 8.dp, height + 8.dp)) {
                // Long sides, short ends: reads as a wide plate rather than a square.
                val across = size.width * 0.18f
                val down = size.height * 0.32f
                val inset = 2.dp.toPx()
                val (l, t, r, b) = listOf(inset, inset, size.width - inset, size.height - inset)
                val corners = Path().apply {
                    moveTo(l, t + down); lineTo(l, t); lineTo(l + across, t)
                    moveTo(r - across, t); lineTo(r, t); lineTo(r, t + down)
                    moveTo(r, b - down); lineTo(r, b); lineTo(r - across, b)
                    moveTo(l + across, b); lineTo(l, b); lineTo(l, b - down)
                }
                // A dark outline under the white keeps the brackets visible on bright scenes.
                drawPath(corners, Color.Black.copy(alpha = 0.35f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(corners, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text(
                stringResource(R.string.scan_guide_hint),
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f)),
                modifier = Modifier.height(HINT_SPACE).padding(top = 8.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

private const val GUIDE_WIDTH = 0.4f
/** -1 top, 0 middle, 1 bottom of the camera view. */
private const val GUIDE_VERTICAL_BIAS = 0.25f
private const val PLATE_ASPECT = 520f / 110f
private val HINT_SPACE = 36.dp

/** Round button over the camera: flash fires on capture when on (filled), never when off. */
@Composable
private fun FlashToggle(on: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.scan_flash)
    Box(
        modifier.size(48.dp)
            .background(if (on) Color.White else Color.White.copy(alpha = 0.16f), CircleShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) AppIcons.Flash else AppIcons.FlashOff,
            contentDescription = null,
            tint = if (on) CameraBg else Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
