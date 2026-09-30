package com.activos.pentagrama.model

/**
 * Minimal JSON implementation (no external dependencies) used to save scores.
 */
sealed class JsonValue {
    data class Obj(val map: Map<String, JsonValue>) : JsonValue()
    data class Arr(val list: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Double) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()

    operator fun get(key: String): JsonValue? = (this as? Obj)?.map?.get(key)
    val str: String? get() = (this as? Str)?.value
    val int: Int? get() = (this as? Num)?.value?.toInt()
    val dbl: Double? get() = (this as? Num)?.value
    val bool: Boolean? get() = (this as? Bool)?.value
    val arr: List<JsonValue> get() = (this as? Arr)?.list ?: emptyList()
}

object Json {
    fun write(v: JsonValue): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: JsonValue, sb: StringBuilder) {
        when (v) {
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, value) in v.map) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k, sb); sb.append(':'); write(value, sb)
                }
                sb.append('}')
            }
            is JsonValue.Arr -> {
                sb.append('[')
                v.list.forEachIndexed { i, e -> if (i > 0) sb.append(','); write(e, sb) }
                sb.append(']')
            }
            is JsonValue.Str -> writeString(v.value, sb)
            is JsonValue.Num -> {
                val d = v.value
                if (d == d.toLong().toDouble()) sb.append(d.toLong()) else sb.append(d)
            }
            is JsonValue.Bool -> sb.append(v.value)
            JsonValue.Null -> sb.append("null")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): JsonValue = Parser(text).run { val v = value(); ws(); v }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): JsonValue {
            ws()
            if (i >= s.length) error("JSON inesperadamente vacío")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JsonValue.Str(string())
                't' -> { expect("true"); JsonValue.Bool(true) }
                'f' -> { expect("false"); JsonValue.Bool(false) }
                'n' -> { expect("null"); JsonValue.Null }
                else -> if (c == '-' || c.isDigit()) num() else error("Carácter inesperado '$c' en $i")
            }
        }
        fun expect(w: String) { if (!s.startsWith(w, i)) error("Se esperaba $w en $i"); i += w.length }
        fun obj(): JsonValue {
            i++; val m = LinkedHashMap<String, JsonValue>()
            ws(); if (s[i] == '}') { i++; return JsonValue.Obj(m) }
            while (true) {
                ws(); val k = string(); ws()
                if (s[i] != ':') error("Se esperaba ':' en $i"); i++
                m[k] = value(); ws()
                when (s[i]) { ',' -> i++; '}' -> { i++; return JsonValue.Obj(m) }; else -> error("Se esperaba ',' o '}' en $i") }
            }
        }
        fun arr(): JsonValue {
            i++; val l = mutableListOf<JsonValue>()
            ws(); if (s[i] == ']') { i++; return JsonValue.Arr(l) }
            while (true) {
                l += value(); ws()
                when (s[i]) { ',' -> i++; ']' -> { i++; return JsonValue.Arr(l) }; else -> error("Se esperaba ',' o ']' en $i") }
            }
        }
        fun string(): String {
            if (s[i] != '"') error("Se esperaba texto en $i")
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): JsonValue {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return JsonValue.Num(s.substring(st, i).toDouble())
        }
    }
}

// ---------- Score <-> JSON ----------

private fun obj(vararg p: Pair<String, JsonValue?>) =
    JsonValue.Obj(p.filter { it.second != null }.associate { it.first to it.second!! })

private fun s(v: String?) = v?.let { JsonValue.Str(it) }
private fun n(v: Number) = JsonValue.Num(v.toDouble())
private fun b(v: Boolean) = JsonValue.Bool(v)

