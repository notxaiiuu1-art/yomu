package app.yomu.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.extension.CatalogEntry
import app.yomu.extension.ContentWarning
import app.yomu.ui.CoverImage
import app.yomu.ui.langName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ExtensionsTab {
    private var filter by mutableStateOf("")
    private var onlyInstalled by mutableStateOf(false)
    private var refreshing by mutableStateOf(false)

    @Composable
    fun Content(scope: CoroutineScope) {
        val context = LocalContext.current
        val catalog by Yomu.extensions.catalog.collectAsState()
        val installed by Yomu.extensions.installed.collectAsState()
        val busy by Yomu.extensions.busy.collectAsState()
        val error by Yomu.extensions.catalogError.collectAsState()
        val languages by Yomu.prefs.languages.flow.collectAsState()
        val showNsfw by Yomu.prefs.showNsfw.flow.collectAsState()

        val installedByPkg = installed.associateBy { it.pkg }
        val visible = catalog
            .filter { showNsfw || it.contentWarning != ContentWarning.NSFW }
            .filter { it.lang in languages || it.sources.any { s -> s.lang in languages } || languages.isEmpty() }
            .filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
            .filter { !onlyInstalled || it.pkg in installedByPkg }
            .sortedWith(compareByDescending<CatalogEntry> { it.pkg in installedByPkg }.thenBy { it.name.lowercase() })

        fun refresh() {
            scope.launch {
                refreshing = true
                Yomu.extensions.refreshCatalog()
                refreshing = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Erweiterungen") },
                    actions = {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = ::refresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Erweiterung suchen …") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyInstalled,
                        onClick = { onlyInstalled = !onlyInstalled },
                        label = { Text("Nur installierte (${installed.size})") },
                    )
                    FilterChip(
                        selected = showNsfw,
                        onClick = { Yomu.prefs.showNsfw.set(!showNsfw) },
                        label = { Text("18+ anzeigen") },
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                if (catalog.isEmpty() && error == null) {
                    Text("Lade Erweiterungen von Keiyoushi …", modifier = Modifier.padding(16.dp))
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.pkg }) { entry ->
                        val ext = installedByPkg[entry.pkg]
                        val working = entry.pkg in busy
                        ListItem(
                            leadingContent = {
                                CoverImage(
                                    entry.iconUrl,
                                    null,
                                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            },
                            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                val parts = buildList {
                                    add(langName(entry.lang))
                                    add("v${entry.versionName}")
                                    if (entry.contentWarning == ContentWarning.NSFW) add("18+")
                                    ext?.error?.let { add("⚠ $it") }
                                }
                                Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                when {
                                    working -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    ext == null -> FilledTonalButton(onClick = {
                                        scope.launch {
                                            Yomu.extensions.install(entry).onFailure {
                                                Toast.makeText(context, it.message ?: "Fehler", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }) { Text("Installieren") }
                                    ext.versionCode < entry.versionCode -> FilledTonalButton(onClick = {
                                        scope.launch { Yomu.extensions.install(entry) }
                                    }) { Text("Update") }
                                    else -> OutlinedButton(onClick = { Yomu.extensions.uninstall(entry.pkg) }) {
                                        Text("Entfernen")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
