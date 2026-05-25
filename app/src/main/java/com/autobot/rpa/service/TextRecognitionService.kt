
package com.autobot.rpa.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.system.measureTimeMillis

data class TextMatchResult(
    val x: Int,
    val y: Int,
    val similarity: Double,
    val text: String,
    val boundingBox: android.graphics.Rect?
) {
    // 为了兼容性，添加 centerX 和 centerY 属性
    val centerX: Int
        get() = x
    
    val centerY: Int
        get() = y
    
    // 为了兼容性，添加 confidence 属性
    val confidence: Double
        get() = similarity
}

class TextRecognitionService private constructor() {

    companion object {
        private const val TAG = "TextRecognitionService"
        @Volatile
        private var instance: TextRecognitionService? = null
        @Volatile
        private var isInitialized = false

        private val textRecognizer by lazy {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }

        fun getInstance(): TextRecognitionService {
            return instance ?: synchronized(this) {
                instance ?: TextRecognitionService().also { instance = it }
            }
        }

        fun init(context: android.content.Context) {
            Log.d(TAG, "TextRecognitionService initialized")
            isInitialized = true
        }

        fun waitForInit(timeout: Long = 0, unit: java.util.concurrent.TimeUnit? = null): Boolean {
            return true
        }

        fun release() {
            if (isInitialized) {
                try {
                    textRecognizer.close()
                    Log.d(TAG, "TextRecognitionService resources released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing TextRecognitionService resources", e)
                }
                instance = null
                isInitialized = false
            }
        }
    }

    data class TextRecognitionResult(
        val bestMatch: TextMatchResult?,
        val allMatches: List<TextMatchResult>
    )

    suspend fun findTextWithAllResults(
        bitmap: Bitmap,
        targetText: String,
        threshold: Double = 0.8
    ): TextRecognitionResult {
        Log.d(TAG, "===== Starting text recognition (with all results) ======")
        Log.d(TAG, "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")

        if (!isInitialized) {
            Log.e(TAG, "TextRecognitionService not initialized!")
            return TextRecognitionResult(null, emptyList())
        }

        Log.d(TAG, "Target text: $targetText")
        Log.d(TAG, "Threshold: $threshold")

        var bestMatch: TextMatchResult? = null
        var bestSimilarity = 0.0
        val allMatches = mutableListOf<TextMatchResult>()

        val timeMillis = measureTimeMillis {
            try {
                // Get all text detections first
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                Log.d(TAG, "Processing image with ML Kit Text Recognition...")
                val textResult: Text = textRecognizer.process(inputImage).await()
                
                Log.d(TAG, "Text recognition complete. Found ${textResult.textBlocks.size} text blocks")

                for ((blockIndex, block) in textResult.textBlocks.withIndex()) {
                    Log.d(TAG, "  Block $blockIndex: ${block.lines.size} lines, boundingBox=${block.boundingBox}")
                    
                    for ((lineIndex, line) in block.lines.withIndex()) {
                        val similarity = calculateSimilarity(line.text, targetText)
                        Log.d(TAG, "    Line $lineIndex: text='${line.text}', similarity=${String.format("%.3f", similarity)}, boundingBox=${line.boundingBox}")

                        val boundingBox = line.boundingBox
                        val centerX = boundingBox?.centerX() ?: 0
                        val centerY = boundingBox?.centerY() ?: 0

                        val match = TextMatchResult(
                            x = centerX,
                            y = centerY,
                            similarity = similarity,
                            text = line.text,
                            boundingBox = boundingBox
                        )
                        
                        allMatches.add(match)

                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestMatch = match
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during text recognition", e)
            }
        }

        Log.d(TAG, "Total recognition completed in $timeMillis ms, found ${allMatches.size} text elements")
        
        if (allMatches.isNotEmpty()) {
            Log.d(TAG, "All detected texts:")
            allMatches.forEachIndexed { index, match ->
                Log.d(TAG, "  #$index: '${match.text}' at (${match.x}, ${match.y}), bbox=${match.boundingBox}")
            }
        }

        if (bestSimilarity >= threshold) {
            Log.d(TAG, "✅ Text found at (${bestMatch?.x}, ${bestMatch?.y}) with similarity ${String.format("%.3f", bestSimilarity)}")
        } else {
            Log.d(TAG, "❌ No match above threshold (best: ${String.format("%.3f", bestSimilarity)}, threshold: $threshold)")
        }

        return TextRecognitionResult(bestMatch, allMatches)
    }

    suspend fun findText(
        bitmap: Bitmap,
        targetText: String,
        threshold: Double = 0.8
    ): TextMatchResult? {
        return findTextWithAllResults(bitmap, targetText, threshold).bestMatch
    }

    /**
     * 检测位图中的所有文本
     * @param bitmap 要检测的位图
     * @return 检测到的文本匹配结果列表
     */
    suspend fun detectAllText(bitmap: Bitmap): List<TextMatchResult> {
        Log.d(TAG, "===== Detecting all text in bitmap ======")
        
        if (!isInitialized) {
            Log.e(TAG, "TextRecognitionService not initialized!")
            return emptyList()
        }

        val results = mutableListOf<TextMatchResult>()

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val textResult: Text = textRecognizer.process(inputImage).await()

            for (block in textResult.textBlocks) {
                for (line in block.lines) {
                    val boundingBox = line.boundingBox
                    val centerX = boundingBox?.centerX() ?: 0
                    val centerY = boundingBox?.centerY() ?: 0

                    results.add(
                        TextMatchResult(
                            x = centerX,
                            y = centerY,
                            similarity = 1.0,
                            text = line.text,
                            boundingBox = boundingBox
                        )
                    )
                }
            }
            
            Log.d(TAG, "Detected ${results.size} text elements")
        } catch (e: Exception) {
            Log.e(TAG, "Error during all text detection", e)
        }

        return results
    }

    fun drawTextDetectionOnBitmap(
        bitmap: Bitmap,
        matchResult: TextMatchResult?,
        found: Boolean
    ): Bitmap {
        return drawMultipleTextDetectionsOnBitmap(bitmap, listOfNotNull(matchResult), "Target: ${matchResult?.text ?: "none"}")
    }
    
    fun drawMultipleTextDetectionsOnBitmap(
        bitmap: Bitmap,
        results: List<TextMatchResult>,
        title: String? = null
    ): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        // 边框画笔
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = Color.RED
        }
        
