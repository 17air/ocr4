package com.example.businesscardocr

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.provider.MediaStore
import com.example.businesscardocr.ui.theme.BusinessCardOCRTheme
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BusinessCardOCRTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.processImage(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.processImage(bitmap)
        }
    }

    Column {
        Row {
            Button(onClick = { cameraLauncher.launch() }) {
                Text(text = context.getString(R.string.take_photo))
            }
            Button(onClick = { galleryLauncher.launch("image/*") }) {
                Text(text = context.getString(R.string.pick_gallery))
            }
        }
        Text(text = context.getString(R.string.ocr_text) + "\n" + state.ocrText)
        Text(text = context.getString(R.string.ner_result))
        Text(text = "이름: ${state.name}")
        Text(text = "전화번호: ${state.phone}")
        Text(text = "메일: ${state.email}")
        Text(text = "직책: ${state.title}")
        Text(text = "회사: ${state.company}")
    }
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val nerApi: NerApi
    private val recognizer =
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    init {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        nerApi = Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NerApi::class.java)
    }

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            val image = InputImage.fromBitmap(bitmap, 0)
            val textResult = recognizer.process(image).await()
            val text = textResult.text
            _uiState.value = _uiState.value.copy(ocrText = text)

            val nerResponse = nerApi.analyze(
                NerRequest(text),
                auth = "Bearer YOUR_HUGGINGFACE_TOKEN"
            )
            val categorized = parseEntities(text, nerResponse)
            _uiState.value = _uiState.value.copy(
                name = categorized.name,
                phone = categorized.phone,
                email = categorized.email,
                title = categorized.title,
                company = categorized.company
            )
        }
    }

    private fun parseEntities(text: String, entities: List<NerEntity>): CategorizedText {
        var name = ""
        var phone = ""
        var email = ""
        var title = ""
        var company = ""
        // Simplified extraction based on entity labels
        entities.forEach { e ->
            when (e.entityGroup) {
                "PER" -> if (name.isEmpty()) name = e.word
                "PHONE" -> if (phone.isEmpty()) phone = e.word
                "EMAIL" -> if (email.isEmpty()) email = e.word
                "TITLE" -> if (title.isEmpty()) title = e.word
                "ORG" -> if (company.isEmpty()) company = e.word
            }
        }
        return CategorizedText(name, phone, email, title, company)
    }

}

data class UiState(
    val ocrText: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val title: String = "",
    val company: String = ""
)

data class CategorizedText(
    val name: String,
    val phone: String,
    val email: String,
    val title: String,
    val company: String
)


// NER API models and interface

data class NerRequest(val inputs: String)

data class NerEntity(
    val entityGroup: String,
    val word: String
)

interface NerApi {
    @POST("models/<your-model>")
    suspend fun analyze(
        @Body request: NerRequest,
        @Header("Authorization") auth: String
    ): List<NerEntity>
}

@Preview
@Composable
defaultPreview() {
    BusinessCardOCRTheme {
        MainScreen(MainViewModel())
    }
}

