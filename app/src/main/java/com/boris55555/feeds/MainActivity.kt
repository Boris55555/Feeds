package com.boris55555.feeds

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.boris55555.feeds.ui.theme.FeedsTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import java.net.URI

private const val PREFS_NAME = "FeedsPrefs"
private const val KEY_SOURCES = "sources"
private const val KEY_TAGS = "all_tags"
private const val KEY_REFRESH_RATE = "refresh_rate"
private const val KEY_ARCHIVES = "archives"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeedsTheme(eInkMode = true) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(emptyList<FeedItem>()) }
    var sources by remember { mutableStateOf(emptyList<FeedSource>()) }
    var archivedItems by remember { mutableStateOf(emptyList<FeedItem>()) }
    var allTags by remember { mutableStateOf(emptyList<String>()) }
    var refreshRate by remember { mutableStateOf("Manually") }
    var selectedTag by remember { mutableStateOf("All") }
    var selectedItem by remember { mutableStateOf<FeedItem?>(null) }
    var showArchives by remember { mutableStateOf(false) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManageFeedsDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    
    var newFeedUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var discoveredSources by remember { mutableStateOf(emptyList<FeedSource>()) }
    
    val scope = rememberCoroutineScope()
    val parser = remember { FeedParser() }
    val tagListState = rememberLazyListState()

    // Load data on startup
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSources = prefs.getString(KEY_SOURCES, null)
        val savedTags = prefs.getString(KEY_TAGS, null)
        val savedArchives = prefs.getString(KEY_ARCHIVES, null)
        refreshRate = prefs.getString(KEY_REFRESH_RATE, "Manually") ?: "Manually"
        
        if (savedTags != null) {
            val tagArray = JSONArray(savedTags)
            val loadedTags = mutableListOf<String>()
            for (i in 0 until tagArray.length()) loadedTags.add(tagArray.getString(i))
            allTags = loadedTags
        }

        if (savedArchives != null) {
            try {
                val jsonArray = JSONArray(savedArchives)
                val loadedArchives = mutableListOf<FeedItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loadedArchives.add(FeedItem(
                        obj.getString("title"),
                        obj.getString("summary"),
                        obj.getString("date"),
                        obj.getString("sourceUrl"),
                        obj.optString("link", ""),
                        if (obj.has("fullContent")) obj.getString("fullContent") else null
                    ))
                }
                archivedItems = loadedArchives
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (savedSources != null) {
            try {
                val jsonArray = JSONArray(savedSources)
                val loadedSources = mutableListOf<FeedSource>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val tagList = mutableListOf<String>()
                    val tagArray = obj.optJSONArray("tags")
                    if (tagArray != null) {
                        for (j in 0 until tagArray.length()) tagList.add(tagArray.getString(j))
                    }
                    loadedSources.add(FeedSource(
                        obj.getString("title"), 
                        obj.getString("url"),
                        obj.optString("sourceLang", "auto"),
                        obj.optString("targetLang", "fi"),
                        obj.optBoolean("isTranslationEnabled", false),
                        tagList
                    ))
                }
                sources = loadedSources
                
                if (sources.isNotEmpty() && refreshRate == "While app opens") {
                    isLoading = true
                    refreshItems(sources, parser) { items = it; isLoading = false }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Save data when it changes
    LaunchedEffect(sources, allTags, refreshRate, archivedItems) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        
        val sourceArray = JSONArray()
        sources.forEach { s ->
            val obj = JSONObject()
            obj.put("title", s.title)
            obj.put("url", s.url)
            obj.put("sourceLang", s.sourceLang)
            obj.put("targetLang", s.targetLang)
            obj.put("isTranslationEnabled", s.isTranslationEnabled)
            val tagArray = JSONArray()
            s.tags.forEach { tagArray.put(it) }
            obj.put("tags", tagArray)
            sourceArray.put(obj)
        }
        prefs.putString(KEY_SOURCES, sourceArray.toString())
        
        val tagArray = JSONArray()
        allTags.forEach { tagArray.put(it) }
        prefs.putString(KEY_TAGS, tagArray.toString())
        
        val archiveArray = JSONArray()
        archivedItems.forEach { i ->
            val obj = JSONObject()
            obj.put("title", i.title)
            obj.put("summary", i.summary)
            obj.put("date", i.date)
            obj.put("sourceUrl", i.sourceUrl)
            obj.put("link", i.link)
            if (i.fullContent != null) obj.put("fullContent", i.fullContent)
            archiveArray.put(obj)
        }
        prefs.putString(KEY_ARCHIVES, archiveArray.toString())
        
        prefs.putString(KEY_REFRESH_RATE, refreshRate)
        
        prefs.apply()
        
        scheduleRefresh(context, refreshRate)
    }

    if (selectedItem != null) {
        val source = sources.find { it.url == selectedItem!!.sourceUrl }
        val isArchived = archivedItems.any { it.link == selectedItem!!.link && it.title == selectedItem!!.title }
        BackHandler { selectedItem = null }
        ItemDetailScreen(
            item = selectedItem!!, 
            onBack = { selectedItem = null },
            parser = parser,
            source = source,
            isArchived = isArchived,
            onToggleArchive = { item, content ->
                if (isArchived) {
                    archivedItems = archivedItems.filterNot { it.link == item.link && it.title == item.title }
                } else {
                    archivedItems = archivedItems + item.copy(fullContent = content)
                }
            }
        )
    } else {
        if (showArchives) {
            BackHandler { showArchives = false }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (showArchives) "ARCHIVES" else "FEEDS",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        },
                        navigationIcon = {
                            if (showArchives) {
                                IconButton(onClick = { showArchives = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                                }
                            }
                        },
                        actions = {
                            if (!showArchives) {
                                IconButton(onClick = { 
                                    isLoading = true
                                    scope.launch {
                                        refreshItems(sources, parser) { items = it; isLoading = false }
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.Black)
                                }
                                IconButton(onClick = { showArchives = true }) {
                                    Icon(Icons.Default.Archive, contentDescription = "Archives", tint = Color.Black)
                                }
                            }
                            IconButton(onClick = { 
                                showAddDialog = true 
                                discoveredSources = emptyList()
                                newFeedUrl = ""
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Feed", tint = Color.Black)
                            }
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Black)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = Color.Black
                        )
                    )
                    
                    if (!showArchives && allTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tagListState.canScrollBackward) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val targetIndex = (tagListState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                                            tagListState.animateScrollToItem(targetIndex)
                                        }
                                    },
                                    modifier = Modifier.width(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Scroll Left", tint = Color.Black)
                                }
                            }
                            
                            LazyRow(
                                state = tagListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = if (tagListState.canScrollBackward || tagListState.canScrollForward) 0.dp else 16.dp)
                            ) {
                                item {
                                    if (!tagListState.canScrollBackward) Spacer(modifier = Modifier.width(16.dp))
                                    TagChip(
                                        name = "All",
                                        isSelected = selectedTag == "All",
                                        onClick = { selectedTag = "All" }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                items(allTags) { tag ->
                                    TagChip(
                                        name = tag,
                                        isSelected = selectedTag == tag,
                                        onClick = { selectedTag = tag }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                item {
                                    if (!tagListState.canScrollForward) Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            if (tagListState.canScrollForward) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val targetIndex = tagListState.firstVisibleItemIndex + 2
                                            tagListState.animateScrollToItem(targetIndex)
                                        }
                                    },
                                    modifier = Modifier.width(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Scroll Right", tint = Color.Black)
                                }
                            }
                        }
                    }
                    HorizontalDivider(thickness = 4.dp, color = Color.Black, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                if (isLoading) {
                    Text("Loading...", modifier = Modifier.padding(16.dp))
                }
                
                val currentList = if (showArchives) archivedItems else items
                val filteredItems = if (showArchives || selectedTag == "All") {
                    currentList
                } else {
                    val allowedUrls = sources.filter { it.tags.contains(selectedTag) }.map { it.url }
                    currentList.filter { allowedUrls.contains(it.sourceUrl) }
                }

                if (filteredItems.isEmpty() && !isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (showArchives) "No archived articles." else if (items.isEmpty()) "No feeds. Press + to add." else "No feeds for this tag.", 
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    FeedList(
                        items = filteredItems,
                        onItemClick = { selectedItem = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Dialogs
            if (showAddDialog) {
                AddFeedDialog(
                    newFeedUrl = newFeedUrl,
                    onUrlChange = { newFeedUrl = it; discoveredSources = emptyList() },
                    discoveredSources = discoveredSources,
                    onSearch = {
                        scope.launch {
                            isLoading = true
                            discoveredSources = parser.discoverFeeds(newFeedUrl)
                            isLoading = false
                        }
                    },
                    onAddDirect = {
                        scope.launch {
                            isLoading = true
                            showAddDialog = false
                            val (title, _) = parser.fetchFeedItems(newFeedUrl)
                            sources = sources + FeedSource(title, newFeedUrl)
                            refreshItems(sources, parser) { items = it; isLoading = false }
                        }
                    },
                    onAddDiscovered = { source ->
                        scope.launch {
                            isLoading = true
                            showAddDialog = false
                            val (title, _) = parser.fetchFeedItems(source.url)
                            sources = sources + FeedSource(title, source.url)
                            refreshItems(sources, parser) { items = it; isLoading = false }
                        }
                    },
                    onDismiss = { showAddDialog = false }
                )
            }

            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Button(
                                onClick = { showSettingsDialog = false; showManageFeedsDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RectangleShape
                            ) { Text("Manage Feeds") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showSettingsDialog = false; showManageTagsDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RectangleShape
                            ) { Text("Manage Tags") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val exportData = JSONObject()
                                        val sourcesArray = JSONArray()
                                        sources.forEach { s ->
                                            val obj = JSONObject()
                                            obj.put("title", s.title)
                                            obj.put("url", s.url)
                                            obj.put("sourceLang", s.sourceLang)
                                            obj.put("targetLang", s.targetLang)
                                            obj.put("isTranslationEnabled", s.isTranslationEnabled)
                                            val tagsArr = JSONArray()
                                            s.tags.forEach { tagsArr.put(it) }
                                            obj.put("tags", tagsArr)
                                            sourcesArray.put(obj)
                                        }
                                        exportData.put("sources", sourcesArray)
                                        val tagsArray = JSONArray()
                                        allTags.forEach { tagsArray.put(it) }
                                        exportData.put("tags", tagsArray)
                                        
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Feeds Export", exportData.toString())
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Exported to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RectangleShape
                                ) { Text("Export") }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showSettingsDialog = false; showImportDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RectangleShape
                                ) { Text("Import") }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Refresh Rate:", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            RefreshRateSelector(
                                current = refreshRate,
                                onSelected = { refreshRate = it }
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showSettingsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = RectangleShape) {
                            Text("CLOSE")
                        }
                    },
                    containerColor = Color.White,
                    shape = RectangleShape
                )
            }

            if (showManageFeedsDialog) {
                ManageFeedsDialog(
                    sources = sources,
                    onRemove = { source ->
                        sources = sources.filter { it.url != source.url }
                        scope.launch {
                            isLoading = true
                            refreshItems(sources, parser) { items = it; isLoading = false }
                        }
                    },
                    onUpdateSource = { updated ->
                        sources = sources.map { if (it.url == updated.url) updated else it }
                    },
                    onDone = { 
                        showManageFeedsDialog = false
                        scope.launch {
                            isLoading = true
                            refreshItems(sources, parser) { items = it; isLoading = false }
                        }
                    }
                )
            }

            if (showManageTagsDialog) {
                ManageTagsDialog(
                    allTags = allTags,
                    sources = sources,
                    onAddTag = { if (it.isNotBlank() && !allTags.contains(it)) allTags = allTags + it },
                    onDeleteTag = { tag ->
                        allTags = allTags.filter { it != tag }
                        sources = sources.map { s -> s.copy(tags = s.tags.filter { it != tag }) }
                        if (selectedTag == tag) selectedTag = "All"
                    },
                    onMoveTag = { from, to ->
                        val mutable = allTags.toMutableList()
                        val item = mutable.removeAt(from)
                        mutable.add(to, item)
                        allTags = mutable
                    },
                    onToggleFeedInTag = { tag, source ->
                        sources = sources.map { s ->
                            if (s.url == source.url) {
                                val newTags = if (s.tags.contains(tag)) s.tags.filter { it != tag } else s.tags + tag
                                s.copy(tags = newTags)
                            } else s
                        }
                    },
                    onDone = { showManageTagsDialog = false }
                )
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Import Feeds", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Paste your export JSON below:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black, cursorColor = Color.Black),
                                shape = RectangleShape
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                try {
                                    val data = JSONObject(importText)
                                    val importedTags = data.optJSONArray("tags")
                                    if (importedTags != null) {
                                        val newTags = allTags.toMutableList()
                                        for (i in 0 until importedTags.length()) {
                                            val t = importedTags.getString(i)
                                            if (!newTags.contains(t)) newTags.add(t)
                                        }
                                        allTags = newTags
                                    }
                                    
                                    val importedSources = data.optJSONArray("sources")
                                    if (importedSources != null) {
                                        val newSources = sources.toMutableList()
                                        for (i in 0 until importedSources.length()) {
                                            val obj = importedSources.getJSONObject(i)
                                            val url = obj.getString("url")
                                            if (newSources.none { it.url == url }) {
                                                val tagList = mutableListOf<String>()
                                                val tagArr = obj.optJSONArray("tags")
                                                if (tagArr != null) {
                                                    for (j in 0 until tagArr.length()) tagList.add(tagArr.getString(j))
                                                }
                                                newSources.add(FeedSource(
                                                    obj.getString("title"),
                                                    url,
                                                    obj.optString("sourceLang", "auto"),
                                                    obj.optString("targetLang", "fi"),
                                                    obj.optBoolean("isTranslationEnabled", false),
                                                    tagList
                                                ))
                                            }
                                        }
                                        sources = newSources
                                    }
                                    showImportDialog = false
                                    importText = ""
                                    android.widget.Toast.makeText(context, "Import successful!", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Invalid JSON!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RectangleShape
                        ) { Text("IMPORT") }
                    },
                    dismissButton = {
                        Button(onClick = { showImportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), border = BorderStroke(1.dp, Color.Black), shape = RectangleShape) {
                            Text("CANCEL")
                        }
                    },
                    containerColor = Color.White,
                    shape = RectangleShape
                )
            }
        }
    }
}

