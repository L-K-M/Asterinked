package ch.lkmc.asterinked.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import ch.lkmc.asterinked.R
import ch.lkmc.asterinked.ui.EditorScreens.descendants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class PageDialogNavigationTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun startClean() {
        File(app.filesDir, "documents").deleteRecursively()
        app.getSharedPreferences("pen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun nextNoteSkipsBlankPagesAndIgnoresAnInvalidTypedDestination() = activity { activity ->
        val destinations = mutableListOf<Int>()
        val dialog = showPageDialog(activity, Components(activity), 2, 10, listOf(0, 2, 7), destinations::add)
        field(dialog).setText("0")

        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        val next = button(dialog, activity.getString(R.string.next_note_page, 8))
        assertTrue(next.isShown && next.isEnabled)
        next.performClick()

        assertEquals(listOf(7), destinations)
        assertFalse(dialog.isShowing)
    }

    @Test fun previousNoteSkipsTheCurrentPageAndKeepsBoundariesDisabled() = activity { activity ->
        val destinations = mutableListOf<Int>()
        val dialog = showPageDialog(activity, Components(activity), 7, 10, listOf(0, 2, 7), destinations::add)

        assertFalse(button(dialog, activity.getString(R.string.next_note)).isEnabled)
        button(dialog, activity.getString(R.string.previous_note_page, 3)).performClick()
        assertEquals(listOf(2), destinations)
    }

    @Test fun aDocumentWithoutInkStillAllowsManualPageNavigation() = activity { activity ->
        val destinations = mutableListOf<Int>()
        val dialog = showPageDialog(activity, Components(activity), 2, 10, emptyList(), destinations::add)

        assertFalse(button(dialog, activity.getString(R.string.previous_note)).isEnabled)
        assertFalse(button(dialog, activity.getString(R.string.next_note)).isEnabled)
        assertTrue(texts(dialog).any { it.text == activity.getString(R.string.no_notes) })
        field(dialog).setText("10")
        field(dialog).onEditorAction(EditorInfo.IME_ACTION_GO)

        assertEquals(listOf(9), destinations)
        assertFalse(dialog.isShowing)
    }

    @Test fun invalidManualInputDoesNotDismissOrNavigate() = activity { activity ->
        val destinations = mutableListOf<Int>()
        val dialog = showPageDialog(activity, Components(activity), 2, 10, listOf(2), destinations::add)
        field(dialog).setText("99")
        field(dialog).onEditorAction(EditorInfo.IME_ACTION_GO)

        assertTrue(destinations.isEmpty())
        assertTrue(dialog.isShowing)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertTrue(texts(dialog).any { it.text == activity.resources.getQuantityString(R.plurals.annotated_pages, 1, 1) })
    }

    @Test fun pageCounterAnnouncesItsPositionAndNavigationPurposeAsAButton() = activity { activity ->
        EditorScreens.publish(activity, EditorScreens.editing())
        val counter = counter(activity)

        assertTrue(counter is Button)
        assertEquals(activity.getString(R.string.page_navigation_description, 3, 12), counter.contentDescription)
        assertTrue(counter.isShown && counter.isEnabled)
    }

    @Test fun reopeningNavigationUsesCurrentInkAndDismissesForAnotherDocument() = activity { activity ->
        val state = EditorScreens.editing()
        val draft = state.draft!!
        val marked = draft.ink.getValue(draft.page)
        EditorScreens.publish(activity, state.copy(draft = draft.copy(ink = draft.ink + (7 to marked))))
        counter(activity).performClick()
        val first = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(button(first, activity.getString(R.string.next_note_page, 8)).isEnabled)
        first.dismiss()

        EditorScreens.publish(activity, state.copy(draft = draft.copy(ink = draft.ink + (7 to emptyList()))))
        counter(activity).performClick()
        val second = ShadowDialog.getLatestDialog() as AlertDialog
        assertFalse(button(second, activity.getString(R.string.next_note)).isEnabled)

        EditorScreens.publish(activity, state.copy(draft = draft.copy(source = File("another.pdf"))))
        assertFalse("Old note destinations cannot outlive their document", second.isShowing)
        assertEquals(2, ViewModelProvider(activity)[EditorViewModel::class.java].state.value!!.draft!!.page)
    }

    @Test fun delayedDismissalCannotOrphanAReopenedDialog() = activity { activity ->
        val state = EditorScreens.editing()
        EditorScreens.publish(activity, state)
        counter(activity).performClick()
        ShadowDialog.getLatestDialog().dismiss()
        // Reopen before Android delivers the first dialog's dismissal callback.
        counter(activity).performClick()
        val reopened = ShadowDialog.getLatestDialog()
        shadowOf(Looper.getMainLooper()).idle()

        EditorScreens.publish(activity, state.copy(draft = state.draft!!.copy(source = File("another.pdf"))))
        assertFalse("The reopened dialog is still owned by the activity", reopened.isShowing)
    }

    private fun activity(check: (MainActivity) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            EditorScreens.settle()
            check(controller.get())
        }
    }

    private fun counter(activity: MainActivity): View = descendants(activity.window.decorView)
        .single { it.tooltipText == activity.getString(R.string.go_to_page) }

    private fun field(dialog: AlertDialog) = descendants(dialog.window!!.decorView).filterIsInstance<EditText>().single()
    private fun texts(dialog: AlertDialog) = descendants(dialog.window!!.decorView).filterIsInstance<TextView>()
    private fun button(dialog: AlertDialog, text: String) = texts(dialog).filterIsInstance<Button>().single { it.text == text }
}
