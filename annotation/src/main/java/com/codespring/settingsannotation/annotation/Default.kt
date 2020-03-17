package com.codespring.settingsannotation.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class Default(val defaultValue: String = "[null]")