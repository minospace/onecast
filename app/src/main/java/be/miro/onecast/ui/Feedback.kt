package be.miro.onecast.ui

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import be.miro.onecast.R
import com.google.android.material.snackbar.Snackbar

/**
 * Show this confirmation dialog with its confirm button in the design system's `danger` colour —
 * for actions that delete or remove something. The title should already be the question
 * ("Delete 3 downloads?") and the button repeat its verb ("Delete").
 */
fun AlertDialog.Builder.showDestructive(): AlertDialog {
    val dialog = create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(dialog.context, R.color.app_danger))
    }
    dialog.show()
    return dialog
}

/**
 * A brief confirmation with an Undo action, for anything the user can take back (marking played,
 * changing Up Next). Stays up for about 6 seconds, as the design system asks for a toast with an
 * action. Shown above [anchor] when one is given and visible (the mini-player), so it doesn't
 * cover it.
 */
fun showUndo(root: View, message: CharSequence, anchor: View? = null, undo: () -> Unit) {
    val bar = Snackbar.make(root, message, UNDO_DURATION_MS)
        .setAction(R.string.undo) { undo() }
    if (anchor != null && anchor.isShown && anchor.height > 0) bar.anchorView = anchor
    bar.show()
}

private const val UNDO_DURATION_MS = 6000
