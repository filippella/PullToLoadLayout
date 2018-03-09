package org.dalol.pulltoloadlayout;

import android.widget.FrameLayout;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * @author Filippo Engidashet <filippo.eng@gmail.com>
 * @version 1.0.0
 * @since Tuesday, 06/03/2018 at 13:15.
 */
public class PullToLoadLayout extends FrameLayout {

    private static final String TAG = PullToLoadLayout.class.getSimpleName();

    private static final int DRAG_MAX_DISTANCE = 48;
    private static final int LOADING_SPINNER_SIZE = 22;
    private static final int LOADING_SPINNER_MARGIN = 10;
    private static final float LOADING_SPINNER_STROKE_WIDTH = 2.5f;

    private static final String LABEL_TOP_LOAD = "Fetching old posts...";
    private static final String LABEL_LOAD_BOTTOM = "Loading more...";
    public static final int TYPE_PULL = 0, TYPE_LOAD = 1;

    private View mChildView;
    private boolean mIsRefreshing;
    private float mTouchY;
    private boolean mIsLoadMore;
    private int mTotalDragDistance;

    private boolean mUserWantsToRefreshMore, mUserWantsToLoadMore;

    private boolean mIsTopDrag, mIsBottomDrag;
    private RectF mTopPullProgressRectF, mBottomPullProgressRectF;
    private float mTopSweepAngle, mBottomSweepAngle;
    private Paint mPaint;
    private TextPaint mTxtPaint;
    private int mPullCounter, mLoadCounter;
    private boolean mShowTopLoadingText, mShowBottomLoadingText;

    private boolean mIsScrolling;

    public PullToLoadLayout(@NonNull Context context) {
        super(context);
        initialize(context, null);
    }

    public PullToLoadLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    public PullToLoadLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PullToLoadLayout(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        if (isInEditMode()) {
            return;
        }

        if (getChildCount() > 1) {
            throw new RuntimeException("can only have one child widget");
        }

        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
        setWillNotDraw(false);

        mTotalDragDistance = toPixel(DRAG_MAX_DISTANCE);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);
        int color = 0xFFfe0657;
        mPaint.setColor(color);
        mPaint.setStrokeWidth(toPixel(LOADING_SPINNER_STROKE_WIDTH));

        mTxtPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTxtPaint.setStyle(Paint.Style.FILL);
        mTxtPaint.setTextAlign(Paint.Align.CENTER);
        //mTxtPaint.setTextSize(60f);
        mTxtPaint.setColor(color);
    }

    public int toPixel(float dimension) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dimension, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mChildView = getChildAt(0);

        if (mChildView == null) {
            return;
        }
        ((RecyclerView) mChildView).addOnScrollListener(mOnScrollListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mChildView != null) {
            ((RecyclerView) mChildView).removeOnScrollListener(mOnScrollListener);
        }
    }

    private final RecyclerView.OnScrollListener mOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (RecyclerView.SCROLL_STATE_DRAGGING == newState) {
                mIsScrolling = true;
            } else if (RecyclerView.SCROLL_STATE_IDLE == newState) {
                mIsScrolling = false;
            }
        }
    };

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
//        if (!isEnabled() || mIsLoadMore || mIsRefreshing) {//This will ensure on directional load at a time
//            return false;
//        }

        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_DOWN:
                mTouchY = event.getY();
                mDirection = ScrollingDirection.NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                float currentY = event.getY();
                float dy = currentY - mTouchY;

                boolean canScrollUp = dy > 0 && !ViewUtils.canChildScrollUp(mChildView) && !mIsRefreshing && mIsScrolling;
                boolean canScrollDown = dy < 0 && !ViewUtils.canChildScrollDown(mChildView) && !mIsLoadMore && mIsScrolling;

                if (canScrollUp) {
                    mDirection = ScrollingDirection.TOP;
                    return true;
                }

                if (canScrollDown) {
                    mDirection = ScrollingDirection.BOTTOM;
                    return true;
                }

                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

