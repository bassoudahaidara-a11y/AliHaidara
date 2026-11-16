package com.ali.haidara.lecteur

/** Découpe un texte en petits morceaux faciles à comprendre (100 % hors ligne). */
object Chunker {
    fun split(text: String, maxChars: Int = 240): List<String> {
        val clean = text.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n")
        val out = mutableListOf<String>()
        for (p in clean.split("\n\n")) {
            val para = p.replace("\n", " ").replace(Regex(" {2,}"), " ").trim()
            if (para.isEmpty()) continue
            val sentences = para.split(Regex("(?<=[.!?…:;])\\s+"))
            var cur = StringBuilder()
            for (s in sentences) {
                for (piece in hardSplit(s, maxChars)) {
                    if (cur.isNotEmpty() && cur.length + piece.length + 1 > maxChars) {
                        out.add(cur.toString()); cur = StringBuilder()
                    }
                    if (cur.isNotEmpty()) cur.append(' ')
                    cur.append(piece)
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return out
    }

    private fun hardSplit(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val res = mutableListOf<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOf(", ", max)
            if (cut < max / 3) cut = rest.lastIndexOf(' ', max)
            if (cut <= 0) cut = max
            res.add(rest.substring(0, cut + 1).trim())
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) res.add(rest)
        return res
    }
}
