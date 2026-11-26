package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LegalUpdateItem
import com.example.ui.components.DisclaimerBanner
import com.example.ui.theme.VerifiedEmerald

@Composable
fun LegalUpdatesScreen(
    items: List<LegalUpdateItem>,
    bookmarkedIds: Set<String>,
    onToggleBookmark: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCourt by remember { mutableStateOf("All") }
    var onlyBookmarked by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<LegalUpdateItem?>(null) }

    val courts = remember(items) {
        listOf("All") + items.map { it.court }.distinct()
    }

    val filtered = remember(items, searchQuery, selectedCourt, onlyBookmarked, bookmarkedIds) {
        items.filter { item ->
            val matchesCourt = selectedCourt == "All" || item.court == selectedCourt
            val matchesSaved = !onlyBookmarked || item.id in bookmarkedIds
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.caseNumber.lowercase().contains(q) ||
                item.summary.lowercase().contains(q) ||
                item.tags.any { it.lowercase().contains(q) }
            matchesCourt && matchesSaved && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(text = "Legal Updates & Judgments", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            DisclaimerBanner(text = stringResource(R.string.legal_disclaimer))
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search case number, court, or topic") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("legal_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = onlyBookmarked,
                        onClick = { onlyBookmarked = !onlyBookmarked },
                        label = { Text("Bookmarked (${bookmarkedIds.size})") }
                    )
                }
                items(courts) { court ->
                    FilterChip(
                        selected = selectedCourt == court,
                        onClick = { selectedCourt = court },
                        label = { Text(court) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matching legal updates found.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try clearing filters or search keywords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val isSaved = item.id in bookmarkedIds
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedItem = item }
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
                            Text(
                                text = "${item.court} • ${item.decisionDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            IconButton(
                                onClick = { onToggleBookmark(item.id) },
                                modifier = Modifier.testTag("bookmark_legal_${item.id}")
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = if (isSaved) "Remove bookmark" else "Bookmark judgment"
                                )
                            }
                        }

                        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.caseNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Reviewed",
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Reviewed by ${item.reviewerName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = VerifiedEmerald
                            )
                        }
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(item.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${item.court} • ${item.caseNumber} (${item.decisionDate})",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(text = item.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Editorial Review: ${item.reviewerName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = VerifiedEmerald
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))
                    runCatching { context.startActivity(intent) }
                }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Official Source", modifier = Modifier.padding(start = 6.dp))
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${item.title} (${item.court}, ${item.caseNumber})\n${item.summary}\nSource: ${item.sourceUrl}\n(Informational summary via LexShield — not legal advice)"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Legal Update"))
                    }) {
                        Text("Share")
                    }
                    TextButton(onClick = { selectedItem = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
