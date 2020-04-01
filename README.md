# Settings Annotation
[![](https://jitpack.io/v/surfsnowpro/settingsannotation.svg)](https://jitpack.io/#surfsnowpro/settingsannotation)

Annotation processor and code generator used to create categorized `SharedPreferences` files. Key constants are created for each property as well as the file itself and placed in a companion object.

```Java
@SharedPrefs
interface SystemSettings {
    @Default("unit102")
    var unitId: String
    var key: String
    @Retain
    @Default("20")
    var someInteger: Int

    @OnReset
    fun reset()
}
```

## Available Annotations

### `@SharedPref(<options>)`
_Target: interface_

Used to annotate an interface as a base `SharedPreferences` file.  The generated file name will be in the format \<interfaceName>Prefs.  So for 
```Java
@SharedPref
interface SystemSettings {}
```
the generated file will be `SystemSettingsPrefs`. 

The `SharedPreferences` file key will be in the format KEY\_\<interfaceName>\_PREFS where the interfaceName will be converted to snake case.  So for the previous example, the key will be `KEY_SYSTEM_SETTINGS_PREFS`

##### Options
| option | type | default | description |
|---|---|---|---|
|privatePrefKeys|`Boolean`|`true`| the generated keys for properties will be marked with an access modifier of `private`|
|privateFileKey|`Boolean`|`false`| the generated file key will be marked with an access modifier of `private`|
|useKoin|`Boolean`|`false`| The generated file will obtain its `SharedPreferences` instance through dependency injection using the Koin DI library|
|showTraces|`Boolean`|`false`|Used for troubleshooting. Will print logs for the code generator|

Example: `useKoin = false` : The generated constructor will be
```Java
class SystemSettingsPrefs(
  context: Context
) : SystemSettings {
  ...
}
```
where an instance to the `SharedPreferences` will be obtained in the normal way.  With `useKoin = true` : The generated constructor and preferences instance will be :
```Java
class SystemSettingsPrefs : SystemSettings, KoinComponent {
  val prefs: SharedPreferences by inject { parametersOf(KEY_SYSTEM_SETTINGS_PREFS) }
  ...
}

```
**Note:** This will require you to have a module definition for injecting a `SharedPreferences` instance that takes the file name as a parameter.


### `@Default(value: String)`
_Target: property_

Properties annotated with `@Default` will use the default value specified in the case where the preference value has not been set yet.

The following values will be used as defaults if no default is specified:

|type|default|
|--|--|
|`String`| \"\" (empty string)|
|`Int`|-1|
|`Boolean`|false|
|`Float`|-1f|
|`Double`|-1.0|
|`Long`|-1|


### `@Retain`
_Target: property_

The values for the properties annotated with this will not be cleared when the `@OnReset` method is called



### `@OnReset`
_Target: method_

Creates a method that, when called, will clear all properties (unless annotated with `@Retain`). While more than one `OnReset` method can be generated, it is not recommended since each method will have the same generated code.

## Installation
##### Gradle
In your project `build.gradle`:
```Java
allProjects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
In your app's `build.gradle`
```Java
dependencies {
  implementation 'com.github.surfsnowpro.settingsannotation:annotation:version'
  kapt 'com.github.surfsnowpro.settingsannotation:codegen:version'
}
```
