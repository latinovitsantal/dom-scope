package org.domscope

import org.domscope.RerenderMode.Always
import org.domscope.RerenderMode.Memoized
import org.w3c.dom.HTMLElement
import kotlin.reflect.KMutableProperty0

typealias Deps = Sequence<State<out Any>>

fun deps(vararg state: State<out Any>) = state.asSequence()

fun Deps.toEffectiveDeps() = filterNot { (it.lastComponent as? ComponentImpl)?.isBeingRendered == true }
operator fun Deps.invoke() = map { it.value }.toList()

fun <V: Any> state(value: V): State<V> = SimpleState(value)

abstract class State<V: Any> {
  private val subscriptions = mutableSetOf<() -> Unit>()
  var lastComponent: Component? = null; private set
  protected abstract var stateValue: V
  var value: V
    get() = stateValue
    set(value) {
      stateValue = value
      subscriptions.toList().forEach { it() }
    }
  fun subscribe(component: Component?, action: () -> Unit) {
    subscriptions += action
    (component as? ComponentImpl)?.let {
      lastComponent = component
      it.onDetach { subscriptions -= action }
    }
  }
  operator fun invoke(value: V) { this.value = value }
  operator fun invoke() = value
}

private class SimpleState<V: Any>(override var stateValue: V) : State<V>()
private class KPropertyState<V: Any>(private val prop: KMutableProperty0<V>): State<V>() {
  override var stateValue: V
    get() = prop.get()
    set(value) { prop.set(value) }
}

fun <V: Any> KMutableProperty0<V>.toState(): State<V> = KPropertyState(this)

interface Component {
  val isAttached: Boolean
}

private class ComponentImpl : Component {
  override var isAttached = true; private set
  var isBeingRendered = true
  private val cleanups = mutableSetOf<() -> Unit>()
  fun onDetach(action: () -> Unit) { cleanups.add(action) }
  fun detach() {
    isAttached = false
    cleanups.forEach { it() }
  }
  fun createChild(): ComponentImpl {
    val child = ComponentImpl()
    val childDetach = child::detach
    cleanups += childDetach
    child.cleanups += { cleanups -= childDetach }
    return child
  }
}

enum class RerenderMode { Always, Memoized }

private val component = Scope.value<ComponentImpl?> { null }

private fun <E: HTMLElement> IDomScope<E>.rerenderAlways(
  tagName: String,
  effDeps: Deps,
  parentScope: DomScope,
  ext: Ext<E>
) {
  val component = component()?.createChild() ?: ComponentImpl()
  component(component)
  ext()
  component.isBeingRendered = false
  effDeps.forEach {
    it.subscribe(component) {
      component.detach()
      replaceElement<E>(tagName, parentScope) {
        rerenderAlways(tagName, effDeps, parentScope, ext)
      }
    }
  }
}

private fun <E: HTMLElement> IDomScope<E>.rerenderMemoized(
  tagName: String,
  effDeps: Deps,
  parentScope: DomScope,
  ext: Ext<E>
) {
  val memoMap = mutableMapOf<List<Any>, DomScope>()
  lateinit var mutableScope: DomScope
  val component = component()
  effDeps.forEach { s ->
    s.subscribe(component) {
      val memoKey = effDeps()
      memoMap[memoKey]?.let { mutableScope.element.replaceWith(it.element) } ?: run {
        mutableScope.replaceElement<E>(tagName, parentScope) {
          ext()
          mutableScope = this
          memoMap[memoKey] = this
        }
      }
    }
  }
  element<E>(tagName) {
    ext()
    mutableScope = this
    memoMap[effDeps()] = this
  }
}

fun <E: HTMLElement> DomScope.element(name: String, deps: Deps, rerenderMode: RerenderMode, ext: Ext<E>) {
  element<E>(name) {
    val effDeps = deps.toEffectiveDeps()
    when(rerenderMode) {
      Memoized -> rerenderMemoized(name, effDeps, this@element, ext)
      Always -> if (effDeps.any()) rerenderAlways(name, effDeps, this@element, ext) else ext()
    }
  }
}

fun <E: HTMLElement> DomScope.element(name: String, className: String, deps: Deps, rerenderMode: RerenderMode, ext: Ext<E>) =
  element<E>(name, deps, rerenderMode) { element.className = className; ext() }

fun DomScope.using(deps: Deps, action: () -> Unit) {
  action()
  val component = component()
  deps.toEffectiveDeps().forEach { it.subscribe(component, action) }
}
