package latinovitsantal.domscope

import org.w3c.dom.css.CSSStyleDeclaration
import kotlin.dom.appendText

fun DomScope.className(className: String) { element.className += " $className" }
fun DomScope.style(buildStyle: (@ScopeDsl CSSStyleDeclaration).() -> Unit) { element.style.buildStyle() }
fun DomScope.text(text: String) { element.appendText(text) }