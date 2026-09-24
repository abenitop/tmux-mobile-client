package com.tmuxmobile.phase0

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape

/**
 * Chat bubble outline: a rounded rectangle with one squared-off bottom corner that a
 * small tail triangle hangs off, the way WhatsApp's bubbles are shaped.
 *
 * One shape per (side, tailed-or-not) rather than a library: the tail has to be part of
 * the SAME outline as the body, otherwise a background painted from two separate shapes
 * shows its seam wherever they overlap. Hence a Path, not a RoundedCornerShape.
 *
 * @param tailOnRight true for the user's own messages (right-aligned), false for the
 *   other party's (left-aligned).
 */
internal fun chatBubbleShape(radiusPx: Float, tailPx: Float, tailOnRight: Boolean): Shape =
    GenericShape { size, _ ->
        val corner = CornerRadius(radiusPx, radiusPx)
        val square = CornerRadius(0f, 0f)
        // The tail lives INSIDE the bounds, so the body is inset by tailPx on that side.
        // Sizing the shape is then the caller's only concern; nothing overflows its slot.
        val bodyLeft = if (tailOnRight) 0f else tailPx
        val bodyRight = if (tailOnRight) size.width - tailPx else size.width
        val bodyBottom = size.height - tailPx

        addRoundRect(
            RoundRect(
                left = bodyLeft,
                top = 0f,
                right = bodyRight,
                // Leave room under the body for the tail to sit in.
                bottom = bodyBottom,
                topLeftCornerRadius = corner,
                topRightCornerRadius = corner,
                // The tail's corner is the squared one -- rounding it would leave a notch
                // between the body and the tail.
                bottomRightCornerRadius = if (tailOnRight) square else corner,
                bottomLeftCornerRadius = if (tailOnRight) corner else square,
            ),
        )

        // Tail: a right triangle whose vertical edge is flush with the body's bottom
        // corner and whose point lands at the outer edge of the bounds.
        if (tailOnRight) {
            moveTo(bodyRight, bodyBottom)
            lineTo(size.width, size.height)
            lineTo(bodyRight, size.height)
        } else {
            moveTo(bodyLeft, bodyBottom)
            lineTo(0f, size.height)
            lineTo(bodyLeft, size.height)
        }
        close()
    }

/** Plain rounded bubble -- used for every bubble except the last one in a group. */
internal fun plainBubbleShape(radiusPx: Float): Shape = GenericShape { size, _ ->
    val corner = CornerRadius(radiusPx, radiusPx)
    addRoundRect(RoundRect(left = 0f, top = 0f, right = size.width, bottom = size.height, corner))
}
