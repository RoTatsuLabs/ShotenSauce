package org.koitharu.kotatsu.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.dialog.DialogButtonSpec
import org.koitharu.kotatsu.core.ui.dialog.ExpressiveButtonGrid
import org.koitharu.kotatsu.core.ui.dialog.ExpressiveDialogCard
import org.koitharu.kotatsu.core.ui.dialog.showComposeDialog
import org.koitharu.kotatsu.details.ui.scrobbling.iconResId
import org.koitharu.kotatsu.details.ui.scrobbling.labelResId
import org.koitharu.kotatsu.scrobbling.common.domain.SmartStatusPrompt
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblingStatus

/** Asks whether smart reading status may change a tracker status, using the same card as the chapter jump popup. */
fun showSmartStatusDialog(
	activity: FragmentActivity,
	prompt: SmartStatusPrompt,
	onConfirm: () -> Unit,
	onDecline: () -> Unit,
) {
	showComposeDialog(activity, onCancel = onDecline) { dismiss ->
		SmartStatusContent(
			prompt = prompt,
			onConfirm = {
				dismiss()
				onConfirm()
			},
			onDecline = {
				dismiss()
				onDecline()
			},
		)
	}
}

@Composable
private fun SmartStatusContent(
	prompt: SmartStatusPrompt,
	onConfirm: () -> Unit,
	onDecline: () -> Unit,
) {
	val status = stringResource(prompt.status.labelResId)
	val service = stringResource(prompt.service.titleResId)
	val (title, message) = when (prompt.status) {
		ScrobblingStatus.RE_READING -> R.string.smart_prompt_reread_title to R.string.smart_prompt_reread_message
		ScrobblingStatus.COMPLETED -> R.string.smart_prompt_complete_title to R.string.smart_prompt_complete_message
		else -> R.string.smart_prompt_resume_title to R.string.smart_prompt_resume_message
	}
	ExpressiveDialogCard(
		icon = painterResource(prompt.status.iconResId),
		title = stringResource(title),
		message = stringResource(message, prompt.title, status, service),
		footer = {
			ExpressiveButtonGrid(
				buttons = listOf(
					DialogButtonSpec(
						label = stringResource(R.string.smart_prompt_confirm, status),
						icon = painterResource(R.drawable.ic_eye_check),
						primary = true,
						onClick = onConfirm,
					),
					DialogButtonSpec(
						label = stringResource(R.string.smart_prompt_decline),
						icon = painterResource(R.drawable.ic_close),
						primary = false,
						onClick = onDecline,
					),
				),
			)
		},
	)
}
