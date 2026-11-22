package com.codejam.taptation.logic

/**
 * An app the user can pick. Built by the backend (service/AppDirectory) from the package manager;
 * screens show [label] and the icon, never the package name. [notificationsEnabled] is whether the
 * app may post notifications (always true before Android 13, where it can't be checked).
 */
data class InstalledApp(val packageName: String, val label: String, val notificationsEnabled: Boolean = true)

/** Launchable apps: null while loading; [error] when the package manager query failed. */
data class AppList(val apps: List<InstalledApp>? = null, val error: String? = null)

/** One row of the Notification rules list: an eligible app and its saved rule. */
data class RuleApp(val app: InstalledApp, val rule: AppRule)

/**
 * Everything the Notification rules page lists, decided by the backend: which apps are eligible
 * (launchable, not Taptation, allowed to post notifications when Android can tell) and which saved
 * rules belong to apps that are no longer installed.
 *
 * @property apps eligible apps with their rule, sorted by name; null while loading.
 * @property missingRules packages with a saved rule that are no longer installed.
 * @property notificationFilterSupported false before Android 13, where every app is listed.
 */
data class RulePicker(
    val apps: List<RuleApp>? = null,
    val missingRules: List<String> = emptyList(),
    val notificationFilterSupported: Boolean = true,
    val error: String? = null,
) {
    /** Case-insensitive match on the app name; a blank query keeps everything. Pure display filtering. */
    fun search(query: String): List<RuleApp> {
        val needle = query.trim()
        return apps.orEmpty().filter { needle.isEmpty() || it.app.label.contains(needle, ignoreCase = true) }
    }

    companion object {
        fun build(list: AppList, config: GameConfig?, notificationFilterSupported: Boolean): RulePicker {
            val installed = list.apps ?: return RulePicker(error = list.error,
                notificationFilterSupported = notificationFilterSupported)
            val eligible = installed.filter { it.notificationsEnabled || !notificationFilterSupported }
            val rules = config?.appRules.orEmpty()
            val installedPackages = installed.mapTo(HashSet()) { it.packageName }
            return RulePicker(
                apps = eligible.map { RuleApp(it, config?.ruleFor(it.packageName) ?: AppRule.LetUsDecide) },
                missingRules = rules.keys.filter { it !in installedPackages }.sorted(),
                notificationFilterSupported = notificationFilterSupported,
                error = list.error,
            )
        }
    }
}
