package com.bearded.derek.ankicar.data

import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.view.View
import com.bearded.derek.ankicar.utils.dpToPx

class VerticalSpaceItemDecorator(val offset: Rect) : RecyclerView.ItemDecoration() {

    companion object {
        fun buildDecorator(leftDp: Int, topDp: Int, rightDp: Int, bottomDp: Int, density: Float) : VerticalSpaceItemDecorator {
            return VerticalSpaceItemDecorator(Rect(dpToPx(leftDp, density),
                    dpToPx(topDp, density),
                    dpToPx(rightDp, density),
                    dpToPx(bottomDp, density)))
        }
    }

    override fun getItemOffsets(outRect: Rect?, view: View?, parent: RecyclerView?, state: RecyclerView.State?) {
        outRect?.left = offset.left
        outRect?.top = offset.top
        outRect?.right = offset.right
        outRect?.bottom = offset.bottom
    }
}