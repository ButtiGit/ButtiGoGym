package it.pulse.workout.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.test.platform.app.InstrumentationRegistry

/** MediaStore survives the test runner uninstalling the app after the suite. API 35 CI only. */
internal fun saveTestScreenshot(rule: ComposeTestRule, name: String) {
    rule.waitForIdle()
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ButtiGoGymQA")
    }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    checkNotNull(resolver.openOutputStream(uri)).use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
