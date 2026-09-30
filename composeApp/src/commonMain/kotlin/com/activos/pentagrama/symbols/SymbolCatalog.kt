package com.activos.pentagrama.symbols

import com.activos.pentagrama.model.Clef
import com.activos.pentagrama.model.EndBar
import com.activos.pentagrama.model.EventKind
import com.activos.pentagrama.model.NoteHead
import com.activos.pentagrama.model.NoteValue
import com.activos.pentagrama.model.StartBar
import com.activos.pentagrama.model.TimeSymbol

enum class SymbolCategory(val es: String) {
    NOTES("Notas"),
    RESTS("Silencios"),
    RHYTHM("Ritmo / slash"),
    ACCIDENTALS("Alteraciones"),
    MODIFIERS("Puntillo, ligadura, tresillo"),
    CLEFS("Claves"),
    KEYS("Armaduras"),
    TIMES("Compás (métrica)"),
    BARLINES("Barras y repeticiones"),
    NAVIGATION("Navegación (D.S., Coda...)"),
    DYNAMICS("Dinámicas"),
    ARTICULATIONS("Articulaciones"),
    ORNAMENTS("Ornamentos y técnicas"),
    TEXT("Cifrado y texto"),
    TOOLS("Herramientas"),
    ALL_SMUFL("Todos (SMuFL)"),
}

sealed interface SymbolAction {
    data class PlaceEvent(
        val kind: EventKind,
        val value: NoteValue,
        val dots: Int = 0,
        val head: NoteHead = NoteHead.NORMAL,
        val triplet: Boolean = false,
    ) : SymbolAction

    data class Accidental(val alter: Int) : SymbolAction
    data object ToggleDot : SymbolAction
    data object ToggleTie : SymbolAction
    data object ToggleTriplet : SymbolAction
    data class SetHead(val head: NoteHead) : SymbolAction
    data class Attach(val markId: String) : SymbolAction
    data class SetClef(val clef: Clef) : SymbolAction
    data class SetKey(val fifths: Int) : SymbolAction
    data class SetTime(val num: Int, val den: Int, val symbol: TimeSymbol = TimeSymbol.NUMERIC) : SymbolAction
    data class SetStartBar(val bar: StartBar) : SymbolAction
    data class SetEndBar(val bar: EndBar) : SymbolAction
    data class MeasureMark(val markId: String) : SymbolAction
    data class Ending(val text: String) : SymbolAction
    data object MeasureRepeat : SymbolAction
    data object ChordSymbol : SymbolAction
    data object SectionLabel : SymbolAction
    data object FreeText : SymbolAction
    data object Lyric : SymbolAction
    data class Free(val codepoint: Int) : SymbolAction
    data object Eraser : SymbolAction
    data object Select : SymbolAction
}

data class MusicSymbol(
    val id: String,
    val nameEs: String,
    val category: SymbolCategory,
    /** Glyph shown in the palette (Bravura). May be empty when [label] is used. */
    val glyph: String,
    val keywords: String,
    val action: SymbolAction,
    /** Text preview for symbols without a glyph (e.g. "Am7", "Coro"). */
    val label: String? = null,
) {
    private val haystack = normalize("$nameEs $keywords ${category.es} $id")
    fun matches(query: String): Boolean {
        val q = normalize(query).trim()
        if (q.isEmpty()) return true
        return q.split(' ').filter { it.isNotBlank() }.all { haystack.contains(it) }
    }
}

enum class MarkPlacement { ABOVE, BELOW, LEFT }

/** Symbols that can be attached to a note/rest or to a measure. */
data class MarkDef(val id: String, val nameEs: String, val codepoint: Int?, val text: String?, val placement: MarkPlacement)

