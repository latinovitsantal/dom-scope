package org.domscope

import org.domscope.RerenderMode.Always
import org.w3c.dom.HTMLElement
import kotlin.browser.document

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class ScopeDsl

typealias Ext<E> = IDomScope<E>.() -> Unit
typealias DomScope = IDomScope<*>

interface Property<V> {
  fun default(scope: Scope): V
}

@ScopeDsl
interface Scope {
  fun <V> Property<V>.orNull(): V?
  operator fun <V> Property<V>.invoke(): V
  operator fun <V> Property<V>.invoke(value: V)
  companion object {
    fun <V> value(lazy: Boolean = false, default: Scope.() -> V) = when(lazy) {
      true -> object : Property<V> {
        var value: V? = null
        override fun default(scope: Scope) = value ?: scope.default().also { value = it }
      }
      else -> object : Property<V> {
        override fun default(scope: Scope) = scope.default()
      }
    }
  }
}

@ScopeDsl
interface ScopeExtender<out S: Scope> {
  fun scope(extension: S.() -> Unit)
  operator fun <V> Property<V>.invoke(value: V, extension: S.() -> Unit)
}

interface IDomScope<out E: HTMLElement> : Scope, ScopeExtender<IDomScope<E>> {
  val element: E
  fun <C: HTMLElement> createChildScope(childElement: C): IDomScope<C>

  fun <C: HTMLElement> element(elementName: String, ext: Ext<C>) {
    val childElement = document.createElement(elementName).unsafeCast<C>()
    createChildScope(childElement).ext()
    element.appendChild(childElement)
  }

  fun <C: HTMLElement> element(elementName: String, className: String, ext: Ext<C>) {
    element<C>(elementName) { element.className = className; ext() }
  }

  fun <N: HTMLElement> replaceElement(newElement: N, parentScope: DomScope, ext: Ext<N>) {
    element.replaceWith(parentScope.createChildScope(newElement).also(ext).element)
  }

  fun <N: HTMLElement> replaceElement(elementName: String, parentScope: DomScope, ext: Ext<N>) {
    val newElement = document.createElement(elementName).unsafeCast<N>()
    replaceElement(newElement, parentScope, ext)
  }

  operator fun <D: HTMLElement> TagName<D>.invoke(ext: Ext<D> = {}) {
    element(tagName, ext)
  }

  operator fun <D: HTMLElement> TagName<D>.invoke(className: String, ext: Ext<D> = {}) {
    element(tagName, className, ext)
  }

  operator fun <D: HTMLElement> TagName<D>.invoke(deps: Deps, rerenderMode: RerenderMode = Always, ext: Ext<D> = {}) {
    element(tagName, deps, rerenderMode, ext)
  }

  operator fun <D: HTMLElement> TagName<D>.invoke(className: String, deps: Deps, rerenderMode: RerenderMode = Always, ext: Ext<D> = {}) {
    element(tagName, className, deps, rerenderMode, ext)
  }

}

private class Entry(private val key: Property<*>, private val value: Any?, val next: Entry?) {
  operator fun get(key: Any): Any? {
    var actual: Entry? = this
    while (actual != null) {
      if (actual.key == key)
        return actual.value
      actual = actual.next
    }
    return null
  }
}

private class DomScopeImpl<E: HTMLElement>(override val element: E, var entry: Entry?) : IDomScope<E> {
  override fun <V> Property<V>.orNull(): V? = entry?.get(this).unsafeCast<V?>()
  override fun <V> Property<V>.invoke(): V = (entry?.get(this) ?: default(this@DomScopeImpl)).unsafeCast<V>()
  override fun <V> Property<V>.invoke(value: V) { entry = Entry(this, value, entry) }
  override fun <C : HTMLElement> createChildScope(childElement: C) = DomScopeImpl(childElement, entry)
  override fun scope(extension: IDomScope<E>.() -> Unit) = DomScopeImpl(element, entry).extension()
  override fun <V> Property<V>.invoke(value: V, extension: IDomScope<E>.() -> Unit) {
    scope {
      invoke(value)
      extension()
    }
  }
}

fun <E: HTMLElement> createMainScope(element: E): IDomScope<E> = DomScopeImpl(element, null)

fun <E: HTMLElement> domScope(element: E, ext: Ext<E>) = createMainScope(element).ext()
