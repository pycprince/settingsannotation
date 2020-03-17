package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.Default
import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs
interface AppSettings {
    @Default("unit102")
    var unitId: String
    var key: String
}