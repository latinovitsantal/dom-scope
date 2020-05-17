package latinovitsantal.domscope

import org.w3c.dom.HTMLElement
import kotlin.reflect.KMutableProperty0

typealias Deps = Sequence<State<*>>

fun deps(vararg state: State<*>) = state.asSequence()

fun Deps.toEffectiveDeps() = filterNot { (it.lastComponent as? ComponentImpl)?.isBeingRendered == true }

fun <V> state(value: V): State<V> =
  SimpleState(value)

abstract class State<V> {
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
    lastComponent = component
    subscriptions += action
    (component as? ComponentImpl)?.onDetach { subscriptions -= action }
  }
  operator fun invoke(value: V) { this.value = value }
  operator fun invoke() = value
}

private class SimpleState<V>(override var stateValue: V) : State<V>()
private class KPropertyState<V>(private val prop: KMutableProperty0<V>): State<V>() {
  override var stateValue: V
    get() = prop.get()
    set(value) { prop.set(value) }
}

fun <V> KMutableProperty0<V>.toState(): State<V> =
  KPropertyState(this)

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

private val component =
  Scope.value<ComponentImpl?> { null }

private fun <E: HTMLElement> IDomScope<E>.component(tagName: String, className: String?, effDeps: Deps, ext: Ext<E>) {
  val component = component()?.createChild() ?: ComponentImpl()
  component(component)
  ext()
  component.isBeingRendered = false
  effDeps.forEach {
    it.subscribe(component) {
      component.detach()
      replaceElement<E>(tagName, className) {
        component(tagName, className, effDeps, ext)
      }
    }
  }
}

fun <E: HTMLElement> DomScope.element(name: String, deps: Deps, className: String?, ext: Ext<E>) {
  element<E>(name, className) {
    val effDeps = deps.toEffectiveDeps()
    if (effDeps.any()) component(name, className, effDeps, ext.unsafeCast<Ext<*>>()) else ext()
  }
}

fun DomScope.using(deps: Deps, action: () -> Unit) {
  action()
  val component = component()
  deps.toEffectiveDeps().forEach { it.subscribe(component, action) }
}