package com.wolfeleo2.subsy.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File

/**
 * The downloaded detector: spec.json, vocab.tsv, encoder.int8.onnx and heads.onnx from one export in [dir]. Loading
 * reads about 100 MB, so build one per detection job and [close] it after. Runs on the calling thread; keep it off main.
 */
class ChargeModel(
    dir: File,
) : Closeable {
    val spec = DetectorSpec.parse(File(dir, "spec.json").readText())
    private val tokenizer = File(dir, "vocab.tsv").useLines { UnigramTokenizer.read(it) }
    private val env = OrtEnvironment.getEnvironment()
    private val encoder = env.createSession(File(dir, "encoder.int8.onnx").path, OrtSession.SessionOptions())
    private val heads = env.createSession(File(dir, "heads.onnx").path, OrtSession.SessionOptions())

    fun read(message: String): ModelReading {
        val input = encodeForModel(message, spec, tokenizer)
        val tensors = mutableListOf<OnnxTensor>()

        fun tensor(data: Any) = OnnxTensor.createTensor(env, data).also { tensors += it }
        try {
            val encoded =
                encoder.run(
                    mapOf(
                        "input_ids" to tensor(arrayOf(input.inputIds)),
                        "attention_mask" to tensor(arrayOf(LongArray(input.inputIds.size) { 1L })),
                    ),
                )
            encoded.use {
                val outputs =
                    heads.run(
                        mapOf(
                            "tokens" to encoded.get(0) as OnnxTensor,
                            "word_index" to tensor(arrayOf(input.wordIndex)),
                            "word_mask" to tensor(arrayOf(input.wordMask)),
                        ),
                    )
                outputs.use {
                    @Suppress("UNCHECKED_CAST")
                    val heads =
                        HeadOutputs.from(
                            spanIndex = (outputs.get(0).value as Array<Array<Array<LongArray>>>)[0],
                            spanLogits = (outputs.get(1).value as Array<Array<FloatArray>>)[0],
                            spanValid = (outputs.get(2).value as Array<Array<BooleanArray>>)[0],
                            nullLogits = (outputs.get(3).value as Array<FloatArray>)[0],
                            kindLogits = (outputs.get(4).value as Array<FloatArray>)[0],
                            categoryLogits = (outputs.get(5).value as Array<FloatArray>)[0],
                        )
                    return ChargeDecoder.decode(spec, input.text, input.words, heads)
                }
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    override fun close() {
        encoder.close()
        heads.close()
    }
}
