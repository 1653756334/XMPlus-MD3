package com.xuleo.xmplus.md3.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.browse.DiscoverViewModel
import com.xuleo.xmplus.md3.core.designsystem.XmLayout
import com.xuleo.xmplus.md3.ui.components.*

/** The whole 热播榜 behind a channel's preview card. The preview already loaded it, so this normally opens at once. */
@Composable
fun HotRankingScreen(viewModel: DiscoverViewModel, pageId: Int, onOpenAlbum: (String) -> Unit = {}, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.pages[pageId]
    val ranking = page?.ranking
    val title = when {
        ranking == null -> stringResource(R.string.discover_ranking_default)
        ranking.wholeSite -> stringResource(R.string.discover_ranking_whole)
        else -> ranking.section.title.ifEmpty { stringResource(R.string.discover_ranking_default) }
    }
    BrowsePage(title, onBack) {
        when {
            ranking != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = XmLayout.ContentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = XmLayout.PagePadding),
                ) {
                    itemsIndexed(ranking.section.albums, key = { _, album -> album.id }) { index, album ->
                        RankRow(index + 1, album, ranking.section.stats[album.id], Modifier.padding(horizontal = XmLayout.PagePadding, vertical = 8.dp)) { onOpenAlbum(album.id) }
                    }
                }
            }
            page?.rankingFailed == true -> ErrorState("network", { viewModel.retryRanking(pageId) })
            else -> ListSkeleton()
        }
    }
}