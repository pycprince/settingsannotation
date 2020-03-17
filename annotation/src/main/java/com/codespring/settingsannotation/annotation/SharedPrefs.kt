package com.codespring.settingsannotation.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SharedPrefs(
    val privatePrefKeys: Boolean = true,
    val privateFileKey: Boolean = false,
    val useKoin: Boolean = false,
    val showTraces: Boolean = false
)