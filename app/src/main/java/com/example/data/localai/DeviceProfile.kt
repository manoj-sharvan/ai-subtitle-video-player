package com.example.data.localai

data class DeviceProfile(
    val ramGb: Int,
    val cpuCores: Int,
    val abi: String,
    val recommendedWhisper: String,
    val recommendedLlm: String
)