suspend fun refreshItems(sources: List<FeedSource>, parser: FeedParser, onUpdate: (List<FeedItem>) -> Unit) {
    val allItems = mutableListOf<FeedItem>()
    sources.forEach { s ->
        val (_, fetched) = parser.fetchFeedItems(s.url)
        val limited = fetched.take(10)
        if (s.isTranslationEnabled && s.sourceLang != s.targetLang) {
            limited.forEach { item ->
                val translatedTitle = parser.translate(item.title, s.sourceLang, s.targetLang)
                allItems.add(item.copy(title = translatedTitle))
            }
        } else {
            allItems.addAll(limited)
        }
    }
    onUpdate(allItems.distinctBy { it.title }.sortedByDescending { it.parsedDate })
}

fun scheduleRefresh(context: Context, rate: String) {
    val workManager = WorkManager.getInstance(context)
    // Always cancel periodic work as we now only support startup or manual refresh
    workManager.cancelUniqueWork("FeedRefresh")
}

@Composable
fun TagChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = name.uppercase(),
        modifier = Modifier
            .border(1.dp, Color.Black)
            .background(if (isSelected) Color.Black else Color.White)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (isSelected) Color.White else Color.Black,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
}

@Composable
fun AddFeedDialog(
    newFeedUrl: String,
    onUrlChange: (String) -> Unit,
    discoveredSources: List<FeedSource>,
    onSearch: () -> Unit,
    onAddDirect: () -> Unit,
    onAddDiscovered: (FeedSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Feed", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = newFeedUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Feed or Website URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black, cursorColor = Color.Black),
                    shape = RectangleShape
                )
                if (discoveredSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select a feed to add:", fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(discoveredSources) { source ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddDiscovered(source) }
                                    .padding(vertical = 8.dp)
                                    .border(1.dp, Color.Black)
                                    .padding(8.dp)
                            ) {
                                Text(source.title, fontWeight = FontWeight.Bold)
                                Text(source.url, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            val isLikelyFeed = newFeedUrl.endsWith(".rss") || newFeedUrl.endsWith(".xml") || newFeedUrl.contains("/feed")
            Button(
                onClick = { if (isLikelyFeed) onAddDirect() else onSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RectangleShape
            ) { Text(if (isLikelyFeed) "ADD" else "SEARCH") }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), border = BorderStroke(1.dp, Color.Black), shape = RectangleShape) {
                Text("CANCEL")
            }
        },
        containerColor = Color.White,
        shape = RectangleShape
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeedsDialog(
    sources: List<FeedSource>,
    onRemove: (FeedSource) -> Unit,
    onUpdateSource: (FeedSource) -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Manage Feeds", fontWeight = FontWeight.Bold) },
        text = {
            if (sources.isEmpty()) {
                Text("No feeds to manage.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(sources) { source ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(1.dp, Color.Black)
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(source.title, fontWeight = FontWeight.Bold)
                                    Text(source.url, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { onRemove(source) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", tint = Color.Black)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Translate: ", style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { onUpdateSource(source.copy(isTranslationEnabled = !source.isTranslationEnabled)) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (source.isTranslationEnabled) Color.Black else Color.White,
                                        contentColor = if (source.isTranslationEnabled) Color.White else Color.Black
                                    ),
                                    border = BorderStroke(1.dp, Color.Black),
                                    shape = RectangleShape,
                                    modifier = Modifier.height(32.dp).padding(horizontal = 4.dp)
                                ) {
                                    Text(if (source.isTranslationEnabled) "ON" else "OFF", fontSize = 10.sp)
                                }
                            }
                            if (source.isTranslationEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LanguageSelector(
                                        current = source.sourceLang,
                                        onSelected = { onUpdateSource(source.copy(sourceLang = it)) }
                                    )
                                    Text(" -> ", style = MaterialTheme.typography.bodySmall)
                                    LanguageSelector(
                                        current = source.targetLang,
                                        onSelected = { onUpdateSource(source.copy(targetLang = it)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = RectangleShape) {
                Text("DONE")
            }
        },
        containerColor = Color.White,
        shape = RectangleShape
    )
}

@Composable
fun ManageTagsDialog(
    allTags: List<String>,
    sources: List<FeedSource>,
    onAddTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onMoveTag: (Int, Int) -> Unit,
    onToggleFeedInTag: (String, FeedSource) -> Unit,
    onDone: () -> Unit
) {
    var newTagName by remember { mutableStateOf("") }
    var selectedTagToEdit by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (selectedTagToEdit == null) "Manage Tags" else "Edit Tag: $selectedTagToEdit", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (selectedTagToEdit == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = { Text("New Tag Name") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black, cursorColor = Color.Black),
                            shape = RectangleShape
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onAddTag(newTagName); newTagName = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RectangleShape
                        ) { Text("ADD") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(allTags.size) { index ->
                            val tag = allTags[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, Color.Black)
                                    .clickable { selectedTagToEdit = tag }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tag, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                
                                IconButton(
                                    onClick = { onMoveTag(index, index - 1) },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (index > 0) Color.Black else Color.Gray)
                                }
                                
                                IconButton(
                                    onClick = { onMoveTag(index, index + 1) },
                                    enabled = index < allTags.size - 1
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (index < allTags.size - 1) Color.Black else Color.Gray)
                                }

                                IconButton(onClick = { onDeleteTag(tag) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Delete Tag", tint = Color.Black)
                                }
                            }
                        }
                    }
                } else {
                    Text("Select feeds for this tag:", fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(sources) { source ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFeedInTag(selectedTagToEdit!!, source) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = source.tags.contains(selectedTagToEdit),
                                    onCheckedChange = { onToggleFeedInTag(selectedTagToEdit!!, source) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color.Black, uncheckedColor = Color.Black, checkmarkColor = Color.White)
                                )
                                Column {
                                    Text(source.title, fontWeight = FontWeight.Bold)
                                    Text(source.url, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedTagToEdit != null) selectedTagToEdit = null else onDone() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RectangleShape
            ) { Text(if (selectedTagToEdit != null) "BACK" else "DONE") }
        },
        containerColor = Color.White,
        shape = RectangleShape
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshRateSelector(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val rates = listOf("Manually", "While app opens")
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black, cursorColor = Color.Black),
            shape = RectangleShape,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            rates.forEach { rate ->
                DropdownMenuItem(text = { Text(rate) }, onClick = { onSelected(rate); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val languages = listOf("auto", "fi", "en", "it", "de", "fr", "es", "sv")
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(80.dp)
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Black, unfocusedBorderColor = Color.Black, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black),
            shape = RectangleShape,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            languages.forEach { lang ->
                DropdownMenuItem(text = { Text(lang) }, onClick = { onSelected(lang); expanded = false })
            }
        }
    }
}

@Composable
fun FeedList(items: List<FeedItem>, onItemClick: (FeedItem) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        items(items) { item ->
            FeedEntry(item, onClick = { onItemClick(item) })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeedEntry(item: FeedItem, onClick: () -> Unit) {
    val displayDate = remember(item.date) {
        item.parsedDate?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)) ?: item.date
    }
    
    val sourceName = remember(item.sourceUrl) {
        try {
            URI(item.sourceUrl).host?.removePrefix("www.") ?: item.sourceUrl
        } catch (e: Exception) {
            item.sourceUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.Black)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayDate.isNotBlank()) {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = " | ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Normal
                )
            }
            Text(
                text = sourceName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    item: FeedItem, 
    onBack: () -> Unit, 
    parser: FeedParser, 
    source: FeedSource?,
    isArchived: Boolean,
    onToggleArchive: (FeedItem, String) -> Unit
) {
    var fullContent by remember { mutableStateOf(item.fullContent ?: item.summary) }
    var isFetching by remember { mutableStateOf(item.fullContent == null) }
    val displayDate = remember(item.date) { item.parsedDate?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) ?: item.date }
    
    val sourceName = remember(item.sourceUrl) {
        try {
            URI(item.sourceUrl).host?.removePrefix("www.") ?: item.sourceUrl
        } catch (e: Exception) {
            item.sourceUrl
        }
    }

    LaunchedEffect(item.link) {
        if (item.fullContent == null && item.link.isNotBlank()) {
            isFetching = true
            var fetched = parser.fetchFullContent(item.link)
            if (source != null && source.isTranslationEnabled && source.sourceLang != source.targetLang && fetched.isNotBlank()) {
                fetched = parser.translate(fetched, source.sourceLang, source.targetLang)
            }
            if (fetched.isNotBlank()) fullContent = fetched
            isFetching = false
        } else isFetching = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("READ", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black) } },
                actions = {
                    IconButton(onClick = { onToggleArchive(item, fullContent) }) {
                        Icon(
                            if (isArchived) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Archive",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (displayDate.isNotBlank()) {
                    Text(displayDate, style = MaterialTheme.typography.labelLarge)
                    Text(" | ", style = MaterialTheme.typography.labelLarge)
                }
                Text(sourceName, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(thickness = 2.dp, color = Color.Black); Spacer(modifier = Modifier.height(16.dp))
            if (isFetching && fullContent == item.summary) {
                Text("Loading and translating...", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(fullContent, style = MaterialTheme.typography.bodyLarge, fontSize = 24.sp, lineHeight = 36.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Black), shape = RectangleShape) { Text("CLOSE") }
        }
    }
}