object Marks {
    val all: List<MarkDef> = listOf(
        // Articulations
        MarkDef("accent", "Acento", 0xE4A0, null, MarkPlacement.ABOVE),
        MarkDef("staccato", "Staccato", 0xE4A2, null, MarkPlacement.ABOVE),
        MarkDef("tenuto", "Tenuto", 0xE4A4, null, MarkPlacement.ABOVE),
        MarkDef("staccatissimo", "Staccatissimo", 0xE4A6, null, MarkPlacement.ABOVE),
        MarkDef("marcato", "Marcato", 0xE4AC, null, MarkPlacement.ABOVE),
        MarkDef("stress", "Énfasis", 0xE4B6, null, MarkPlacement.ABOVE),
        MarkDef("fermata", "Calderón", 0xE4C0, null, MarkPlacement.ABOVE),
        MarkDef("fermataShort", "Calderón corto", 0xE4C4, null, MarkPlacement.ABOVE),
        MarkDef("fermataLong", "Calderón largo", 0xE4C6, null, MarkPlacement.ABOVE),
        MarkDef("breath", "Respiración", 0xE4CE, null, MarkPlacement.ABOVE),
        MarkDef("caesura", "Cesura", 0xE4D1, null, MarkPlacement.ABOVE),
        // Ornaments / techniques
        MarkDef("trill", "Trino", 0xE566, null, MarkPlacement.ABOVE),
        MarkDef("mordent", "Mordente", 0xE56D, null, MarkPlacement.ABOVE),
        MarkDef("shortTrill", "Mordente superior", 0xE56C, null, MarkPlacement.ABOVE),
        MarkDef("turn", "Grupeto", 0xE567, null, MarkPlacement.ABOVE),
        MarkDef("turnInv", "Grupeto invertido", 0xE568, null, MarkPlacement.ABOVE),
        MarkDef("tremolo1", "Trémolo 1", 0xE220, null, MarkPlacement.ABOVE),
        MarkDef("tremolo2", "Trémolo 2", 0xE221, null, MarkPlacement.ABOVE),
        MarkDef("tremolo3", "Trémolo 3", 0xE222, null, MarkPlacement.ABOVE),
        MarkDef("arpeggio", "Arpegio", 0xE63C, null, MarkPlacement.LEFT),
        MarkDef("graceAcc", "Nota de adorno (acciaccatura)", 0xE560, null, MarkPlacement.LEFT),
        MarkDef("graceApp", "Apoyatura", 0xE562, null, MarkPlacement.LEFT),
        MarkDef("downBow", "Arco abajo", 0xE610, null, MarkPlacement.ABOVE),
        MarkDef("upBow", "Arco arriba", 0xE612, null, MarkPlacement.ABOVE),
        MarkDef("mutedClosed", "Tapado (+)", 0xE5E5, null, MarkPlacement.ABOVE),
        MarkDef("open", "Abierto (o)", 0xE5E7, null, MarkPlacement.ABOVE),
        MarkDef("pedal", "Pedal", 0xE650, null, MarkPlacement.BELOW),
        MarkDef("pedalUp", "Soltar pedal", 0xE655, null, MarkPlacement.BELOW),
        // Dynamics
        MarkDef("ppp", "Pianississimo", 0xE52A, null, MarkPlacement.BELOW),
        MarkDef("pp", "Pianissimo", 0xE52B, null, MarkPlacement.BELOW),
        MarkDef("p", "Piano", 0xE520, null, MarkPlacement.BELOW),
        MarkDef("mp", "Mezzo piano", 0xE52C, null, MarkPlacement.BELOW),
        MarkDef("mf", "Mezzo forte", 0xE52D, null, MarkPlacement.BELOW),
        MarkDef("f", "Forte", 0xE522, null, MarkPlacement.BELOW),
        MarkDef("ff", "Fortissimo", 0xE52F, null, MarkPlacement.BELOW),
        MarkDef("fff", "Fortississimo", 0xE530, null, MarkPlacement.BELOW),
        MarkDef("fp", "Fortepiano", 0xE534, null, MarkPlacement.BELOW),
        MarkDef("sf", "Sforzando", 0xE536, null, MarkPlacement.BELOW),
        MarkDef("sfz", "Sforzato", 0xE539, null, MarkPlacement.BELOW),
        MarkDef("cresc", "Crescendo", 0xE53E, null, MarkPlacement.BELOW),
        MarkDef("dim", "Diminuendo", 0xE53F, null, MarkPlacement.BELOW),
        // Measure-level navigation marks
        MarkDef("segno", "Segno", 0xE047, null, MarkPlacement.ABOVE),
        MarkDef("coda", "Coda", 0xE048, null, MarkPlacement.ABOVE),
        MarkDef("ds", "Dal Segno (D.S.)", 0xE045, null, MarkPlacement.ABOVE),
        MarkDef("dc", "Da Capo (D.C.)", 0xE046, null, MarkPlacement.ABOVE),
        MarkDef("ottava", "8va", 0xE511, null, MarkPlacement.ABOVE),
        MarkDef("ottavaBassa", "8vb", 0xE51C, null, MarkPlacement.BELOW),
        MarkDef("dcAlFine", "D.C. al Fine", null, "D.C. al Fine", MarkPlacement.ABOVE),
        MarkDef("dsAlCoda", "D.S. al Coda", null, "D.S. al Coda", MarkPlacement.ABOVE),
        MarkDef("dsAlFine", "D.S. al Fine", null, "D.S. al Fine", MarkPlacement.ABOVE),
        MarkDef("dcAlCoda", "D.C. al Coda", null, "D.C. al Coda", MarkPlacement.ABOVE),
        MarkDef("toCoda", "Al Coda", null, "Al Coda", MarkPlacement.ABOVE),
        MarkDef("fine", "Fine", null, "Fine", MarkPlacement.ABOVE),
        MarkDef("x2", "Repetir 2 veces", null, "x2", MarkPlacement.ABOVE),
        MarkDef("x3", "Repetir 3 veces", null, "x3", MarkPlacement.ABOVE),
        MarkDef("x4", "Repetir 4 veces", null, "x4", MarkPlacement.ABOVE),
        MarkDef("rit", "Ritardando", null, "rit.", MarkPlacement.ABOVE),
        MarkDef("accel", "Accelerando", null, "accel.", MarkPlacement.ABOVE),
        MarkDef("atempo", "A tempo", null, "a tempo", MarkPlacement.ABOVE),
        MarkDef("tacet", "Tacet / silencio", null, "N.C.", MarkPlacement.ABOVE),
    )
    private val byId = all.associateBy { it.id }
    operator fun get(id: String): MarkDef? = byId[id]
}