//        if (!mIsBeingDragged) {
//            return super.onTouchEvent(event);
//        }

        switch (MotionEventCompat.getActionMasked(event)) {
            case MotionEvent.ACTION_MOVE:
                float currentY = event.getY();
                if (mDirection == ScrollingDirection.TOP) {
                    int overScrollTop = (int) ((currentY - mTouchY) * 0.5f);
                    if (overScrollTop < 0) overScrollTop = 0;
                    mIsTopDrag = true;

                    int height = getMeasuredHeight();
                    int width = getMeasuredWidth();
                    int left = getPaddingLeft();
                    int top = overScrollTop;
                    int right = getPaddingRight();

                    if (overScrollTop < mTotalDragDistance) {
                        mUserWantsToRefreshMore = false;
                        setTopSweepAngle(overScrollTop * 360 / mTotalDragDistance);
                    } else {
                        setTopSweepAngle(360);
                        mUserWantsToRefreshMore = true;
                    }

                    mChildView.layout(left, top, left + width - right, height);
                }

                if (mDirection == ScrollingDirection.BOTTOM) {
                    float overScrollBottom = (currentY - mTouchY) * 0.5f;
                    if (overScrollBottom > getPaddingBottom())
                        overScrollBottom = getPaddingBottom();

                    int height = getMeasuredHeight();
                    int width = getMeasuredWidth();
                    int left = getPaddingLeft();
                    int top = getPaddingTop();
                    int right = getPaddingRight();

                    int bottom = (int) Math.abs(overScrollBottom);
                    mIsBottomDrag = true;

                    if (bottom < mTotalDragDistance) {
                        setBottomSweepAngle(bottom * 360 / mTotalDragDistance);
                        mUserWantsToLoadMore = false;
                    } else {
                        mUserWantsToLoadMore = true;
                        setBottomSweepAngle(360);
                    }
                    mChildView.layout(left, top, left + width - right, height - bottom);
                    mChildView.scrollBy(0, bottom);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mChildView != null) {
                    if (mDirection == ScrollingDirection.TOP) {
                        if (mUserWantsToRefreshMore && !mIsRefreshing) {
                            mIsRefreshing = true;
                            setTopSweepAngle(0);
                            mIsTopDrag = false;
                            mShowTopLoadingText = true;
                            invalidate();
                            if (mPullListener != null) {
                                mPullListener.onLoadMoreFromTop(++mPullCounter);
                            }
                            animateListToTop(mChildView.getTop(), mTotalDragDistance);
                        } else {
                            animateListToTop(mChildView.getTop(), 0);
                            animateToResetTopSweep();
                        }
                    }

                    if (mDirection == ScrollingDirection.BOTTOM) {
                        if (mUserWantsToLoadMore && !mIsLoadMore) {
                            mIsLoadMore = true;
                            setBottomSweepAngle(0);
                            mIsBottomDrag = false;
                            mShowBottomLoadingText = true;
                            invalidate();

                            int height = getMeasuredHeight();
                            int start = height - (height - mChildView.getBottom());
                            int end = height - mTotalDragDistance;
                            animateListToBottom(start, end);

                            if (mPullListener != null) {
                                mPullListener.onLoadMoreFromBottom(++mLoadCounter);
                            }
                        } else {
                            int height = getMeasuredHeight();
                            int start = height - (height - mChildView.getBottom());
                            animateListToBottom(start, height);
                            animateToResetBottomSweep();
                        }
                    }
                }
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mIsTopDrag) {
            canvas.drawArc(mTopPullProgressRectF, -90, mTopSweepAngle, false, mPaint);
        }

        if (mShowTopLoadingText) {
            int yPos = mTotalDragDistance / 2;
            canvas.drawText(LABEL_TOP_LOAD, canvas.getWidth() / 2, yPos, mTxtPaint);
        }

        if (mShowBottomLoadingText) {
            int yPos = mTotalDragDistance / 2;
            canvas.drawText(LABEL_LOAD_BOTTOM, canvas.getWidth() / 2, getHeight() - yPos, mTxtPaint);
        }

        if (mIsBottomDrag) {
            canvas.drawArc(mBottomPullProgressRectF, -90, mBottomSweepAngle, false, mPaint);
        }
    }

    private void animateToResetBottomSweep() {
        ValueAnimator valueAnimator = ValueAnimator.ofInt((int) mBottomSweepAngle, 0);
        valueAnimator.setDuration(150);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int t = (Integer) valueAnimator.getAnimatedValue();
                setBottomSweepAngle(t);
            }
        });
        valueAnimator.start();
    }

    private void animateListToTop(int start, int end) {
        ValueAnimator valueAnimator = ValueAnimator.ofInt(start, end);
        valueAnimator.setDuration(150);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int t = (Integer) valueAnimator.getAnimatedValue();

                int height = getMeasuredHeight();
                int width = getMeasuredWidth();
                int left = getPaddingLeft();
                int right = getPaddingRight();

                mChildView.layout(left, t, left + width - right, height);
            }
        });
        valueAnimator.start();
    }

    private void animateListToBottom(int start, int end) {
        ValueAnimator valueAnimator = ValueAnimator.ofInt(start, end);
        valueAnimator.setDuration(150);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int t = (Integer) valueAnimator.getAnimatedValue();

                int width = getMeasuredWidth();
                int left = getPaddingLeft();
                int top = getPaddingTop();
                int right = getPaddingRight();

                mChildView.layout(left, top, left + width - right, t);
            }
        });
        valueAnimator.start();
    }

    private void animateToResetTopSweep() {
        ValueAnimator valueAnimator = ValueAnimator.ofInt((int) mTopSweepAngle, 0);
        valueAnimator.setDuration(150);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int t = (Integer) valueAnimator.getAnimatedValue();
                setTopSweepAngle(t);
            }
        });
        valueAnimator.start();
    }

    public void setTopSweepAngle(float topSweepAngle) {
        mTopSweepAngle = topSweepAngle;
        invalidate();
    }

    public void setBottomSweepAngle(float bottomSweepAngle) {
        mBottomSweepAngle = bottomSweepAngle;
        invalidate();
    }


    public void stopLoadingFromTop() {
        mShowTopLoadingText = false;
        invalidate();
        animateListToTop(mChildView.getTop(), 0);
        mIsRefreshing = false;
        mIsTopDrag = false;
    }

    public void stopLoadingFromBottom() {
        mShowBottomLoadingText = false;
        int height = getMeasuredHeight();
        int start = height - mTotalDragDistance;
        animateListToBottom(start, height);
        mIsLoadMore = false;
        mIsBottomDrag = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        int centerX = getWidth() / 2;
        int size = toPixel(LOADING_SPINNER_SIZE);

        int left = centerX - (size / 2);
        int top = toPixel(LOADING_SPINNER_MARGIN);
        int right = left + size;
        int bottom = top + size;
        mTopPullProgressRectF = new RectF(left, top, right, bottom);

        int bottomTop = getHeight() - (top + size);
        int bottomBottom = getHeight() - top;
        mBottomPullProgressRectF = new RectF(left, bottomTop, right, bottomBottom);
    }

    private OnPullListener mPullListener;

    public void setPullListener(OnPullListener pullListener) {
        this.mPullListener = pullListener;
    }

    public void resetCounters() {
        mPullCounter = 0;
        mLoadCounter = 0;
    }

    public void decrement(int type) {
        switch (type) {
            case TYPE_PULL:
                --mPullCounter;
                break;
            case TYPE_LOAD:
                --mLoadCounter;
                break;
        }
    }

    public interface OnPullListener {

        void onLoadMoreFromTop(int pullCount);

        void onLoadMoreFromBottom(int loadCount);
    }

    private ScrollingDirection mDirection = ScrollingDirection.NONE;

    private enum ScrollingDirection {

        TOP, BOTTOM, NONE
    }
}