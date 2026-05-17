package com.yiqun.translator.data.remote.ai.chatgpt

object SenseGroupChunkPolicy {
    const val SYSTEM_PROMPT =
        "Return JSON only. First translate the whole sentence naturally into the target language using context. Then choose the smallest target-language sense group for the pointed word. If a target verb needs its object or complement to express the event, include it; avoid bare verbs. Also return the exact aligned source words. Never choose the whole sentence unless unavoidable. Format: {\"source_chunk\":\"...\",\"target_chunk\":\"...\"}"

    private val clauseBoundaries = setOf(
        "and", "but", "or", "because", "although", "though", "while", "when", "where", "which",
        "who", "whom", "whose", "that", "if", "unless", "until", "after", "before", "since", "so",
        "despite", "whereas"
    )

    private val leftVerbHelpers = setOf(
        "am", "is", "are", "was", "were", "be", "being", "been", "has", "have", "had",
        "do", "does", "did", "will", "would", "can", "could", "shall", "should", "may",
        "might", "must", "not", "to"
    )

    private val commonVerbs = setOf(
        "make", "made", "makes", "making", "take", "took", "taken", "takes", "taking",
        "give", "gave", "given", "gives", "giving", "get", "got", "gets", "getting",
        "go", "went", "gone", "goes", "going", "come", "came", "comes", "coming",
        "see", "saw", "seen", "sees", "seeing", "read", "reads", "reading", "write",
        "wrote", "written", "writes", "writing", "review", "reviewed", "reviewing",
        "decide", "decided", "decides", "deciding", "show", "shows", "showed", "shown",
        "keep", "kept", "keeps", "keeping", "return", "returns", "returned", "returning",
        "translate", "translates", "translated", "translating", "solve", "solves", "solved",
        "begin", "begins", "began", "begun", "stay", "stays", "stayed", "notice", "notices",
        "jump", "jumps", "point", "points", "appear", "appears", "carry", "carries",
        "finish", "finishes", "wrap", "wraps", "lose", "loses", "rest", "rests",
        "select", "selects", "separate", "separates", "check", "checks", "pass", "passes",
        "open", "opens", "start", "starts", "split", "splits", "move", "moves", "save", "saves",
        "trim", "trims", "mark", "marks", "join", "joins", "include", "includes"
    )

    private val rightVerbStopWords = clauseBoundaries + setOf(
        "today", "tomorrow", "yesterday", "tonight", "now", "later", "soon", "again",
        "here", "there", "nearby", "outside", "inside", "upstairs", "downstairs", "beside", "near",
        "with", "from", "by", "during", "next"
    )

    private val singleMeaningfulVerbs = setOf(
        "appear", "appears", "appeared", "stay", "stays", "stayed", "rest", "rests", "rested"
    )

    private val nounLeftBoundaries = leftVerbHelpers + commonVerbs + clauseBoundaries + setOf(
        "in", "on", "at", "by", "with", "from", "for", "over", "under", "into", "onto", "through"
    )

    private val nounRightBoundaries = leftVerbHelpers + commonVerbs + clauseBoundaries + setOf(
        "in", "on", "at", "by", "with", "from", "for", "over", "under", "into", "onto", "through",
        "of", "as", "than"
    )

    fun refineChunk(
        sentence: String,
        modelChunk: String,
        word: String,
        pointedTokenOffset: Int?,
    ): String? {
        val rawChunk = modelChunk.trim()
        val rawRange = ChatGPTKit.chunkCharRange(sentence, rawChunk, pointedTokenOffset)
        val rawIsUsable = rawRange != null &&
                rawChunk.contains(word) &&
                (pointedTokenOffset == null || containsExclusive(rawRange, pointedTokenOffset))

        val localChunk = localMinimalChunk(sentence, word, pointedTokenOffset)
        if (rawIsUsable) {
            val localRange = localChunk?.let { ChatGPTKit.chunkCharRange(sentence, it, pointedTokenOffset) }
            if (
                localChunk != null &&
                localRange != null &&
                (isOverWide(sentence, rawRange!!) || isStrictlyWiderThan(rawRange, localRange, sentence))
            ) {
                return localChunk
            }
            return rawChunk
        }

        return localChunk
    }

    private fun isOverWide(sentence: String, range: IntRange): Boolean {
        val tokens = tokenRanges(sentence)
        if (tokens.size <= 4) return false
        val chunkTokenCount = tokens.count { it.first >= range.first && it.second <= range.last }
        val firstToken = tokens.firstOrNull() ?: return false
        val lastToken = tokens.lastOrNull() ?: return false
        val coversWholeSentence = range.first <= firstToken.first && range.last >= lastToken.second
        return coversWholeSentence || chunkTokenCount >= maxOf(7, (tokens.size * 0.7).toInt())
    }

