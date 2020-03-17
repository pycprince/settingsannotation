package com.codespring.settingsannotation

import com.codespring.settingsannotation.annotation.Pref
import com.codespring.settingsannotation.annotation.SharedPrefs

@SharedPrefs(showTraces = true)
interface InstanceSettings {
    @Pref
    var wifiPassword: String
    @Pref
    var sessionInProgress: Boolean
    @Pref
    var termsAccepted: Boolean
    @Pref
    var instanceId: String
    @Pref
    var isCheckedIn: Boolean
}