package com.autobot.rpa.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

data class MatchResult(
    val x: Int,
    val y: Int,
    val similarity: Double
)

class ImageMatchingService private constructor() {

    companion object {
        private const val TAG = "ImageMatchingService"
        @Volatile
        private var instance: ImageMatchingService? = null
        @Volatile
        private var isInitialized = false

        fun getInstance(): ImageMatchingService {
            return instance ?: synchronized(this) {
                instance ?: ImageMatchingService().also { instance = it }
            }
        }

        fun init(context: Context) {
            isInitialized = true
            Log.d(TAG, "ImageMatchingService initialized successfully")
        }

        fun waitForInit(timeout: Long = 0, unit: java.util.concurrent.TimeUnit? = null): Boolean {
            return true
        }
    }

    fun findMatch(
        screenBitmap: Bitmap,
        templateBitmap: Bitmap,
        threshold: Double = 0.7
    ): MatchResult? {
        Log.d(TAG, "===== Starting image matching ======")
        
        if (!isInitialized) {
            Log.e(TAG, "ImageMatchingService not initialized!")
            return null
        }

        val screenWidth = screenBitmap.width
        val screenHeight = screenBitmap.height
        val templateWidth = templateBitmap.width
        val templateHeight = templateBitmap.height

        Log.d(TAG, "Screen size: ${screenWidth}x$screenHeight")
        Log.d(TAG, "Template size: ${templateWidth}x$templateHeight")
        Log.d(TAG, "Threshold: $threshold")

        if (templateWidth > screenWidth || templateHeight > screenHeight) {
            Log.w(TAG, "Template ($templateWidth x $templateHeight) is larger than screen ($screenWidth x $screenHeight)")
            return null
        }

        var bestMatch: MatchResult? = null
        var bestSimilarity = 0.0

        val timeMillis = measureTimeMillis {
            try {
                // 只进行不缩放的搜索
                val result = tryFastSearch(screenBitmap, templateBitmap, threshold)
                if (result != null) {
                    bestMatch = result
                    bestSimilarity = result.similarity
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during image matching", e)
            }
        }

        Log.d(TAG, "Total matching completed in $timeMillis ms")

        if (bestSimilarity >= threshold) {
            Log.d(TAG, "✅ Match found at (${bestMatch?.x}, ${bestMatch?.y}) with similarity ${String.format("%.3f", bestSimilarity)}")
        } else {
            Log.d(TAG, "❌ No match above threshold (best similarity: ${String.format("%.3f", bestSimilarity)}, threshold: $threshold)")
        }
        
        // 始终返回最佳匹配（即使低于阈值），这样调试模式可以显示它
        return bestMatch
    }

    // 快速搜索策略 - 不缩放，使用大步长
    private fun tryFastSearch(screen: Bitmap, template: Bitmap, threshold: Double): MatchResult? {
        Log.d(TAG, "--- Starting fast search (no scaling) ---")
        
        val templatePixels = getGrayPixels(template)
        val screenPixels = getGrayPixels(screen)
        val templateStats = TemplateStats(templatePixels, template.width, template.height)
        
        val searchWidth = screen.width - template.width
        val searchHeight = screen.height - template.height
        
        // 根据模板大小选择步长
        val step = when {
            template.width < 50 -> 2
            template.width < 100 -> 4
            else -> 8
        }
        
        Log.d(TAG, "Fast search - step: $step, area: ${searchWidth}x$searchHeight")
        
        var bestMatch: MatchResult? = null
        var bestSimilarity = 0.0
        var iterations = 0
        
        for (y in 0..searchHeight step step) {
            for (x in 0..searchWidth step step) {
                iterations++
                val similarity = fastCorrelation(
                    screenPixels, screen.width,
                    templatePixels, template.width, template.height,
                    x, y, templateStats
                )
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    val centerX = x + template.width / 2
                    val centerY = y + template.height / 2
                    bestMatch = MatchResult(centerX, centerY, similarity)
                    
                    // 找到足够好的匹配就退出
                    if (similarity >= threshold) {
                        Log.d(TAG, "Fast search found match at ($centerX, $centerY), similarity: ${String.format("%.3f", similarity)}")
                        return bestMatch
                    }
                }
            }
        }
        
        Log.d(TAG, "Fast search completed, $iterations iterations, best similarity: ${String.format("%.3f", bestSimilarity)}")
        
        // 如果有接近的匹配，在周围进行精细搜索
        if (bestMatch != null && bestSimilarity > threshold * 0.7) {
            Log.d(TAG, "Found close match, refining search around it...")
            return refineSearch(screen, template, bestMatch, templatePixels, templateStats, threshold, step)
        }
        
        return bestMatch
    }

