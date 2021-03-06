package ru.sawim.ui.widget.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.util.List;

import ru.sawim.Options;
import ru.sawim.R;
import ru.sawim.SawimApplication;
import ru.sawim.SawimResources;
import ru.sawim.Scheme;
import ru.sawim.comm.JLocale;
import ru.sawim.text.InternalURLSpan;
import ru.sawim.text.TextLinkClickListener;
import ru.sawim.ui.widget.Util;

/**
 * Created with IntelliJ IDEA.
 * User: Gerc
 * Date: 25.08.13
 * Time: 20:37
 * To change this template use File | Settings | File Templates.
 */
public class MessageItemView extends View {

    private static final TextPaint messageTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    static Paint highlightPaint;

    private static final int AVATAR_WIDTH = Options.getBoolean(JLocale.getString(R.string.pref_users_avatars)) ? Util.dipToPixels(SawimApplication.getContext(), 56) : 0;
    public static final int PADDING_LEFT = Util.dipToPixels(SawimApplication.getContext(), 9);
    public static final int PADDING_TOP = Util.dipToPixels(SawimApplication.getContext(), 12);
    public static final int PADDING_RIGHT = Util.dipToPixels(SawimApplication.getContext(), 20);
    public static final int PADDING_BOTTOM = Util.dipToPixels(SawimApplication.getContext(), 12);

    public static final int BACKGROUND_CORNER = Util.dipToPixels(SawimApplication.getContext(), 7);

    public static final int BACKGROUND_NONE = 0;
    public static final int BACKGROUND_INCOMING = 1;
    public static final int BACKGROUND_OUTCOMING = 2;

    private int backgroundIndex = BACKGROUND_NONE;
    private String msgTimeText;
    private String nickText;
    private int nickColor;
    private int msgTimeColor;
    private int msgTextColor;
    private Typeface nickTypeface;
    private Typeface msgTimeTypeface;
    private Typeface msgTextTypeface;
    private int nickSize;
    private int msgTimeSize;
    private int msgTextSize;
    private Bitmap checkImage;
    private Drawable image;
    private Bitmap avatarBitmap;

    @Nullable
    private Layout layout;
    private boolean wasLayout;
    private TextLinkClickListener textLinkClickListener;
    private boolean isSecondTap;
    private boolean isLongTap;
    private boolean isShowDivider = false;
    private int titleHeight;
    private int textHeight;
    private int textY;
    boolean isUrl;

    public MessageItemView(Context context) {
        super(context);
        textPaint.setAntiAlias(true);

        if (highlightPaint == null) {
            highlightPaint = new Paint();
            highlightPaint.setStyle(Paint.Style.FILL);
            highlightPaint.setAntiAlias(true);
        }
        highlightPaint.setColor(Scheme.isBlack() ? 0xFF4C4C4C : Color.WHITE);
        Util.setSelectableItemBackground(this);
    }

