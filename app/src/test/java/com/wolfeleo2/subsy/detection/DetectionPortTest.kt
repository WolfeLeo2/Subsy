package com.wolfeleo2.subsy.detection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin port against the Python reference it was proven equal to gliner2 with (eval/reference.py, unigram.py).
 * Resources come from eval/export_app.py and hold synthetic messages only.
 */
class DetectionPortTest {
    private val spec = DetectorSpec.parse(resource("detection/spec.json"))
    private val tokenizer = UnigramTokenizer.read(resource("detection/vocab-subset.tsv").lineSequence())

    @Test
    fun splitsAndTokenizesEveryMessageLikeTheReference() {
        val cases = lines("detection/tokens.jsonl")
        cases.forEach { case ->
            val text = case.string("text")
            val input = encodeForModel(text, spec, tokenizer)
            assertEquals(text, case.words(), input.words)
            val ids = case["ids"]!!.jsonArray.map { it.jsonPrimitive.long }
            assertEquals(text, ids, input.inputIds.drop(spec.prefixIds.size))
        }
        assertTrue("expected the full synthetic set", cases.size > 1000)
    }

    @Test
    fun decodesHeadOutputsLikeTheReference() {
        val cases = lines("detection/fixtures.jsonl")
        cases.forEach { case ->
            val text = case.string("text")
            val input = encodeForModel(text, spec, tokenizer)
            val wordIndex = case["word_index"]!!.jsonArray.map { it.jsonPrimitive.long }
            assertArrayEquals(text, wordIndex.toLongArray(), input.wordIndex.copyOf(wordIndex.size))

            val heads = case["heads"]!!.jsonObject
            val outputs =
                HeadOutputs.from(
                    spanIndex = heads.batch("span_index").map { q -> q.jsonArray.map { c -> c.longs() }.toTypedArray() }.toTypedArray(),
                    spanLogits = heads.batch("span_logits").map { it.floats() }.toTypedArray(),
                    spanValid =
                        heads
                            .batch("span_valid")
                            .map { q ->
                                q.jsonArray.map { it.jsonPrimitive.content.toBoolean() }.toBooleanArray()
                            }.toTypedArray(),
                    nullLogits = heads.batch("null_logits").floats(),
                    kindLogits = heads.batch("kind_logits").floats(),
                    categoryLogits = heads.batch("category_logits").floats(),
                )
            val expected = case["result"]!!.jsonObject
            val want =
                ModelReading(
                    entities =
                        expected["entities"]!!.jsonObject.mapValues { (_, v) ->
                            v.jsonArray.map { it.jsonPrimitive.content }
                        },
                    kind = expected.string("kind"),
                    category = expected.string("category"),
                )
            assertEquals(text, want, ChargeDecoder.decode(spec, input.text, input.words, outputs))
        }
        assertTrue(cases.isNotEmpty())
    }

    @Test
    fun normalizesLikeGliner() {
        assertEquals(".", normalizeForModel(""))
        assertEquals("Paid Ksh 50.", normalizeForModel("Paid Ksh 50"))
        assertEquals("Done!", normalizeForModel("Done!"))
    }

    private fun resource(path: String): String = javaClass.classLoader!!.getResource(path)!!.readText()

    private fun lines(path: String) =
        resource(path)
            .lineSequence()
            .filter {
                it.isNotBlank()
            }.map { Json.parseToJsonElement(it).jsonObject }
            .toList()

    private fun Map<String, JsonElement>.string(key: String) = this[key]!!.jsonPrimitive.content

    private fun Map<String, JsonElement>.words() =
        this["words"]!!.jsonArray.map {
            val w = it.jsonArray
            Word(w[0].jsonPrimitive.content, w[1].jsonPrimitive.int, w[2].jsonPrimitive.int)
        }

    /** Drops the batch dimension of a heads.onnx output. */
    private fun Map<String, JsonElement>.batch(key: String) = this[key]!!.jsonArray[0].jsonArray

    private fun JsonArray.floats() = map { it.jsonPrimitive.content.toFloat() }.toFloatArray()

    private fun JsonElement.longs() = jsonArray.map { it.jsonPrimitive.long }.toLongArray()

    private fun JsonElement.floats() = jsonArray.floats()
}
