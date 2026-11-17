package com.xuleo.xmplus.md3.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.xuleo.xmplus.md3.R

enum class Destination(
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Discover(R.string.tab_discover, Icons.Outlined.Explore, Icons.Filled.Explore),
    Library(R.string.tab_library, Icons.Outlined.Headphones, Icons.Filled.Headphones),
    Downloads(R.string.tab_downloads, Icons.Outlined.Download, Icons.Filled.Download),
    Me(R.string.tab_me, Icons.Outlined.Person, Icons.Filled.Person),
}