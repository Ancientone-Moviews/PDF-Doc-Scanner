package com.example.data

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Moshi Compatible Request/Response Data Classes ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Retrofit Interface ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

// --- API Client ---

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Compress to 80% quality to save bandwidth and fit within token limits
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Performs OCR on a document bitmap and returns the extracted text.
     */
    suspend fun performOcr(bitmap: Bitmap, documentType: String = "Document"): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return "Error: Gemini API Key is missing. Please configure it in the Secrets panel in AI Studio."
        }

        val prompt = """
            Analyze this image of a $documentType. Perform high-accuracy Optical Character Recognition (OCR) to extract all text.
            Maintain the formatting as closely as possible (e.g. paragraphs, bullet lists, table layouts, headers, and key-value sections).
            If it is a business card, extract details clearly like Name, Title, Company, Phone, Email, and Address.
            If it is a receipt, extract details clearly like Merchant, Date, Items, Tax, and Total.
            Return ONLY the extracted text content. Do not include any meta-commentary like "Here is the text".
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.1f)
        )

        return try {
            val response = service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No text detected in the document."
        } catch (e: Exception) {
            Log.e(TAG, "OCR Request failed", e)
            "Error extracting text: ${e.localizedMessage}"
        }
    }

    /**
     * Detects document edges/corners automatically.
     * Returns 4 normalized coordinates [TL, TR, BR, BL] scaled 0.0 to 1.0.
     * If detection fails, returns null.
     */
    suspend fun detectEdges(bitmap: Bitmap, paperSize: String? = null): List<PointF>? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        // Scale down bitmap for edge detection to save bandwidth and improve performance
        val scaledWidth = 400
        val scaledHeight = (bitmap.height * (scaledWidth.toFloat() / bitmap.width)).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val paperContext = if (paperSize != null && paperSize != "Auto") {
            "The document is expected to be a standard $paperSize size paper. Use its typical layout and aspect ratio constraints to find the corners accurately."
        } else {
            "Detect the boundaries of the main paper document, card, receipt, or whiteboard in the image."
        }

        val prompt = """
            Locate the corners of the main paper document, receipt, card, or whiteboard in this image.
            $paperContext
            Return the four outer corners (Top-Left, Top-Right, Bottom-Right, Bottom-Left) in order.
            Each corner must be represented as a percentage of the image width and height from 0.0 to 100.0.
            
            You MUST return ONLY a JSON block in this exact format:
            {
              "top_left": [x, y],
              "top_right": [x, y],
              "bottom_right": [x, y],
              "bottom_left": [x, y]
            }
            Do not include any other text or markdown wrappers.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = scaledBitmap.toBase64()))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1f
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "Edge detection JSON: $jsonText")
            if (jsonText != null) {
                parseCornersJson(jsonText)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Edge detection failed", e)
            null
        } finally {
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
        }
    }

    private fun parseCornersJson(jsonText: String): List<PointF>? {
        return try {
            // Robust parsing using regex to avoid moshi strictness on different whitespace
            val regex = """"(\w+)":\s*\[\s*([0-9.]+)\s*,\s*([0-9.]+)\s*]""".toRegex()
            val matches = regex.findAll(jsonText).associate {
                val key = it.groupValues[1]
                val x = it.groupValues[2].toFloatOrNull() ?: 0f
                val y = it.groupValues[3].toFloatOrNull() ?: 0f
                key to PointF(x / 100f, y / 100f) // Convert percentage (0-100) to ratio (0-1)
            }

            val topLeft = matches["top_left"] ?: PointF(0.1f, 0.1f)
            val topRight = matches["top_right"] ?: PointF(0.9f, 0.1f)
            val bottomRight = matches["bottom_right"] ?: PointF(0.9f, 0.9f)
            val bottomLeft = matches["bottom_left"] ?: PointF(0.1f, 0.9f)

            listOf(topLeft, topRight, bottomRight, bottomLeft)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing corners JSON", e)
            null
        }
    }
}
