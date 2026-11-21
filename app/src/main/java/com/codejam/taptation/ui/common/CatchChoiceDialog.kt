package com.codejam.taptation.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.codejam.taptation.R

/** Choosing a destination acknowledges an already-applied catch; it never applies damage. */
@Composable
fun CatchChoiceDialog(onTaptation: () -> Unit, onOriginal: () -> Unit) {
    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.caught_title)) },
        text = { Text(stringResource(R.string.caught_message)) },
        confirmButton = { TextButton(onClick = onTaptation) { Text(stringResource(R.string.caught_our_app)) } },
        dismissButton = { TextButton(onClick = onOriginal) { Text(stringResource(R.string.caught_original_app)) } })
}
