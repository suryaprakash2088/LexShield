package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AdvocateCard
import com.example.model.CuratedReferenceData
import com.example.model.FeedComment
import com.example.model.FeedPost
import com.example.model.UserProfile
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.EmergencyCrimson
import com.example.ui.theme.VerifiedEmerald

@Composable
fun NetworkScreen(
    currentUserId: String,
    profile: UserProfile?,
    posts: List<FeedPost>,
    peers: List<AdvocateCard>,
    selectedPostForComments: FeedPost?,
    comments: List<FeedComment>,
    initialTab: Int = 0,
    onCreatePost: (courtTag: String, body: String) -> Unit,
    onToggleLike: (FeedPost) -> Unit,
    onReportPost: (FeedPost) -> Unit,
    onOpenComments: (FeedPost?) -> Unit,
    onAddComment: (FeedPost, String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var newPostBody by remember { mutableStateOf("") }
    var newPostCourt by remember { mutableStateOf("Delhi High Court") }
    var directoryQuery by remember { mutableStateOf("") }
    var directoryStateFilter by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Feed") },
                modifier = Modifier.testTag("network_tab_feed")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Police Directory") },
                modifier = Modifier.testTag("network_tab_police")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Advocates") },
                modifier = Modifier.testTag("network_tab_peers")
            )
        }

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        DisclaimerBanner(text = stringResource(R.string.feed_confidentiality_notice))
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Share Practice or Safety Update", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = newPostCourt,
                                    onValueChange = { newPostCourt = it },
                                    label = { Text("Court / Bar Tag") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("feed_court_tag_input")
                                )
                                OutlinedTextField(
                                    value = newPostBody,
                                    onValueChange = { newPostBody = it },
                                    label = { Text("Post update (no confidential client details)") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth().testTag("feed_body_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            if (newPostBody.isNotBlank()) {
                                                onCreatePost(newPostCourt, newPostBody)
                                                newPostBody = ""
                                            }
                                        },
                                        enabled = newPostBody.isNotBlank(),
                                        modifier = Modifier.testTag("feed_publish_button")
                                    ) {
                                        Text("Publish")
                                    }
                                }
                            }
                        }
                    }

                    if (posts.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("No posts in the network yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Share a court logistics update or Bar Association advisory above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            val isLiked = currentUserId in post.likedByUids
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                                            if (post.authorVerified) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Filled.VerifiedUser,
                                                    contentDescription = "Verified",
                                                    tint = VerifiedEmerald,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatTimestamp(post.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "${post.courtTag} • ${post.authorPlatformId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(post.body, style = MaterialTheme.typography.bodyMedium)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { onToggleLike(post) }) {
                                                Icon(
                                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                    contentDescription = "Like",
                                                    tint = if (isLiked) EmergencyCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text("${post.likeCount}", style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(onClick = { onOpenComments(post) }) {
                                                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Comments")
                                            }
                                            Text("${post.commentCount}", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                val shareText = "${post.authorName} (${post.courtTag}):\n${post.body}\n— Shared via LexShield"
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    if (profile?.premiumActive == true) {
                                                        setPackage("com.whatsapp")
                                                    }
                                                }
                                                runCatching {
                                                    context.startActivity(
                                                        if (profile?.premiumActive == true) intent
                                                        else Intent.createChooser(intent, "Share Advocate Post")
                                                    )
                                                }.onFailure {
                                                    val fallback = Intent.createChooser(
                                                        Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                                        },
                                                        "Share Advocate Post"
                                                    )
                                                    context.startActivity(fallback)
                                                }
                                            }) {
                                                Icon(Icons.Filled.Share, contentDescription = "Share post")
                                            }
                                            IconButton(onClick = { onReportPost(post) }) {
                                                Icon(Icons.Filled.Flag, contentDescription = "Report post")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                val allStations = CuratedReferenceData.policeDirectory
                val states = remember { listOf("All") + allStations.map { it.state }.distinct() }
                val filteredStations = remember(directoryQuery, directoryStateFilter) {
                    allStations.filter { entry ->
                        val matchesState = directoryStateFilter == "All" || entry.state == directoryStateFilter
                        val q = directoryQuery.trim().lowercase()
                        val matchesText = q.isEmpty() ||
                            entry.name.lowercase().contains(q) ||
                            entry.city.lowercase().contains(q) ||
                            entry.district.lowercase().contains(q) ||
                            entry.rankOrDesignation.lowercase().contains(q)
                        matchesState && matchesText
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        DisclaimerBanner(
                            text = "Validated reference directory with last-verified dates. LexShield is not integrated with police CAD/dispatch systems."
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = directoryQuery,
                            onValueChange = { directoryQuery = it },
                            label = { Text("Search station, officer rank, city, or district") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("police_directory_search")
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            states.take(4).forEach { st ->
                                FilterChip(
                                    selected = directoryStateFilter == st,
                                    onClick = { directoryStateFilter = st },
                                    label = { Text(st) }
                                )
                            }
                        }
                    }
                    items(filteredStations, key = { it.id }) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.entryType.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Verified: ${entry.lastVerifiedDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VerifiedEmerald
                                    )
                                }
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${entry.rankOrDesignation} • ${entry.agency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    entry.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Source: ${entry.sourceAttribution}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Button(onClick = {
                                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.phone}"))
                                        runCatching { context.startActivity(dial) }
                                    }) {
                                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(" Call ${entry.phone}", modifier = Modifier.padding(start = 6.dp))
                                    }
                                    OutlinedButton(onClick = {
                                        val geo = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("geo:${entry.lat},${entry.lng}?q=${Uri.encode(entry.name)}")
                                        )
                                        runCatching { context.startActivity(geo) }
                                    }) {
                                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(" Map", modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Discoverable Advocates (${peers.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Only advocates who opted into discoverability are shown. Precise home/live coordinates are never exposed here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(peers, key = { it.userId }) { peer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(peer.platformId, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    "${peer.courtLocation} • ${peer.city}, ${peer.state}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (peer.e2eeKeyFingerprint.isNotBlank()) {
                                    Text(
                                        "E2EE Key: ${peer.e2eeKeyFingerprint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VerifiedEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPostForComments?.let { post ->
        var commentText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { onOpenComments(null) },
            title = { Text("Comments • ${post.authorName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (comments.isEmpty()) {
                        Text("No comments yet. Start the discussion.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        comments.take(8).forEach { c ->
                            Column {
                                Text(
                                    "${c.authorName} (${c.authorPlatformId})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(c.body, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = { Text("Add a professional comment") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onAddComment(post, commentText)
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Post", modifier = Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { onOpenComments(null) }) { Text("Close") }
            }
        )
    }
}
