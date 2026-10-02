package com.activos.pentagrama.render

/** Minimal PDF 1.4 writer: one full-page JPEG per A4 page. */
object PdfWriter {
    private class Bytes {
        var buf = ByteArray(1 shl 16); var size = 0
        fun add(b: ByteArray) {
            if (size + b.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + b.size))
            b.copyInto(buf, size); size += b.size
        }
        fun add(s: String) = add(s.encodeToByteArray())
    }

    fun write(pages: List<Triple<ByteArray, Int, Int>>): ByteArray {
        val out = Bytes()
        val offsets = mutableListOf<Int>()
        fun obj(body: () -> Unit) { offsets += out.size; out.add("${offsets.size} 0 obj\n"); body(); out.add("\nendobj\n") }
        out.add("%PDF-1.4\n")
        val kids = pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
        obj { out.add("<< /Type /Catalog /Pages 2 0 R >>") }
        obj { out.add("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>") }
        pages.forEachIndexed { i, (jpeg, w, h) ->
            val pageId = 3 + i * 3
            obj { out.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 ${pageId + 1} 0 R >> >> /Contents ${pageId + 2} 0 R >>") }
            obj {
                out.add("<< /Type /XObject /Subtype /Image /Width $w /Height $h /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
                out.add(jpeg); out.add("\nendstream")
            }
            val content = "q 595 0 0 842 0 0 cm /Im0 Do Q"
            obj { out.add("<< /Length ${content.length} >>\nstream\n$content\nendstream") }
        }
        val xref = out.size
        out.add("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.add(it.toString().padStart(10, '0') + " 00000 n \n") }
        out.add("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.buf.copyOf(out.size)
    }
}
