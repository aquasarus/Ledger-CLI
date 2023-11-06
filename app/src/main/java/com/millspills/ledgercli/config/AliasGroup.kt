package com.millspills.ledgercli.config

data class AliasGroup(
    val aliases: MutableSet<String> = mutableSetOf()
) {
    private var _mostUsedCategory: String? = null
    internal val categories: MutableMap<String, Int> = mutableMapOf()
    private var _totalCount = 0
    internal var _forcedCategory: String? = null

    fun addAlias(alias: String) {
        aliases.add(alias)
    }

    fun countCategory(category: String, count: Int = 1) {
        categories[category]?.let {
            categories[category] = it + count
        } ?: run {
            categories[category] = count
        }

        _totalCount += count

        val mostUsedCategoryCount = categories[_mostUsedCategory] ?: 0
        // using >= here to take most recent category if counts are equal
        if (categories[category]!! >= mostUsedCategoryCount) {
            _mostUsedCategory = category
        }
    }

    fun getMostUsedCategory(): Pair<String?, Boolean> {
        _forcedCategory?.let {
            return Pair(_forcedCategory, true)
        }

        _mostUsedCategory ?: return Pair(null, true)

        val mostUsedCount = categories[_mostUsedCategory]!!

        return if (mostUsedCount.toDouble() / _totalCount.toDouble() > 0.8) {
            Pair(_mostUsedCategory, true)
        } else {
            Pair(_mostUsedCategory, false)
        }
    }

    fun forceCategory(category: String) {
        _forcedCategory = category
    }
}
