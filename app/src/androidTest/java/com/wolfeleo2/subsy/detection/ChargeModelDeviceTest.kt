package com.wolfeleo2.subsy.detection

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wolfeleo2.subsy.SubsyApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The downloaded model run through onnxruntime on the phone, against the Python results in fixtures.jsonl (shared
 * with the JVM tests). The int8 kernels on ARM may round differently from the Mac, so a few flips are allowed.
 *
 * Needs charge detection switched on and downloaded. Run it without uninstalling the app (connectedAndroidTest
 * uninstalls it and wipes its data):
 *   ./gradlew :app:installDebug :app:installDebugAndroidTest
 *   adb shell am instrument -w -e class com.wolfeleo2.subsy.detection.ChargeModelDeviceTest \
 *     com.wolfeleo2.subsy.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class ChargeModelDeviceTest {
    @Test
    fun readsMessagesLikeThePythonReference() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SubsyApplication
        val models = app.modelRepository
        assumeTrue("Turn on charge detection in Settings and let it download first", models.isReady())

        val cases =
            javaClass.classLoader!!
                .getResource("detection/fixtures.jsonl")!!
                .readText()
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { Json.parseToJsonElement(it).jsonObject }
                .toList()
        val mismatches = mutableListOf<String>()
        val loadStart = System.nanoTime()
        ChargeModel(models.dir).use { model ->
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000
            val readStart = System.nanoTime()
            cases.forEach { case ->
                val text = case["text"]!!.jsonPrimitive.content
                val expected = case["result"]!!.jsonObject
                val want =
                    ModelReading(
                        entities = expected["entities"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } },
                        kind = expected["kind"]!!.jsonPrimitive.content,
                        category = expected["category"]!!.jsonPrimitive.content,
                    )
                val got = model.read(text)
                if (got != want) mismatches += "$text\n  want $want\n  got  $got"
            }
            val perMessageMs = (System.nanoTime() - readStart) / 1_000_000 / cases.size
            Log.i(TAG, "load ${loadMs}ms, ${perMessageMs}ms per message, ${mismatches.size}/${cases.size} differ")
        }
        mismatches.forEach { Log.i(TAG, it) }
        assertTrue("${mismatches.size}/${cases.size} differ:\n${mismatches.joinToString("\n")}", mismatches.size * 20 <= cases.size)
    }

    private companion object {
        const val TAG = "ChargeModelDeviceTest"
    }
}
