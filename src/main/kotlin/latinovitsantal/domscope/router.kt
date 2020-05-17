package latinovitsantal.domscope

import org.w3c.dom.*
import kotlin.browser.*

private typealias Div = HTMLDivElement

private fun String.uriEncoded() = window.asDynamic().encodeURIComponent(this) as String
private fun String.uriDecoded() = window.asDynamic().decodeURIComponent(this) as String
fun queryString(vararg pairs: Pair<String, String>) = pairs.joinToString("&") { (key, value) ->
  key.uriEncoded() + "=" + value.uriEncoded()
}

private fun noRouterError(): Nothing = error("There is no router in the scope")
val router =
  Scope.value<Router> { noRouterError() }
val routeParams =
  Scope.value<PathParams> { noRouterError() }
val routeQueryParams =
  Scope.value<QueryParams> { noRouterError() }

fun DomScope.router(defRoutes: (@ScopeDsl DefRoutes).() -> Unit) {
  val routes = Routes().also(defRoutes)
  val routeMatch = state(routes.match(window.location.pathname))
  val onPopState = { _: PopStateEvent -> routeMatch.value = routes.match(window.location.pathname) }
  window.onpopstate = onPopState
  div(deps(routeMatch)) {
    val (route, params, queryParams) = routeMatch.value
    router(Router(routes, routeMatch))
    routeParams(params)
    routeQueryParams(queryParams)
    route.extendDivScope(this)
  }
}

interface DefRoutes {
  fun route(path: String, title: String = path, extendDivScope: Ext<Div>)
}

class Routes : DefRoutes {
  private val routes = mutableListOf<Route>()
  override fun route(path: String, title: String, extendDivScope: Ext<Div>) {
    routes.add(Route(title, path, extendDivScope))
  }
  fun match(path: String): RouteMatch {
    val pathParts = path.split('?')
    val pathname = pathParts.first()
    val (route, params) = routes.mapNotNull { r -> r.matchParams(pathname)?.let { r to it } }.firstOrNull()
      ?: error("No matching route for pathname $pathname")
    val queryParams =
      if (pathParts.size > 1)
        pathParts[1].split('&').associate { it.split('=')
          .let { (key, value) -> key.uriDecoded() to value.uriDecoded() } }
      else mapOf()
    return RouteMatch(
      route,
      PathParams(params),
      QueryParams(queryParams)
    )
  }
}

class Router(private val routes: Routes, private val routeMatch: State<RouteMatch>) {
  fun push(newPath: String) {
    val match = routes.match(newPath)
    window.history.pushState(newPath, match.route.title, window.location.origin + newPath)
    routeMatch.value = match
  }
  fun replace(newPath: String) {
    val match = routes.match(newPath)
    window.history.replaceState(newPath, match.route.title, window.location.origin + newPath)
    routeMatch.value = match
  }
  fun pop() {
    window.history.back()
  }
}

class Route(val title: String, pathPattern: String, val extendDivScope: Ext<Div>) {
  private val patternSegments = pathPattern.split('/')
  fun matchParams(path: String): Map<String, String>? {
    val segments = path.split('/')
    val result = mutableMapOf<String, String>()
    for (i in patternSegments.indices) {
      if (patternSegments[i] == "*") continue
      when {
        patternSegments[i].startsWith(':') -> result[patternSegments[i].drop(1)] = segments[i]
        patternSegments[i] != segments[i] -> return null
      }
    }
    return result
  }
}
class PathParams(pathParams: Map<String, String>): Map<String, String> by pathParams

class QueryParams(queryParams: Map<String, String>): Map<String, String> by queryParams
data class RouteMatch(val route: Route, val params: PathParams, val queryParams: QueryParams)