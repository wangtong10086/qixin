package io.github.magisk317.uikit.surface

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.SubcomposeAsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** An application icon is identified only by its installed package and requested size. */
data class AppIconRequest(
    val packageName: String?,
    val sizePx: Int,
)

internal fun AppIconRequest.cacheKey(): String =
    "app_icon:${packageName.orEmpty()}:$sizePx"

object AppIconLoader {

    @Volatile
    private var imageLoader: ImageLoader? = null

    fun imageLoader(context: Context): ImageLoader {
        val existing = imageLoader
        if (existing != null) return existing
        return synchronized(this) {
            val cached = imageLoader
            if (cached != null) {
                cached
            } else {
                ImageLoader.Builder(context.applicationContext)
                    .components {
                        add(AppIconKeyer())
                        add(AppIconFetcher.Factory(context.applicationContext))
                    }
                    .build()
                    .also { imageLoader = it }
            }
        }
    }

    /** Package identity is required; display labels must not trigger an all-apps scan. */
    internal fun resolvePackageName(packageName: String?): String? =
        packageName?.takeIf(String::isNotBlank)
}

private class AppIconKeyer : Keyer<AppIconRequest> {
    override fun key(data: AppIconRequest, options: Options): String = data.cacheKey()
}

private class AppIconFetcher(
    private val context: Context,
    private val data: AppIconRequest,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val pm = context.packageManager
        val resolvedPackage = AppIconLoader.resolvePackageName(data.packageName)
            ?: throw IllegalStateException("No package resolved for icon request")

        val appInfo = pm.getApplicationInfo(resolvedPackage, 0)
        val drawable = appInfo.loadIcon(pm)
        val targetSize = data.sizePx.coerceIn(1, MAX_ICON_SIZE_PX)
        val bitmap = drawable.toBitmap(
            width = targetSize,
            height = targetSize,
            config = Bitmap.Config.ARGB_8888,
        )

        return ImageFetchResult(
            image = bitmap.asImage(shareable = false),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconRequest> {
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = AppIconFetcher(context = context, data = data)
    }
}

@Composable
fun AppIconImage(
    packageName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    fallbackIcon: ImageVector = Icons.Default.Android,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetIconPx = remember(size, density) {
        with(density) { size.roundToPx() }.coerceIn(1, MAX_ICON_SIZE_PX)
    }
    val imageLoader = remember(context) { AppIconLoader.imageLoader(context) }
    val request = remember(packageName, targetIconPx) {
        AppIconRequest(packageName = packageName, sizePx = targetIconPx)
    }
    val showFallback = packageName.isNullOrBlank()

    Box(modifier = modifier.size(size)) {
        if (showFallback) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.outline,
            )
        } else {
            SubcomposeAsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size)
                    .clip(MaterialTheme.shapes.small),
                loading = {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(size),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
                error = {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(size),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }
    }
}

private const val MAX_ICON_SIZE_PX = 256
