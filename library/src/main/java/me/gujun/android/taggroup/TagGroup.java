package me.gujun.android.taggroup;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.method.ArrowKeyMovementMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

/**
 * This class is used to create a group for a set of tags. The tag group has two modes:
 * <p/>
 * 1. APPEND mode<br/>
 * 2. DISPLAY mode
 * <p/>
 * Default is DISPLAY mode. When in APPEND mode, the group is capable of input and append new tags,
 * it always shows a INPUT state tag view at the last position of group. When you finish input,
 * click the blank region of the tag group to end the INPUT state.Click the NORMAL state tag,
 * will check the tag, next click to the checked tag will delete the tag.
 * <p/>
 * When in DISPLAY mode, the group is only for display NORMAL state tags, and all the tags in group
 * is not focusable.
 *
 * @author Jun Gu (http://2dxgujun.com)
 * @version 1.0
 * @since 2015-2-3 14:16:32
 */
public class TagGroup extends ViewGroup {
    private final int default_bright_color = Color.rgb(0x49, 0xC1, 0x20);
    private final int default_dim_color = Color.rgb(0xAA, 0xAA, 0xAA);
    private final float default_border_width;
    private final float default_text_size;
    private final float default_horizontal_spacing;
    private final float default_vertical_spacing;
    private final float default_horizontal_padding;
    private final float default_vertical_padding;

    /**
     * Indicates whether this TagGroup is set up to APPEND mode or DISPLAY mode.
     */
    private boolean isAppendMode;

    /**
     * The tag outline border and text bright color.
     */
    private int mBrightColor;

    /**
     * The tag outline border and text dim color.
     */
    private int mDimColor;

    /**
     * The tag outline border stroke width, default is 0.5dp.
     */
    private float mBorderWidth;

    /**
     * The tag text size, default is 13sp.
     */
    private float mTextSize;

    /**
     * The horizontal tag spacing, default is 8.0dp.
     */
    private int mHorizontalSpacing;

    /**
     * The vertical tag spacing, default is 4.0dp.
     */
    private int mVerticalSpacing;

    /**
     * The horizontal tag padding, default is 12.0dp.
     */
    private int mHorizontalPadding;

    /**
     * The vertical tag padding, default is 3.0dp.
     */
    private int mVerticalPadding;

    /**
     * Listener used to dispatch tag change events.
     */
    private OnTagChangeListener mOnTagChangeListener;

    public TagGroup(Context context) {
        this(context, null);
    }

