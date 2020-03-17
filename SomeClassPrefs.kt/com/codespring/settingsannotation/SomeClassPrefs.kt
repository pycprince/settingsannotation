package com.codespring.settingsannotation

import kotlin.Boolean
import kotlin.Int
import kotlin.String

class SomeClassPrefs {
  var isTrue: Boolean
    get() = prefs.getBoolean(KEY_IS_TRUE, "") ?: ""
    set(value) {
      putValue(KEY_IS_TRUE, value)
    }

  var someVar: String
    get() = prefs.getString(KEY_SOME_VAR, "") ?: ""
    set(value) {
      putValue(KEY_SOME_VAR, value)
    }

  var anotherVar: Int
    get() = prefs.getInt(KEY_ANOTHER_VAR, "") ?: ""
    set(value) {
      putValue(KEY_ANOTHER_VAR, value)
    }

  fun putValue(key: String) {
    with (editor) {
        if (value == null) {
            remove(key)
            return
        }
        when (value) {
            is Int -> putInt(key, value)
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            else -> throw IllegalArgumentException("Unable to infer type for storing in shared preferences")
        }
        apply()
    }
  }

  companion object {
    private const val KEY_IS_TRUE: String = "com.codespring.settingsannotation.SomeClass.IS_TRUE"

    private const val KEY_SOME_VAR: String = "com.codespring.settingsannotation.SomeClass.SOME_VAR"

    private const val KEY_ANOTHER_VAR: String =
        "com.codespring.settingsannotation.SomeClass.ANOTHER_VAR"
  }
}
