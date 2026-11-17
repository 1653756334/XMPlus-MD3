package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.runtime.Composable
import com.xuleo.xmplus.md3.ui.Destination

@Composable
fun DiscoverScreen() = EmptyTab(Destination.Discover.label, Destination.Discover.icon)

@Composable
fun LibraryScreen() = EmptyTab(Destination.Library.label, Destination.Library.icon)

@Composable
fun DownloadsScreen() = EmptyTab(Destination.Downloads.label, Destination.Downloads.icon)