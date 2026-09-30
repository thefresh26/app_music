package com.activos.pentagrama.model

import com.activos.pentagrama.symbols.Marks

/** Exports a [Score] to MusicXML 3.1 (partwise) so it can be opened in MuseScore, Finale, Sibelius, etc. */
object MusicXml {

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun export(score: Score): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no"?>""").append('\n')
        sb.append("""<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">""").append('\n')
        sb.append("""<score-partwise version="3.1">""").append('\n')
        sb.append("  <work><work-title>${esc(score.title)}</work-title></work>\n")
        sb.append("  <identification>")
        if (score.composer.isNotBlank()) sb.append("<creator type=\"composer\">${esc(score.composer)}</creator>")
        sb.append("<encoding><software>Pentagrama</software></encoding></identification>\n")
        sb.append("  <part-list><score-part id=\"P1\"><part-name>Música</part-name></score-part></part-list>\n")
        sb.append("  <part id=\"P1\">\n")
        score.measures.forEachIndexed { i, m -> measure(sb, score, i, m) }
        sb.append("  </part>\n</score-partwise>\n")
        return sb.toString()
    }

    private fun measure(sb: StringBuilder, score: Score, index: Int, m: Measure) {
        sb.append("    <measure number=\"${index + 1}\">\n")
        if (m.startBar != StartBar.NORMAL || m.ending != null) {
            sb.append("      <barline location=\"left\">")
            when (m.startBar) {
                StartBar.REPEAT_START -> sb.append("<bar-style>heavy-light</bar-style>")
                StartBar.DOUBLE -> sb.append("<bar-style>light-light</bar-style>")
                StartBar.NORMAL -> {}
            }
            m.ending?.let { sb.append("<ending number=\"${esc(it.trim('.'))}\" type=\"start\">${esc(it)}</ending>") }
            if (m.startBar == StartBar.REPEAT_START) sb.append("<repeat direction=\"forward\"/>")
            sb.append("</barline>\n")
        }
        if (index == 0) {
            sb.append("      <attributes><divisions>$TICKS_PER_QUARTER</divisions>")
            sb.append("<key><fifths>${score.keyFifths}</fifths></key>")
            val sym = when (score.timeSymbol) { TimeSymbol.COMMON -> " symbol=\"common\""; TimeSymbol.CUT -> " symbol=\"cut\""; else -> "" }
            sb.append("<time$sym><beats>${score.timeNum}</beats><beat-type>${score.timeDen}</beat-type></time>")
            val (sign, line, oct) = when (score.clef) {
                Clef.TREBLE -> Triple("G", 2, 0); Clef.BASS -> Triple("F", 4, 0); Clef.ALTO -> Triple("C", 3, 0)
                Clef.TENOR -> Triple("C", 4, 0); Clef.TREBLE_8VB -> Triple("G", 2, -1); Clef.PERCUSSION -> Triple("percussion", 2, 0)
            }
            sb.append("<clef><sign>$sign</sign><line>$line</line>")
            if (oct != 0) sb.append("<clef-octave-change>$oct</clef-octave-change>")
            sb.append("</clef></attributes>\n")
            sb.append("      <direction placement=\"above\"><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>${score.tempoBpm}</per-minute></metronome></direction-type><sound tempo=\"${score.tempoBpm}\"/></direction>\n")
        }
        m.section?.let { words(sb, it, rehearsal = true) }
        m.text?.let { words(sb, it) }
        for (id in m.marks) {
            when (id) {
                "segno" -> sb.append("      <direction placement=\"above\"><direction-type><segno/></direction-type><sound segno=\"s1\"/></direction>\n")
                "coda" -> sb.append("      <direction placement=\"above\"><direction-type><coda/></direction-type><sound coda=\"c1\"/></direction>\n")
                else -> Marks[id]?.let { words(sb, it.text ?: it.nameEs) }
            }
        }
        // Chords are emitted before the note that starts at their tick.
        val chordsLeft = m.chords.sortedBy { it.tick }.toMutableList()
        var tick = 0
        if (m.repeatMeasure || m.events.isEmpty()) {
            chordsLeft.forEach { harmony(sb, it.text, it.tick) }
            chordsLeft.clear()
            if (m.repeatMeasure) sb.append("      <attributes><measure-style><measure-repeat type=\"start\">1</measure-repeat></measure-style></attributes>\n")
            sb.append("      <note><rest measure=\"yes\"/><duration>${score.measureTicks}</duration></note>\n")
            if (m.repeatMeasure) sb.append("      <attributes><measure-style><measure-repeat type=\"stop\"/></measure-style></attributes>\n")
        } else {
            for (e in m.events) {
                while (chordsLeft.isNotEmpty() && chordsLeft.first().tick <= tick) {
                    val c = chordsLeft.removeAt(0)
                    harmony(sb, c.text, 0)
                }
                note(sb, e)
                tick += e.ticks
            }
            chordsLeft.forEach { harmony(sb, it.text, 0) }
        }
        if (m.endBar != EndBar.SINGLE) {
            sb.append("      <barline location=\"right\">")
            sb.append(
                when (m.endBar) {
                    EndBar.DOUBLE -> "<bar-style>light-light</bar-style>"
                    EndBar.FINAL, EndBar.REPEAT_END -> "<bar-style>light-heavy</bar-style>"
                    EndBar.NONE -> "<bar-style>none</bar-style>"
                    EndBar.SINGLE -> ""
                }
            )
            if (m.ending != null) sb.append("<ending number=\"${esc(m.ending.trim('.'))}\" type=\"stop\"/>")
            if (m.endBar == EndBar.REPEAT_END) sb.append("<repeat direction=\"backward\"/>")
            sb.append("</barline>\n")
        }
        sb.append("    </measure>\n")
    }

    private fun words(sb: StringBuilder, text: String, rehearsal: Boolean = false) {
        val tag = if (rehearsal) "rehearsal" else "words"
        sb.append("      <direction placement=\"above\"><direction-type><$tag>${esc(text)}</$tag></direction-type></direction>\n")
    }

    private fun note(sb: StringBuilder, e: Event) {
        val pitches = if (e.kind == EventKind.NOTE && e.pitches.isNotEmpty()) e.pitches else listOf(null)
        pitches.forEachIndexed { i, p ->
            sb.append("      <note>")
            if (i > 0) sb.append("<chord/>")
            when {
                e.kind == EventKind.REST -> sb.append("<rest/>")
                e.kind == EventKind.SLASH || p == null -> sb.append("<pitch><step>B</step><octave>4</octave></pitch>")
                else -> {
                    sb.append("<pitch><step>${STEP_NAMES[p.step]}</step>")
                    if (p.alter != 0) sb.append("<alter>${p.alter}</alter>")
                    sb.append("<octave>${p.octave}</octave></pitch>")
                }
            }
            sb.append("<duration>${e.ticks}</duration>")
            if (e.tieToNext && e.kind == EventKind.NOTE) sb.append("<tie type=\"start\"/>")
            sb.append("<type>${e.value.xmlType}</type>")
            repeat(e.dots) { sb.append("<dot/>") }
            if (p != null && e.kind == EventKind.NOTE && p.alter != 0) {
                sb.append("<accidental>${when (p.alter) { -2 -> "flat-flat"; -1 -> "flat"; 1 -> "sharp"; else -> "double-sharp" }}</accidental>")
            }
            if (e.triplet) sb.append("<time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>")
            when {
                e.kind == EventKind.SLASH -> sb.append("<notehead>slash</notehead>")
                e.head == NoteHead.X -> sb.append("<notehead>x</notehead>")
                e.head == NoteHead.DIAMOND -> sb.append("<notehead>diamond</notehead>")
                e.head == NoteHead.TRIANGLE -> sb.append("<notehead>triangle</notehead>")
                e.head == NoteHead.CIRCLE_X -> sb.append("<notehead>circle-x</notehead>")
                else -> {}
            }
            if (i == 0) {
                val notations = StringBuilder()
                if (e.tieToNext && e.kind == EventKind.NOTE) notations.append("<tied type=\"start\"/>")
                val artic = e.marks.mapNotNull {
                    when (it) {
                        "accent" -> "<accent/>"; "staccato" -> "<staccato/>"; "tenuto" -> "<tenuto/>"
                        "staccatissimo" -> "<staccatissimo/>"; "marcato" -> "<strong-accent/>"; "breath" -> "<breath-mark/>"
                        "caesura" -> "<caesura/>"; "stress" -> "<stress/>"; else -> null
                    }
                }
                if (artic.isNotEmpty()) notations.append("<articulations>${artic.joinToString("")}</articulations>")
                val orn = e.marks.mapNotNull {
                    when (it) { "trill" -> "<trill-mark/>"; "mordent" -> "<mordent/>"; "shortTrill" -> "<inverted-mordent/>"; "turn" -> "<turn/>"; "turnInv" -> "<inverted-turn/>"; else -> null }
                }
                if (orn.isNotEmpty()) notations.append("<ornaments>${orn.joinToString("")}</ornaments>")
                if (e.marks.any { it.startsWith("fermata") }) notations.append("<fermata/>")
                val dyn = e.marks.filter { it in setOf("ppp", "pp", "p", "mp", "mf", "f", "ff", "fff", "fp", "sf", "sfz") }
                if (dyn.isNotEmpty()) notations.append("<dynamics>${dyn.joinToString("") { "<$it/>" }}</dynamics>")
                if (notations.isNotEmpty()) sb.append("<notations>$notations</notations>")
                e.lyric?.let { sb.append("<lyric number=\"1\"><syllabic>single</syllabic><text>${esc(it)}</text></lyric>") }
            }
            sb.append("</note>\n")
        }
    }

    /** Parses chord symbols like "F#m7b5", "Bb", "C/E", "B-" and writes a <harmony> element. */
    private fun harmony(sb: StringBuilder, text: String, offset: Int) {
        val t = text.trim()
        if (t.isEmpty()) return
        val root = t[0].uppercaseChar()
        if (root !in 'A'..'G') { words(sb, t); return }
        var i = 1
        var alter = 0
        while (i < t.length && (t[i] == '#' || t[i] == 'b' || t[i] == '♯' || t[i] == '♭')) {
            alter += if (t[i] == '#' || t[i] == '♯') 1 else -1; i++
        }
        var rest = t.substring(i)
        var bass: String? = null
        val slash = rest.indexOf('/')
        if (slash >= 0) { bass = rest.substring(slash + 1); rest = rest.substring(0, slash) }
        val kind = when (rest.lowercase()) {
            "", "maj", "M" -> "major"
            "m", "-", "min", "mi" -> "minor"
            "7" -> "dominant"
            "maj7", "m7+", "7m", "δ", "δ7", "ma7" -> "major-seventh"
            "m7", "-7", "min7", "mi7" -> "minor-seventh"
            "dim", "o", "°" -> "diminished"
            "dim7", "o7", "°7" -> "diminished-seventh"
            "m7b5", "ø", "-7b5", "ø7" -> "half-diminished"
            "aug", "+" -> "augmented"
            "sus4", "sus" -> "suspended-fourth"
            "sus2" -> "suspended-second"
            "6" -> "major-sixth"
            "m6", "-6" -> "minor-sixth"
            "9" -> "dominant-ninth"
            "maj9" -> "major-ninth"
            "m9", "-9" -> "minor-ninth"
            "5" -> "power"
            else -> "other"
        }
        sb.append("      <harmony>")
        sb.append("<root><root-step>$root</root-step>")
        if (alter != 0) sb.append("<root-alter>$alter</root-alter>")
        sb.append("</root><kind text=\"${esc(rest)}\">$kind</kind>")
        bass?.takeIf { it.isNotEmpty() && it[0].uppercaseChar() in 'A'..'G' }?.let { b ->
            val ba = b.drop(1).count { it == '#' } - b.drop(1).count { it == 'b' }
            sb.append("<bass><bass-step>${b[0].uppercaseChar()}</bass-step>")
            if (ba != 0) sb.append("<bass-alter>$ba</bass-alter>")
            sb.append("</bass>")
        }
        if (offset > 0) sb.append("<offset>$offset</offset>")
        sb.append("</harmony>\n")
    }
}