object SymbolCatalog {
    private fun g(cp: Int) = G.str(cp)

    val curated: List<MusicSymbol> by lazy { build() }

    private fun build(): List<MusicSymbol> {
        val l = mutableListOf<MusicSymbol>()
        val C = SymbolCategory.entries

        // ---- Tools
        l += MusicSymbol("select", "Seleccionar", SymbolCategory.TOOLS, "", "seleccionar mover cursor elegir", SymbolAction.Select, "☝")
        l += MusicSymbol("eraser", "Borrar", SymbolCategory.TOOLS, "", "borrar eliminar goma quitar delete", SymbolAction.Eraser, "⌫")

        // ---- Notes
        val noteGlyphs = mapOf(
            NoteValue.DOUBLE_WHOLE to 0xE1D0, NoteValue.WHOLE to 0xE1D2, NoteValue.HALF to 0xE1D3,
            NoteValue.QUARTER to 0xE1D5, NoteValue.EIGHTH to 0xE1D7, NoteValue.SIXTEENTH to 0xE1D9,
            NoteValue.THIRTY_SECOND to 0xE1DB, NoteValue.SIXTY_FOURTH to 0xE1DD,
        )
        val enNames = mapOf(
            NoteValue.DOUBLE_WHOLE to "breve double whole", NoteValue.WHOLE to "whole semibreve",
            NoteValue.HALF to "half minim", NoteValue.QUARTER to "quarter crotchet",
            NoteValue.EIGHTH to "eighth quaver", NoteValue.SIXTEENTH to "sixteenth semiquaver 16",
            NoteValue.THIRTY_SECOND to "thirty-second demisemiquaver 32", NoteValue.SIXTY_FOURTH to "sixty-fourth 64",
        )
        for ((v, cp) in noteGlyphs) {
            l += MusicSymbol("note_${v.name}", v.es, SymbolCategory.NOTES, g(cp), "nota figura ${enNames[v]} note", SymbolAction.PlaceEvent(EventKind.NOTE, v))
            if (v != NoteValue.DOUBLE_WHOLE && v != NoteValue.SIXTY_FOURTH) {
                l += MusicSymbol("note_${v.name}_dot", "${v.es} con puntillo", SymbolCategory.NOTES, g(cp) + g(0xE1E7), "nota puntillo dotted ${enNames[v]}", SymbolAction.PlaceEvent(EventKind.NOTE, v, dots = 1))
            }
        }
        l += MusicSymbol("note_triplet_8", "Corchea de tresillo", SymbolCategory.NOTES, g(0xE1D7) + g(G.TUPLET_3), "tresillo triplet corchea eighth", SymbolAction.PlaceEvent(EventKind.NOTE, NoteValue.EIGHTH, triplet = true))
        l += MusicSymbol("note_triplet_4", "Negra de tresillo", SymbolCategory.NOTES, g(0xE1D5) + g(G.TUPLET_3), "tresillo triplet negra quarter", SymbolAction.PlaceEvent(EventKind.NOTE, NoteValue.QUARTER, triplet = true))
        l += MusicSymbol("note_x", "Nota con cabeza X", SymbolCategory.NOTES, g(G.HEAD_X), "cabeza x percusion platillo hi-hat ghost muerta", SymbolAction.PlaceEvent(EventKind.NOTE, NoteValue.QUARTER, head = NoteHead.X))
        l += MusicSymbol("note_diamond", "Nota diamante (armónico)", SymbolCategory.NOTES, g(G.HEAD_DIAMOND), "armonico diamante harmonic", SymbolAction.PlaceEvent(EventKind.NOTE, NoteValue.HALF, head = NoteHead.DIAMOND))
        l += MusicSymbol("note_triangle", "Nota triángulo", SymbolCategory.NOTES, g(G.HEAD_TRIANGLE), "triangulo percusion", SymbolAction.PlaceEvent(EventKind.NOTE, NoteValue.QUARTER, head = NoteHead.TRIANGLE))

        // ---- Rests
        val restGlyphs = mapOf(
            NoteValue.DOUBLE_WHOLE to G.REST_DOUBLE_WHOLE, NoteValue.WHOLE to G.REST_WHOLE, NoteValue.HALF to G.REST_HALF,
            NoteValue.QUARTER to G.REST_QUARTER, NoteValue.EIGHTH to G.REST_8, NoteValue.SIXTEENTH to G.REST_16,
            NoteValue.THIRTY_SECOND to G.REST_32, NoteValue.SIXTY_FOURTH to G.REST_64,
        )
        for ((v, cp) in restGlyphs) {
            l += MusicSymbol("rest_${v.name}", "Silencio de ${v.es.lowercase()}", SymbolCategory.RESTS, g(cp), "silencio pausa rest ${enNames[v]}", SymbolAction.PlaceEvent(EventKind.REST, v))
        }
        l += MusicSymbol("rest_QUARTER_dot", "Silencio de negra con puntillo", SymbolCategory.RESTS, g(G.REST_QUARTER) + g(G.DOT), "silencio puntillo", SymbolAction.PlaceEvent(EventKind.REST, NoteValue.QUARTER, dots = 1))
        l += MusicSymbol("rest_EIGHTH_dot", "Silencio de corchea con puntillo", SymbolCategory.RESTS, g(G.REST_8) + g(G.DOT), "silencio puntillo", SymbolAction.PlaceEvent(EventKind.REST, NoteValue.EIGHTH, dots = 1))

        // ---- Rhythm slashes (chord charts)
        l += MusicSymbol("slash_4", "Barra rítmica (negra)", SymbolCategory.RHYTHM, g(G.SLASH), "slash ritmo barra marcar tiempo acompañamiento rasgueo", SymbolAction.PlaceEvent(EventKind.SLASH, NoteValue.QUARTER))
        l += MusicSymbol("slash_2", "Barra rítmica (blanca)", SymbolCategory.RHYTHM, g(G.SLASH_HALF), "slash ritmo blanca", SymbolAction.PlaceEvent(EventKind.SLASH, NoteValue.HALF))
        l += MusicSymbol("slash_1", "Barra rítmica (redonda)", SymbolCategory.RHYTHM, g(G.SLASH_WHOLE), "slash ritmo redonda", SymbolAction.PlaceEvent(EventKind.SLASH, NoteValue.WHOLE))
        l += MusicSymbol("slash_8", "Barra rítmica (corchea)", SymbolCategory.RHYTHM, g(G.SLASH) + g(G.FLAG_8_UP), "slash ritmo corchea", SymbolAction.PlaceEvent(EventKind.SLASH, NoteValue.EIGHTH))
        l += MusicSymbol("slash_4d", "Barra rítmica (negra con puntillo)", SymbolCategory.RHYTHM, g(G.SLASH) + g(G.DOT), "slash ritmo puntillo", SymbolAction.PlaceEvent(EventKind.SLASH, NoteValue.QUARTER, dots = 1))
        l += MusicSymbol("repeat1", "Repetir compás (%)", SymbolCategory.RHYTHM, g(G.REPEAT_1_BAR), "porcentaje % repetir compas simile igual", SymbolAction.MeasureRepeat)

        // ---- Accidentals
        l += MusicSymbol("acc_sharp", "Sostenido", SymbolCategory.ACCIDENTALS, g(G.SHARP), "sostenido sharp #", SymbolAction.Accidental(1))
        l += MusicSymbol("acc_flat", "Bemol", SymbolCategory.ACCIDENTALS, g(G.FLAT), "bemol flat b", SymbolAction.Accidental(-1))
        l += MusicSymbol("acc_natural", "Becuadro", SymbolCategory.ACCIDENTALS, g(G.NATURAL), "becuadro natural", SymbolAction.Accidental(0))
        l += MusicSymbol("acc_dsharp", "Doble sostenido", SymbolCategory.ACCIDENTALS, g(G.DOUBLE_SHARP), "doble sostenido double sharp x", SymbolAction.Accidental(2))
        l += MusicSymbol("acc_dflat", "Doble bemol", SymbolCategory.ACCIDENTALS, g(G.DOUBLE_FLAT), "doble bemol double flat bb", SymbolAction.Accidental(-2))

        // ---- Modifiers
        l += MusicSymbol("mod_dot", "Puntillo", SymbolCategory.MODIFIERS, g(G.DOT), "puntillo dot aumentar", SymbolAction.ToggleDot)
        l += MusicSymbol("mod_tie", "Ligadura de prolongación", SymbolCategory.MODIFIERS, "", "ligadura tie slur unir arco", SymbolAction.ToggleTie, "⁀")
        l += MusicSymbol("mod_triplet", "Tresillo", SymbolCategory.MODIFIERS, g(G.TUPLET_3), "tresillo triplet 3", SymbolAction.ToggleTriplet)
        l += MusicSymbol("head_normal", "Cabeza normal", SymbolCategory.MODIFIERS, g(G.HEAD_BLACK), "cabeza normal notehead", SymbolAction.SetHead(NoteHead.NORMAL))
        l += MusicSymbol("head_x", "Cabeza en X", SymbolCategory.MODIFIERS, g(G.HEAD_X), "cabeza x", SymbolAction.SetHead(NoteHead.X))
        l += MusicSymbol("head_cx", "Cabeza X en círculo", SymbolCategory.MODIFIERS, g(G.HEAD_CIRCLE_X), "cabeza x circulo", SymbolAction.SetHead(NoteHead.CIRCLE_X))
        l += MusicSymbol("head_diamond", "Cabeza diamante", SymbolCategory.MODIFIERS, g(G.HEAD_DIAMOND), "cabeza diamante armonico", SymbolAction.SetHead(NoteHead.DIAMOND))

        // ---- Clefs
        l += MusicSymbol("clef_g", "Clave de Sol", SymbolCategory.CLEFS, g(G.G_CLEF), "clave sol treble violin", SymbolAction.SetClef(Clef.TREBLE))
        l += MusicSymbol("clef_f", "Clave de Fa", SymbolCategory.CLEFS, g(G.F_CLEF), "clave fa bass bajo", SymbolAction.SetClef(Clef.BASS))
        l += MusicSymbol("clef_c3", "Clave de Do en 3ª", SymbolCategory.CLEFS, g(G.C_CLEF), "clave do alto viola", SymbolAction.SetClef(Clef.ALTO))
        l += MusicSymbol("clef_c4", "Clave de Do en 4ª", SymbolCategory.CLEFS, g(G.C_CLEF), "clave do tenor", SymbolAction.SetClef(Clef.TENOR))
        l += MusicSymbol("clef_g8", "Clave de Sol 8vb", SymbolCategory.CLEFS, g(G.G_CLEF_8VB), "clave sol octava guitarra tenor voz", SymbolAction.SetClef(Clef.TREBLE_8VB))
        l += MusicSymbol("clef_perc", "Clave de percusión", SymbolCategory.CLEFS, g(G.PERC_CLEF), "clave percusion bateria drums", SymbolAction.SetClef(Clef.PERCUSSION))

        // ---- Key signatures
        val keyNames = mapOf(
            0 to "Do M / La m", 1 to "Sol M / Mi m", 2 to "Re M / Si m", 3 to "La M / Fa# m", 4 to "Mi M / Do# m",
            5 to "Si M / Sol# m", 6 to "Fa# M / Re# m", 7 to "Do# M / La# m",
            -1 to "Fa M / Re m", -2 to "Sib M / Sol m", -3 to "Mib M / Do m", -4 to "Lab M / Fa m",
            -5 to "Reb M / Sib m", -6 to "Solb M / Mib m", -7 to "Dob M / Lab m",
        )
        for (f in listOf(0, 1, 2, 3, 4, 5, 6, 7, -1, -2, -3, -4, -5, -6, -7)) {
            val glyph = when {
                f > 0 -> g(G.SHARP).repeat(minOf(f, 3)) + if (f > 3) "…" else ""
                f < 0 -> g(G.FLAT).repeat(minOf(-f, 3)) + if (f < -3) "…" else ""
                else -> ""
            }
            val acc = if (f > 0) "$f sostenidos" else if (f < 0) "${-f} bemoles" else "sin alteraciones"
            l += MusicSymbol("key_$f", "Armadura ${keyNames[f]}", SymbolCategory.KEYS, glyph, "armadura tonalidad key signature $acc", SymbolAction.SetKey(f), if (f == 0) "C" else null)
        }

        // ---- Time signatures
        fun timeGlyph(n: Int, d: Int) = n.toString().map { g(G.TIME_0 + (it - '0')) }.joinToString("") + "/" + d.toString().map { g(G.TIME_0 + (it - '0')) }.joinToString("")
        for ((n, d) in listOf(4 to 4, 3 to 4, 2 to 4, 2 to 2, 6 to 8, 9 to 8, 12 to 8, 5 to 4, 7 to 8, 3 to 8, 6 to 4, 5 to 8)) {
            l += MusicSymbol("time_${n}_$d", "Compás $n/$d", SymbolCategory.TIMES, timeGlyph(n, d), "compas metrica time signature $n/$d", SymbolAction.SetTime(n, d))
        }
        l += MusicSymbol("time_c", "Compasillo (C)", SymbolCategory.TIMES, g(G.TIME_COMMON), "compasillo common time c 4/4", SymbolAction.SetTime(4, 4, TimeSymbol.COMMON))
        l += MusicSymbol("time_cut", "Partido (alla breve)", SymbolCategory.TIMES, g(G.TIME_CUT), "alla breve partido cut time 2/2", SymbolAction.SetTime(2, 2, TimeSymbol.CUT))

        // ---- Barlines & repeats
        l += MusicSymbol("bar_single", "Barra simple", SymbolCategory.BARLINES, g(0xE030), "barra simple linea divisoria barline", SymbolAction.SetEndBar(EndBar.SINGLE))
        l += MusicSymbol("bar_double", "Doble barra", SymbolCategory.BARLINES, g(0xE031), "doble barra seccion double barline", SymbolAction.SetEndBar(EndBar.DOUBLE))
        l += MusicSymbol("bar_final", "Barra final", SymbolCategory.BARLINES, g(0xE032), "barra final fin final barline", SymbolAction.SetEndBar(EndBar.FINAL))
        l += MusicSymbol("bar_rep_start", "Inicio de repetición", SymbolCategory.BARLINES, g(0xE040), "repeticion inicio repeat start casilla", SymbolAction.SetStartBar(StartBar.REPEAT_START))
        l += MusicSymbol("bar_rep_end", "Fin de repetición", SymbolCategory.BARLINES, g(0xE041), "repeticion fin repeat end volver", SymbolAction.SetEndBar(EndBar.REPEAT_END))
        l += MusicSymbol("bar_double_start", "Doble barra al inicio", SymbolCategory.BARLINES, g(0xE031), "doble barra inicio seccion", SymbolAction.SetStartBar(StartBar.DOUBLE))
        l += MusicSymbol("bar_start_normal", "Quitar barra de inicio", SymbolCategory.BARLINES, "", "quitar inicio repeticion normal", SymbolAction.SetStartBar(StartBar.NORMAL), "|")
        l += MusicSymbol("ending_1", "Casilla 1", SymbolCategory.BARLINES, "", "casilla primera vez 1 volta ending", SymbolAction.Ending("1."), "1.")
        l += MusicSymbol("ending_2", "Casilla 2", SymbolCategory.BARLINES, "", "casilla segunda vez 2 volta ending", SymbolAction.Ending("2."), "2.")
        l += MusicSymbol("ending_3", "Casilla 3", SymbolCategory.BARLINES, "", "casilla tercera vez 3 volta", SymbolAction.Ending("3."), "3.")
        l += MusicSymbol("ending_none", "Quitar casilla", SymbolCategory.BARLINES, "", "quitar casilla volta", SymbolAction.Ending(""), "⌀")

        // ---- Navigation
        l += MusicSymbol("repeat1b", "Repetir compás (%)", SymbolCategory.NAVIGATION, g(G.REPEAT_1_BAR), "porcentaje % repetir compas simile", SymbolAction.MeasureRepeat)

        // ---- Marks by category
        val navIds = setOf("segno", "coda", "ds", "dc", "dcAlFine", "dsAlCoda", "dsAlFine", "dcAlCoda", "toCoda", "fine", "x2", "x3", "x4", "rit", "accel", "atempo", "tacet", "ottava", "ottavaBassa")
        val dynIds = setOf("ppp", "pp", "p", "mp", "mf", "f", "ff", "fff", "fp", "sf", "sfz", "cresc", "dim")
        val artIds = setOf("accent", "staccato", "tenuto", "staccatissimo", "marcato", "stress", "fermata", "fermataShort", "fermataLong", "breath", "caesura")
        for (m in Marks.all) {
            val cat = when (m.id) {
                in navIds -> SymbolCategory.NAVIGATION
                in dynIds -> SymbolCategory.DYNAMICS
                in artIds -> SymbolCategory.ARTICULATIONS
                else -> SymbolCategory.ORNAMENTS
            }
            val action = if (cat == SymbolCategory.NAVIGATION) SymbolAction.MeasureMark(m.id) else SymbolAction.Attach(m.id)
            val kw = when (cat) {
                SymbolCategory.DYNAMICS -> "dinamica intensidad volumen dynamic ${m.id}"
                SymbolCategory.NAVIGATION -> "navegacion salto repeticion ${m.id}"
                SymbolCategory.ARTICULATIONS -> "articulacion ${m.id}"
                else -> "ornamento tecnica ${m.id}"
            }
            l += MusicSymbol("mark_${m.id}", m.nameEs, cat, m.codepoint?.let { g(it) } ?: "", kw, action, m.text)
        }

        // ---- Text
        l += MusicSymbol("chord", "Acorde / cifrado", SymbolCategory.TEXT, "", "acorde cifrado chord symbol americano Am C G7 armonia", SymbolAction.ChordSymbol, "Am7")
        l += MusicSymbol("section", "Etiqueta de sección", SymbolCategory.TEXT, "", "seccion intro estrofa coro puente final rehearsal ensayo marca", SymbolAction.SectionLabel, "Coro")
        l += MusicSymbol("text", "Texto libre", SymbolCategory.TEXT, "", "texto nota indicacion comentario bass bat", SymbolAction.FreeText, "Abc")
        l += MusicSymbol("lyric", "Letra (bajo la nota)", SymbolCategory.TEXT, "", "letra lyric cancion silaba", SymbolAction.Lyric, "la")

        require(C.isNotEmpty())
        return l
    }
}

