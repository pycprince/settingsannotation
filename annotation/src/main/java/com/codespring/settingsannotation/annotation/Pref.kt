package com.codespring.settingsannotation.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Pref(val defaultValue: String = "[null]", val testValue: String = "[null]")