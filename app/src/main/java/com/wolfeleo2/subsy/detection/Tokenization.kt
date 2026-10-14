package com.wolfeleo2.subsy.detection

import java.text.Normalizer
import java.util.regex.Pattern
import kotlin.math.max

/** heads.onnx was exported with this many word slots; longer messages are cut, as gliner2 does. */
const val MAX_WORDS = 128

/** A word as the model sees it (lowercased), with its offsets in the normalized message. */
data class Word(
    val text: String,
    val start: Int,
    val end: Int,
)

/** What the two graphs take for one message. [wordIndex] and [wordMask] are padded to [MAX_WORDS]. */
class ModelInput(
    /** The normalized message; [words] offsets point into it. */
    val text: String,
    val words: List<Word>,
    val inputIds: LongArray,
    /** Position of each word's first token in [inputIds]; padding slots are 0 and masked out. */
    val wordIndex: LongArray,
    val wordMask: BooleanArray,
)

/** gliner2 ends every text in sentence punctuation before splitting it, and the model was trained on that. */
fun normalizeForModel(text: String): String =
    when {
        text.isEmpty() -> "."
        text.endsWith('.') || text.endsWith('!') || text.endsWith('?') -> text
        else -> "$text."
    }

/** Builds the encoder input the way eval/reference.py does: schema prefix, then each word's pieces. */
fun encodeForModel(
    message: String,
    spec: DetectorSpec,
    tokenizer: UnigramTokenizer,
): ModelInput {
    val text = normalizeForModel(message)
    val words = WordSplitter.split(text).take(MAX_WORDS)
    val ids = ArrayList<Long>(spec.prefixIds)
    val wordIndex = LongArray(MAX_WORDS)
    val wordMask = BooleanArray(MAX_WORDS)
    words.forEachIndexed { i, word ->
        wordIndex[i] = ids.size.toLong()
        wordMask[i] = true
        tokenizer.encodeWord(word.text).forEach { ids += it.toLong() }
    }
    return ModelInput(text, words, ids.toLongArray(), wordIndex, wordMask)
}

/**
 * gliner2's WhitespaceTokenSplitter: URLs, emails and @handles stay whole, hyphen or underscore runs stay one word
 * ("2547****013-MOSES" is one word), any other non-space character is its own word. Python's `\w` plus every
 * combining mark is `[\p{L}\p{N}\p{M}_]` here.
 */
object WordSplitter {
    private const val WORD = """[\p{L}\p{N}\p{M}_‌‍]"""
    private val pattern =
        Pattern.compile(
            """(?:https?://\S+|www\.\S+)|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}|@[a-z0-9_]+|$WORD+(?:[-_]$WORD+)*|\S""",
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS,
        )

    fun split(text: String): List<Word> {
        val matcher = pattern.matcher(text)
        val words = mutableListOf<Word>()
        while (matcher.find()) words += Word(matcher.group().lowercase(), matcher.start(), matcher.end())
        return words
    }
}

/**
 * SentencePiece Unigram, as eval/unigram.py: the best-scoring split of "▁" + the word (Viterbi). A character no piece
 * covers costs the lowest piece score minus 10 and becomes the unknown piece; runs of unknowns fuse into one.
 */
class UnigramTokenizer(
    private val pieces: Map<String, Piece>,
    private val unkId: Int,
    minScore: Double,
) {
    class Piece(
        val id: Int,
        val score: Double,
    )

    private val unkScore = minScore - UNK_PENALTY
    private val maxPieceLength = pieces.keys.maxOf { it.codePointCount(0, it.length) }

    fun encodeWord(word: String): List<Int> {
        // Code points, not chars, so piece lengths match the Python reference.
        val chars = (MARKER + Normalizer.normalize(word, Normalizer.Form.NFC)).codePoints().toArray()
        val n = chars.size
        val bestScore = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }.also { it[0] = 0.0 }
        val bestStart = IntArray(n + 1) { -1 }
        val bestId = IntArray(n + 1) { -1 }
        for (end in 1..n) {
            for (start in max(0, end - maxPieceLength) until end) {
                if (bestScore[start] == Double.NEGATIVE_INFINITY) continue
                val piece = pieces[String(chars, start, end - start)]
                val (score, id) =
                    when {
                        piece != null -> bestScore[start] + piece.score to piece.id
                        end - start == 1 -> bestScore[start] + unkScore to unkId
                        else -> continue
                    }
                if (score > bestScore[end]) {
                    bestScore[end] = score
                    bestStart[end] = start
                    bestId[end] = id
                }
            }
        }
        val ids = ArrayDeque<Int>()
        var end = n
        while (end > 0) {
            ids.addFirst(bestId[end])
            end = bestStart[end]
        }
        return ids.filterIndexed { i, id -> !(id == unkId && i > 0 && ids[i - 1] == unkId) }
    }

    companion object {
        private const val MARKER = "▁"
        private const val UNK_PENALTY = 10.0

        /** Reads vocab.tsv from eval/export_app.py: "unk_id" and "min_score" rows, then "id, piece, score" rows. */
        fun read(lines: Sequence<String>): UnigramTokenizer {
            var unkId = 0
            var minScore = 0.0
            val pieces = HashMap<String, Piece>()
            lines.filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split('\t')
                when (parts[0]) {
                    "unk_id" -> unkId = parts[1].toInt()
                    "min_score" -> minScore = parts[1].toDouble()
                    else -> pieces[parts[1]] = Piece(parts[0].toInt(), parts[2].toDouble())
                }
            }
            return UnigramTokenizer(pieces, unkId, minScore)
        }
    }
}