/** Remove accents and lowercase, so "clave" matches "Clavé" etc. */
fun normalize(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s.lowercase()) {
        sb.append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                else -> c
            }
        )
    }
    return sb.toString()
}

/** Spanish names for the most common SMuFL categories. */
val SMUFL_CATEGORY_ES = mapOf(
    "Noteheads" to "Cabezas de nota", "Clefs" to "Claves", "Dynamics" to "Dinámicas", "Rests" to "Silencios",
    "Articulation" to "Articulación", "Flags" to "Corchetes", "Time signatures" to "Compases",
    "Standard accidentals (12-EDO)" to "Alteraciones estándar", "Repeats" to "Repeticiones", "Barlines" to "Barras",
    "Individual notes" to "Notas individuales", "Beamed groups of notes" to "Grupos de notas", "Tremolos" to "Trémolos",
    "Holds and pauses" to "Calderones y pausas", "Common ornaments" to "Ornamentos comunes", "Octaves" to "Octavas",
    "Guitar" to "Guitarra", "Fingering" to "Digitación", "String techniques" to "Técnicas de cuerda",
    "Keyboard techniques" to "Técnicas de teclado", "Brass techniques" to "Técnicas de metales",
    "Wind techniques" to "Técnicas de viento", "Vocal techniques" to "Técnicas vocales", "Chord symbols" to "Cifrado",
    "Chord diagrams" to "Diagramas de acordes", "Tuplets" to "Grupos irregulares", "Metronome marks" to "Metrónomo",
    "Drums pictograms" to "Pictogramas de batería", "Cymbals pictograms" to "Platillos", "Bar repeats" to "Repetición de compás",
    "Slash noteheads" to "Cabezas slash", "Figured bass" to "Bajo cifrado", "Harp techniques" to "Técnicas de arpa",
    "Accordion" to "Acordeón", "Staves" to "Pentagramas", "Stems" to "Plicas", "Lyrics" to "Letra",
    "Percussion playing technique pictograms" to "Técnicas de percusión", "Plucked techniques" to "Técnicas de pulsado",
    "Precomposed trills and mordents" to "Trinos y mordentes", "Other baroque ornaments" to "Ornamentos barrocos",
    "Arrows and arrowheads" to "Flechas", "Conductor symbols" to "Dirección", "Scale degrees" to "Grados de escala",
    "Other accidentals" to "Otras alteraciones", "Analytics" to "Análisis", "Handbells" to "Campanas de mano",
    "Beaters pictograms" to "Baquetas", "Electronic music pictograms" to "Música electrónica",
)

