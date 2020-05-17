package com.paolovalerdi.abbey.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout

class StatusBarMarginFrameLayout : FrameLayout {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val lp = layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = insets.systemWindowInsetTop
        layoutParams = lp
        return super.onApplyWindowInsets(insets)
    }
}
