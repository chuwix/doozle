package com.goholand.doozle.ui.screens.compare

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    state: ComparisonState,
    onWinnerSelected: (winner: com.goholand.doozle.engine.PairCandidate, loser: com.goholand.doozle.engine.PairCandidate) -> Unit,
    onBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (state) {
                        is ComparisonState.Ready -> {
                            Text("${state.totalPhotos} ranked | ${state.unseenCount} unseen")
                        }
                        else -> Text("Compare")
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("<") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                is ComparisonState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ComparisonState.NeedMorePhotos -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Need at least 2 photos",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add photos to your project folder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ComparisonState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ComparisonState.Ready -> {
                    ComparisonPair(
                        left = state.left,
                        right = state.right,
                        isLandscape = isLandscape,
                        onLeftWins = { onWinnerSelected(state.left, state.right) },
                        onRightWins = { onWinnerSelected(state.right, state.left) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonPair(
    left: com.goholand.doozle.engine.PairCandidate,
    right: com.goholand.doozle.engine.PairCandidate,
    isLandscape: Boolean,
    onLeftWins: () -> Unit,
    onRightWins: () -> Unit
) {
    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize()) {
            PhotoPanel(
                candidate = left,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = onLeftWins
            )
            Spacer(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.Black))
            PhotoPanel(
                candidate = right,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = onRightWins
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            PhotoPanel(
                candidate = left,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onClick = onLeftWins
            )
            Spacer(modifier = Modifier.height(2.dp).fillMaxWidth().background(Color.Black))
            PhotoPanel(
                candidate = right,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onClick = onRightWins
            )
        }
    }
}

@Composable
private fun PhotoPanel(
    candidate: com.goholand.doozle.engine.PairCandidate,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = Uri.parse(candidate.path),
            contentDescription = "Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Unseen badge
        if (candidate.isUnseen) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "NEW",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }
}