    private fun isStrictlyWiderThan(
        candidate: IntRange,
        minimal: IntRange,
        sentence: String,
    ): Boolean {
        if (candidate.first > minimal.first || candidate.last < minimal.last) return false
        return tokenCount(sentence, candidate) > tokenCount(sentence, minimal)
    }

    private fun tokenCount(sentence: String, range: IntRange): Int {
        return tokenRanges(sentence).count { it.first >= range.first && it.second <= range.last }
    }

    private fun localMinimalChunk(
        sentence: String,
        word: String,
        pointedTokenOffset: Int?,
    ): String? {
        val tokens = tokenRanges(sentence)
        if (tokens.isEmpty()) return null
        val pointedIndex = tokens.indexOfFirst { range ->
            if (pointedTokenOffset != null) {
                pointedTokenOffset >= range.first && pointedTokenOffset < range.second
            } else {
                sentence.substring(range.first, range.second) == word
            }
        }
        if (pointedIndex < 0) return null

        val pointedToken = sentence.substring(tokens[pointedIndex].first, tokens[pointedIndex].second)
        val lower = pointedToken.lowercase()
        val phraseRange = if (isLikelyVerb(lower)) {
            verbPhraseRange(sentence, tokens, pointedIndex)
        } else {
            nounPhraseRange(sentence, tokens, pointedIndex)
        }
        return sentence.substring(phraseRange.first, phraseRange.second).trim().takeIf { it.contains(word) }
    }

    private fun verbPhraseRange(
        sentence: String,
        tokens: List<Pair<Int, Int>>,
        pointedIndex: Int,
    ): Pair<Int, Int> {
        var startIndex = pointedIndex
        while (startIndex > 0) {
            val previous = tokenText(sentence, tokens[startIndex - 1]).lowercase()
            if (previous !in leftVerbHelpers) break
            startIndex--
        }

        var endIndex = pointedIndex
        var consumedAfterVerb = 0
        val pointedVerb = tokenText(sentence, tokens[pointedIndex]).lowercase()
        while (endIndex + 1 < tokens.size && consumedAfterVerb < 4) {
            val next = tokenText(sentence, tokens[endIndex + 1]).lowercase()
            if (shouldStopVerbExpansion(next, consumedAfterVerb, pointedVerb)) break
            endIndex++
            consumedAfterVerb++
        }
        return tokens[startIndex].first to tokens[endIndex].second
    }

    private fun shouldStopVerbExpansion(
        next: String,
        consumedAfterVerb: Int,
        pointedVerb: String,
    ): Boolean {
        if (next in rightVerbStopWords && (consumedAfterVerb > 0 || pointedVerb in singleMeaningfulVerbs)) {
            return true
        }
        return consumedAfterVerb > 0 && next.endsWith("ly")
    }

    private fun nounPhraseRange(
        sentence: String,
        tokens: List<Pair<Int, Int>>,
        pointedIndex: Int,
    ): Pair<Int, Int> {
        var startIndex = pointedIndex
        var consumedLeft = 0
        while (startIndex > 0 && consumedLeft < 3) {
            val previous = tokenText(sentence, tokens[startIndex - 1]).lowercase()
            if (previous in nounLeftBoundaries) break
            startIndex--
            consumedLeft++
        }

        var endIndex = pointedIndex
        var consumedRight = 0
        while (endIndex + 1 < tokens.size && consumedRight < 3) {
            val next = tokenText(sentence, tokens[endIndex + 1]).lowercase()
            if (next in nounRightBoundaries) break
            endIndex++
            consumedRight++
        }
        return tokens[startIndex].first to tokens[endIndex].second
    }

    private fun isLikelyVerb(token: String): Boolean {
        return token in commonVerbs ||
                token in leftVerbHelpers ||
                token.endsWith("ed") ||
                token.endsWith("ing")
    }

    private fun tokenText(sentence: String, range: Pair<Int, Int>): String {
        return sentence.substring(range.first, range.second)
    }

    private fun tokenRanges(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start: Int? = null
        for (index in text.indices) {
            if (isTokenChar(text, index)) {
                if (start == null) start = index
            } else if (start != null) {
                ranges.add(start to index)
                start = null
            }
        }
        if (start != null) ranges.add(start to text.length)
        return ranges
    }

    private fun isTokenChar(text: String, index: Int): Boolean {
        val char = text[index]
        if (char.isLetterOrDigit()) return true
        if (char != '\'' && char != '\u2019') return false
        val previous = text.getOrNull(index - 1)
        val next = text.getOrNull(index + 1)
        return previous?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true
    }

    private fun containsExclusive(range: IntRange, offset: Int): Boolean {
        return offset >= range.first && offset < range.last
    }
}