/** Glyph metrics in staff spaces (SMuFL): advance width and ink bounding box (y grows upwards). */
data class GlyphBox(val advance: Float, val swX: Float, val swY: Float, val neX: Float, val neY: Float) {
    val width: Float get() = neX - swX
    val height: Float get() = neY - swY
}

object GlyphMetrics {
    private val boxes = HashMap<Int, GlyphBox>()
    private val default = GlyphBox(1.2f, 0f, -0.5f, 1.2f, 0.5f)
    val loaded: Boolean get() = boxes.isNotEmpty()
    fun register(cp: Int, box: GlyphBox) { boxes[cp] = box }
    operator fun get(cp: Int): GlyphBox = boxes[cp] ?: default

    /** Combined box of a string of glyphs laid out with their advances. Non-glyph chars count as 1 space. */
    fun boxOf(text: String): GlyphBox {
        var x = 0f
        var swX = Float.MAX_VALUE; var swY = Float.MAX_VALUE; var neX = -Float.MAX_VALUE; var neY = -Float.MAX_VALUE
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val cp = c.code
            if (cp in 0xE000..0xF8FF) {
                val b = this[cp]
                swX = minOf(swX, x + b.swX); neX = maxOf(neX, x + b.neX)
                swY = minOf(swY, b.swY); neY = maxOf(neY, b.neY)
                x += if (b.advance > 0f) b.advance else b.width
            } else {
                swX = minOf(swX, x); neX = maxOf(neX, x + 0.8f); swY = minOf(swY, -0.5f); neY = maxOf(neY, 0.5f)
                x += 0.8f
            }
            i++
        }
        if (swX == Float.MAX_VALUE) return default
        return GlyphBox(x, swX, swY, neX, neY)
    }
}

