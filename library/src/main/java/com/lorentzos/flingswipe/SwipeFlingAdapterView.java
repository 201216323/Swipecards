package com.lorentzos.flingswipe;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.Adapter;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * Created by dionysis_lorentzos on 5/8/14
 * for package com.lorentzos.swipecards
 * and project Swipe cards.
 * Use with caution dinosaurs might appear!
 */

public class SwipeFlingAdapterView extends BaseFlingAdapterView {


    private int MAX_VISIBLE = 4;
    private int MIN_ADAPTER_STACK = 6;
    private float ROTATION_DEGREES = 15.f;

    private Adapter mAdapter;
    private int LAST_OBJECT_IN_STACK = 0;
    private onFlingListener mFlingListener;
    private AdapterDataSetObserver mDataSetObserver;
    private boolean mInLayout = false;
    private View mActiveCard = null;
    private OnItemClickListener mOnItemClickListener;
    private FlingCardListener flingCardListener;
    private PointF mLastTouchPoint;
    private int scale = 0;
    private float fract = 0;
    // 每个卡片的递减值
    private int decrease = 20;
    private boolean openStackAnim = true;
    private long popAnimDura;



    public SwipeFlingAdapterView(Context context) {
        this(context, null);
    }

    public SwipeFlingAdapterView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.SwipeFlingStyle);
    }

    public SwipeFlingAdapterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwipeFlingAdapterView, defStyle, 0);
        MAX_VISIBLE = a.getInt(R.styleable.SwipeFlingAdapterView_max_visible, MAX_VISIBLE);
        MIN_ADAPTER_STACK = a.getInt(R.styleable.SwipeFlingAdapterView_min_adapter_stack, MIN_ADAPTER_STACK);
        ROTATION_DEGREES = a.getFloat(R.styleable.SwipeFlingAdapterView_rotation_degrees, ROTATION_DEGREES);
        a.recycle();
    }


    /**
     * A shortcut method to set both the listeners and the adapter.
     *
     * @param context The activity context which extends onFlingListener, OnItemClickListener or both
     * @param mAdapter The adapter you have to set.
     */
    public void init(final Context context, Adapter mAdapter) {
        if(context instanceof onFlingListener) {
            mFlingListener = (onFlingListener) context;
        }else{
            throw new RuntimeException("Activity does not implement SwipeFlingAdapterView.onFlingListener");
        }
        if(context instanceof OnItemClickListener){
            mOnItemClickListener = (OnItemClickListener) context;
        }
        setAdapter(mAdapter);
    }

 	@Override
    public View getSelectedView() {
        return mActiveCard;
    }


    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // if we don't have an adapter, we don't need to do anything
        if (mAdapter == null) {
            return;
        }

        mInLayout = true;
        final int adapterCount = mAdapter.getCount();

        if(adapterCount == 0) {
            removeAllViewsInLayout();
        }else {
            View topCard = getChildAt(LAST_OBJECT_IN_STACK);
            if(mActiveCard!=null && topCard!=null && topCard==mActiveCard) {
                if (this.flingCardListener.isTouching()) {
                    PointF lastPoint = this.flingCardListener.getLastPoint();
                    if (this.mLastTouchPoint == null || !this.mLastTouchPoint.equals(lastPoint)) {
                        this.mLastTouchPoint = lastPoint;
                        removeViewsInLayout(0, LAST_OBJECT_IN_STACK);
                        layoutChildren(1, adapterCount);
                    }
                }
            }else{
                // Reset the UI and set top view listener
                removeAllViewsInLayout();
                layoutChildren(0, adapterCount);
                setTopView();
            }
        }

        mInLayout = false;
        
        if(adapterCount <= MIN_ADAPTER_STACK) mFlingListener.onAdapterAboutToEmpty(adapterCount);
    }


    private void layoutChildren(int startingIndex, int adapterCount){
        while (startingIndex < Math.min(adapterCount, MAX_VISIBLE) ) {
            View newUnderChild = mAdapter.getView(startingIndex, null, this);
            if (newUnderChild.getVisibility() != GONE) {
                LAST_OBJECT_IN_STACK = startingIndex;
                makeAndAddView(newUnderChild);
            }
            startingIndex++;
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void makeAndAddView(View child) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        if(openStackAnim){
            int orignWidth = lp.width;
            scale =LAST_OBJECT_IN_STACK*decrease;
            fract = (float)orignWidth/(float)(orignWidth-2*decrease);
            lp.width-=2*scale;
        }
        addViewInLayout(child, 0, lp, true);

        final boolean needToMeasure = child.isLayoutRequested();
        if (needToMeasure) {
            int childWidthSpec = getChildMeasureSpec(getWidthMeasureSpec(),
                    getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin,
                    lp.width);
            int childHeightSpec = getChildMeasureSpec(getHeightMeasureSpec(),
                    getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin,
                    lp.height);
            child.measure(childWidthSpec, childHeightSpec);
        } else {
            cleanupLayoutState(child);
        }

        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();

        int gravity = lp.gravity;
        if (gravity == -1) {
            gravity = Gravity.TOP | Gravity.START;
        }


        int layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
        final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

        int childLeft;
        int childTop;
        switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                childLeft = (getWidth() + getPaddingLeft() - getPaddingRight()  - w) / 2 +
                        lp.leftMargin - lp.rightMargin;
                break;
            case Gravity.END:
                childLeft = getWidth() + getPaddingRight() - w - lp.rightMargin;
                break;
            case Gravity.START:
            default:
                childLeft = getPaddingLeft() + lp.leftMargin;
                break;
        }
        switch (verticalGravity) {
            case Gravity.CENTER_VERTICAL:
                childTop = (getHeight() + getPaddingTop() - getPaddingBottom()  - h) / 2 +
                        lp.topMargin - lp.bottomMargin;
                break;
            case Gravity.BOTTOM:
                childTop = getHeight() - getPaddingBottom() - h - lp.bottomMargin;
                break;
            case Gravity.TOP:
            default:
                childTop = getPaddingTop() + lp.topMargin;
                break;
        }
        child.layout(childLeft, childTop+scale, childLeft + w, childTop + h+scale);
//        child.layout(childLeft, childTop, childLeft + w, childTop + h);
    }




    /**
    *  Set the top view and add the fling listener
    */
    private void setTopView() {
        if(getChildCount()>0){

            mActiveCard = getChildAt(LAST_OBJECT_IN_STACK);
            if(mActiveCard!=null) {

                flingCardListener = new FlingCardListener(mActiveCard, mAdapter.getItem(0),
                        ROTATION_DEGREES, new FlingCardListener.FlingListener() {

                            @Override
                            public void onCardExited() {
                                mActiveCard = null;
                                mFlingListener.removeFirstObjectInAdapter();
                            }

                            @Override
                            public void leftExit(Object dataObject) {
                                mFlingListener.onLeftCardExit(dataObject);
                            }

                            @Override
                            public void rightExit(Object dataObject) {
                                mFlingListener.onRightCardExit(dataObject);
                            }

                            @Override
                            public void onClick(Object dataObject) {
                                if(mOnItemClickListener!=null)
                                    mOnItemClickListener.onItemClicked(0, dataObject);

                            }

                            @Override
                            public void onScroll(float scrollProgressPercent) {
                                mFlingListener.onScroll(scrollProgressPercent);
                            }
                        });

                mActiveCard.setOnTouchListener(flingCardListener);
            }
        }
    }

    public FlingCardListener getTopCardListener() throws NullPointerException{
        if(flingCardListener==null){
            throw new NullPointerException();
        }
        return flingCardListener;
    }

    public void setMaxVisible(int MAX_VISIBLE){
        this.MAX_VISIBLE = MAX_VISIBLE;
    }

    public void setMinStackInAdapter(int MIN_ADAPTER_STACK){
        this.MIN_ADAPTER_STACK = MIN_ADAPTER_STACK;
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }


    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
            mDataSetObserver = null;
        }

        mAdapter = adapter;

        if (mAdapter != null  && mDataSetObserver == null) {
            mDataSetObserver = new AdapterDataSetObserver();
            mAdapter.registerDataSetObserver(mDataSetObserver);
        }
    }

    public void setFlingListener(onFlingListener onFlingListener) {
        this.mFlingListener = onFlingListener;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener){
        this.mOnItemClickListener = onItemClickListener;
    }




    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new FrameLayout.LayoutParams(getContext(), attrs);
    }


    private class AdapterDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            doCardPopAnimation();
        }
        @Override
        public void onInvalidated() {
            requestLayout();
        }

    }

    private void doCardPopAnimation() {
        ArrayList<Animator> animators = new ArrayList<Animator>();
        if(openStackAnim&&LAST_OBJECT_IN_STACK-1>=0&&mActiveCard==null){
            int i = 1;
            while(i<Math.min(mAdapter.getCount(), MAX_VISIBLE)){
                View childView = getChildAt(LAST_OBJECT_IN_STACK-i);
                if(childView!=null){
                    childView.setPivotX(childView.getWidth()/2);
                    ObjectAnimator animScale = ObjectAnimator.ofFloat(childView,"scaleX",fract);
                    ObjectAnimator animTranslate = ObjectAnimator.ofFloat(childView,"translationY",-decrease);
                    if(i==1){
                        animScale.setInterpolator(new BounceInterpolator());
                        animTranslate.setInterpolator(new BounceInterpolator());
                    }
                    animators.add(animScale);
                    animators.add(animTranslate);
                }
                i++;
            }
            AnimatorSet set = new AnimatorSet();
            set.setDuration(300);
            set.playTogether(animators);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    requestLayout();
                }
                @Override
                public void onAnimationCancel(Animator animation) {

                }
                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }else{
            requestLayout();
        }
    }

    public void setCardDecrease(int decrease){
        this.decrease = decrease;
    }

    /**
     * 打开弹出卡片的
     * @param openStackAnim
     */
    public void openPopCardAnim(boolean openStackAnim){
        this.openStackAnim = openStackAnim;
    }

    public void setPopCardAnimationDuration(long duration){
        this.popAnimDura = duration;
    }

    public interface OnItemClickListener {
        void onItemClicked(int itemPosition, Object dataObject);
    }

    public interface onFlingListener {
        void removeFirstObjectInAdapter();
        void onLeftCardExit(Object dataObject);
        void onRightCardExit(Object dataObject);
        void onAdapterAboutToEmpty(int itemsInAdapter);
        void onScroll(float scrollProgressPercent);
    }


}
