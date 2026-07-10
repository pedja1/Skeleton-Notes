package org.skynetsoftware.skeletonnotes.util

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.R
import org.skynetsoftware.skeletonnotes.widget.ImageGridLayout

/**
 * Binds a list of local image [paths] into an [ImageGridLayout]. This is the "external" half of the
 * grid: it owns the display concerns the layout deliberately does not — creating/reusing
 * [ImageView] children, computing each image's aspect ratio, feeding those ratios to the layout via
 * [ImageGridLayout.LayoutParams.aspectRatio], and loading the bitmaps with [ImageLoader]. The layout
 * itself only measures and arranges the children.
 *
 * Passing an empty list hides the grid. Safe to call repeatedly (e.g. from a RecyclerView bind):
 * children are reused and stale bitmaps are dropped when a recycled cell's path changes.
 */
fun ImageGridLayout.bindImages(paths: List<String>, scope: CoroutineScope) {
    if (paths.isEmpty()) {
        visibility = View.GONE
        for (i in 0 until childCount) clearCell(getChildAt(i) as ImageView)
        return
    }
    visibility = View.VISIBLE

    while (childCount < paths.size) {
        addView(createImageCell(context))
    }
    for (i in 0 until childCount) {
        val cell = getChildAt(i) as ImageView
        if (i >= paths.size) {
            clearCell(cell)
            continue
        }
        cell.visibility = View.VISIBLE
        val path = paths[i]
        if (cell.getTag(R.id.image_grid_path_tag) != path) {
            // Recycled cell now shows a different image: cancel/clear before the reload.
            (cell.getTag(R.id.image_loader_job_tag) as? Job)?.cancel()
            cell.setImageDrawable(null)
            cell.setTag(R.id.image_grid_path_tag, path)
            cell.setTag(R.id.image_grid_loaded_key, null)
        }
        cell.aspectLayoutParams().aspectRatio = ImageLoader.cachedAspectRatio(path) ?: 1f
    }
    requestLayout()
    loadCells(paths, scope)

    // Read any not-yet-known aspect ratios off the disk header, then re-arrange + reload.
    val grid = this
    scope.launch {
        val ratios = withContext(Dispatchers.IO) { paths.map { ImageLoader.aspectRatio(it) } }
        if (!grid.stillBoundTo(paths)) return@launch
        for (i in paths.indices) {
            (grid.getChildAt(i) as ImageView).aspectLayoutParams().aspectRatio = ratios[i]
        }
        grid.requestLayout()
        grid.loadCells(paths, scope)
    }
}

/** True while each grid cell still holds the same path it was bound to (guards stale async work). */
private fun ImageGridLayout.stillBoundTo(paths: List<String>): Boolean {
    if (childCount < paths.size) return false
    return paths.indices.all { getChildAt(it).getTag(R.id.image_grid_path_tag) == paths[it] }
}

private fun ImageGridLayout.loadCells(paths: List<String>, scope: CoroutineScope) {
    val available = (width.takeIf { it > 0 }?.minus(paddingLeft + paddingRight)
        ?: resources.displayMetrics.widthPixels).coerceAtLeast(1)
    val single = paths.size == 1

    for (i in paths.indices) {
        val cell = getChildAt(i) as ImageView
        val aspect = cell.aspectLayoutParams().aspectRatio
        val reqWidth: Int
        val reqHeight: Int
        if (single) {
            reqWidth = available
            reqHeight = (available / aspect).toInt().coerceAtLeast(1)
        } else {
            reqHeight = targetRowHeight
            reqWidth = (targetRowHeight * aspect).toInt().coerceAtLeast(1)
        }

        val key = "${paths[i]}@${reqWidth}x$reqHeight"
        if (cell.getTag(R.id.image_grid_loaded_key) != key) {
            ImageLoader.load(cell, paths[i], reqWidth, reqHeight, scope)
            cell.setTag(R.id.image_grid_loaded_key, key)
        }
    }
}

private fun ImageView.aspectLayoutParams(): ImageGridLayout.LayoutParams =
    layoutParams as ImageGridLayout.LayoutParams

private fun clearCell(cell: ImageView) {
    cell.visibility = View.GONE
    (cell.getTag(R.id.image_loader_job_tag) as? Job)?.cancel()
    cell.setImageDrawable(null)
    cell.setTag(R.id.image_grid_loaded_key, null)
    cell.setTag(R.id.image_grid_path_tag, null)
}

private fun createImageCell(context: Context): ImageView {
    val cornerRadius = context.resources.displayMetrics.density * 8f
    return ImageView(context).apply {
        layoutParams = ImageGridLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }
}