data class SmuflGlyph(val codepoint: Int, val name: String, val category: String, val description: String) {
    val categoryEs: String get() = SMUFL_CATEGORY_ES[category] ?: category
    private val hay = normalize("$name $category $categoryEs $description")
    fun matches(query: String): Boolean {
        val q = normalize(query).trim()
        if (q.isEmpty()) return true
        return q.split(' ').filter { it.isNotBlank() }.all { hay.contains(it) }
    }

    fun toSymbol() = MusicSymbol(
        id = "smufl_$name", nameEs = description.lowercase().replaceFirstChar { it.uppercase() },
        category = SymbolCategory.ALL_SMUFL, glyph = G.str(codepoint),
        keywords = "$name $category $categoryEs", action = SymbolAction.Free(codepoint),
    )

    companion object {
        /** Parses the bundled smufl.tsv and registers glyph metrics. */
        fun parseTsv(text: String): List<SmuflGlyph> = text.lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            val cp = p[0].toIntOrNull(16) ?: return@mapNotNull null
            if (p.size >= 9) {
                val f = p.subList(4, 9).map { it.toFloatOrNull() ?: 0f }
                GlyphMetrics.register(cp, GlyphBox(f[0], f[1], f[2], f[3], f[4]))
            }
            SmuflGlyph(cp, p[1], p[2], p[3])
        }.toList()
    }
}
