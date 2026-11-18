package com.xuleo.xmplus.md3.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import com.xuleo.xmplus.md3.R
import java.util.Locale

/**
 * A count as short text: 12,345 is 1.2万 or 12K, by language. Below ten units of a step it keeps one decimal,
 * above that none; it rounds down so a count never reads higher than it is.
 */
@Composable
fun formatCount(count: Long): String {
    val tiers = listOf(
        integerResource(R.integer.count_step_large) to R.string.count_unit_large,
        integerResource(R.integer.count_step_medium) to R.string.count_unit_medium,
        integerResource(R.integer.count_step_small) to R.string.count_unit_small,
    )
    val (step, unit) = tiers.firstOrNull { (step, _) -> step > 0 && count >= step } ?: return count.toString()
    val scaled = count.toDouble() / step
    val number = if (scaled < 10) String.format(Locale.ROOT, "%.1f", Math.floor(scaled * 10) / 10).removeSuffix(".0")
        else scaled.toLong().toString()
    return stringResource(unit, number)
}