        // 文字画笔
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            textSize = 48f
            color = Color.RED
        }
        
        // 背景画笔 - 用于文字标签背景
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            alpha = 180
        }

        // 绘制标题（如果提供）
        if (title != null) {
            bgPaint.color = Color.BLACK
            bgPaint.alpha = 200
            canvas.drawRect(20f, 20f, (title.length * 40 + 60).toFloat(), 90f, bgPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = 56f
            canvas.drawText(title, 40f, 75f, textPaint)
        }
        
        // 绘制识别到的文字数量
        val countText = "Total detected: ${results.size}"
        bgPaint.color = Color.BLACK
        bgPaint.alpha = 200
        val countTextWidth = textPaint.measureText(countText)
        canvas.drawRect(20f, 100f, countTextWidth + 60f, 160f, bgPaint)
        textPaint.color = Color.YELLOW
        textPaint.textSize = 40f
        canvas.drawText(countText, 40f, 140f, textPaint)

        // 如果没有识别到任何文字，显示提示
        if (results.isEmpty()) {
            bgPaint.color = Color.YELLOW
            bgPaint.alpha = 200
            val warningText = "⚠️ No text detected by ML Kit"
            val warningWidth = textPaint.measureText(warningText)
            canvas.drawRect(20f, 180f, warningWidth + 60f, 250f, bgPaint)
            textPaint.color = Color.BLACK
            textPaint.textSize = 48f
            canvas.drawText(warningText, 40f, 230f, textPaint)
            Log.w(TAG, "No text detected in bitmap - showing warning")
        } else {
            for ((index, result) in results.withIndex()) {
                val color = when (index % 5) {
                    0 -> Color.RED
                    1 -> Color.BLUE
                    2 -> Color.GREEN
                    3 -> Color.MAGENTA
                    4 -> Color.CYAN
                    else -> Color.RED
                }
                
                result.boundingBox?.let { box ->
                    // 绘制边框
                    boxPaint.color = color
                    canvas.drawRect(box, boxPaint)

                    // 绘制文字标签
                    textPaint.color = color
                    val label = "#${index + 1}: ${result.text.take(30)}"
                    val yPos = (box.top - 10).toFloat().coerceAtLeast(280f)
                    
                    // 绘制标签背景
                    val labelWidth = textPaint.measureText(label)
                    bgPaint.color = color
                    bgPaint.alpha = 200
                    canvas.drawRect(box.left.toFloat() - 5f, yPos - 45f, box.left + labelWidth + 10f, yPos + 5f, bgPaint)
                    
                    textPaint.color = Color.WHITE
                    canvas.drawText(
                        label,
                        box.left.toFloat(),
                        yPos,
                        textPaint
                    )
                } ?: run {
                    // 如果没有 boundingBox，只在屏幕底部显示文字
                    val label = "#${index + 1}: ${result.text}"
                    val yPos = 300f + (index * 60f)
                    
                    bgPaint.color = color
                    bgPaint.alpha = 200
                    val labelWidth = textPaint.measureText(label)
                    canvas.drawRect(20f, yPos - 45f, labelWidth + 40f, yPos + 5f, bgPaint)
                    
                    textPaint.color = Color.WHITE
                    canvas.drawText(label, 40f, yPos, textPaint)
                    Log.w(TAG, "Text #${index + 1} has no boundingBox: '${result.text}'")
                }
            }
        }

        return mutableBitmap
    }

    private suspend fun tryTextRecognition(
        bitmap: Bitmap,
        targetText: String,
        threshold: Double
    ): TextMatchResult? {
        Log.d(TAG, "--- Starting ML Kit text recognition ---")

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val textResult: Text = textRecognizer.process(inputImage).await()

        var bestMatch: TextMatchResult? = null
        var bestSimilarity = 0.0

        for (block in textResult.textBlocks) {
            for (line in block.lines) {
                val similarity = calculateSimilarity(line.text, targetText)
                Log.d(TAG, "Found text: '${line.text}', similarity: ${String.format("%.3f", similarity)}")

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    val boundingBox = line.boundingBox
                    val centerX = boundingBox?.centerX() ?: 0
                    val centerY = boundingBox?.centerY() ?: 0

                    bestMatch = TextMatchResult(
                        x = centerX,
                        y = centerY,
                        similarity = similarity,
                        text = line.text,
                        boundingBox = boundingBox
                    )
                }
            }
        }

        return bestMatch
    }

    private fun calculateSimilarity(text1: String, text2: String): Double {
        val t1 = text1.lowercase().trim()
        val t2 = text2.lowercase().trim()

        if (t1.contains(t2) || t2.contains(t1)) {
            return 0.9
        }

        val distance = levenshteinDistance(t1, t2)
        val maxLength = maxOf(t1.length, t2.length)
        if (maxLength == 0) return 1.0

        return 1.0 - (distance.toDouble() / maxLength)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
