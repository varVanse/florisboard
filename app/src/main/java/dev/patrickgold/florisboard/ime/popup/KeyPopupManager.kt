/*
 * Copyright (C) 2020 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.popup

import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyView
import dev.patrickgold.florisboard.ime.text.keyboard.KeyboardView
import dev.patrickgold.florisboard.util.setTextTintColor

/**
 * Manages the creation and dismissal of key popups as well as the checks if the pointer moved
 * out of the popup bound (only for extended popups).
 *
 * @property keyboardView Reference to the [KeyboardView] to which this manager class belongs to.
 */
class KeyPopupManager(private val keyboardView: KeyboardView) {

    private var anchorLeft: Boolean = false
    private var anchorRight: Boolean = false
    private var activeExtIndex: Int? = null
    private var keyPopupWidth =
        keyboardView.resources.getDimension(R.dimen.key_popup_width).toInt()
    private var keyPopupHeight =
        keyboardView.resources.getDimension(R.dimen.key_popup_height).toInt()
    private val popupView = View.inflate(
        keyboardView.context,
        R.layout.key_popup, null
    ) as LinearLayout
    private val popupViewExt = View.inflate(
        keyboardView.context,
        R.layout.key_popup_extended, null
    ) as FlexboxLayout
    private var row0count: Int = 0
    private var row1count: Int = 0
    private var window: PopupWindow = createPopupWindow(popupView)
    private var windowExt: PopupWindow = createPopupWindow(popupViewExt)

    /** Is true if the preview popup is visible to the user, else false */
    val isShowingPopup: Boolean
        get() = popupView.visibility == View.VISIBLE
    /** Is true if the extended popup is visible to the user, else false */
    val isShowingExtendedPopup: Boolean
        get() = windowExt.isShowing

    init {
        popupView.visibility = View.INVISIBLE
    }

