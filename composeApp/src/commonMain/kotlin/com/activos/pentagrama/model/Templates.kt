package com.activos.pentagrama.model

/** Starting points for new scores and a demo chart similar to the handwritten ones. */
object Templates {

    enum class Kind(val es: String) {
        BLANK("Partitura en blanco"),
        CHART("Cifrado (acordes + barras rítmicas)"),
        SONG("Canción: Intro / Estrofa / Coro / Puente"),
    }

    fun create(kind: Kind, title: String, clef: Clef, keyFifths: Int, timeNum: Int, timeDen: Int): Score {
        val base = Score(title = title, clef = clef, keyFifths = keyFifths, timeNum = timeNum, timeDen = timeDen)
        return when (kind) {
            Kind.BLANK -> base.copy(measures = List(8) { Measure() }.let { withFinal(it) })
            Kind.CHART -> ScoreOpsLite.slashes(base.copy(measures = withFinal(List(16) { Measure() })))
            Kind.SONG -> {
                val sections = listOf("Intro" to 4, "Estrofa" to 8, "Coro" to 8, "Puente" to 4, "Final" to 4)
                val ms = mutableListOf<Measure>()
                for ((name, n) in sections) {
                    repeat(n) { i ->
                        ms += Measure(
                            section = if (i == 0) name else null,
                            startBar = if (i == 0 && ms.isNotEmpty()) StartBar.DOUBLE else StartBar.NORMAL,
                        )
                    }
                }
                ScoreOpsLite.slashes(base.copy(measures = withFinal(ms)))
            }
        }
    }

    private fun withFinal(l: List<Measure>) = l.mapIndexed { i, m -> if (i == l.lastIndex) m.copy(endBar = EndBar.FINAL) else m }

    private fun chart(chords: List<String>, section: String? = null, repeatStart: Boolean = false, repeatEnd: Boolean = false, text: String? = null): List<Measure> =
        chords.mapIndexed { i, c ->
            val parts = c.split(' ').filter { it.isNotBlank() }
            val chordMarks = when (parts.size) {
                0 -> emptyList()
                1 -> listOf(ChordMark(0, parts[0]))
                else -> listOf(ChordMark(0, parts[0]), ChordMark(TICKS_PER_QUARTER * 2, parts[1]))
            }
            Measure(
                events = List(4) { Event(EventKind.SLASH, NoteValue.QUARTER) },
                chords = if (c == "%") emptyList() else chordMarks,
                repeatMeasure = c == "%",
                section = if (i == 0) section else null,
                text = if (i == chords.lastIndex) text else null,
                startBar = if (i == 0 && repeatStart) StartBar.REPEAT_START else StartBar.NORMAL,
                endBar = if (i == chords.lastIndex && repeatEnd) EndBar.REPEAT_END else EndBar.SINGLE,
            ).let { if (it.repeatMeasure) it.copy(events = emptyList()) else it }
        }

    /** Demo inspired by a typical church-band chord chart. */
    fun demoChart(): Score {
        val ms = mutableListOf<Measure>()
        ms += chart(listOf("Bm", "G", "D", "A"), "Intro", repeatStart = true, repeatEnd = true)
        ms += chart(listOf("G", "Bm", "F#m", "A"), "Estrofa", repeatStart = true, repeatEnd = true)
        ms += chart(listOf("Bm", "G", "D", "F#m A"), "Coro", repeatStart = true, repeatEnd = true, text = "x2")
        ms += chart(listOf("G", "Bm", "D", "A"), "Estrofa")
        ms += chart(listOf("Em7", "G", "D", "A"), "Puente")
        ms += chart(listOf("Bm", "G", "D", "F#m A"), "Coro (percusión)", repeatStart = true, repeatEnd = true)
        ms += chart(listOf("G A", "Bm A"), "No lo hay", repeatStart = true, repeatEnd = true)
        ms[ms.lastIndex] = ms.last().copy(endBar = EndBar.FINAL)
        return Score(
            title = "No lo hay",
            subtitle = "Ejemplo de cifrado · BAT",
            clef = Clef.TREBLE, keyFifths = 2, timeNum = 4, timeDen = 4, tempoBpm = 76,
            measuresPerLine = 4, measures = ms,
        )
    }
}

/** Tiny helper used by templates (kept here so the model has no dependency on the editor package). */
internal object ScoreOpsLite {
    fun slashes(score: Score): Score = score.copy(measures = score.measures.map { m ->
        if (m.events.isEmpty() && !m.repeatMeasure) m.copy(events = List(score.timeNum) {
            Event(EventKind.SLASH, when (score.timeDen) { 2 -> NoteValue.HALF; 8 -> NoteValue.EIGHTH; else -> NoteValue.QUARTER })
        }) else m
    })
}
