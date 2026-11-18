package com.xuleo.xmplus.md3.ui.components

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** A date in the reader's own format, as the list rows show it. */
fun formatDate(millis: Long, locale: Locale): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))