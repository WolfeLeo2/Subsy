package com.wolfeleo2.subsy.detection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The fixed schema the detector was exported with (eval/reference.py writes spec.json). It ships with the model files:
 * the prefix ids and label order only fit the graphs they came from, so never mix files from different exports.
 */
@Serializable
data class DetectorSpec(
    /** Token ids of the schema prompt that precedes every message. */
    @SerialName("prefix_ids") val prefixIds: List<Long>,
    /** Labels in heads.onnx output order. */
    val kinds: List<String>,
    val categories: List<String>,
    /** Entity queries in heads.onnx output order: amount, merchant, reference. */
    val entities: List<String>,
    @SerialName("span_threshold") val spanThreshold: Double,
    @SerialName("abstention_threshold") val abstentionThreshold: Double,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): DetectorSpec = json.decodeFromString(text)
    }
}
