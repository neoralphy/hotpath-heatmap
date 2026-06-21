package com.hotpath.heatmap.analysis

import java.util.Locale

/**
 * Keyword tables and matchers for the static heuristics. Everything here is a cheap string
 * check on names already available from PSI — no extra resolution or I/O.
 */
object HeuristicSignals {

    /** Method names that often hide an expensive operation (DB / IO / messaging / dispatch). */
    private val EXPENSIVE_METHOD_NAMES = setOf(
        "find", "findby", "findall", "findone", "findoneby", "get", "getby", "load",
        "save", "persist", "flush", "update", "insert", "delete", "remove",
        "query", "fetch", "fetchall", "select", "execute",
        "send", "request", "call", "publish", "dispatch", "emit", "notify",
    )

    /** Class-name suffixes/keywords that smell like a data-access boundary. */
    private val REPOSITORY_LIKE = listOf("repository", "repo", "dao", "mapper", "entitymanager")

    /** Class-name suffixes/keywords that smell like an external I/O boundary. */
    private val CLIENT_LIKE =
        listOf("client", "gateway", "provider", "adapter", "httpclient", "apiclient", "connector")

    /**
     * Known I/O *function* names that are themselves external (built-in / library) so we never
     * traverse into them, but should still register as cost at the call site — chiefly JS HTTP
     * globals like `fetch`. Matched exactly (not by prefix) to avoid over-firing.
     */
    private val IO_GLOBALS = setOf("fetch", "ajax", "xmlhttprequest", "got")

    fun isExpensiveMethodName(name: String?): Boolean {
        val n = name?.lowercase(Locale.ROOT) ?: return false
        // Exact match, or a known prefix like "findActiveUsers" / "fetchOrders".
        return n in EXPENSIVE_METHOD_NAMES ||
            EXPENSIVE_METHOD_NAMES.any { n.length > it.length && n.startsWith(it) }
    }

    fun isRepositoryLikeClass(className: String?): Boolean = endsWithAny(className, REPOSITORY_LIKE)

    fun isClientLikeClass(className: String?): Boolean = endsWithAny(className, CLIENT_LIKE)

    fun isKnownIoCall(name: String?): Boolean =
        name?.lowercase(Locale.ROOT) in IO_GLOBALS

    /**
     * Matches data-access / I/O classes by *suffix*, following the usual naming convention
     * (`UserRepository`, `MailClient`, `PaymentGateway`). A substring match would over-fire —
     * e.g. "ReportService" contains "repo", "Mediator" contains "dao".
     */
    private fun endsWithAny(className: String?, keywords: List<String>): Boolean {
        val c = className?.lowercase(Locale.ROOT) ?: return false
        return keywords.any { c.endsWith(it) }
    }

    /** Path-based exclusion of vendor / generated / cache directories. */
    fun isVendorPath(path: String?): Boolean {
        val p = path?.replace('\\', '/')?.lowercase(Locale.ROOT) ?: return false
        return p.contains("/vendor/") ||
            p.contains("/node_modules/") ||
            p.contains("/generated/") ||
            p.contains("/var/cache/") ||
            p.contains("/.cache/")
    }

    /** Path/name-based detection of test code. */
    fun isTestPath(path: String?): Boolean {
        val p = path?.replace('\\', '/')?.lowercase(Locale.ROOT) ?: return false
        return p.contains("/tests/") ||
            p.contains("/test/") ||
            p.endsWith("test.php") ||
            p.endsWith("testcase.php") ||
            p.contains("/spec/") ||
            p.endsWith("spec.php")
    }
}