    public void setTextSize(int size) {
        textPaint.setTextSize(size * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setLayout(@Nullable Layout layout) {
        this.layout = layout;
        wasLayout = false;
    }

    public static Layout buildLayout(CharSequence parsedText, Typeface msgTextTypeface) {
        return makeLayout(parsedText, msgTextTypeface, getMessageWidth());
    }

    public static int getMessageWidth() {
        return SawimApplication.getContext().getResources().getDisplayMetrics().widthPixels - PADDING_LEFT * 2 - PADDING_RIGHT - AVATAR_WIDTH;
    }

    public static Layout makeLayout(CharSequence parsedText, Typeface msgTextTypeface, int width) {
        if (width <= 0) return null;
        DisplayMetrics displayMetrics = SawimApplication.getContext().getResources().getDisplayMetrics();
        messageTextPaint.setAntiAlias(true);
        messageTextPaint.linkColor = Scheme.getColor(R.attr.link);
        messageTextPaint.setTextAlign(Paint.Align.LEFT);
        messageTextPaint.setTextSize(SawimApplication.getFontSize() * displayMetrics.scaledDensity);
        messageTextPaint.setTypeface(msgTextTypeface);
        try {
            return new StaticLayout(parsedText, messageTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0F, 0.0F, false);
        } catch (ArrayIndexOutOfBoundsException e) {
            return new StaticLayout(parsedText.toString(), messageTextPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0F, 0.0F, false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean isAddTitleView = nickText != null;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = isAddTitleView ? measureHeight(heightMeasureSpec) : getPaddingTop() + getPaddingBottom();
        titleHeight = isAddTitleView ? height - getPaddingTop() : getPaddingTop();

        if (isUrl) {
            height += getMessageWidth();
        }
        if (layout != null) {
            textHeight = layout.getLineTop(layout.getLineCount());
            height += textHeight;
        }
        setMeasuredDimension(width, height);
    }

    private int measureHeight(int measureSpec) {
        int result = 0;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        int ascent = (int) messageTextPaint.ascent();
        int descent = (int) messageTextPaint.descent();
        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize;
        } else {
            int textHeight = (-ascent + descent) + getPaddingTop() + getPaddingBottom();
            int iconHeight = checkImage == null ? 0 : checkImage.getHeight();
            result = Math.max(textHeight, iconHeight);
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        computeCoordinates(right - left, bottom - top);

        int layoutWidth = getMeasuredWidth() - getPaddingRight() - getPaddingLeft() * 2 - AVATAR_WIDTH;
        if (layout != null && layout.getWidth() != layoutWidth) {
            layout = makeLayout(layout.getText(), msgTextTypeface, layoutWidth);
        }

        wasLayout = true;
    }

    private void computeCoordinates(int viewWidth, int viewHeight) {
        textY = getPaddingTop() - (int) messageTextPaint.ascent();
    }

    public void setAvatarBitmap(Bitmap avatarBitmap) {
        this.avatarBitmap = avatarBitmap;
        repaint();
        invalidate();
    }

    public void setBackgroundIndex(int index) {
        this.backgroundIndex = index;
    }

    public void setNick(int nickColor, int nickSize, Typeface nickTypeface, String nickText) {
        this.nickColor = nickColor;
        this.nickSize = nickSize;
        this.nickTypeface = nickTypeface;
        this.nickText = nickText;
    }

    public void setMsgTime(int msgTimeColor, int msgTimeSize, Typeface msgTimeTypeface, String msgTimeText) {
        this.msgTimeColor = msgTimeColor;
        this.msgTimeSize = msgTimeSize;
        this.msgTimeTypeface = msgTimeTypeface;
        this.msgTimeText = msgTimeText;
    }

    public void setCheckImage(Bitmap image) {
        checkImage = image;
    }

    public void setTextColor(int color) {
        msgTextColor = color;
    }

    public void setLinks(List<String> links) {
        String imageLink = getFirstImageLink(links);
        if (layout != null) {
            isUrl = ru.sawim.comm.Util.isUrlContains(layout.getText().toString());
        } else {
            isUrl = links != null && !links.isEmpty();
        }
        image = null;

        SimpleTarget<Bitmap> target = new SimpleTarget<Bitmap>(getMessageWidth(), getMessageWidth()) {

            @Override
            public void onResourceReady(Bitmap bitmap, GlideAnimation<? super Bitmap> glideAnimation) {
                image = new BitmapDrawable(getResources(), bitmap);
                repaint();
                invalidate();
            }
        };
        Glide.clear(target);
        if (imageLink == null) {
            return;
        }
        Glide.with(getContext())
                .load(imageLink)
                .asBitmap()
                .centerCrop()
                .dontTransform()
                .dontAnimate()
                .placeholder(new ColorDrawable(ContextCompat.getColor(getContext(), R.color.accent)))
                .error(new ColorDrawable(ContextCompat.getColor(getContext(), R.color.accent)))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(target);
    }

    public void setLinkTextColor(int color) {
        textPaint.linkColor = color;
    }

    public void setTypeface(Typeface typeface) {
        msgTextTypeface = typeface;
    }

    public void setMsgTextSize(int size) {
        msgTextSize = size;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void repaint() {
        requestLayout();
        //invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!wasLayout) {
            requestLayout();
            return;
        }
        int width = getWidth();
        int stopX = width - getPaddingRight();
        if (isShowDivider) {
            textPaint.setColor(Scheme.getColor(R.attr.text));
            canvas.drawLine(getPaddingLeft(), getScrollY() - 2, stopX, getScrollY() - 2, textPaint);
        }
        boolean incoming = backgroundIndex == BACKGROUND_INCOMING;
        if (avatarBitmap != null) {
            canvas.drawBitmap(avatarBitmap, (incoming ? getPaddingLeft() : stopX - avatarBitmap.getWidth()), getHeight() - avatarBitmap.getHeight(), null);
        }
        if (incoming) {
            setDrawableBounds(SawimResources.backgroundDrawableIn, AVATAR_WIDTH, getPaddingTop() / 2 + titleHeight - titleHeight / 5, width - getPaddingRight() / 2 - AVATAR_WIDTH, getHeight() - getPaddingBottom() / 2 - titleHeight + titleHeight / 5);
            SawimResources.backgroundDrawableIn.draw(canvas);
            //canvas.drawRoundRect(new RectF(getPaddingLeft() + AVATAR_WIDTH, getPaddingTop(), width - getPaddingRight() / 2, getHeight()), BACKGROUND_CORNER, BACKGROUND_CORNER, highlightPaint);
        } else {
            setDrawableBounds(SawimResources.backgroundDrawableOut, getPaddingLeft() / 2, getPaddingTop() / 2 + titleHeight - titleHeight / 5, width - getPaddingRight() - AVATAR_WIDTH, getHeight() - getPaddingBottom() / 2 - titleHeight + titleHeight / 5);
            SawimResources.backgroundDrawableOut.draw(canvas);
            //canvas.drawRoundRect(new RectF((float) (getPaddingLeft() * 1.5), getPaddingTop(), width - getPaddingRight() / 2- AVATAR_WIDTH, getHeight()), BACKGROUND_CORNER, BACKGROUND_CORNER, highlightPaint);
        }

        if (nickText != null) {
            textPaint.setColor(nickColor);
            textPaint.setTextAlign(Paint.Align.LEFT);
            setTextSize(nickSize);
            textPaint.setTypeface(nickTypeface);
            canvas.drawText(nickText, incoming ? AVATAR_WIDTH + getPaddingLeft() * 2 : getPaddingLeft(), textY - getPaddingTop() / 2, textPaint);
        }

        if (msgTimeText != null) {
            textPaint.setColor(msgTimeColor);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            setTextSize(msgTimeSize);
            textPaint.setTypeface(msgTimeTypeface);
            canvas.drawText(msgTimeText,
                    stopX - (checkImage == null ? 0 : checkImage.getWidth() << 1) - (incoming ? 0 : AVATAR_WIDTH + getPaddingRight()), textY, textPaint);
        }
        if (checkImage != null) {
            canvas.drawBitmap(checkImage,
                    stopX - checkImage.getWidth() - (incoming ? 0 : AVATAR_WIDTH), getPaddingTop() + checkImage.getHeight() / 2, null);
        }

        if (image != null) {
            int imageWidth = (int) Math.ceil(getMessageWidth() * (float) image.getIntrinsicWidth() / image.getIntrinsicHeight());
            setDrawableBounds(image,
                    (incoming ? AVATAR_WIDTH + getPaddingLeft() * 2 : getPaddingLeft())
                            + (imageWidth > getMessageWidth() ? 0 : getMessageWidth() / 2 - imageWidth / 2),
                    titleHeight + getPaddingTop() / 2,
                    imageWidth,
                    getMessageWidth());
            image.draw(canvas);
        }
        if (layout != null) {
            canvas.save();
            messageTextPaint.setColor(msgTextColor);
            messageTextPaint.setTextAlign(Paint.Align.LEFT);
            messageTextPaint.setTextSize(msgTextSize * getResources().getDisplayMetrics().scaledDensity);
            messageTextPaint.setTypeface(msgTextTypeface);
            int y = titleHeight + getPaddingTop() / 2;
            if (image != null) {
                y += getMessageWidth();
            }
            canvas.translate((incoming ? AVATAR_WIDTH + getPaddingLeft() * 2 : getPaddingLeft()), y);
            layout.draw(canvas);
            canvas.restore();
        }
    }

    public void setShowDivider(boolean showDivider) {
        isShowDivider = showDivider;
        textPaint.setStrokeWidth(Util.dipToPixels(getContext(), 5));
    }

    private void setDrawableBounds(Drawable drawable, int x, int y, int w, int h) {
        drawable.setBounds(x, y, x + w, y + h);
    }

    private int getLineForVertical(int vertical) {
        int high = layout.getLineCount(), low = -1, guess;
        while (high - low > 1) {
            guess = (high + low) / 2;
            if (layout.getLineTop(guess) > vertical)
                high = guess;
            else
                low = guess;
        }
        return low;
    }

    private static final BackgroundColorSpan linkHighlightColor = new BackgroundColorSpan(Scheme.getColor(R.attr.link_highlight));
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (layout == null || layout.getText() == null) return super.onTouchEvent(event);
        if (layout.getText() instanceof Spannable) {
            final Spannable buffer = (Spannable) layout.getText();
            int action = event.getAction();
            int x = (int) event.getX();
            int y = (int) event.getY();
            x += getScrollX();
            y += getScrollY() - titleHeight;
            int line = getLineForVertical(y);
            if (line < 0) return super.onTouchEvent(event);

            int off = layout.getOffsetForHorizontal(line, x);
            final InternalURLSpan[] urlSpans = buffer.getSpans(off, off, InternalURLSpan.class);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE) {
                isSecondTap = true;
            }
            if (urlSpans.length != 0) {
                Runnable longPressed = new Runnable() {
                    public void run() {
                        if (textLinkClickListener != null && !isSecondTap && !isLongTap) {
                            if (getParent() != null) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            isLongTap = true;
                            textLinkClickListener.onTextLinkClick(MessageItemView.this, buildUrl(urlSpans), true);
                        }
                    }
                };
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL) {
                    buffer.removeSpan(linkHighlightColor);
                    removeCallbacks(longPressed);
                    repaint();
                }
                if (action == MotionEvent.ACTION_DOWN) {
                    isSecondTap = false;
                    isLongTap = false;
                    buffer.setSpan(linkHighlightColor,
                            buffer.getSpanStart(urlSpans[0]),
                            buffer.getSpanEnd(urlSpans[0]), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    repaint();
                    removeCallbacks(longPressed);
                    postDelayed(longPressed, ViewConfiguration.getLongPressTimeout());
                }
                if (action == MotionEvent.ACTION_UP) {
                    if (!isLongTap) {
                        isSecondTap = true;
                        if (textLinkClickListener != null)
                            textLinkClickListener.onTextLinkClick(MessageItemView.this, buildUrl(urlSpans), false);
                    } else {
                        removeCallbacks(longPressed);
                    }
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private String buildUrl(InternalURLSpan[] urlSpans) {
        String link = urlSpans[0].clickedSpan;
        if (urlSpans.length == 2
                && urlSpans[1].clickedSpan.length() > urlSpans[0].clickedSpan.length()) {
            link = urlSpans[1].clickedSpan;
        }
        return link;
    }

    private String getFirstImageLink(List<String> links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        for (String link : links) {
            //if (ru.sawim.comm.Util.isImageFile(link)) {
                return link;
            //}
        }
        return links.get(0);
    }

    public void setOnTextLinkClickListener(TextLinkClickListener listener) {
        textLinkClickListener = listener;
    }
}
