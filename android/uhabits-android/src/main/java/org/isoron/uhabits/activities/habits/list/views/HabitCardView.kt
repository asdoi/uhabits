/*
 * Copyright (C) 2016 Álinson Santos Xavier <isoron@gmail.com>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.activities.habits.list.views

import android.content.*
import android.graphics.Typeface
import android.os.*
import android.os.Build.VERSION.*
import android.os.Build.VERSION_CODES.*
import android.text.*
import android.view.*
import android.view.ViewGroup.LayoutParams.*
import android.widget.*
import com.google.auto.factory.*
import org.isoron.androidbase.activities.*
import org.isoron.uhabits.*
import org.isoron.uhabits.activities.common.views.*
import org.isoron.uhabits.core.models.*
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.ui.screens.habits.list.*
import org.isoron.uhabits.core.utils.*
import org.isoron.uhabits.utils.*
import kotlin.math.roundToInt

@AutoFactory
class HabitCardView(
        @Provided @ActivityContext context: Context,
        @Provided private val preferences: Preferences,
        @Provided private val checkmarkPanelFactory: CheckmarkPanelViewFactory,
        @Provided private val numberPanelFactory: NumberPanelViewFactory,
        @Provided private val behavior: ListHabitsBehavior
) : FrameLayout(context),
    ModelObservable.Listener {

    var buttonCount
        get() = checkmarkPanel.buttonCount
        set(value) {
            checkmarkPanel.buttonCount = value
            numberPanel.buttonCount = value
        }

    var dataOffset = 0
        set(value) {
            field = value
            checkmarkPanel.dataOffset = value
            numberPanel.dataOffset = value
        }

    var habit: Habit? = null
        set(newHabit) {
            if (isAttachedToWindow) {
                field?.observable?.removeListener(this)
                newHabit?.observable?.addListener(this)
            }
            field = newHabit
            if (newHabit != null) copyAttributesFrom(newHabit)
        }

    var score
        get() = scoreRing.percentage.toDouble()
        set(value) {
            scoreRing.percentage = value.toFloat()
            scoreRing.precision = 1.0f / 16
            scoreText.text = "${(value * 100).roundToInt()}%"
        }

    var unit
        get() = numberPanel.units
        set(value) {
            numberPanel.units = value
        }

    var values
        get() = checkmarkPanel.values
        set(values) {
            checkmarkPanel.values = values
            numberPanel.values = values.map { it / 1000.0 }.toDoubleArray()
            if (habit?.isNumerical == false && scoreAsText) {
                streakText.apply {
                    visibility = if (values[1] == 0 && values[0] == 0) View.GONE else View.VISIBLE
                }
            }
        }

    var threshold: Double
        get() = numberPanel.threshold
        set(value) {
            numberPanel.threshold = value
        }

    private val scoreAsText: Boolean
        get() = preferences.scoreAsText

    private var checkmarkPanel: CheckmarkPanelView
    private var numberPanel: NumberPanelView
    private var innerFrame: LinearLayout
    private var label: TextView
    private var scoreRing: RingView
    private var scoreText: TextView
    private var streakText: TextView

    init {
        scoreRing = RingView(context).apply {
            val thickness = dp(3f)
            val margin = dp(8f).toInt()
            val ringSize = dp(15f).toInt()
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize).apply {
                setMargins(margin, 0, margin, 0)
                gravity = Gravity.CENTER
            }
            setThickness(thickness)
        }

        scoreText = TextView(context).apply {
            val margin = dp(8f).toInt()
            setSingleLine()
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margin, 0, margin, 0)
                gravity = Gravity.CENTER
            }
        }

        streakText = TextView(context).apply {
            val marginRight = dp(8f).toInt()
            setSingleLine()
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, 0, marginRight, 0)
                gravity = Gravity.CENTER
            }
        }

        label = TextView(context).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            if (SDK_INT >= M) breakStrategy = Layout.BREAK_STRATEGY_BALANCED
        }

        checkmarkPanel = checkmarkPanelFactory.create().apply {
            onToggle = { timestamp, value ->
                triggerRipple(timestamp)
                habit?.let { behavior.onToggle(it, timestamp, value) }
            }
        }

        numberPanel = numberPanelFactory.create().apply {
            visibility = GONE
            onEdit = { timestamp ->
                triggerRipple(timestamp)
                habit?.let { behavior.onEdit(it, timestamp) }
            }
        }

        innerFrame = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            if (SDK_INT >= LOLLIPOP) elevation = dp(1f)

            addView(scoreText)
            addView(streakText)
            addView(scoreRing)
            addView(label)
            addView(checkmarkPanel)
            addView(numberPanel)

            setOnTouchListener { v, event ->
                if (SDK_INT >= LOLLIPOP)
                    v.background.setHotspot(event.x, event.y)
                false
            }
        }

        clipToPadding = false
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        val margin = dp(3f).toInt()
        setPadding(margin, 0, margin, margin)
        addView(innerFrame)
    }

    override fun onModelChange() {
        Handler(Looper.getMainLooper()).post {
            habit?.let { copyAttributesFrom(it) }
        }
    }

    override fun setSelected(isSelected: Boolean) {
        super.setSelected(isSelected)
        updateBackground(isSelected)
    }

    fun triggerRipple(timestamp: Timestamp) {
        val today = DateUtils.getTodayWithOffset()
        val offset = timestamp.daysUntil(today) - dataOffset
        val button = checkmarkPanel.buttons[offset]
        val y = button.height / 2.0f
        val x = checkmarkPanel.x + button.x + (button.width / 2).toFloat()
        triggerRipple(x, y)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        habit?.observable?.addListener(this)
    }

    override fun onDetachedFromWindow() {
        habit?.observable?.removeListener(this)
        super.onDetachedFromWindow()
    }

    private fun copyAttributesFrom(h: Habit) {

        fun getActiveColor(habit: Habit): Int {
            return when (habit.isArchived) {
                true -> sres.getColor(R.attr.mediumContrastTextColor)
                false -> PaletteUtils.getColor(context, habit.color)
            }
        }

        val c = getActiveColor(h)
        label.apply {
            text = h.name
            setTextColor(c)
        }
        scoreRing.apply {
            color = c
            visibility = if (scoreAsText) View.GONE else View.VISIBLE
        }
        scoreText.apply {
            setTextColor(c)
            visibility = if (scoreAsText) View.VISIBLE else View.GONE
        }

        if (!h.isNumerical && scoreAsText) {
            val streaks = h.streaks.all
            var isLastStreakBestStreak = false
            val lastStreak =
                    if (streaks != null && streaks.size > 0) {
                        if (h.streaks.getBest(1)[0] == streaks[0])
                            isLastStreakBestStreak = true
                        streaks[0].length
                    } else 0

            streakText.apply {
                text = "$lastStreak |"
                setTextColor(when (isLastStreakBestStreak) {
                    true -> c
                    false -> sres.getColor(R.attr.mediumContrastTextColor)
                })
                visibility = View.VISIBLE
            }
        } else {
            streakText.apply {
                visibility = View.GONE
            }
        }

        checkmarkPanel.apply {
            color = c
            visibility = when (h.isNumerical) {
                true -> View.GONE
                false -> View.VISIBLE
            }
        }
        numberPanel.apply {
            color = c
            units = h.unit
            threshold = h.targetValue
            visibility = when (h.isNumerical) {
                true -> View.VISIBLE
                false -> View.GONE
            }
        }
    }

    private fun triggerRipple(x: Float, y: Float) {
        val background = innerFrame.background
        if (SDK_INT >= LOLLIPOP) background.setHotspot(x, y)
        background.state = intArrayOf(android.R.attr.state_pressed,
                                      android.R.attr.state_enabled)
        Handler().postDelayed({ background.state = intArrayOf() }, 25)
    }

    private fun updateBackground(isSelected: Boolean) {
        if (SDK_INT < LOLLIPOP) {
            val background = when (isSelected) {
                true -> sres.getDrawable(R.attr.selectedBackground)
                false -> sres.getDrawable(R.attr.cardBackground)
            }
            innerFrame.setBackgroundDrawable(background)
            return
        }

        val background = when (isSelected) {
            true -> R.drawable.selected_box
            false -> R.drawable.ripple
        }
        innerFrame.setBackgroundResource(background)
    }
}