    // 缩放搜索策略
    private fun tryScaledSearch(screen: Bitmap, template: Bitmap, threshold: Double): MatchResult? {
        Log.d(TAG, "--- Starting scaled search ---")
        
        val maxSize = 800 // 提高最大尺寸，保留更多细节
        val maxDim = maxOf(screen.width, screen.height)
        
        if (maxDim <= maxSize) {
            Log.d(TAG, "Screen already small enough, no scaling needed")
            return null
        }
        
        val scale = maxSize.toDouble() / maxDim
        val newScreenWidth = (screen.width * scale).toInt()
        val newScreenHeight = (screen.height * scale).toInt()
        val newTemplateWidth = (template.width * scale).toInt().coerceAtLeast(20)
        val newTemplateHeight = (template.height * scale).toInt().coerceAtLeast(20)
        
        Log.d(TAG, "Scaling - Screen: ${newScreenWidth}x$newScreenHeight, Template: ${newTemplateWidth}x$newTemplateHeight, Scale: $scale")
        
        val resizedScreen = Bitmap.createScaledBitmap(screen, newScreenWidth, newScreenHeight, true)
        val resizedTemplate = Bitmap.createScaledBitmap(template, newTemplateWidth, newTemplateHeight, true)
        
        val templatePixels = getGrayPixels(resizedTemplate)
        val screenPixels = getGrayPixels(resizedScreen)
        val templateStats = TemplateStats(templatePixels, resizedTemplate.width, resizedTemplate.height)
        
        val searchWidth = newScreenWidth - newTemplateWidth
        val searchHeight = newScreenHeight - newTemplateHeight
        val step = if (newTemplateWidth < 50) 2 else 4
        
        var bestSimilarity = 0.0
        var bestScaledX = 0
        var bestScaledY = 0
        
        for (y in 0..searchHeight step step) {
            for (x in 0..searchWidth step step) {
                val similarity = fastCorrelation(
                    screenPixels, newScreenWidth,
                    templatePixels, newTemplateWidth, newTemplateHeight,
                    x, y, templateStats
                )
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestScaledX = x
                    bestScaledY = y
                }
            }
        }
        
        if (bestSimilarity > 0.5) {
            // 转换回原始坐标
            val originalX = (bestScaledX / scale + newTemplateWidth / 2 / scale).toInt()
            val originalY = (bestScaledY / scale + newTemplateHeight / 2 / scale).toInt()
            Log.d(TAG, "Scaled search found candidate at ($originalX, $originalY), similarity: ${String.format("%.3f", bestSimilarity)}")
            
            return MatchResult(originalX, originalY, bestSimilarity)
        }
        
        return null
    }

    // 在粗略匹配点周围进行精细搜索
    private fun refineSearch(
        screen: Bitmap,
        template: Bitmap,
        roughMatch: MatchResult,
        templatePixels: IntArray,
        templateStats: TemplateStats,
        threshold: Double,
        originalStep: Int
    ): MatchResult? {
        val screenPixels = getGrayPixels(screen)
        
        // 计算搜索区域
        val searchRadius = originalStep * 2
        val centerX = roughMatch.x - template.width / 2
        val centerY = roughMatch.y - template.height / 2
        
        val startX = (centerX - searchRadius).coerceAtLeast(0)
        val startY = (centerY - searchRadius).coerceAtLeast(0)
        val endX = (centerX + searchRadius).coerceAtMost(screen.width - template.width)
        val endY = (centerY + searchRadius).coerceAtMost(screen.height - template.height)
        
        Log.d(TAG, "Refining search: x[$startX..$endX], y[$startY..$endY]")
        
        var bestSimilarity = roughMatch.similarity
        var bestMatch = roughMatch
        
        for (y in startY..endY) {
            for (x in startX..endX) {
                val similarity = fastCorrelation(
                    screenPixels, screen.width,
                    templatePixels, template.width, template.height,
                    x, y, templateStats
                )
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    val centerX = x + template.width / 2
                    val centerY = y + template.height / 2
                    bestMatch = MatchResult(centerX, centerY, similarity)
                    
                    if (similarity >= threshold) {
                        Log.d(TAG, "Refined match found at ($centerX, $centerY), similarity: ${String.format("%.3f", similarity)}")
                        return bestMatch
                    }
                }
            }
        }
        
        Log.d(TAG, "Refined search complete, best similarity: ${String.format("%.3f", bestSimilarity)}")
        return bestMatch
    }

    // 转换为灰度像素数组
    private fun getGrayPixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // 使用亮度公式
            pixels[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return pixels
    }

    // 模板统计信息，避免重复计算
    private class TemplateStats(val pixels: IntArray, width: Int, height: Int) {
        val mean: Double
        val sumSq: Double
        val sum: Double
        
        init {
            var sum = 0.0
            var sumSq = 0.0
            val n = width * height
            
            for (i in 0 until n) {
                val value = pixels[i].toDouble()
                sum += value
                sumSq += value * value
            }
            
            this.sum = sum
            mean = sum / n
            this.sumSq = sumSq - (sum * sum) / n
        }
    }

    // 快速相关系数计算
    private fun fastCorrelation(
        screenPixels: IntArray,
        screenWidth: Int,
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        startX: Int,
        startY: Int,
        templateStats: TemplateStats
    ): Double {
        var numerator = 0.0
        var screenSum = 0.0
        var screenSumSq = 0.0
        
        val n = templateWidth * templateHeight

        for (y in 0 until templateHeight) {
            var screenIdx = (startY + y) * screenWidth + startX
            var templateIdx = y * templateWidth
            
            for (x in 0 until templateWidth) {
                val screenGray = screenPixels[screenIdx].toDouble()
                val templateGray = templatePixels[templateIdx].toDouble()
                
                screenSum += screenGray
                numerator += screenGray * templateGray
                screenSumSq += screenGray * screenGray
                
                screenIdx++
                templateIdx++
            }
        }

        val screenMean = screenSum / n
        val screenSumSqNorm = screenSumSq - (screenSum * screenSum) / n
        
        val crossTerm = numerator - screenMean * templateStats.sum
        val denominator = sqrt(screenSumSqNorm * templateStats.sumSq)
        
        return if (denominator > 0.00001) {
            crossTerm / denominator
        } else {
            0.0
        }
    }
}
