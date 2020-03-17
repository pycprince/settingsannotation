package com.codespring.settingsannotation.codegen

import com.codespring.settingsannotation.annotation.Pref
import com.codespring.settingsannotation.annotation.SharedPrefs
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import java.io.File
import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import kotlin.collections.HashMap

@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(SharedPrefsGenerator.KAPT_KOTLIN_GENERATED_OPTION_NAME)
class SharedPrefsGenerator : AbstractProcessor() {

    private lateinit var messager: Messager
    private var privatePrefKeys = true
    private var privateFileKey = false
    private var useKoin = false
    private var showTraces = false

    override fun init(env: ProcessingEnvironment) {
        super.init(env)
        messager = env.messager
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(SharedPrefs::class.java.name, Pref::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latest()
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(SharedPrefs::class.java)?.forEach { element ->
            val annotationValues = element.getAnnotation(SharedPrefs::class.java)
            privatePrefKeys = annotationValues.privatePrefKeys
            privateFileKey = annotationValues.privateFileKey
            useKoin = annotationValues.useKoin
            showTraces = annotationValues.showTraces

            val prefsList = getPrefsList(roundEnv)
            val varList = getVarList(element)
            val prefs = assignTypeToPrefs(prefsList, varList)

            val className = element.simpleName.toString()
            val packageName = processingEnv.elementUtils.getPackageOf(element).toString()

            val spec = generateContent(className, packageName, prefs)

            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.let {
                val file = File(it)
                spec.writeTo(file)
            }
        }
        return true
    }

    private fun assignTypeToPrefs(
        prefList: HashMap<String, PrefValues>,
        varList: HashMap<String, String>
    ) : List<PrefValues> {
        return prefList.map {
            messager.w("Mapping ${it.key} to type ${varList[it.key]}  \n")
            it.value.apply { type = varList[it.key] }
        }
    }

    private fun getVarList(element: Element) : HashMap<String, String> {
        val varSet = hashMapOf<String, String>()
        element.enclosedElements.forEach { field ->
            val name = field.simpleName.toString()
            if (field.kind == ElementKind.METHOD) {
                val type = (field as ExecutableElement).returnType
                    .toString()
                    .substringAfterLast(".")
                    .capitalize()
                if (type != "Void") {
                    when {
                        name.startsWith("get") -> {
                            val firstChar = name.substring(3)[0]
                            varSet[name.substring(3).replaceFirst(firstChar, firstChar + 32)] = type
                        }
                        else -> {
                            varSet[name] = type
                        }
                    }
                }
            }
        }
        if (showTraces) {
            varSet.forEach { (key, value) ->
                messager.w("Found var $key type $value  \n")
            }
        }
        return varSet
    }

    private fun getPrefsList(roundEnv: RoundEnvironment) : HashMap<String, PrefValues> {
        val map = HashMap<String, PrefValues>()
        roundEnv.getElementsAnnotatedWith(Pref::class.java)?.map {
            val values = it.getAnnotation(Pref::class.java)
            val name = when (val index = it.simpleName.indexOf("$")) {
                -1 -> it.simpleName.toString()
                else -> it.simpleName.substring(0, index)
            }
            val defaultValue = if (values.defaultValue == "[null]") null else values.defaultValue
            val testValue = if (values.testValue == "[null]") null else values.testValue
            map[name] = PrefValues(name, defaultValue, testValue)
        }
        if (showTraces) {
            map.forEach { (name, values) ->
                messager.w("Pref $name with default ${values.defaultValue}  \n")
            }
        }
        return map
    }

    private fun getDefaultValueFor(type: String?, default: String?) =
        when (type) {
            "String" -> if (default != null) "\"$default\"" else "\"\""
            "Int" -> default?.toInt() ?: "-1"
            "Boolean" -> default ?: "false"
            "Float" -> default ?: "-1f"
            "Double" -> default ?: "-1.0"
            "Long" -> default ?: "-1"
            else -> throw java.lang.IllegalArgumentException("Default value must be a primitive type - $type ")
        }

    private fun generateContent(className: String, packageName: String, prefs: List<PrefValues>) : FileSpec {
        val fileBuilder = FileSpec.builder("$packageName", "${className}Prefs")
        val classBuilder = TypeSpec.classBuilder("${className}Prefs")
        if (!useKoin) {
            classBuilder.primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("context",
                    ClassName("android.content", "Context"))
                .build())
        }
        classBuilder.addSuperinterface(ClassName(packageName, className))

        val variables = mutableListOf<PropertySpec>()
        val keys = mutableListOf<PropertySpec>()

        val editorProperty = PropertySpec.builder("editor", ClassName("android.content", "SharedPreferences.Editor"))

        // Modifiers for shared preference member keys
        val prefKeyModifiers = mutableListOf(KModifier.CONST).apply {
            if (privatePrefKeys) add(KModifier.PRIVATE)
        }.toList()
        // Modifiers for shared preference file key name
        val fileKeyModifiers  = mutableListOf(KModifier.CONST).apply {
            if (privateFileKey) add(KModifier.PRIVATE)
        }.toList()

        prefs.forEach {
            val clazz = when (it.type) {
                "String" -> String::class
                "Int" -> Int::class
                "Boolean" -> Boolean::class
                "Float" -> Float::class
                "Double" -> Double::class
                "Long" -> Long::class
                else -> throw IllegalArgumentException("Properties must be a primitive type : ${it.name} is ${it.type}")
            }
            val defaultValue = getDefaultValueFor(it.type, it.defaultValue)
            val returnStatement = "return prefs.get${it.type}(${it.name.toKey()}, $defaultValue)" +
                    if (it.type == "String") " ?: $defaultValue" else ""

            variables.add(PropertySpec.builder(it.name, clazz, listOf(KModifier.OVERRIDE))
                .mutable()
                .getter(FunSpec.getterBuilder()
                    .addStatement(returnStatement)
                    .build()
                )
                .setter(FunSpec.setterBuilder()
                    .addParameter("value", clazz)
                    .addStatement("putValue(${it.name.toKey()}, value)")
                    .build()
                )
                .build()
            )
            keys.add(
                PropertySpec.builder(it.name.toKey(), String::class, prefKeyModifiers)
                    .initializer("\"$packageName.$className.${it.name.toCamelCase()}\"")
                    .build()
            )
        }
        keys.add(
            PropertySpec.builder(className.toKey(), String::class, fileKeyModifiers)
                .initializer("\"$packageName.$className.${className.toCamelCase()}_PREFS\"")
                .build()
        )

        val putFunction = FunSpec.builder("putValue")
            .addParameter("key", String::class)
            .addParameter("value", Any::class.asTypeName().copy(nullable = true))
            .addCode("""
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
            """.trimIndent().replace(" ", "·"))

        val companionObject = TypeSpec.companionObjectBuilder().addProperties(keys)
        classBuilder.addProperty(getSharedPreferencesPropertyBuilder(className.toKey()).build())
        classBuilder.addProperty(editorProperty
            .initializer("prefs.edit()")
            .build())
        classBuilder.addProperties(variables)
        classBuilder.addFunction(putFunction.build())
        classBuilder.addType(companionObject.build())
        if (useKoin) {
            fileBuilder.addImport("org.koin.core", "KoinComponent", "inject", "parametersOf")
        }
        return fileBuilder
            .addImport("android.content", "SharedPreferences", "SharedPreferences.Editor")
            .addType(classBuilder.build()).build()
    }

