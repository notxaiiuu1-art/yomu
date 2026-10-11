package app.yomu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yomu.Yomu
import app.yomu.ui.CoverImage
import app.yomu.ui.LocalNavigator
import app.yomu.ui.NsfwChips
import app.yomu.ui.langName
import kotlinx.coroutines.CoroutineScope

/** Installed sources plus a search box that searches all of them at once. */
class BrowseTab {
    private var query by mutableStateOf("")

    @Composable
    fun Content(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope) {
        val nav = LocalNavigator.current
        val installed by Yomu.extensions.installed.collectAsState()
        val catalog by Yomu.extensions.catalog.collectAsState()
        // Each source shows its extension's icon from the repo listing.
        val iconByPkg = catalog.associate { it.pkg to it.iconUrl }
        val iconBySource = installed.flatMap { ext -> ext.sources.map { it.id to iconByPkg[ext.pkg] } }.toMap()
        val languages by Yomu.prefs.languages.flow.collectAsState()
        val nsfwFilter by Yomu.prefs.nsfwFilter.flow.collectAsState()

        val sources = installed
            .filter { nsfwFilter.allows(it.nsfw) }
            .flatMap { it.sources }
            .filter { it.lang in languages || languages.isEmpty() }
            .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))

        Scaffold(topBar = { TopAppBar(title = { Text("Entdecken") }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("In allen Quellen suchen …") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) nav.push(GlobalSearchScreen(query.trim(), sources.map { it.id }))
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // The global search uses exactly the sources shown here, so "Nur 18+" searches only those.
                NsfwChips(Modifier.padding(horizontal = 16.dp))

                if (sources.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (installed.isEmpty()) {
                                "Noch keine Quellen.\nInstalliere unter „Erweiterungen“ zuerst eine Quelle."
                            } else {
                                "Keine passende Quelle.\nPrüfe den 18+-Filter oben oder die Sprachen in den Einstellungen."
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                    return@Column
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(sources, key = { it.id }) { source ->
                        ListItem(
                            leadingContent = {
                                CoverImage(
                                    iconBySource[source.id],
                                    null,
                                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            },
                            headlineContent = { Text(source.name) },
                            supportingContent = {
                                Text(langName(source.lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable { nav.push(SourceScreen(source.id)) },
                        )
                    }
                }
            }
        }
    }
}
