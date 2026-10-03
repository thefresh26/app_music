package com.activos.pentagrama.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.graphicsLayer
import com.activos.pentagrama.Paper
import com.activos.pentagrama.pressScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.activos.pentagrama.LocalMusicFont
import com.activos.pentagrama.LocalSmufl
import com.activos.pentagrama.symbols.GlyphMetrics
import com.activos.pentagrama.symbols.MusicSymbol
import com.activos.pentagrama.symbols.SymbolCatalog
import com.activos.pentagrama.symbols.SymbolCategory
import kotlin.math.min

/** Draws a SMuFL glyph string centered in its box using the font metrics. */
@Composable
fun GlyphView(glyph: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val font = LocalMusicFont.current
    val measurer = rememberTextMeasurer(cacheSize = 256)
    Canvas(modifier.size(size)) {
        val box = GlyphMetrics.boxOf(glyph)
        val pad = this.size.width * 0.1f
        val avail = this.size.width - 2 * pad
        val space = min(avail / box.width.coerceAtLeast(0.5f), avail / box.height.coerceAtLeast(0.5f)).coerceAtMost(this.size.width / 3.2f)
        val style = TextStyle(fontFamily = font, fontSize = (4f * space).toSp())
        val r = measurer.measure(glyph, style)
        val baseline = this.size.height / 2 + (box.neY + box.swY) / 2 * space
        val x = this.size.width / 2 - (box.swX + box.neX) / 2 * space
        drawText(r, color, Offset(x, baseline - r.firstBaseline))
    }
}

@Composable
fun SymbolPalette(
    selectedId: String,
    onSelect: (MusicSymbol) -> Unit,
    modifier: Modifier = Modifier,
) {
    val smufl = LocalSmufl.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<SymbolCategory?>(SymbolCategory.NOTES) }

    val results: List<MusicSymbol> = remember(query, category, smufl) {
        val q = query.trim()
        val curated = SymbolCatalog.curated.filter {
            (category == null || it.category == category) && it.matches(q)
        }
        val includeSmufl = category == SymbolCategory.ALL_SMUFL || (category == null && q.length >= 2)
        val extra = if (includeSmufl) smufl.asSequence().filter { it.matches(q) }.take(400).map { it.toSymbol() }.toList() else emptyList()
        if (category == SymbolCategory.ALL_SMUFL) extra else curated + extra
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; if (it.isNotBlank() && category != SymbolCategory.ALL_SMUFL) category = null },
            placeholder = { Text("Buscar: negra, sostenido, coda, calderón, clave de fa…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Limpiar") } },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { FilterChip(category == null, { category = null }, label = { Text("Todas") }) }
            items(SymbolCategory.entries) { c ->
                FilterChip(category == c, { category = c }, label = { Text(c.es) })
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No hay figuras que coincidan con \"$query\".", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(results, key = { it.id }) { sym ->
                SymbolCell(sym, sym.id == selectedId, Modifier.animateItem(fadeInSpec = tween(220), placementSpec = tween(260, easing = Paper.Ease), fadeOutSpec = tween(120))) { onSelect(sym) }
            }
        }
    }
}

@Composable
private fun SymbolCell(sym: MusicSymbol, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primaryContainer else cs.surfaceContainerHigh, tween(200))
    val line by animateColorAsState(if (selected) cs.primary else Color.Transparent, tween(200))
    val pop by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(2.dp, line),
        interactionSource = src,
        modifier = modifier.pressScale(src).graphicsLayer { scaleX = pop; scaleY = pop },
    ) {
        Column(Modifier.padding(4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (sym.glyph.isNotEmpty()) GlyphView(sym.glyph, 44.dp, if (selected) cs.onPrimaryContainer else cs.onSurface)
                else Text(sym.label ?: "?", style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp), maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                sym.nameEs, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant,
            )
        }
    }
}