    private fun getSharedPreferencesPropertyBuilder(name: String) : PropertySpec.Builder {
        val prefsProperty = PropertySpec.builder("prefs", ClassName("android.content", "SharedPreferences"))
        if (useKoin) {
            prefsProperty.delegate("inject { parametersOf($name) }")
        } else {
            prefsProperty.initializer("context.getSharedPreferences($name, Context.MODE_PRIVATE)")
        }
        return prefsProperty
    }

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}

internal fun Messager.w(message: String) = this.printMessage(Diagnostic.Kind.WARNING, message)
internal fun Messager.n(message: String) = this.printMessage(Diagnostic.Kind.NOTE, message)

internal fun String.split(predicate: (Char) -> Boolean, translation: ((initial: String) -> String) = { it }) : List<String> {
    var lastIndex = 0
    val array = mutableListOf<String>()
    this.mapIndexed { index, char ->
        if (index > 0 && predicate.invoke(char)) {
            array.add(translation.invoke(this.substring(lastIndex, index)))
            lastIndex = index
        }
    }
    array.add(translation.invoke(this.substring(lastIndex)))
    return array
}

internal fun String.toCamelCase() =
    this.split({ it.toInt() in 65..90 }, {it.toUpperCase(Locale.getDefault())}).joinToString("_")

internal fun String.toKey() =
    "KEY_${this.toCamelCase()}"