package org.telegram.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AnimatedBackground extends FrameLayout {
    AnimationEditorActivity.AnimationSettings.AnimationBgObject bgObject;
    SensorManager sensorManager;
    Sensor sensor;
    float x;
    float y;
    float z;
    int scrollOffset;

    Paint bgPaint = new Paint();
    Drawable drawable;
    ValueAnimator animatorOffset;
    ValueAnimator animatorTilt;

    static Handler handler;
    static Bitmap bgBitmap;

    float scale = 1.5f;

    float top;
    float bottom;
    float tilt_x;
    float tilt_y;

    public AnimatedBackground(Context context, AnimationEditorActivity.AnimationSettings.AnimationBgObject bgObject) {
        super(context);
        this.bgObject = bgObject;

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        setWillNotDraw(false);

        animateOpen();
    }

    public void update(AnimationEditorActivity.AnimationSettings.AnimationBgObject bg) {
        this.bgObject = bg;
        redrawBg();
    }

    @Override
    public void invalidate() {
        bgPaint.setColor(bgObject.color2);

        top = scrollOffset + tilt_y;
        if (Math.abs(scrollOffset) > getHeight() && getHeight() != 0) {
            top = scrollOffset - (scrollOffset / getHeight()) * getHeight();
        }
        if (top > 0) {
            top = -top;
        }
        bottom = getHeight() + top;

        super.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bgObject.color2);

        if (bgBitmap != null) {
            canvas.scale(scale, scale, bgBitmap.getWidth() / 2f, bgBitmap.getHeight() / 2f);
            canvas.drawBitmap(bgBitmap, tilt_x, top, bgPaint);
            canvas.drawBitmap(bgBitmap, tilt_x, bottom, bgPaint);
        } else {
            canvas.drawColor(bgObject.color2);
        }
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        createBgBitmap();
    }

    public void createBgBitmap(){
         createBgBitmap(getMeasuredWidth() + 1,getMeasuredHeight() + 1);
    }

    public void createBgBitmap(int w, int h) {

        if (bgBitmap != null) {
            return;
        }

        final View self = this;

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                self.invalidate();
            }
        };

        new Thread(() -> {
            Paint drawablePaint = new Paint();
            drawablePaint.setMaskFilter(new BlurMaskFilter(150, BlurMaskFilter.Blur.NORMAL));
            Rect b = new Rect(0, 0, w, h);
            if (b.isEmpty()) {
                b = new Rect(0, 0, 720, 1280);
            }

            final Rect bounds = b;

            float dx = 0;
            float radius = bounds.width() / 3f;
            float dy = (bounds.height() - bounds.width()) / 3f;

            drawable = new Drawable() {
                @Override
                public void draw(@NonNull Canvas canvas) {
                    canvas.clipRect(bounds);

                    drawablePaint.setColor(bgObject.color1);
                    canvas.drawCircle(dx + radius, radius + dy, radius, drawablePaint);

                    drawablePaint.setColor(bgObject.color4);
                    canvas.drawCircle(radius + dx, bounds.bottom - radius - dy, radius, drawablePaint);

                    drawablePaint.setColor(bgObject.color2);
                    canvas.drawCircle(bounds.right - radius - dx, radius + dy, radius, drawablePaint);

                    drawablePaint.setColor(bgObject.color3);
                    canvas.drawCircle(bounds.right - radius - dx, bounds.bottom - radius - dy, radius, drawablePaint);
                }

                @Override
                public void setAlpha(int i) {
                }

                @Override
                public void setColorFilter(@Nullable ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return 0;
                }
            };

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);
            bgBitmap = bitmap;
            ;
            handler.sendEmptyMessage(0);
        }).start();
    }

    public void setScroll(int scroll) {
        if (scroll == 0) {
            return;
        }
        scrollOffset += scroll;
        invalidate();
    }

    public void emulateOpen(){
        ValueAnimator openAnim = ValueAnimator.ofFloat(-150, 0);
        openAnim.addUpdateListener(v -> {
            tilt_x = (float) v.getAnimatedValue();
            invalidate();
        });

        openAnim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                tilt_x = 0;
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        openAnim.setDuration(1000);
        openAnim.start();
    }

    void animateOpen() {
        final float offset = 50;
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent e) {
                if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    x = e.values[0];
                    y = e.values[1];
                    z = e.values[2];
                    tilt_x = offset * x;
                    tilt_y = offset*y;
                    invalidate();
                    if (bgBitmap != null && (animatorTilt == null || !animatorTilt.isRunning())) {
                        //  left =  50*x;
//                        float targetLeft = (bgBitmap.getWidth()*scale -bgBitmap.getWidth())*x/2f;//(getWidth()/3f)*x;
//                        animatorTilt = ValueAnimator.ofFloat(left,targetLeft);
//                        animatorTilt.addUpdateListener(v->{
//                            left = (float) v.getAnimatedValue();
//                            prepareInvalidate();
//                        });
//                        animatorTilt.setDuration(50);
//                        animatorTilt.start();
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };

        ValueAnimator openAnim = ValueAnimator.ofFloat(-200, 0);
        openAnim.addUpdateListener(v -> {
            tilt_x = (float) v.getAnimatedValue();
            invalidate();
        });

        openAnim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                tilt_x = 0;
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        openAnim.setDuration(1000);
        openAnim.start();
    }

    public void setScrollAnimate(int scroll, long duration) {
        if (scroll == 0) {
            return;
        }

        if (animatorOffset != null) {
            animatorOffset.cancel();
        }

        final int[] last = {0};
        animatorOffset = ValueAnimator.ofInt(0, scroll);
        animatorOffset.addUpdateListener(v -> {
            scrollOffset += (int) v.getAnimatedValue() - last[0];
            last[0] = (int) v.getAnimatedValue();
            invalidate();
        });
        animatorOffset.setDuration(duration);
        animatorOffset.start();
    }

    public void redrawBg() {
        bgBitmap = null;
        createBgBitmap();
    }

    public void redrawBg(int w, int h) {
        bgBitmap = null;
        createBgBitmap(w,h);
    }

    public void clear() {
        bgBitmap = null;
    }
}
