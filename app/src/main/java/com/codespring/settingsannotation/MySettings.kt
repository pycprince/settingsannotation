package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.Pref
import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs(privatePrefKeys = false)
interface MySettings {
    @Pref
    var apiToken: String
    @Pref
    var sinchNumber: String
    @Pref("false")
    var activeSession: Boolean
    @Pref("334kdkjf")
    var wifiPassword: String
    @Pref("20")
    var someInt: Int
}