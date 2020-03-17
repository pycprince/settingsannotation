package com.codespring.settingsannotation.codegen

data class PrefValues(
    var name: String,
    var defaultValue: String?,
    var testValue: String?,
    var type: String? = null
)