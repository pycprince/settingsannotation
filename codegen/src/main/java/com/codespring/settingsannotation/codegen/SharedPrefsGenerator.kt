package com.codespring.settingsannotation.codegen

import com.codespring.settingsannotation.annotation.Default
import com.codespring.settingsannotation.annotation.OnReset
import com.codespring.settingsannotation.annotation.Retain
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
        return mutableSetOf(
            SharedPrefs::class.java.name,
            Default::class.java.name,
            OnReset::class.java.name
        )
    }

    override fun getSupportedSourceVersion() : SourceVersion = SourceVersion.latest()

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(SharedPrefs::class.java)?.forEach { element ->
            val className = element.simpleName.toString()
            val packageName = processingEnv.elementUtils.getPackageOf(element).toString()

            val annotationValues = element.getAnnotation(SharedPrefs::class.java)
            privatePrefKeys = annotationValues.privatePrefKeys
            privateFileKey = annotationValues.privateFileKey
            useKoin = annotationValues.useKoin
            showTraces = annotationValues.showTraces

            val defaults = getDefaultList(roundEnv)
            val varList = getVarList(element)
            val prefs = assignDefaultsToPrefs(defaults, varList)
            val retentionList = getRetentionList(roundEnv)

            val resets = getResetMethods(roundEnv, className)

            if (resets.size > 1) {
                messager.w("Found multiple reset method. Recommend only supplying a single reset method  \n")
            }

            if (showTraces) {
                messager.w(" resets for $className : $resets  \n")
                messager.w(" retention list for $className : $retentionList  \n")
            }

            val spec = generateContent(className, packageName, prefs, retentionList, resets)
            processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.let {
                val file = File(it)
                spec.writeTo(file)
            }
        }
        return true
    }

    private fun getResetMethods(roundEnv: RoundEnvironment, className: String): List<String> =
        roundEnv.getElementsAnnotatedWith(OnReset::class.java)?.filter {
            val enclosingAnnotation = it.enclosingElement?.getAnnotation(SharedPrefs::class.java)
            enclosingAnnotation != null && it.enclosingElement.simpleName.toString() == className
        }?.map { it.simpleName.toString() } ?: listOf()

    private fun assignDefaultsToPrefs(
        defaultList: HashMap<String, String?>,
        varList: HashMap<String, String>
    ) : List<PrefValues> = varList.map {
        PrefValues(
            name = it.key,
            type = it.value,
            defaultValue = defaultList[it.key]
        )
    }

    private fun getRetentionList(roundEnv: RoundEnvironment) : List<String> =
        roundEnv.getElementsAnnotatedWith(Retain::class.java)?.map {
            val name = when (val memberIndex = it.simpleName.indexOf("$")) {
                -1 -> it.simpleName.toString()
                else -> it.simpleName.substring(0, memberIndex)
            }
            name.toKey()
        } ?: listOf()

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

    private fun getDefaultList(roundEnv: RoundEnvironment) : HashMap<String, String?> {
        val map = HashMap<String, String?>()
        roundEnv.getElementsAnnotatedWith(Default::class.java)?.forEach {
            val values = it.getAnnotation(Default::class.java)
            val name = when (val index = it.simpleName.indexOf("$")) {
                -1 -> it.simpleName.toString()
                else -> it.simpleName.substring(0, index)
            }
            val default = if (values.defaultValue == "[null]") null else values.defaultValue
            map += name to default
        }
        if (showTraces) {
            map.forEach { (name, default) ->
                messager.w(" default for $name is $default  \n")
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

    private fun generateContent(
        className: String,
        packageName: String,
        prefs: List<PrefValues>,
        retentionList: List<String>,
        resets: List<String>
    ) : FileSpec {
        val fileBuilder = FileSpec.builder(packageName, "${className}Prefs")
        val classBuilder = TypeSpec.classBuilder("${className}Prefs")
            .addSuperinterface(ClassName(packageName, className))
        if (!useKoin) {
            classBuilder.primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("context",
                    ClassName("android.content", "Context"))
                .build())
        } else {
            classBuilder.addSuperinterface(ClassName("org.koin.core", "KoinComponent"))
        }

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
        val prefFileSimpleName = "${className.toKey()}_PREFS"
        val prefFileKey = PropertySpec.builder(prefFileSimpleName, String::class, fileKeyModifiers)
                .initializer("\"$packageName.$className.${className.toCamelCase()}_PREFS\"")
                .build()

        val putFunction = createPutFunction()
        val resetMethods = createResetMethods(resets, keys, retentionList)

        val companionObject = TypeSpec.companionObjectBuilder()
            .addProperties(keys)
            .addProperty(prefFileKey)
        classBuilder.addProperty(getSharedPreferencesPropertyBuilder(prefFileSimpleName).build())
        classBuilder.addProperty(editorProperty
            .initializer("prefs.edit()")
            .build())
        classBuilder.addProperties(variables)
        classBuilder.addFunction(putFunction.build())
        classBuilder.addFunctions(resetMethods)
        classBuilder.addType(companionObject.build())
        if (useKoin) {
            fileBuilder.addImport("org.koin.core", "KoinComponent", "inject")
            fileBuilder.addImport("org.koin.core.parameter", "parametersOf")
        }
        return fileBuilder
            .addImport("android.content", "SharedPreferences", "SharedPreferences.Editor")
            .addType(classBuilder.build()).build()
    }

    private fun createPutFunction(): FunSpec.Builder {
        val putFunction = FunSpec.builder("putValue")
            .addParameter("key", String::class)
            .addParameter("value", Any::class.asTypeName().copy(nullable = true))
            .addCode(
                """
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
                """.trimIndent().replace(" ", "·")
            )
        return putFunction
    }

    private fun createResetMethods(
        resets: List<String>,
        keys: MutableList<PropertySpec>,
        retentionList: List<String>
    ): MutableList<FunSpec> {
        val resetMethods = mutableListOf<FunSpec>()
        resets.forEach { name ->
            val func = FunSpec.builder(name)
                .addModifiers(KModifier.OVERRIDE)
            for (spec in keys) {
                // Do not reset variables marked @Retain
                if (spec.name in retentionList) continue
                func.addCode("editor.remove(${spec.name})\n")
            }
            func.addCode("editor.apply()")
            resetMethods.add(func.build())
        }
        return resetMethods
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