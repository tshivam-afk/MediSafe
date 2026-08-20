package com.medisafe.update

object AppVersion {
    fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")

    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parts(version: String): List<Int> =
        normalize(version).split('.').map { it.toIntOrNull() ?: 0 }
}
