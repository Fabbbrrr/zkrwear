package com.zkrwatch.data.net

/** Small helpers for navigating the untyped `Map<String, Any?>` API responses. */

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.child(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.list(key: String): List<Any?>? = this[key] as? List<Any?>

fun Map<String, Any?>.str(key: String): String? = this[key]?.toString()

fun Map<String, Any?>.bool(key: String): Boolean = this[key] == true

fun Map<String, Any?>.isSuccess(): Boolean = bool("success")

/** Follow a dotted path of object keys, e.g. `data.electricVehicleStatus.chargeLevel`. */
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.path(vararg keys: String): Any? {
    var current: Any? = this
    for (k in keys) {
        current = (current as? Map<String, Any?>)?.get(k) ?: return null
    }
    return current
}

fun Map<String, Any?>.numAt(vararg keys: String): Double? = when (val v = path(*keys)) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

fun Map<String, Any?>.strAt(vararg keys: String): String? = path(*keys)?.toString()

fun Map<String, Any?>.boolAt(vararg keys: String): Boolean? = when (val v = path(*keys)) {
    is Boolean -> v
    is Number -> v.toInt() == 1
    is String -> v == "true" || v == "1"
    else -> null
}
