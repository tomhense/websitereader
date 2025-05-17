package com.example.websitereader.tts


import android.content.Context

object TTSProviderFactory {
    fun createProvider(providerClassName: String, context: Context): Provider? {
        return try {
            val clazz = Class.forName(providerClassName)
            val cons = clazz.getDeclaredConstructor(Context::class.java)
            cons.isAccessible = true
            cons.newInstance(context) as Provider
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}