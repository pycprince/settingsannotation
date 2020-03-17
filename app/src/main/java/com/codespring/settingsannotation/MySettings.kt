package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs
interface InstanceSettings {
    var wifiPassword: String
}