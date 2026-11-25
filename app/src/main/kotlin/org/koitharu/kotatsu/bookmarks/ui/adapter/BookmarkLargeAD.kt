package org.koitharu.kotatsu.bookmarks.ui.adapter

import android.content.res.ColorStateList
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.bookmarks.domain.Bookmark
import org.koitharu.kotatsu.bookmarks.domain.highlightText
import org.koitharu.kotatsu.core.ui.list.AdapterDelegateClickListenerAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.databinding.ItemBookmarkLargeBinding
import org.koitharu.kotatsu.list.ui.model.ListModel

fun bookmarkLargeAD(
	clickListener: OnListItemClickListener<Bookmark>,
) = adapterDelegateViewBinding<Bookmark, ListModel, ItemBookmarkLargeBinding>(
	{ inflater, parent -> ItemBookmarkLargeBinding.inflate(inflater, parent, false) },
) {
	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)

	bind {
		val color = item.color
		binding.colorDot.isVisible = color != null
		if (color != null) {
			binding.colorDotFill.backgroundTintList = ColorStateList.valueOf(color)
		}
		val highlight = item.highlightText
		binding.textViewHighlight.isVisible = highlight != null
		binding.imageViewThumb.isVisible = highlight == null
		binding.progressView.isVisible = highlight == null
		if (highlight == null) {
			binding.imageViewThumb.setImageAsync(item)
			binding.progressView.setProgress(item.percent, false)
		} else {
			binding.textViewHighlight.text = highlight
		}
	}
}
