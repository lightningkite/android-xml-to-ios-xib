package com.lightningkite.convertlayout.android

import com.fasterxml.jackson.annotation.JsonIgnore
import com.lightningkite.convertlayout.util.camelCase
import com.lightningkite.convertlayout.xml.attributeMap
import com.lightningkite.convertlayout.xml.children
import com.lightningkite.convertlayout.xml.get
import com.lightningkite.convertlayout.xml.readXml
import org.w3c.dom.Element
import java.io.File

data class AndroidLayoutFile(
    val name: String,
    val variants: Set<String>,
    val files: Set<File>,
    val bindings: Map<String, AndroidIdHook>,
    val sublayouts: Map<String, AndroidSubLayout>,
    val emitCurse: Map<String, AndroidAction>
) {
    @get:JsonIgnore val className: String get() = name.capitalize().camelCase() + "Xml"
    companion object {
        fun combine(iter: Sequence<AndroidLayoutFile>): AndroidLayoutFile =
            AndroidLayoutFile(
                name = iter.first().name,
                variants = iter.flatMap { it.variants.asSequence() }.toSet(),
                files = iter.flatMap { it.files }.toSet(),
                bindings = run {
                    (iter.flatMap { it.bindings.asSequence() }.associate { it.toPair() }).mapValues { (key, value) ->
                        if (iter.all { it.bindings[key] != null }) value
                        else value.copy(optional = true)
                    }
                },
                sublayouts = run {
                    (iter.flatMap { it.sublayouts.asSequence() }.associate { it.toPair() }).mapValues { (key, value) ->
                        if (iter.all { it.sublayouts[key] != null }) value
                        else value.copy(optional = true)
                    }
                },
                emitCurse = run {
                    (iter.flatMap { it.emitCurse.asSequence() }.associate { it.toPair() }).mapValues { (key, value) ->
                        if (iter.all { it.emitCurse[key] != null }) value
                        else value.copy(optional = true)
                    }
                }
            )

        fun parseAll(folder: File, resources: AndroidResources): Map<String, AndroidLayoutFile> {
            return folder.listFiles()!!.asSequence().filter { it.name.startsWith("layout") }
                .flatMap { it.listFiles()!!.asSequence() }
                .map { it.name }
                .distinct()
                .map { parseSet(folder, it, resources) }
                .associateBy { it.name }
        }

        fun parseSet(folder: File, filename: String, resources: AndroidResources): AndroidLayoutFile {
            return folder.listFiles()!!.asSequence().filter { it.name.startsWith("layout") }
                .map { it.resolve(filename) }
                .filter { it.exists() }
                .map { parse(it, it.parentFile.name.substringAfter("layout-", ""), resources) }
                .let { combine(it) }
        }

        fun parse(file: File, variant: String, resources: AndroidResources): AndroidLayoutFile {
            val node = file.readXml().documentElement
            val bindings = ArrayList<AndroidIdHook>()
            val sublayouts = ArrayList<AndroidSubLayout>()
            val emitCurse = HashMap<String, AndroidAction>()

            fun addBindings(node: Element) {
                if(node.tagName == "com.google.android.material.tabs.TabItem") {
                    return
                }
                val allAttributes =
                    node.attributeMap + (node["style"]?.let { resources.read(it) as? AndroidStyle }?.map ?: mapOf())
                allAttributes["android:id"]?.let { raw ->
                    val id = raw.removePrefix("@+id/").removePrefix("@id/")
                    val camelCasedId = id.camelCase()
                    if (node.tagName == "include") {
                        val layout = allAttributes["layout"]!!.removePrefix("@layout/")
                        sublayouts.add(
                            AndroidSubLayout(
                                name = camelCasedId,
                                resourceId = id,
                                layoutXmlClass = layout.camelCase().capitalize() + "Xml"
                            )
                        )
                    } else {
                        val name = raw.removePrefix("@+id/").removePrefix("@id/").camelCase()
                        bindings.add(
                            AndroidIdHook(
                                name = name,
                                type = node.tagName,
                                resourceId = raw.removePrefix("@+id/").removePrefix("@id/")
                            )
                        )
                    }
                }
                node.children.mapNotNull { it as? Element }.forEach {
                    addBindings(it)
                }
            }
            addBindings(node)
            return AndroidLayoutFile(
                name = file.nameWithoutExtension,
                variants = if(variant.isNotEmpty()) setOf(variant) else setOf(),
                files = setOf(file),
                bindings = bindings.associateBy { it.name },
                sublayouts = sublayouts.associateBy { it.name },
                emitCurse = emitCurse
            )
        }
    }

    fun toString(packageName: String, applicationPackage: String): String = """
    |//
    |// $className.kt
    |// Created by Khrysalis XML Android
    |//
    |package $packageName
    |
    |import android.widget.*
    |import android.view.*
    |import $applicationPackage.R
    |
    |class $className {
    |
    |    ${bindings.values.joinToString("\n|    ") { it.declaration }}
    |    ${sublayouts.values.joinToString("\n|    ") { it.declaration }}
    |    lateinit var xmlRoot: View
    |
    |    fun inflate(context: Context): View {
    |        val view = LayoutInflater.from(context).inflate(R.layout.$name, null, false)
    |        return setup(view)
    |    }
    |    fun setup(view: View): View {
    |        xmlRoot = view
    |        ${bindings.values.joinToString("\n|        ") { it.initiation }}
    |        ${sublayouts.values.joinToString("\n|        ") { it.initiation }}
    |        ${emitCurse.values.joinToString("\n|        ") { it.invocation }}
    |        return view
    |    }
    |}
    """.trimMargin("|")

}
