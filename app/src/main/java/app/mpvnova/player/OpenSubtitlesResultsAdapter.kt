package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.AbsListView
import android.widget.TextView
import android.widget.ListView

internal class OpenSubtitlesResultsAdapter(
    private val cleanText: (String) -> String,
) : BaseAdapter() {
    private val results = mutableListOf<OpenSubtitlesResult>()
    private var downloadedIds: Set<Int> = emptySet()

    fun setDownloadedIds(ids: Set<Int>) {
        downloadedIds = ids.toSet()
        notifyDataSetChanged()
    }

    fun replace(items: List<OpenSubtitlesResult>) {
        results.clear()
        results.addAll(items)
        notifyDataSetChanged()
    }

    fun snapshot(): List<OpenSubtitlesResult> = results.toList()
    override fun getCount(): Int = results.size
    override fun getItem(position: Int): OpenSubtitlesResult = results[position]
    override fun getItemId(position: Int): Long = getItem(position).fileId.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.row_opensubtitles_result, parent, false).also(UiFont::applyToViewTree)
        val item = getItem(position)
        val context = parent.context
        row.findViewById<TextView>(R.id.osResultName).text = cleanText(item.fileName)
            .ifBlank { context.getString(R.string.os_search_unnamed) }
        row.findViewById<TextView>(R.id.osResultRelease).apply {
            text = cleanText(item.release)
            visibility = if (text.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
        val flags = buildList {
            if (item.fileId in downloadedIds) add(context.getString(R.string.os_search_downloaded))
            if (item.hearingImpaired) add(context.getString(R.string.os_search_hearing_impaired))
            if (item.hashMatched) add(context.getString(R.string.os_search_hash_match))
        }.joinToString(" / ")
        row.findViewById<TextView>(R.id.osResultMeta).text = listOf(cleanText(item.language), flags)
            .filter { it.isNotBlank() }.joinToString(" / ")
        row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight(row))
        row.isActivated = parent is ListView && parent.hasFocus() && parent.selectedItemPosition == position
        row.isHovered = false
        return row
    }

    private fun rowHeight(row: View): Int {
        val textHeight = listOf(R.id.osResultName, R.id.osResultRelease, R.id.osResultMeta).sumOf { id ->
            val text = row.findViewById<TextView>(id)
            val margins = text.layoutParams as ViewGroup.MarginLayoutParams
            val metrics = text.paint.fontMetricsInt
            val height = if (text.includeFontPadding) metrics.bottom - metrics.top
                else metrics.descent - metrics.ascent
            height + text.compoundPaddingTop + text.compoundPaddingBottom + margins.topMargin + margins.bottomMargin
        }
        return maxOf(row.minimumHeight, textHeight + row.paddingTop + row.paddingBottom)
    }
}
