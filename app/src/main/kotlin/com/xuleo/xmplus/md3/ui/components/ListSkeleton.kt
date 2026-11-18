package com.xuleo.xmplus.md3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xuleo.xmplus.md3.core.designsystem.XmLayout

/** Placeholder rows for a list of covers with two lines of text. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        repeat(7) {
            Row(horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).skeleton(MaterialTheme.shapes.medium))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(width = 200.dp, height = 14.dp).skeleton())
                    Box(Modifier.size(width = 120.dp, height = 12.dp).skeleton())
                }
            }
        }
    }
}

/** Placeholder tiles for a grid of album covers. */
@Composable
fun GridSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(XmLayout.PagePadding), verticalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XmLayout.ItemGap)) {
                repeat(3) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).skeleton(MaterialTheme.shapes.medium))
                        Box(Modifier.fillMaxWidth(0.8f).size(height = 14.dp, width = 80.dp).skeleton())
                    }
                }
            }
        }
    }
}