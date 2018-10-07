package com.bearded.derek.ankicar.view

import android.view.GestureDetector
import android.view.MotionEvent
import com.bearded.derek.ankicar.utils.*
import com.bearded.derek.ankicar.view.ReviewGestureListener.Direction.SWIPE_DOWN
import com.bearded.derek.ankicar.view.ReviewGestureListener.Direction.SWIPE_LEFT
import com.bearded.derek.ankicar.view.ReviewGestureListener.Direction.SWIPE_RIGHT
import com.bearded.derek.ankicar.view.ReviewGestureListener.Direction.SWIPE_UP

class ReviewGestureListener(val callback: ReviewGestureCallback) : GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {

    object Direction {
        val SWIPE_UP = "up"
        val SWIPE_DOWN = "down"
        val SWIPE_RIGHT = "right"
        val SWIPE_LEFT = "left"
    }

    interface ReviewGestureCallback {
        fun onFling(direction: Pair<String, Double>): Boolean
        fun onLongPress()
        fun onDoubleTap(): Boolean
    }

    override fun onShowPress(e: MotionEvent?) = Unit
    override fun onSingleTapUp(e: MotionEvent?): Boolean= false
    override fun onDown(e: MotionEvent?): Boolean = false
    override fun onDoubleTapEvent(e: MotionEvent?): Boolean = false
    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean = false
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean = false

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        val numHist = e2!!.historySize
        val x1 =
            if (numHist > 0) {
                e2.getHistoricalX(numHist - 1)
            } else {
                e1!!.x
            }

        val y1 =
            if (numHist > 0) {
                e2.getHistoricalY(numHist - 1)
            } else {
                e1!!.y
            }

        val x2 = e2.x
        val y2 = e2.y

        val xVect = x2 - x1
        val yVect = -1*(y2 - y1) // -1 to normalize the top-left x-y convention for pixel

        return callback.onFling(findClosestAxis(Pair(xVect, yVect)))
    }

    override fun onLongPress(e: MotionEvent?) {
        callback.onLongPress()
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        return callback.onDoubleTap()
    }

    private fun findClosestAxis(direction: Pair<Float, Float>): Pair<String, Double> {
        return listOf<Pair<String, Double>>(
                Pair(SWIPE_UP, cosineSimilarity(direction.first, direction.second, posYVect.first, posYVect.second)),
                Pair(SWIPE_DOWN, cosineSimilarity(direction.first, direction.second, negYVect.first, negYVect.second)),
                Pair(SWIPE_RIGHT, cosineSimilarity(direction.first, direction.second, posXVect.first, posXVect.second)),
                Pair(SWIPE_LEFT, cosineSimilarity(direction.first, direction.second, negXVect.first, negXVect.second))
        ).maxBy {
            it.second
        }!!
    }
}