    /**
     * Helper function to create a [KeyPopupExtendedSingleView] and preconfigure it.
     *
     * @param keyView Reference to the keyView currently controlling the popup.
     * @param k The index of the key in the [KeyData] popup array.
     * @param isInitActive If it should initially be marked as active.
     * @param isWrapBefore If the [FlexboxLayout] should wrap before this view.
     * @return A preconfigured [KeyPopupExtendedSingleView].
     */
    private fun createTextView(
        keyView: KeyView,
        k: Int,
        isInitActive: Boolean = false,
        isWrapBefore: Boolean = false
    ): KeyPopupExtendedSingleView {
        val textView = KeyPopupExtendedSingleView(keyView.context, isInitActive)
        val lp = FlexboxLayout.LayoutParams(keyPopupWidth, keyView.measuredHeight)
        lp.isWrapBefore = isWrapBefore
        textView.layoutParams = lp
        textView.gravity = Gravity.CENTER
        setTextTintColor(
            textView,
            R.attr.key_popup_fgColor
        )
        val textSize = keyboardView.resources.getDimension(R.dimen.key_popup_textSize)
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, when (keyView.data.popup[k].code) {
                KeyCode.URI_COMPONENT_TLD -> textSize * 0.6f
                else -> textSize
            }
        )
        textView.text = keyView.getComputedLetter(keyView.data.popup[k])
        return textView
    }

    /**
     * Helper function for a convenient way of creating a [PopupWindow].
     *
     * @param view The view to set as content view of the [PopupWindow].
     * @return A new [PopupWindow] already preconfigured and ready-to-go.
     */
    private fun createPopupWindow(view: View): PopupWindow {
        return PopupWindow(keyboardView.context).apply {
            animationStyle = 0
            contentView = view
            enterTransition = null
            exitTransition = null
            isClippingEnabled = false
            isFocusable = false
            isTouchable = false
            setBackgroundDrawable(null)
        }
    }

    /**
     * Shows a preview popup for the passed [keyView]. Ignores show requests for key views which
     * key code is equal to or less than [KeyCode.SPACE].
     *
     * @param keyView Reference to the keyView currently controlling the popup.
     */
    fun show(keyView: KeyView) {
        if (keyView.data.code <= KeyCode.SPACE) {
            return
        }

        // Update keyPopupWidth and keyPopupHeight
        when (keyboardView.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                keyPopupWidth = (keyboardView.desiredKeyWidth * 0.6f).toInt()
                keyPopupHeight = (keyboardView.desiredKeyHeight * 3.0f).toInt()
            }
            else -> {
                keyPopupWidth = (keyboardView.desiredKeyWidth * 1.1f).toInt()
                keyPopupHeight = (keyboardView.desiredKeyHeight * 2.5f).toInt()
            }
        }

        val keyPopupX = (keyView.measuredWidth - keyPopupWidth) / 2
        val keyPopupY = -keyPopupHeight
        if (window.isShowing) {
            window.update(keyView, keyPopupX, keyPopupY, keyPopupWidth, keyPopupHeight)
        } else {
            window.width = keyPopupWidth
            window.height = keyPopupHeight
            window.showAsDropDown(keyView, keyPopupX, keyPopupY, Gravity.NO_GRAVITY)
        }
        popupView.findViewById<TextView>(R.id.key_popup_text).text = keyView.getComputedLetter()
        popupView.findViewById<ImageView>(R.id.key_popup_threedots).visibility = when {
            keyView.data.popup.isEmpty() -> View.INVISIBLE
            else -> View.VISIBLE
        }
        popupView.visibility = View.VISIBLE
    }

    /**
     * Extends the currently showing key preview popup if there are popup keys defined in the
     * [KeyData] of the passed [keyView]. Ignores extend requests for key views which key code
     * is equal to or less than [KeyCode.SPACE].
     *
     * Layout of the extended key popup: (n = keyView.data.popup.size)
     *   when n <= 5: single line, row0 only
     *     _ _ _ _ _
     *     K K K K K
     *   when n > 5 && n % 2 == 1: multi line, row0 has 1 more key than row1, empty space position
     *     is depending on the current anchor
     *     anchorLeft           anchorRight
     *     K K ... K _         _ K ... K K
     *     K K ... K K         K K ... K K
     *   when n > 5 && n % 2 == 0: multi line, both same length
     *     K K ... K K
     *     K K ... K K
     *
     * @param keyView Reference to the keyView currently controlling the popup.
     */
    fun extend(keyView: KeyView) {
        if (keyView.data.code <= KeyCode.SPACE || keyView.data.popup.isEmpty()) {
            return
        }

        // Anchor left if keyView is in left half of keyboardView, else anchor right
        anchorLeft = keyView.x < keyboardView.measuredWidth / 2
        anchorRight = !anchorLeft

        // Determine key counts for each row
        val n = keyView.data.popup.size
        when {
            n <= 5 -> {
                row1count = 0
                row0count = n
            }
            n > 5 && n % 2 == 1 -> {
                row1count = (n - 1) / 2
                row0count = (n + 1) / 2
            }
            else -> {
                row1count = n / 2
                row0count = n / 2
            }
        }

        // Build UI
        popupViewExt.removeAllViews()
        for (k in keyView.data.popup.indices) {
            val isInitActive =
                anchorLeft && (k - row1count == 0) ||
                anchorRight && (k - row1count == row0count - 1)
            popupViewExt.addView(
                createTextView(
                    keyView, k, isInitActive, (row1count > 0) && (k - row1count == 0)
                )
            )
            if (isInitActive) {
                activeExtIndex = k
            }
        }
        popupView.findViewById<ImageView>(R.id.key_popup_threedots)?.visibility = View.INVISIBLE

        // Calculate layout params
        val extWidth = row0count * keyPopupWidth
        val extHeight = when {
            row1count > 0 -> keyView.measuredHeight * 2
            else -> keyView.measuredHeight
        }
        popupViewExt.justifyContent = if (anchorLeft) {
            JustifyContent.FLEX_START
        } else {
            JustifyContent.FLEX_END
        }
        if (popupViewExt.layoutParams == null) {
            popupViewExt.layoutParams = ViewGroup.LayoutParams(extWidth, extHeight)
        } else {
            popupViewExt.layoutParams.apply {
                width = extWidth
                height = extHeight
            }
        }
        val x = ((keyView.measuredWidth - keyPopupWidth) / 2) + when {
            anchorLeft -> 0
            else -> -extWidth + keyPopupWidth
        }
        val y = -keyPopupHeight - when {
            row1count > 0 -> keyView.measuredHeight
            else -> 0
        }

        // Position and show popup window
        if (windowExt.isShowing) {
            windowExt.update(keyView, x, y, extWidth, extHeight)
        } else {
            windowExt.width = extWidth
            windowExt.height = extHeight
            windowExt.showAsDropDown(keyView, x, y, Gravity.NO_GRAVITY)
        }
    }

    /**
     * Updates the current selected key in extended popup according to the passed [event].
     * This function does nothing if the extended popup is not showing and will return false.
     *
     * @param keyView Reference to the keyView currently controlling the popup.
     * @param event The [MotionEvent] passed from the [KeyboardView]'s onTouch event.
     * @return True if the pointer movement is within the elements bounds, false otherwise.
     */
    fun propagateMotionEvent(keyView: KeyView, event: MotionEvent): Boolean {
        if (!isShowingExtendedPopup) {
            return false
        }

        val kX: Float = event.x / keyPopupWidth.toFloat()
        val keyPopupDiffX = ((keyView.measuredWidth - keyPopupWidth) / 2)

        // Check if out of boundary on y-axis
        if (event.y < -keyPopupHeight || event.y > 0.9f * keyPopupHeight) {
            return false
        }

        activeExtIndex = when {
            anchorLeft -> when {
                // check if out of boundary on x-axis
                event.x < keyPopupDiffX - keyPopupWidth ||
                event.x > (keyPopupDiffX + (row0count + 1) * keyPopupWidth) -> {
                    return false
                }
                // row 1
                event.y < 0 && row1count > 0 -> when {
                    kX >= row1count -> row1count - 1
                    kX < 0 -> 0
                    else -> kX.toInt()
                }
                // row 0
                else -> when {
                    kX >= row0count -> row1count + row0count - 1
                    kX < 0 -> row1count
                    else -> row1count + kX.toInt()
                }
            }
            anchorRight -> when {
                // check if out of boundary on x-axis
                event.x > keyView.measuredWidth - keyPopupDiffX + keyPopupWidth ||
                event.x < (keyView.measuredWidth -
                        keyPopupDiffX - (row0count + 1) * keyPopupWidth) -> {
                    return false
                }
                // row 1
                event.y < 0 && row1count > 0 -> when {
                    kX >= 0 -> row1count - 1
                    kX < -(row1count - 1) -> 0
                    else -> row1count - 2 + kX.toInt()
                }
                // row 0
                else -> when {
                    kX >= 0 -> row1count + row0count - 1
                    kX < -(row0count - 1) -> row1count
                    else -> row1count + row0count - 2 + kX.toInt()
                }
            }
            else -> -1
        }

        for (k in keyView.data.popup.indices) {
            val view = popupViewExt.getChildAt(k)
            if (view != null) {
                val textView = view as KeyPopupExtendedSingleView
                textView.isActive = k == activeExtIndex
            }
        }

        return true
    }

    /**
     * Gets the [KeyData] of the currently active key. May be either the key of the popup preview
     * or one of the keys in extended popup, if shown.
     *
     * @param keyView Reference to the keyView currently controlling the popup.
     * @return The [KeyData] object of the currently active key.
     */
    fun getActiveKeyData(keyView: KeyView): KeyData {
        return keyView.data.popup.getOrNull(activeExtIndex ?: -1) ?: keyView.data
    }

    /**
     * Hides the key preview popup as well as the extended popup.
     */
    fun hide() {
        popupView.visibility = View.INVISIBLE
        if (windowExt.isShowing) {
            windowExt.dismiss()
        }

        activeExtIndex = null
    }

    /**
     * Dismisses all currently shown popups. Should be called when [KeyboardView] is closing.
     */
    fun dismissAllPopups() {
        if (window.isShowing) {
            window.dismiss()
        }
        if (windowExt.isShowing) {
            windowExt.dismiss()
        }

        activeExtIndex = null
    }
}