    public TagGroup(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.tagGroupStyle);
    }

    public TagGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        default_border_width = dp2px(0.5f);
        default_text_size = sp2px(13.0f);
        default_horizontal_spacing = dp2px(8.0f);
        default_vertical_spacing = dp2px(4.0f);
        default_horizontal_padding = dp2px(12.0f);
        default_vertical_padding = dp2px(3.0f);

        // Load styled attributes.
        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.TagGroup, defStyleAttr, 0);
        try {
            isAppendMode = a.getBoolean(R.styleable.TagGroup_isAppendMode, false);
            mBrightColor = a.getColor(R.styleable.TagGroup_brightColor, default_bright_color);
            mDimColor = a.getColor(R.styleable.TagGroup_dimColor, default_dim_color);
            mBorderWidth = a.getDimension(R.styleable.TagGroup_borderWidth, default_border_width);
            mTextSize = a.getDimension(R.styleable.TagGroup_textSize, default_text_size);
            mHorizontalSpacing = (int) a.getDimension(R.styleable.TagGroup_horizontalSpacing,
                    default_horizontal_spacing);
            mVerticalSpacing = (int) a.getDimension(R.styleable.TagGroup_verticalSpacing,
                    default_vertical_spacing);
            mHorizontalPadding = (int) a.getDimension(R.styleable.TagGroup_horizontalPadding,
                    default_horizontal_padding);
            mVerticalPadding = (int) a.getDimension(R.styleable.TagGroup_verticalPadding,
                    default_vertical_padding);
        } finally {
            a.recycle();
        }

        setUpTagGroup();
    }

    protected void setUpTagGroup() {
        // If the tag group created in APPEND mode.
        if (isAppendMode) {
            // Append the initial INPUT state tag.
            appendInputTag();

            // Set the TagGroup click listener to handle the end-input click.
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    TagView lastTag = getInputTag();
                    if (lastTag != null && lastTag.getState() == TagView.STATE_INPUT
                            && lastTag.isInputAvailable()) {
                        lastTag.endInput();
                        // Dispatch the tags changed event.
                        if (mOnTagChangeListener != null) {
                            mOnTagChangeListener.onAppend(lastTag.getText().toString());
                        }
                        appendInputTag(); // Append a new INPUT state tag.
                    }
                }
            });
        }
    }

    public void setBrightColor(int brightColor) {
        mBrightColor = brightColor;
        invalidateAllTagsPaint();
        invalidate();
    }

    public int getBrightColor() {
        return mBrightColor;
    }

    protected void invalidateAllTagsPaint() {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            TagView tagView = (TagView) getChildAt(i);
            tagView.invalidatePaint();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int width = 0;
        int height = 0;

        int row = 0; // The row counter.
        int rowWidth = 0; // Calc the current row width.
        int rowMaxHeight = 0; // Calc the max tag height, in current row.

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            if (child.getVisibility() != GONE) {
                rowWidth += childWidth;
                if (rowWidth > widthSize) { // Next line.
                    rowWidth = childWidth; // The next row width.
                    height += rowMaxHeight + mVerticalSpacing;
                    rowMaxHeight = childHeight; // The next row max height.
                    row++;
                } else { // This line.
                    rowMaxHeight = Math.max(rowMaxHeight, childHeight);
                }
                rowWidth += mHorizontalSpacing;
            }
        }
        // Account for the last row height.
        height += rowMaxHeight;

        // Account for the padding too.
        height += getPaddingTop() + getPaddingBottom();

        // If the tags grouped in one row, set the width to wrap the tags.
        if (row == 0) {
            width = rowWidth;
            width += getPaddingLeft() + getPaddingRight();
        } else {// If the tags grouped exceed one line, set the width to match the parent.
            width = widthSize;
        }

        setMeasuredDimension(widthMode == MeasureSpec.EXACTLY ? widthSize : width,
                heightMode == MeasureSpec.EXACTLY ? heightSize : height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int parentLeft = getPaddingLeft();
        final int parentRight = r - l - getPaddingRight();
        final int parentTop = getPaddingTop();
        final int parentBottom = b - t - getPaddingBottom();

        int childLeft = parentLeft;
        int childTop = parentTop;

        int rowMaxHeight = 0;

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            final int width = child.getMeasuredWidth();
            final int height = child.getMeasuredHeight();

            if (child.getVisibility() != GONE) {
                if (childLeft + width > parentRight) { // Next line
                    childLeft = parentLeft;
                    childTop += rowMaxHeight + mVerticalSpacing;
                    rowMaxHeight = height;
                } else {
                    rowMaxHeight = Math.max(rowMaxHeight, height);
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);

                childLeft += width + mHorizontalSpacing;
            }
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    /**
     * Return the INPUT state tag in this group.
     *
     * @return The INPUT state tag or null if none.
     */
    protected TagView getInputTag() {
        if (isAppendMode) {
            final int inputTagIndex = getChildCount() - 1;
            TagView inputTag = (TagView) getChildAt(inputTagIndex);
            return inputTag;
        } else {
            return null;
        }
    }

    /**
     * Return the last NORMAL state tag in this group.
     *
     * @return The last NORMAL state tag or null if none.
     */
    protected TagView getLastNormalTag() {
        final int lastNormalTagIndex = isAppendMode ? getChildCount() - 2 : getChildCount() - 1;
        TagView lastNormalTag = (TagView) getChildAt(lastNormalTagIndex);
        return lastNormalTag;
    }

    protected int getCheckedTagPosition() {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final TagView tagView = (TagView) getChildAt(i);
            if (tagView.isChecked) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Register a callback to be invoked when this tag is changed.
     *
     * @param l The callback that will run.
     */
    public void setOnTagChangeListener(OnTagChangeListener l) {
        mOnTagChangeListener = l;
    }


    /**
     * @see {@link #appendInputTag(String)}
     */
    protected void appendInputTag() {
        appendInputTag(null);
    }

    /**
     * Append a INPUT state tag to this group. It will check the group state first.
     *
     * @param tag The tag text.
     */
    protected void appendInputTag(String tag) {
        TagView lastTag = getInputTag();
        if (lastTag != null && lastTag.getState() == TagView.STATE_INPUT) {
            throw new IllegalStateException("Already has a INPUT state tag in group. " +
                    "You must call endInput() before you append new one.");
        }

        TagView tagView = new TagView(getContext(), TagView.STATE_INPUT, tag);
        tagView.setOnClickListener(new OnTagClickListener());
        addView(tagView);
    }

    /**
     * Set the NORMAL state tag to this group. If the group is in APPEND mode, it will remove
     * all tags except the last INPUT state tag and append the new tags at the beginning of the
     * group.
     * <p/>
     * If the group is in DISPLAY mode, it will remove all tags and append the new tags.
     *
     * @param tagList The tag list to set.
     */
    public void setTags(List<String> tagList) {
        if (isAppendMode) {
            int appendIndex = getChildCount() > 0 ? getChildCount() - 1 : 0;
            for (final String tag : tagList) {
                TagView tagView = new TagView(getContext(), TagView.STATE_NORMAL, tag);
                tagView.setOnClickListener(new OnTagClickListener());
                addView(tagView, appendIndex++);
            }
        } else {
            removeAllViews();
            for (String tag : tagList) {
                TagView tagView = new TagView(getContext(), TagView.STATE_NORMAL, tag);
                addView(tagView);
            }
        }
    }

    public void setTags(String[] tags) {
        List<String> tagList = Arrays.asList(tags);
        setTags(tagList);
    }

    public float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    public float sp2px(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new TagGroup.LayoutParams(getContext(), attrs);
    }

    /**
     * Interface definition for a callback to be invoked when a tag is changed.
     */
    public interface OnTagChangeListener {
        /**
         * Called when a tag has been appended.
         *
         * @param tag The appended tag.
         */
        void onAppend(String tag);

        /**
         * Called when a tag has been deleted.
         *
         * @param tag The deleted tag.
         */
        void onDelete(String tag);
    }

    /**
     * Per-child layout information for layouts.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    /**
     * The tag click listener implementation.
     */
    class OnTagClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            TagView tagView = (TagView) v;
            if (tagView.mState == TagView.STATE_INPUT) {
                // If the clicked tag is in INPUT state, uncheck the previous checked
                // tag if exists.
                final int count = getChildCount();
                for (int i = 0; i < count; i++) {
                    TagView tagV = (TagView) getChildAt(i);
                    if (tagV.isChecked) {
                        tagV.setChecked(false);
                        break;
                    }
                }
            } else {
                // If the clicked tag is checked, remove the tag
                // and dispatch the tags changed event.
                if (tagView.isChecked) {
                    removeView(tagView);
                    if (mOnTagChangeListener != null) {
                        mOnTagChangeListener.onDelete(tagView.getText().toString());
                    }
                } else {
                    // If the clicked tag is unchecked, uncheck the previous checked
                    // tag if exists, then set the clicked tag checked.
                    final int count = getChildCount();
                    for (int i = 0; i < count; i++) {
                        TagView tagV = (TagView) getChildAt(i);
                        if (tagV.isChecked) {
                            tagV.setChecked(false);
                            break;
                        }
                    }
                    tagView.setChecked(true);
                }
            }
        }
    }

    /**
     * A tag view is a two-states tag that can be either NORMAL or INPUT.
     * <p/>
     * When the tag is NORMAL, the user can't press or click it to check it. When the tag is INPUT,
     * it may take the input focus.
     */
    class TagView extends TextView {
        public static final int STATE_NORMAL = 1;
        public static final int STATE_INPUT = 2;

        /**
         * The tag state.
         */
        private int mState;

        /**
         * Indicates the tag if checked.
         */
        private boolean isChecked = false;

        /**
         * The paint of tag outline border and text.
         */
        private Paint mPaint;

        /**
         * The rect for the tag's left corner drawing.
         */
        private RectF mLeftCornerRectF;

        /**
         * The rect for the tag's right corner drawing.
         */
        private RectF mRightCornerRectF;

        /**
         * The rect for the tag's horizontal blank fill area.
         */
        private RectF mHorizontalBlankFillRectF;

        /**
         * The rect for the tag's vertical blank fill area.
         */
        private RectF mVerticalBlankFillRectF;

        /**
         * The path for draw the tag's outline border.
         */
        private Path mBorderPath;

        /**
         * The path effect provide draw the dash border.
         */
        private PathEffect mPathEffect;

        public TagView(Context context, int state, String text) {
            super(context);
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            mLeftCornerRectF = new RectF();
            mRightCornerRectF = new RectF();

            mHorizontalBlankFillRectF = new RectF();
            mVerticalBlankFillRectF = new RectF();

            mBorderPath = new Path();
            mPathEffect = new DashPathEffect(new float[]{10, 5}, 0);

            setPadding(mHorizontalPadding, mVerticalPadding, mHorizontalPadding, mVerticalPadding);
            setLayoutParams(new TagGroup.LayoutParams(TagGroup.LayoutParams.WRAP_CONTENT,
                    TagGroup.LayoutParams.WRAP_CONTENT));

            setGravity(Gravity.CENTER);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            setClickable(true);

            mState = state;

            if (state == STATE_INPUT) {
                setHint("添加标签");
                setFocusable(true);
                setFocusableInTouchMode(true);
                requestFocus();
                setMovementMethod(ArrowKeyMovementMethod.getInstance());
                // Set editor action listener for handle the Enter key down event.
                setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_DONE
                                || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                            if (isInputAvailable()) {
                                // If the input content is available, end the input and dispatch
                                // the event, then append a new INPUT state tag.
                                endInput();
                                if (mOnTagChangeListener != null) {
                                    mOnTagChangeListener.onAppend(getText().toString());
                                }
                                appendInputTag();
                            }
                            return true;
                        }
                        return false;
                    }
                });

                // Set key listener for handle the backspace key down event.
                setOnKeyListener(new OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                            // If the input content is empty, check or remove the last NORMAL state tag.
                            if (TextUtils.isEmpty(getText().toString())) {
                                TagView lastNormalTag = getLastNormalTag();
                                if (lastNormalTag != null) {
                                    if (lastNormalTag.isChecked) {
                                        removeView(lastNormalTag);
                                        if (mOnTagChangeListener != null) {
                                            mOnTagChangeListener.onDelete(lastNormalTag.getText().toString());
                                        }
                                    } else {
                                        final int count = getChildCount();
                                        for (int i = 0; i < count; i++) {
                                            TagView tagV = (TagView) getChildAt(i);
                                            if (tagV.isChecked) {
                                                tagV.setChecked(false);
                                                break;
                                            }
                                        }
                                        lastNormalTag.setChecked(true);
                                    }
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                });
            } else {
                setText(text);
            }

            invalidatePaint();
        }

        /**
         * Call this method to end this tag's INPUT state.
         */
        public void endInput() {
            // Make the view not focusable.
            setFocusable(false);
            setFocusableInTouchMode(false);
            // Set the hint empty, make the TextView measure correctly.
            setHint(null);
            // Take away the cursor.
            setMovementMethod(null);

            mState = STATE_NORMAL;
            invalidatePaint();
            requestLayout();
        }

        @Override
        protected boolean getDefaultEditable() {
            return true;
        }

        public int getState() {
            return mState;
        }

        /**
         * Indicates whether the input content is available.
         *
         * @return True if the input content is available, false otherwise.
         */
        public boolean isInputAvailable() {
            return getText() != null && getText().length() > 0;
        }

        private void invalidatePaint() {
            if (mState == STATE_NORMAL) {
                if (isChecked) {
                    mPaint.setStyle(Paint.Style.FILL);
                    mPaint.setColor(mBrightColor);
                    mPaint.setPathEffect(null);
                    setTextColor(Color.WHITE);
                } else {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(mBorderWidth);
                    mPaint.setColor(mBrightColor);
                    mPaint.setPathEffect(null);
                    setTextColor(mBrightColor);
                }

            } else if (mState == STATE_INPUT) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(mBorderWidth);
                mPaint.setColor(mDimColor);
                mPaint.setPathEffect(mPathEffect);
                setTextColor(mDimColor);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isChecked) {
                canvas.drawArc(mLeftCornerRectF, -180, 90, true, mPaint);
                canvas.drawArc(mLeftCornerRectF, -270, 90, true, mPaint);
                canvas.drawArc(mRightCornerRectF, -90, 90, true, mPaint);
                canvas.drawArc(mRightCornerRectF, 0, 90, true, mPaint);
                canvas.drawRect(mHorizontalBlankFillRectF, mPaint);
                canvas.drawRect(mVerticalBlankFillRectF, mPaint);
            } else {
                canvas.drawPath(mBorderPath, mPaint);
            }
            super.onDraw(canvas);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float left = mBorderWidth;
            float top = mBorderWidth;
            float right = left + w - mBorderWidth * 2;
            float bottom = top + h - mBorderWidth * 2;

            float d = bottom - top;

            mLeftCornerRectF.set(left, top, left + d, top + d);
            mRightCornerRectF.set(right - d, top, right, top + d);

            mBorderPath.reset();
            mBorderPath.addArc(mLeftCornerRectF, -180, 90);
            mBorderPath.addArc(mLeftCornerRectF, -270, 90);
            mBorderPath.addArc(mRightCornerRectF, -90, 90);
            mBorderPath.addArc(mRightCornerRectF, 0, 90);

            int l = (int) (d / 2.0f);
            mBorderPath.moveTo(left + l, top);
            mBorderPath.lineTo(right - l, top);

            mBorderPath.moveTo(left + l, bottom);
            mBorderPath.lineTo(right - l, bottom);

            mBorderPath.moveTo(left, top + l);
            mBorderPath.lineTo(left, bottom - l);

            mBorderPath.moveTo(right, top + l);
            mBorderPath.lineTo(right, bottom - l);

            mHorizontalBlankFillRectF.set(left, top + l, right, bottom - l);
            mVerticalBlankFillRectF.set(left + l, top, right - l, bottom);
        }

        public void setChecked(boolean checked) {
            isChecked = checked;
            invalidatePaint();
        }
    }

    static class SavedState extends BaseSavedState {
        int checkedTagPosition;
        String inputText;


        public SavedState(Parcel source) {
            super(source);
            checkedTagPosition = source.readInt();
            inputText = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(checkedTagPosition);
            dest.writeString(inputText);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}