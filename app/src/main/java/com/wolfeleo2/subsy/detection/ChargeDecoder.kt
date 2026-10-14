package com.wolfeleo2.subsy.detection

import kotlin.math.exp

/** One candidate span from heads.onnx, in words, half-open. */
class SpanCandidate(
    val start: Int,
    val end: Int,
    val logit: Float,
    val valid: Boolean,
)

/** heads.onnx output for one message, batch dimension removed. Lists are per entity query, in [DetectorSpec.entities] order. */
class HeadOutputs(
    val candidates: List<List<SpanCandidate>>,
    val nullLogits: FloatArray,
    val kindLogits: FloatArray,
    val categoryLogits: FloatArray,
) {
    companion object {
        /** From the graph's arrays: span index [Q][C][2], span logits and validity [Q][C], the rest flat. */
        fun from(
            spanIndex: Array<Array<LongArray>>,
            spanLogits: Array<FloatArray>,
            spanValid: Array<BooleanArray>,
            nullLogits: FloatArray,
            kindLogits: FloatArray,
            categoryLogits: FloatArray,
        ) = HeadOutputs(
            candidates =
                spanIndex.indices.map { q ->
                    spanIndex[q].indices.map { c ->
                        SpanCandidate(spanIndex[q][c][0].toInt(), spanIndex[q][c][1].toInt(), spanLogits[q][c], spanValid[q][c])
                    }
                },
            nullLogits = nullLogits,
            kindLogits = kindLogits,
            categoryLogits = categoryLogits,
        )
    }
}

/**
 * What the model read from one message, before the app's guards. Entity values are surfaces of the message, best
 * first; [kind] and [category] are labels from [DetectorSpec].
 */
data class ModelReading(
    val entities: Map<String, List<String>>,
    val kind: String,
    val category: String,
)

/** The decode half of gliner2's boundary extractor for our schema, as eval/reference.py `decode`. */
object ChargeDecoder {
    fun decode(
        spec: DetectorSpec,
        text: String,
        words: List<Word>,
        out: HeadOutputs,
    ): ModelReading {
        val entities =
            spec.entities.withIndex().associate { (q, name) ->
                val found = mutableListOf<String>()
                if (sigmoid(out.nullLogits[q]) <= spec.abstentionThreshold) {
                    // The same bounds can come up twice: keep the higher score.
                    val byBounds = HashMap<Pair<Int, Int>, Double>()
                    out.candidates[q].forEach { c ->
                        val p = sigmoid(c.logit).toDouble()
                        if (c.valid && p >= spec.spanThreshold && c.start >= 0 && c.start < c.end && c.end <= words.size) {
                            byBounds.merge(c.start to c.end, p, ::maxOf)
                        }
                    }
                    bestNonOverlapping(byBounds.map { (bounds, p) -> Scored(p, bounds.first, bounds.second) }).forEach { span ->
                        val surface = text.substring(words[span.start].start, words[span.end - 1].end).trim()
                        if (surface.isNotEmpty() && surface !in found) found += surface
                    }
                }
                name to found.toList()
            }
        return ModelReading(
            entities = entities,
            kind = spec.kinds[out.kindLogits.argmax()],
            category = spec.categories[out.categoryLogits.argmax()],
        )
    }

    private class Scored(
        val p: Double,
        val start: Int,
        val end: Int,
    )

    /** The highest-total-score set of non-overlapping spans (weighted interval scheduling), best score first. */
    private fun bestNonOverlapping(spans: List<Scored>): List<Scored> {
        val sorted = spans.sortedWith(compareBy({ it.end }, { it.start }))
        val totals = DoubleArray(sorted.size + 1)
        val picks = ArrayList<List<Scored>>(sorted.size + 1).apply { add(emptyList()) }
        sorted.forEachIndexed { i, span ->
            val before = sorted.count { it.end <= span.start } // spans that end before this one starts
            val take = totals[before] + span.p
            // Only a strictly better total replaces the previous best, as Python's max() keeps the first on a tie.
            if (take > totals[i]) {
                totals[i + 1] = take
                picks += picks[before] + span
            } else {
                totals[i + 1] = totals[i]
                picks += picks[i]
            }
        }
        return picks.last().sortedWith(compareBy({ -it.p }, { it.start }, { it.end }))
    }

    /** Masked candidates carry logits near -1e9; exp overflows to infinity and the probability is 0, as intended. */
    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private fun FloatArray.argmax(): Int = indices.maxBy { this[it] }
}
