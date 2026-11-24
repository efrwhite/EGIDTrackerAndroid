package com.elizabethwhitebaker.egidtracker

import android.app.Activity
import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Shared pieces of the light "page" screens (Food Tracker, Medications, Allergens, ...):
 * the purple header, calendar date fields and dashed list boxes.
 */

/** Fills in view_page_header: the title, and an optional action link on the right. */
fun Activity.setupPageHeader(@StringRes title: Int, @StringRes action: Int? = null, onAction: (() -> Unit)? = null) {
    findViewById<TextView>(R.id.pageTitle).setText(title)
    val actionView = findViewById<TextView>(R.id.pageAction)
    if (action != null && onAction != null) {
        actionView.setText(action)
        actionView.visibility = View.VISIBLE
        actionView.setOnClickListener { onAction() }
    }
}

/**
 * A field that shows a date as MM/DD/YYYY and picks it from a calendar (typing is disabled).
 * Each screen keeps its own storage format: pass it as [storagePattern] and read/write the
 * stored string with [stored], so existing records keep working.
 */
class DateField(
    private val activity: Activity,
    private val layout: TextInputLayout,
    storagePattern: String = DISPLAY_PATTERN,
    private val allowFuture: Boolean = false
) {
    companion object {
        const val DISPLAY_PATTERN = "MM/dd/yyyy"
    }

    private val storageFormat = SimpleDateFormat(storagePattern, Locale.US)
    private val displayFormat = SimpleDateFormat(DISPLAY_PATTERN, Locale.US)

    var date: Date? = null
        set(value) {
            field = value
            layout.editText?.setText(value?.let(displayFormat::format) ?: "")
            if (value != null) layout.error = null
        }

    /** Called after the user picks a date. */
    var onPicked: ((Date) -> Unit)? = null

    /** The date in the screen's storage format, or "" if none is set. */
    var stored: String
        get() = date?.let(storageFormat::format) ?: ""
        set(value) {
            date = value.takeIf { it.isNotBlank() }?.let {
                runCatching { storageFormat.parse(it) }.getOrNull()
                    // Older records may use the display format
                    ?: runCatching { displayFormat.parse(it) }.getOrNull()
            }
        }

    var isEnabled: Boolean
        get() = layout.isEnabled
        set(value) { layout.isEnabled = value }

    init {
        layout.editText?.apply {
            isFocusable = false
            isCursorVisible = false
            setOnClickListener { pick() }
        }
        layout.setEndIconOnClickListener { pick() }
    }

    fun pick() {
        if (!layout.isEnabled) return
        val calendar = Calendar.getInstance().apply { date?.let { time = it } }
        DatePickerDialog(activity, { _, year, month, day ->
            val picked = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            date = picked
            onPicked?.invoke(picked)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            if (!allowFuture) datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    /** Shows "Required" under the field when empty; returns whether a date is set. */
    fun requireDate(): Boolean {
        if (date == null) layout.error = activity.getString(R.string.error_required)
        return date != null
    }
}

/** One entry in a dashed list box; without [action]/[onClick] it's display-only. */
data class ListRow(
    val title: String,
    val subtitle: String? = null,
    val action: String? = null,
    val onClick: (() -> Unit)? = null
)

/**
 * Shows [rows] in [rowsContainer] (inside a dashed box), or [emptyView] when there are none.
 */
fun fillList(rowsContainer: ViewGroup, emptyView: View, rows: List<ListRow>) {
    rowsContainer.removeAllViews()
    val inflater = LayoutInflater.from(rowsContainer.context)
    rows.forEach { row ->
        val view = inflater.inflate(R.layout.view_list_row, rowsContainer, false)
        view.findViewById<TextView>(R.id.rowTitle).text = row.title
        view.findViewById<TextView>(R.id.rowSubtitle).apply {
            text = row.subtitle
            visibility = if (row.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<TextView>(R.id.rowAction).apply {
            text = row.action
            visibility = if (row.action.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        row.onClick?.let { onClick -> view.setOnClickListener { onClick() } }
        rowsContainer.addView(view)
    }
    rowsContainer.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
}
