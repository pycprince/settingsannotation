package com.codespring.settingsannotation.codegen

data class PrefValues(
    var name: String,
    var defaultValue: String? = null,
    var testValue: String? = null,
    var type: String? = null
)