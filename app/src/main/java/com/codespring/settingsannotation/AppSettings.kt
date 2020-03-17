package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.Default
import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs(useKoin = true)
interface AppSettings {
    @Default("unit102")
    var unitId: String
    var key: String
}