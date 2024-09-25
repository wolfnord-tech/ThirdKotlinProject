package ru.wolfnord.thirdkotlinproject

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    // Создание состояния для отслеживания текущего экрана
    val isCameraScreen = remember { mutableStateOf(true) }

    // Если текущий экран - камера, показываем экран
    if (isCameraScreen.value) {
        CameraScreen(onNavigateToList = { isCameraScreen.value = false }) // При нажатии переключаем на список
    } else {
        ListScreen(onNavigateToCamera = { isCameraScreen.value = true }) // При нажатии переключаем на камеру
    }
}

@Composable
fun ListScreen(onNavigateToCamera: () -> Unit) {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<File>() }

    // Загрузка изображений из папки "photos"
    LaunchedEffect(Unit) {
        loadImages(context, images)
    }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = onNavigateToCamera) {
            Text(text = "Switch To Camera")
        }

        LazyColumn {
            items(images) { imageFile ->
                ImageCard(imageFile = imageFile)
            }
        }
    }
}

fun File.toBitmap(): Bitmap {
    return BitmapFactory.decodeFile(this.absolutePath)
}

@Composable
fun ImageCard(imageFile: File) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { /* Handler for click if needed */ },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = imageFile.toBitmap().asImageBitmap(),
            contentDescription = imageFile.name, // Исправлено с contentDescribtion на contentDescription
            modifier = Modifier.height(200.dp).fillMaxWidth()
        )
        Text(text = imageFile.name) // Отображение имени файла
    }
}

fun loadImages(context: Context, images: MutableList<File>) {
    val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photos")
    if (storageDir.exists()) {
        storageDir.listFiles()?.sortedByDescending { it.lastModified() }?.let {
            images.clear()
            images.addAll(it)
        }
    }
}

@Composable
fun CameraScreen(onNavigateToList: () -> Unit) {
    val context = LocalContext.current  // Получаем текущий контекст
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }  // Переменная для захвата изображения

    Column(modifier = Modifier.fillMaxSize()) {
        // Используем AndroidView для включения представления камеры
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)  // Создание представления предпросмотра
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()  // Получаем поставщика камеры
                    val preview = Preview.Builder().build()  // Создание экземпляра предварительного просмотра
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA  // Выбор задней камеры

                    imageCapture = ImageCapture.Builder().build()  // Создание экземпляра захвата изображения

                    preview.setSurfaceProvider(previewView.surfaceProvider)  // Устанавливаем провайдер для предпросмотра

                    // Привязываем жизненный цикл камеры к текущей активности
                    cameraProvider.bindToLifecycle(
                        (ctx as ComponentActivity), cameraSelector, preview, imageCapture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView  // Возвращаем вид для отображения
            },
            modifier = Modifier.weight(1f)  // Позволяем представлению занимать весь размер экрана
        )

        // Кнопка для захвата фотографии
        Button(onClick = { imageCapture?.let { takePhoto(context, it) } }) {
            Text("Capture Photo")  // Текст кнопки
        }

        // Кнопка для переключения на список
        Button(onClick = { onNavigateToList() }) {
            Text("Switch To List")  // Текст кнопки
        }
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture) {
    // Создаем имя файла с использованием текущей даты и времени
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFileName = "JPEG_$timeStamp.jpg"

    // Создаем выходные параметры для захвата изображения
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, photoFileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES) // Для Android 10 и выше
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()  // Показываем тост
                Log.d("Camera", msg)  // Логируем сообщение
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Photo capture failed: ${exc.message}", exc)  // Логируем ошибку
            }
        }
    )
}
