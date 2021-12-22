package com.lightningkite.convertlayout.web

import com.lightningkite.convertlayout.android.*
import com.lightningkite.convertlayout.ios.Gravity
import com.lightningkite.convertlayout.ios.SwiftIdentifier
import com.lightningkite.convertlayout.ios.toGravity
import com.lightningkite.convertlayout.rules.AttributeReplacement
import com.lightningkite.convertlayout.rules.ElementReplacement
import com.lightningkite.convertlayout.rules.Replacements
import com.lightningkite.convertlayout.util.camelCase
import com.lightningkite.convertlayout.xml.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.min

internal class WebLayoutTranslatorForFile(
    val project: WebProject,
    replacements: Replacements,
    resources: AndroidResources
) : AndroidLayoutTranslator(replacements, resources) {

    var outlets: MutableMap<String, String> = LinkedHashMap()

    override fun getProjectWide(key: String): String? = when (key) {
        "projectName" -> project.name
        else -> null
    }

    override fun unknownRule(tagName: String): ElementReplacement {
        return replacements.getElement("View", mapOf())!!
    }

    override fun convertElement(owner: Element, sourceElement: Element): Element {
        val result = super.convertElement(owner, sourceElement)
        val tagNameClass = "android-${sourceElement.tagName.substringAfterLast('.')}"
        result["class"] = result["class"]?.let { "$it $tagNameClass" } ?: tagNameClass
        return result
    }

    override fun handleChildren(
        rules: List<ElementReplacement>,
        childAddRule: String?,
        sourceElement: Element,
        destElement: Element,
        target: Element
    ) {
        val myAttributes = sourceElement.allAttributes
        when(childAddRule) {
            "linear" -> {
                val isVertical = myAttributes["android:orientation"] == "vertical"
                sourceElement.childElements.forEach { childSrc ->
                    val childDest = this.convertElement(target, childSrc)
                    if(childSrc["android:layout_${if(isVertical) "width" else "height"}"] == "match_parent") {
                        childDest.css["align-self"] = "stretch"
                    } else {
                        childSrc["android:layout_gravity"]?.toGravity()?.let { grav ->
                            childDest.css["align-self"] = grav[!isVertical].name.lowercase()
                        }
                    }
                }
            }
            "frame" -> {
                sourceElement.childElements.forEach { childSrc ->
                    val childDest = this.convertElement(target, childSrc)
                    val childGravity = childSrc["android:layout_gravity"]?.toGravity() ?: Gravity()
                    if(childSrc["android:layout_width"] == "match_parent") {
                        childDest.css["justify-self"] = "stretch"
                    } else {
                        childDest.css["justify-self"] = childGravity[false].name.lowercase()
                    }
                    if(childSrc["android:layout_height"] == "match_parent") {
                        childDest.css["align-self"] = "stretch"
                    } else {
                        childDest.css["align-self"] = childGravity[true].name.lowercase()
                    }
                }
            }
            else -> super.handleChildren(rules, childAddRule, sourceElement, destElement, target)
        }
    }

    fun convertDocument(layout: AndroidLayoutFile, androidXml: Document): Document {
        val html = """<div/>""".readXml()
        val bodyNode = html.documentElement
        val view = convertElement(bodyNode, androidXml.documentElement)
        return html
    }

    fun tsFile(
        layout: AndroidLayoutFile
    ): String {
        return """
            |import {inflateHtmlFile} from "android-xml-runtime";
            |import html from './${layout.name}.html'
            |
            |export interface ${layout.className} {
            |    _root: HTMLElement
            |    ${
            outlets.entries.joinToString("\n|        ") {
                it.key + ": " + (it.value.let { elementMap[it] } ?: "HTMLElement")
            }
        }
            |}
            |
            |export namespace ${layout.className} {
            |   export function inflate() {
            |       return inflateHtmlFile(html, ${
                outlets.keys.map { "\"" + it + "\"" }.joinToString()
            }) as ${layout.className}
            |   }
            |}
            |
        """.trimMargin("|")
    }

    val elementMap = mapOf(
        "a" to "HTMLAnchorElement",
        "abbr" to "HTMLElement",
        "address" to "HTMLElement",
        "area" to "HTMLAreaElement",
        "article" to "HTMLElement",
        "aside" to "HTMLElement",
        "audio" to "HTMLAudioElement",
        "b" to "HTMLElement",
        "base" to "HTMLBaseElement",
        "bdi" to "HTMLElement",
        "bdo" to "HTMLElement",
        "blockquote" to "HTMLQuoteElement",
        "body" to "HTMLBodyElement",
        "br" to "HTMLBRElement",
        "button" to "HTMLButtonElement",
        "canvas" to "HTMLCanvasElement",
        "caption" to "HTMLTableCaptionElement",
        "cite" to "HTMLElement",
        "code" to "HTMLElement",
        "col" to "HTMLTableColElement",
        "colgroup" to "HTMLTableColElement",
        "data" to "HTMLDataElement",
        "datalist" to "HTMLDataListElement",
        "dd" to "HTMLElement",
        "del" to "HTMLModElement",
        "details" to "HTMLDetailsElement",
        "dfn" to "HTMLElement",
        "dialog" to "HTMLDialogElement",
        "dir" to "HTMLDirectoryElement",
        "div" to "HTMLDivElement",
        "dl" to "HTMLDListElement",
        "dt" to "HTMLElement",
        "em" to "HTMLElement",
        "embed" to "HTMLEmbedElement",
        "fieldset" to "HTMLFieldSetElement",
        "figcaption" to "HTMLElement",
        "figure" to "HTMLElement",
        "font" to "HTMLFontElement",
        "footer" to "HTMLElement",
        "form" to "HTMLFormElement",
        "frame" to "HTMLFrameElement",
        "frameset" to "HTMLFrameSetElement",
        "h1" to "HTMLHeadingElement",
        "h2" to "HTMLHeadingElement",
        "h3" to "HTMLHeadingElement",
        "h4" to "HTMLHeadingElement",
        "h5" to "HTMLHeadingElement",
        "h6" to "HTMLHeadingElement",
        "head" to "HTMLHeadElement",
        "header" to "HTMLElement",
        "hgroup" to "HTMLElement",
        "hr" to "HTMLHRElement",
        "html" to "HTMLHtmlElement",
        "i" to "HTMLElement",
        "iframe" to "HTMLIFrameElement",
        "img" to "HTMLImageElement",
        "input" to "HTMLInputElement",
        "ins" to "HTMLModElement",
        "kbd" to "HTMLElement",
        "label" to "HTMLLabelElement",
        "legend" to "HTMLLegendElement",
        "li" to "HTMLLIElement",
        "link" to "HTMLLinkElement",
        "main" to "HTMLElement",
        "map" to "HTMLMapElement",
        "mark" to "HTMLElement",
        "marquee" to "HTMLMarqueeElement",
        "menu" to "HTMLMenuElement",
        "meta" to "HTMLMetaElement",
        "meter" to "HTMLMeterElement",
        "nav" to "HTMLElement",
        "noscript" to "HTMLElement",
        "object" to "HTMLObjectElement",
        "ol" to "HTMLOListElement",
        "optgroup" to "HTMLOptGroupElement",
        "option" to "HTMLOptionElement",
        "output" to "HTMLOutputElement",
        "p" to "HTMLParagraphElement",
        "param" to "HTMLParamElement",
        "picture" to "HTMLPictureElement",
        "pre" to "HTMLPreElement",
        "progress" to "HTMLProgressElement",
        "q" to "HTMLQuoteElement",
        "rp" to "HTMLElement",
        "rt" to "HTMLElement",
        "ruby" to "HTMLElement",
        "s" to "HTMLElement",
        "samp" to "HTMLElement",
        "script" to "HTMLScriptElement",
        "section" to "HTMLElement",
        "select" to "HTMLSelectElement",
        "slot" to "HTMLSlotElement",
        "small" to "HTMLElement",
        "source" to "HTMLSourceElement",
        "span" to "HTMLSpanElement",
        "strong" to "HTMLElement",
        "style" to "HTMLStyleElement",
        "sub" to "HTMLElement",
        "summary" to "HTMLElement",
        "sup" to "HTMLElement",
        "table" to "HTMLTableElement",
        "tbody" to "HTMLTableSectionElement",
        "td" to "HTMLTableCellElement",
        "template" to "HTMLTemplateElement",
        "textarea" to "HTMLTextAreaElement",
        "tfoot" to "HTMLTableSectionElement",
        "th" to "HTMLTableCellElement",
        "thead" to "HTMLTableSectionElement",
        "time" to "HTMLTimeElement",
        "title" to "HTMLTitleElement",
        "tr" to "HTMLTableRowElement",
        "track" to "HTMLTrackElement",
        "u" to "HTMLElement",
        "ul" to "HTMLUListElement",
        "var" to "HTMLElement",
        "video" to "HTMLVideoElement",
        "wbr" to "HTMLElement",
    )
}