private inline fun <reified E : Enum<E>> enumOf(name: String?, def: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

object ScoreJson {
    const val FORMAT_VERSION = 1

    fun encode(score: Score): String = Json.write(
        obj(
            "format" to n(FORMAT_VERSION),
            "title" to s(score.title), "composer" to s(score.composer), "subtitle" to s(score.subtitle),
            "clef" to s(score.clef.name), "key" to n(score.keyFifths),
            "timeNum" to n(score.timeNum), "timeDen" to n(score.timeDen), "timeSymbol" to s(score.timeSymbol.name),
            "tempo" to n(score.tempoBpm), "measuresPerLine" to n(score.measuresPerLine),
            "measures" to JsonValue.Arr(score.measures.map { encodeMeasure(it) }),
        )
    )

    private fun encodeMeasure(m: Measure) = obj(
        "events" to JsonValue.Arr(m.events.map { e ->
            obj(
                "k" to s(e.kind.name), "v" to s(e.value.name),
                "p" to JsonValue.Arr(e.pitches.map { p -> JsonValue.Arr(listOf(n(p.step), n(p.octave), n(p.alter))) }),
                "d" to n(e.dots), "t3" to b(e.triplet), "tie" to b(e.tieToNext), "h" to s(e.head.name),
                "m" to JsonValue.Arr(e.marks.map { JsonValue.Str(it) }), "ly" to s(e.lyric),
            )
        }),
        "chords" to JsonValue.Arr(m.chords.map { obj("t" to n(it.tick), "c" to s(it.text)) }),
        "sb" to s(m.startBar.name), "eb" to s(m.endBar.name),
        "marks" to JsonValue.Arr(m.marks.map { JsonValue.Str(it) }),
        "section" to s(m.section), "ending" to s(m.ending), "text" to s(m.text),
        "rep" to b(m.repeatMeasure),
        "free" to JsonValue.Arr(m.free.map { obj("c" to n(it.codepoint), "x" to n(it.xFrac), "y" to n(it.staffStep)) }),
    )

    fun decode(text: String): Score {
        val j = Json.parse(text)
        val def = Score()
        return Score(
            title = j["title"]?.str ?: def.title,
            composer = j["composer"]?.str ?: "",
            subtitle = j["subtitle"]?.str ?: "",
            clef = enumOf(j["clef"]?.str, Clef.TREBLE),
            keyFifths = j["key"]?.int ?: 0,
            timeNum = j["timeNum"]?.int ?: 4,
            timeDen = j["timeDen"]?.int ?: 4,
            timeSymbol = enumOf(j["timeSymbol"]?.str, TimeSymbol.NUMERIC),
            tempoBpm = j["tempo"]?.int ?: 100,
            measuresPerLine = j["measuresPerLine"]?.int ?: 4,
            measures = j["measures"]?.arr?.map { decodeMeasure(it) } ?: def.measures,
        )
    }

    private fun decodeMeasure(m: JsonValue) = Measure(
        events = m["events"]?.arr?.map { e ->
            Event(
                kind = enumOf(e["k"]?.str, EventKind.NOTE),
                value = enumOf(e["v"]?.str, NoteValue.QUARTER),
                pitches = e["p"]?.arr?.map { p -> val a = p.arr; Pitch(a[0].int ?: 0, a[1].int ?: 4, a[2].int ?: 0) } ?: emptyList(),
                dots = e["d"]?.int ?: 0,
                triplet = e["t3"]?.bool ?: false,
                tieToNext = e["tie"]?.bool ?: false,
                head = enumOf(e["h"]?.str, NoteHead.NORMAL),
                marks = e["m"]?.arr?.mapNotNull { it.str } ?: emptyList(),
                lyric = e["ly"]?.str,
            )
        } ?: emptyList(),
        chords = m["chords"]?.arr?.map { ChordMark(it["t"]?.int ?: 0, it["c"]?.str ?: "") } ?: emptyList(),
        startBar = enumOf(m["sb"]?.str, StartBar.NORMAL),
        endBar = enumOf(m["eb"]?.str, EndBar.SINGLE),
        marks = m["marks"]?.arr?.mapNotNull { it.str } ?: emptyList(),
        section = m["section"]?.str,
        ending = m["ending"]?.str,
        text = m["text"]?.str,
        repeatMeasure = m["rep"]?.bool ?: false,
        free = m["free"]?.arr?.map { FreeSymbol(it["c"]?.int ?: 0xE0A4, (it["x"]?.dbl ?: 0.5).toFloat(), it["y"]?.int ?: 4) } ?: emptyList(),
    )
}
