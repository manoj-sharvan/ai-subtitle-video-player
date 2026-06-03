package com.example.data.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object HardwareDetector {
    fun detectDeviceProfile(context: Context): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamGb = (memoryInfo.totalMem / (1024L * 1024L * 1024L)).toInt()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        
        val (recWhisper, recLlm) = when {
            totalRamGb < 4 -> "Whisper Tiny" to "Qwen 2.5 1.5B"
            totalRamGb in 4..7 -> "Whisper Base" to "Qwen 2.5 3B"
            else -> "Whisper Small" to "Gemma 3 4B"
        }
        
        return DeviceProfile(
            ramGb = totalRamGb,
            cpuCores = cpuCores,
            abi = abi,
            recommendedWhisper = recWhisper,
            recommendedLlm = recLlm
        )
    }
}
