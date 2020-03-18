package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.Default
import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs
interface InstanceSettings {
    var wifiPassword: String
    @Default("20")
    var count: Int
}