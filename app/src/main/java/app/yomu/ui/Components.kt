package app.yomu.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yomu.core.Images
import eu.kanade.tachiyomi.source.Source

@Composable
fun CoverImage(url: String?, source: Source?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (!url.isNullOrBlank()) bitmap = runCatching { Images.cover(url, source, 360) }.getOrNull()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun MangaCard(
    title: String,
    cover: String?,
    source: Source?,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        CoverImage(cover, source, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
        if (badge != null) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) { Text(badge, modifier = Modifier.padding(horizontal = 2.dp)) }
        }
    }
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, webUrl: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) Button(onClick = onRetry) { Text("Erneut versuchen") }
        if (webUrl != null) {
            Button(onClick = { context.startActivity(WebViewActivity.intent(context, webUrl)) }) {
                Text("Im Browser öffnen")
            }
        }
    }
}

fun openExternal(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

fun langName(code: String): String = when (code) {
    "all" -> "Mehrsprachig"
    "de" -> "Deutsch"
    "en" -> "Englisch"
    "ja" -> "Japanisch"
    "ko" -> "Koreanisch"
    "zh", "zh-Hans" -> "Chinesisch"
    "zh-Hant" -> "Chinesisch (trad.)"
    "es" -> "Spanisch"
    "es-419" -> "Spanisch (Lateinam.)"
    "fr" -> "Französisch"
    "it" -> "Italienisch"
    "pt", "pt-BR" -> "Portugiesisch"
    "ru" -> "Russisch"
    "tr" -> "Türkisch"
    "id" -> "Indonesisch"
    "vi" -> "Vietnamesisch"
    "th" -> "Thailändisch"
    "ar" -> "Arabisch"
    "pl" -> "Polnisch"
    "uk" -> "Ukrainisch"
    else -> code
}
