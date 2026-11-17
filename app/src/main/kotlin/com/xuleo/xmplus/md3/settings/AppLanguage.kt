package com.xuleo.xmplus.md3.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val tag: String) {
    System(""),
    Chinese("zh-CN"),
    English("en"),
    ;

    companion object {
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return System
            return if (locales[0]?.language == "zh") Chinese else English
        }
    }
}

// AppCompat recreates the activity and keeps the choice across restarts.
fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(
        if (language == AppLanguage.System) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        },
    )
}