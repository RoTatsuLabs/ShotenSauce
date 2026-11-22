package org.koitharu.kotatsu.details.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_SIZE = 96
private const val COLOR_COUNT = 16
private const val MIN_CHROMA = 36.0

/**
 * An accent taken from the cover: the colour that stands out most in the image, kept at a tone that
 * reads well on the current surface. Null until the cover is loaded, or when it has no usable colour.
 */
@Composable
internal fun rememberCoverAccent(
	imageLoader: ImageLoader,
	coverUrl: String?,
	coverRefreshes: Int,
	isDark: Boolean,
	enabled: Boolean,
): Color? {
	val context = LocalContext.current
	val accent by produceState<Color?>(initialValue = null, coverUrl, coverRefreshes, isDark, enabled) {
		value = if (!enabled || coverUrl.isNullOrEmpty()) {
			null
		} else {
			runCatching {
				val request = ImageRequest.Builder(context)
					.data(coverUrl)
					.size(Size(SAMPLE_SIZE, SAMPLE_SIZE))
					.allowHardware(false)
					.build()
				val bitmap = (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
				bitmap?.let { withContext(Dispatchers.Default) { pickAccent(it, isDark) } }
			}.getOrNull()
		}
	}
	return accent
}

@SuppressLint("RestrictedApi")
private fun pickAccent(bitmap: Bitmap, isDark: Boolean): Color? {
	val scaled = if (bitmap.width > SAMPLE_SIZE || bitmap.height > SAMPLE_SIZE) {
		Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
	} else {
		bitmap
	}
	val pixels = IntArray(scaled.width * scaled.height)
	scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
	val source = Score.score(QuantizerCelebi.quantize(pixels, COLOR_COUNT)).firstOrNull() ?: return null
	val hct = Hct.fromInt(source)
	val accent = Hct.from(hct.hue, maxOf(hct.chroma, MIN_CHROMA), if (isDark) 80.0 else 40.0)
	return Color(accent.toInt())
}
