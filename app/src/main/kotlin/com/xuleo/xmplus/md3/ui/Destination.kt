package com.xuleo.xmplus.md3.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.xuleo.xmplus.md3.ui.icons.XmIcons
import com.xuleo.xmplus.md3.R

enum class Destination(
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Discover(R.string.tab_discover, XmIcons.Outlined.Explore, XmIcons.Filled.Explore),
    Music(R.string.tab_music, XmIcons.Filled.MusicNote, XmIcons.Filled.MusicNote),
    Library(R.string.tab_library, XmIcons.Outlined.Headphones, XmIcons.Filled.Headphones),
    Downloads(R.string.tab_downloads, XmIcons.Outlined.Download, XmIcons.Filled.Download),
    Me(R.string.tab_me, XmIcons.Outlined.Person, XmIcons.Filled.Person),
}