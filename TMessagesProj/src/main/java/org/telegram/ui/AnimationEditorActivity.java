package org.telegram.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
//import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.WallpaperParallaxEffect;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AnimationEditorActivity extends BaseFragment {
    private final static int share_item = 0;
    private final static int import_item = 1;
    private final static int restore_item = 2;

    private final static int SAVE_FILE = 150;
    private final static int READ_FILE = 250;


    private ActionBarMenuItem headerItem;
    private ScrollSlidingTextTabStrip scrollSlidingTextTabStrip;
    private int selectedType = 0;
    private ViewGroup currentPage;
    private ViewGroup pages[];
    private Paint backgroundPaint = new Paint();
    private AnimationSettings animationSettings;
    Context _context;


    @Override
    public View createView(Context context) {
        this._context = context;

        animationSettings = AnimationEditorActivity.AnimationSettings.load(context);

        fragmentView = new LinearLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_avatar_backgroundBlue));
                canvas.drawRect(0, actionBar.getMeasuredHeight() + actionBar.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
        };

        fragmentView.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);


        setupActionBar(context);
        setupTabs(context);
        setupPages(context);
        switchPage(context, 0);

        return fragmentView;
    }



    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        super.onActivityResultFragment(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == SAVE_FILE) {
            Uri uri = data.getData();
            try {
                animationSettings.saveTo(_context, uri);
                // Toast.makeText(_context,"Saved!", Toast.LENGTH_LONG);
            } catch (Exception e) {
                Toast.makeText(_context, "Error", Toast.LENGTH_LONG);
            }
        } else if (requestCode == READ_FILE) {
            Uri uri = data.getData();
            animationSettings = AnimationSettings.loadFrom(_context, uri);
            if (pages != null && pages.length > 0) {
                if (pages[0] != null) {
                    ((BgPage) pages[0]).clearOnRestore();
                }
            }

            setupPages(_context);
            switchPage(_context, scrollSlidingTextTabStrip.getCurrentTabId());
            Toast.makeText(getParentActivity(), "Imported from settings.anim", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onBackPressed() {
        super.onBackPressed();
        animationSettings.save(_context);
        return true;
    }

    private void setupActionBar(Context context) {
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        actionBar.setTitle("Animation editor");
        actionBar.setAllowOverlayTitle(false);
        actionBar.setExtraHeight(AndroidUtilities.dp(44));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    animationSettings.save(context);
                    finishFragment();
                } else if (id == share_item) {
                    animationSettings.save(context);

                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/anim");
                    intent.putExtra(Intent.EXTRA_TITLE, "export_animations.anim");
                    startActivityForResult(intent, SAVE_FILE);
                } else if (id == import_item) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/anim");
                    startActivityForResult(intent, READ_FILE);

                } else if (id == restore_item) {
                    animationSettings = AnimationSettings.createDefault();
                    animationSettings.save(fragmentView.getContext());
                    if (pages != null && pages.length > 0) {
                        if (pages[0] != null) {
                            ((BgPage) pages[0]).clearOnRestore();
                        }
                    }

                    setupPages(context);
                    switchPage(context, scrollSlidingTextTabStrip.getCurrentTabId());
                    Toast.makeText(getParentActivity(), "Restored to default", Toast.LENGTH_LONG).show();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        headerItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        headerItem.addSubItem(share_item, "Share");
        headerItem.addSubItem(import_item, "Import");
        headerItem.addSubItem(restore_item, "Restore");

        ((LinearLayout) fragmentView).addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void setupTabs(Context context) {
        scrollSlidingTextTabStrip = new ScrollSlidingTextTabStrip(context);
        actionBar.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                44, Gravity.LEFT | Gravity.BOTTOM));

        scrollSlidingTextTabStrip.addTextTab(0, "Background");
        scrollSlidingTextTabStrip.addTextTab(1, "Text short");
        scrollSlidingTextTabStrip.addTextTab(2, "Text long");
        scrollSlidingTextTabStrip.addTextTab(3, "Emoji");
        scrollSlidingTextTabStrip.addTextTab(4, "Sticker");
        scrollSlidingTextTabStrip.addTextTab(5, "Photo");
        scrollSlidingTextTabStrip.addTextTab(6, "Audio");
        scrollSlidingTextTabStrip.addTextTab(7, "Video");
        scrollSlidingTextTabStrip.addTextTab(8, "Gif");

        scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int id, boolean forward) {
                if (selectedType == id) {
                    return;
                }
                switchPage(context, id);
            }

            @Override
            public void onPageScrolled(float progress) {
            }
        });
    }

    private void switchPage(Context context, int id) {
        selectedType = id;
        LinearLayout frameLayout = (LinearLayout) fragmentView;
        if (currentPage != null) {
            frameLayout.removeView(currentPage);
        }

        currentPage = pages[id];
        frameLayout.addView(currentPage, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void setupPages(Context context) {
        pages = new ViewGroup[9];
        pages[0] = setupBgPage(context);
        pages[1] = setupShortTextPage(context);
        pages[2] = setupLongTextPage(context);
        pages[3] = setupEmojiPage(context);
        pages[4] = setupStickerPage(context);
        pages[5] = setupPhotoPage(context);
        pages[6] = setupAudioPage(context);
        pages[7] = setupVideoPage(context);
        pages[8] = setupGifPage(context);
    }

    private AnimationPage setupShortTextPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.shortText);

        page.createAnimationPanel(context, "X Position", animationSettings.shortText.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.shortText.y);
        // page.createAnimationPanel(context, "Scale", animationSettings.shortText.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.shortText.opacity);
        return page;
    }

    private AnimationPage setupLongTextPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.longText);
        page.createAnimationPanel(context, "X Position", animationSettings.longText.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.longText.y);
        // page.createAnimationPanel(context, "Scale", animationSettings.longText.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.longText.opacity);
        return page;
    }

    private AnimationPage setupEmojiPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.emoji);

        page.createAnimationPanel(context, "X Position", animationSettings.emoji.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.emoji.y);
        page.createAnimationPanel(context, "Scale", animationSettings.emoji.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.emoji.opacity);
        return page;
    }

    private AnimationPage setupStickerPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.sticker);

        page.createAnimationPanel(context, "X Position", animationSettings.sticker.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.sticker.y);
        page.createAnimationPanel(context, "Scale", animationSettings.sticker.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.sticker.opacity);
        return page;
    }

    private AnimationPage setupGifPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.gif);

        page.createAnimationPanel(context, "X Position", animationSettings.gif.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.gif.y);
        page.createAnimationPanel(context, "Scale", animationSettings.gif.scale);
        return page;
    }



    private AnimationPage setupPhotoPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.photo);

     //   page.createAnimationPanel(context, "X Position", animationSettings.photo.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.photo.y);
      //  page.createAnimationPanel(context, "Scale", animationSettings.photo.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.photo.opacity);
        return page;
    }

    private AnimationPage setupAudioPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.audio);

        page.createAnimationPanel(context, "X Position", animationSettings.audio.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.audio.y);
        //  page.createAnimationPanel(context, "Scale", animationSettings.audio.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.audio.opacity);
        return page;
    }

    private AnimationPage setupVideoPage(Context context) {
        AnimationPage page = new AnimationPage(context, animationSettings.video);

        page.createAnimationPanel(context, "X Position", animationSettings.video.x);
        page.createAnimationPanel(context, "Y Position", animationSettings.video.y);
        // page.createAnimationPanel(context, "Scale", animationSettings.video.scale);
        page.createAnimationPanel(context, "Bg Opacity", animationSettings.video.opacity);
        return page;
    }

    private BgPage setupBgPage(Context context) {
        BgPage page = new BgPage(context, animationSettings.bg);

        return page;
    }

    private static class AnimationPage extends LinearLayout {
        private final AnimationSettings.AnimationObject animationObject;
        private LinearLayout panels;
        private List<AnimationPanel> pages;
        TextView durationTextView;
        ActionBarPopupWindow scrimPopupWindow;
        ScrollView scrollView;


        public AnimationPage(Context context, AnimationSettings.AnimationObject animationObject) {
            super(context);
            this.animationObject = animationObject;
            pages = new ArrayList<>();
            panels = new LinearLayout(context);
            panels.setOrientation(LinearLayout.VERTICAL);

            LayoutInflater inflater = (LayoutInflater) context.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View durationView = inflater.inflate(R.layout.animation_duration_panel, this, true);
            durationTextView = durationView.findViewById(R.id.textViewDuration);
            durationTextView.setText(animationObject.duration + " ms");
            durationView.setOnClickListener(e -> {
                if (scrimPopupWindow != null) {
                    if (scrimPopupWindow.isShowing()) {
                        scrimPopupWindow.dismiss();
                    } else {
                        showTimeSelect(context);
                    }
                } else {
                    showTimeSelect(context);
                }
            });


            scrollView = new ScrollView(context);
            scrollView.addView(panels, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    1, 8, 8, 8, 0));
            this.setOrientation(LinearLayout.VERTICAL);
            this.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private void showTimeSelect(Context context) {
            ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);

            Rect backgroundPaddings = new Rect();
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
            shadowDrawable.getPadding(backgroundPaddings);
            popupLayout.setBackgroundDrawable(shadowDrawable);
            popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

            LinearLayout linearLayout = new LinearLayout(context);
            ScrollView scrollView;
            if (Build.VERSION.SDK_INT >= 21) {
                scrollView = new ScrollView(context, null, 0, R.style.scrollbarShapeStyle) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                        setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                    }
                };
            } else {
                scrollView = new ScrollView(context);
            }
            scrollView.setClipToPadding(false);
            popupLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            linearLayout.setMinimumWidth(AndroidUtilities.dp(200));
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            List<Integer> times = new ArrayList<>(14);
            times.add(200);
            times.add(300);
            times.add(400);
            times.add(500);
            times.add(600);
            times.add(700);
            times.add(800);
            times.add(900);
            times.add(1000);
            times.add(1500);
            times.add(2000);
            times.add(3000);
            times.add(4000);
            times.add(5000);
            times.add(6000);
            for (int a = 0; a < times.size(); a++) {
                ActionBarMenuSubItem cell = new ActionBarMenuSubItem(context, a == 0, a == times.size() - 1);
                cell.setTextAndIcon(String.valueOf(times.get(a)), 0);

                linearLayout.addView(cell);
                final int i = times.get(a);
                cell.setOnClickListener(v1 -> {
                    animationObject.recalculate(i);
                    durationTextView.setText(animationObject.duration + " ms");
                    if (scrimPopupWindow != null) {
                        scrimPopupWindow.dismiss();
                    }
                });
            }
            scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

            scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            scrimPopupWindow.setOutsideTouchable(true);
            scrimPopupWindow.setClippingEnabled(true);
            scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            scrimPopupWindow.setFocusable(true);
            scrimPopupWindow.showAtLocation(scrollView, Gravity.LEFT | Gravity.TOP, 80, 80);
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (pages != null) {
                for (int i = 0; i < pages.size(); ++i) {
                    if (pages.get(i) != null) {
                        pages.get(i).invalidate();
                    }
                }
            }
        }

        public void createAnimationPanel(Context context, String name,
                                         AnimationSettings.AnimationProp prop) {
            AnimationPanel panelView = new AnimationPanel(context, name, animationObject, prop);
            pages.add(panelView);
            panels.addView(panelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    1, 8, 8, 8, 8));
        }

        public void save() {
            for (int i = 0; i < pages.size(); ++i) {
                pages.get(i).save();
            }
        }

    }

    private static class AnimationPanel extends FrameLayout {
        private final AnimationSettings.AnimationObject animationObject;
        private final AnimationSettings.AnimationProp animationProp;
        private GraphDrawer graphDrawer;

        public AnimationPanel(@NonNull Context context, String name,
                              AnimationSettings.AnimationObject animationObject, AnimationSettings.AnimationProp animationProp) {
            super(context);
            this.animationObject = animationObject;
            this.animationProp = animationProp;
            LayoutInflater inflater = (LayoutInflater) context.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View panelView = inflater.inflate(R.layout.animation_panel, this, true);

            TextView panelNameView = panelView.findViewById(R.id.panelNameTextView);
            panelNameView.setText(name);

            LinearLayout graphView = panelView.findViewById(R.id.graphView);
            graphDrawer = new GraphDrawer(context, animationObject, animationProp);
            graphView.addView(graphDrawer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            Slider seekCubic1View = panelView.findViewById(R.id.seekBarCubic1);
            Slider seekCubic2View = panelView.findViewById(R.id.seekBarCubic2);

            seekCubic1View.setLabelFormatter(value -> (int) value + " %");
            seekCubic1View.setValue(animationProp.cubic1.x * 100f);
            seekCubic1View.addOnChangeListener((slider, value, fromUser) -> {
                animationProp.cubic1 = new AnimationSettings.Point(value / 100f, 1);
                graphDrawer.invalidate();

            });

            seekCubic2View.setLabelFormatter(value -> (int) value + " %");
            seekCubic2View.setValue(animationProp.cubic2.x * 100f);
            seekCubic2View.addOnChangeListener((slider, value, fromUser) -> {
                animationProp.cubic2 = new AnimationSettings.Point(value / 100f, 0);
                graphDrawer.invalidate();
            });

            RangeSlider rangeSlider = panelView.findViewById(R.id.rangeSlider);
            rangeSlider.setValues(
                    (float) Integer.min((int) ((animationProp.delay / animationObject.duration) * 100), 100),
                    (float) Integer.min((int) ((animationProp.interrupt / animationObject.duration) * 100), 100));

            rangeSlider.setLabelFormatter(value -> (int) value * (animationObject.duration / 100f) + " ms");
            rangeSlider.addOnChangeListener((RangeSlider.OnChangeListener) (slider, value, fromUser) -> {
                List<Float> values = slider.getValues();
                if (values.size() >= 2) {
                    animationProp.delay = (int) values.get(0).intValue() * (animationObject.duration / 100f);
                    animationProp.interrupt = (int) values.get(1).intValue() * (animationObject.duration / 100f);
                    graphDrawer.invalidate();
                }
            });
        }

        @Override
        public void invalidate() {
            super.invalidate();
            graphDrawer.invalidate();
        }

        public void save() {

        }
    }

    private static class BgPage extends LinearLayout {
        private LinearLayout panels;
        ColorPicker colorPicker;
        AnimationSettings.AnimationBgObject animationBgObject;
        //private List<BgPanel> pages;
        BgPanel bgPanel;
        ActionBarPopupWindow scrimPopupWindow;
        ScrollView scrollView;
        Button currentEditColor;
        private ColorSelectedDelegate delegate;

        public void clearOnRestore() {
            if (bgPanel != null || bgPanel.animatedBg != null) {
                bgPanel.animatedBg.clear();
            }
        }

        public interface ColorSelectedDelegate {
            void colorSelected();
        }

        public void setDelegate(ColorSelectedDelegate delegate) {
            this.delegate = delegate;
        }


        public BgPage(Context context, AnimationSettings.AnimationBgObject animationBgObject) {
            super(context);
            this.setOrientation(LinearLayout.VERTICAL);
            this.animationBgObject = animationBgObject;

            bgPanel = new BgPanel(context, animationBgObject);
            addView(bgPanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


            panels = new LinearLayout(context);
            panels.setOrientation(LinearLayout.VERTICAL);


            scrollView = new ScrollView(context);
            scrollView.addView(panels, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1));
            scrollView.setClipChildren(true);

            this.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            LayoutInflater inflater = (LayoutInflater) context.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View colorPickerView = inflater.inflate(R.layout.animation_color_picker, panels, true);

            Button color1 = colorPickerView.findViewById(R.id.buttonColor1);
            color1.setBackgroundColor(animationBgObject.color1);
            color1.setText(String.format("#%06X", (0xFFFFFF & animationBgObject.color1)));

            Button color2 = colorPickerView.findViewById(R.id.buttonColor2);
            color2.setBackgroundColor(animationBgObject.color2);
            color2.setText(String.format("#%06X", (0xFFFFFF & animationBgObject.color2)));

            Button color3 = colorPickerView.findViewById(R.id.buttonColor3);
            color3.setBackgroundColor(animationBgObject.color3);
            color3.setText(String.format("#%06X", (0xFFFFFF & animationBgObject.color3)));

            Button color4 = colorPickerView.findViewById(R.id.buttonColor4);
            color4.setBackgroundColor(animationBgObject.color4);
            color4.setText(String.format("#%06X", (0xFFFFFF & animationBgObject.color4)));

            color1.setOnClickListener(this::selectColorStart);
            color2.setOnClickListener(this::selectColorStart);
            color3.setOnClickListener(this::selectColorStart);
            color4.setOnClickListener(this::selectColorStart);

            currentEditColor = color1;

            colorPicker = new ColorPicker(context, false, (color, num, applyNow) -> {
                currentEditColor.setText(String.format("#%06X", (0xFFFFFF & color)));
                currentEditColor.setBackgroundColor(color);
                if (currentEditColor == color1) {
                    animationBgObject.color1 = color;
                } else if (currentEditColor == color2) {
                    animationBgObject.color2 = color;
                } else if (currentEditColor == color3) {
                    animationBgObject.color3 = color;
                } else if (currentEditColor == color4) {
                    animationBgObject.color4 = color;
                }

                bgPanel.colorSetChanged();

                if (delegate != null) {
                    delegate.colorSelected();
                }
            });

            colorPicker.setBackgroundColor(Color.WHITE);
            panels.addView(colorPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(100)));
        }


        private void selectColorStart(View v) {
            if (currentEditColor == v) {
                currentEditColor = null;
                colorPicker.setVisibility(GONE);
            }
            currentEditColor = (Button) v;
            Drawable bg = currentEditColor.getBackground();
            if (bg instanceof ColorDrawable) {
                colorPicker.setColor(((ColorDrawable) bg).getColor(), 0);
            }
            colorPicker.setVisibility(VISIBLE);
        }
    }

    private static class BgPanel extends LinearLayout {
        boolean expanded;
        AnimatedBackground animatedBg;

        public BgPanel(@NonNull Context context, AnimationSettings.AnimationBgObject animationBgObject) {
            super(context);
            this.setOrientation(LinearLayout.VERTICAL);
            this.setBackgroundResource(R.drawable.popup_fixed_alert);

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(HORIZONTAL);

            TextView textView = new TextView(context);



            addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    8, 8, 8, 8));

            animatedBg = new AnimatedBackground(context, animationBgObject);
            addView(animatedBg, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            LinearLayout.LayoutParams l = (LayoutParams) animatedBg.getLayoutParams();


            l.height = AndroidUtilities.dp(150);
            animatedBg.setLayoutParams(l);

            textView.setText("Expand");
            textView.setTextColor(Color.BLUE);
            textView.setTextSize(15);
            textView.setOnClickListener(v -> {
                LinearLayout.LayoutParams lp = (LayoutParams) animatedBg.getLayoutParams();

                if (expanded) {
                    textView.setText("Expand");
                    lp.height = AndroidUtilities.dp(150);
                } else {
                    lp.height = AndroidUtilities.dp(500);
                    textView.setText("Collapse");
                }
                animatedBg.setLayoutParams(lp);
                animatedBg.redrawBg(animatedBg.getWidth(), lp.height);

                expanded = !expanded;
            });


            TextView textViewAddMsg = new TextView(context);
            textViewAddMsg.setText("Add message");
            textViewAddMsg.setOnClickListener(v->{
                animatedBg.setScrollAnimate(200, 500);
            });
            textViewAddMsg.setTextColor(Color.BLUE);
            textViewAddMsg.setTextSize(15);

            TextView textViewOpenChat = new TextView(context);
            textViewOpenChat.setText("Open chat");
            textViewOpenChat.setOnClickListener(v->{
                animatedBg.emulateOpen();
            });
            textViewOpenChat.setTextColor(Color.BLUE);
            textViewOpenChat.setTextSize(15);


            topRow.addView(textView,LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    8, 0, 8, 0));
            topRow.addView(textViewAddMsg,LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    8, 0, 8, 0));
            topRow.addView(textViewOpenChat,LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    8, 0, 8, 0));

        }

        public void colorSetChanged() {
            animatedBg.redrawBg();
        }
    }

    private static class GraphDrawer extends View {

        private final AnimationSettings.AnimationObject animationObject;
        private final AnimationSettings.AnimationProp animationProp;


        public GraphDrawer(Context context, AnimationSettings.AnimationObject animationObject,
                           AnimationSettings.AnimationProp animationProp) {
            super(context);
            this.animationProp = animationProp;
            this.animationObject = animationObject;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (animationProp == null || animationObject == null) {
                return;
            }

            int offset_x = 45;
            float scaleX = (getWidth()) / (float) animationObject.duration;
            float _delay = (scaleX) * animationProp.delay;
            float _interrupt = (scaleX) * animationProp.interrupt;

            Paint paint = new Paint();
            paint.setARGB(255, 255, 215, 0);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            int gapsCount = 15;
            int radius = 3;
            int offset = 10;

            int gap = getHeight() / gapsCount;
            for (int i = 0; i < gapsCount; ++i) {
                canvas.drawCircle(_delay + offset_x, i * gap + offset, radius, paint);
                canvas.drawCircle(_interrupt - offset_x, i * gap + offset, radius, paint);
            }

            paint.setARGB(255, 192, 192, 192);
            Path path = new Path();
            path.moveTo(_delay, getHeight() - offset);
            path.cubicTo((1f - animationProp.cubic1.x) * getWidth(), animationProp.cubic1.y * getHeight(), (1f - animationProp.cubic2.x) * getWidth(), animationProp.cubic2.y * getHeight(), _interrupt, 0);

            canvas.drawPath(path, paint);
        }
    }

    public static class AnimationSettings implements Serializable {

        public AnimationBgObject bg;
        public AnimationObject shortText;
        public AnimationObject longText;
        public AnimationObject emoji;
        public AnimationObject sticker;
        public AnimationObject audio;
        public AnimationObject video;
        public AnimationObject photo;
        public AnimationObject gif;

        public AnimationSettings() {
        }

        public static AnimationSettings loadFrom(Context context, Uri uri) {
            ObjectInputStream inputStream = null;
            Object obj = null;
            try {
                InputStream fileInputStream = context.getContentResolver().openInputStream(uri);
                inputStream = new ObjectInputStream(fileInputStream);
                obj = inputStream.readObject();
                fileInputStream.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {

                    }
                }

                if (obj != null && obj instanceof AnimationSettings) {
                    return (AnimationSettings) obj;
                }
                return createDefault();
            }
        }

        public void save(Context context) {

            ObjectOutputStream objectOutputStream = null;
            try {
                FileOutputStream fileOutputStream = context.openFileOutput("settings.anim", Activity.MODE_PRIVATE);
                objectOutputStream = new ObjectOutputStream(fileOutputStream);
                objectOutputStream.writeObject(this);
                fileOutputStream.getFD().sync();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (objectOutputStream != null) {
                    try {
                        objectOutputStream.close();
                    } catch (IOException e) {

                    }
                }
            }
        }


        public void saveTo(Context context, Uri uri) {
            ObjectOutputStream objectOutputStream = null;
            try {
                OutputStream fileOutputStream = context.getContentResolver().openOutputStream(uri);
                objectOutputStream = new ObjectOutputStream(fileOutputStream);
                objectOutputStream.writeObject(this);
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (objectOutputStream != null) {
                    try {
                        objectOutputStream.close();
                    } catch (IOException e) {

                    }
                }
            }
        }

        public static AnimationSettings load(Context context) {
            ObjectInputStream objectInputStream = null;
            Object obj = null;
            try {
                FileInputStream fileInputStream = context.getApplicationContext().openFileInput("settings.anim");
                objectInputStream = new ObjectInputStream(fileInputStream);
                obj = objectInputStream.readObject();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                if (objectInputStream != null) {
                    try {
                        objectInputStream.close();
                    } catch (IOException e) {

                    }
                }

                if (obj != null) {
                    return (AnimationSettings) obj;
                } else {
                    return createDefault();
                }
            }
        }

        private static AnimationSettings createDefault() {


            AnimationSettings animationSettings = new AnimationSettings();
            animationSettings.bg = new AnimationBgObject();
            animationSettings.bg.color1 = Color.parseColor("#FFF6BF");
            animationSettings.bg.color2 = Color.parseColor("#76A076");
            animationSettings.bg.color3 = Color.parseColor("#F6E477");
            animationSettings.bg.color4 = Color.parseColor("#316B4D");


            animationSettings.shortText = new AnimationObject();
            animationSettings.shortText.duration = 300;
            animationSettings.shortText.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.shortText.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.shortText.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.shortText.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.longText = new AnimationObject();
            animationSettings.longText.duration = 300;
            animationSettings.longText.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.longText.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.longText.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.longText.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.emoji = new AnimationObject();
            animationSettings.emoji.duration = 300;
            animationSettings.emoji.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.emoji.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.emoji.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.emoji.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.sticker = new AnimationObject();
            animationSettings.sticker.duration = 300;
            animationSettings.sticker.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.sticker.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.sticker.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.sticker.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.audio = new AnimationObject();
            animationSettings.audio.duration = 300;
            animationSettings.audio.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.audio.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.audio.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.audio.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.video = new AnimationObject();
            animationSettings.video.duration = 300;
            animationSettings.video.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.video.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.video.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.video.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.photo = new AnimationObject();
            animationSettings.photo.duration = 300;
            animationSettings.photo.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.photo.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.photo.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.photo.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));

            animationSettings.gif = new AnimationObject();
            animationSettings.gif.duration = 300;
            animationSettings.gif.x = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.gif.y = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.gif.scale = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));
            animationSettings.gif.opacity = new AnimationProp(0, 300, new Point(1, 1), new Point(0, 0));


            return animationSettings;
        }

        public static class Point implements Serializable {
            public float x;
            public float y;

            public Point() {

            }

            public PointF toPointF() {
                return new PointF(x, y);
            }

            public Point(float x, float y) {
                this.x = x;
                this.y = y;
            }
        }

        public static class AnimationBgObject implements Serializable {
            public int color1;
            public int color2;
            public int color3;
            public int color4;

            public AnimationProp send;
            public AnimationProp open;
            public AnimationProp jump;


        }

        public static class AnimationObject implements Serializable {
            public long duration;
            public AnimationProp x;
            public AnimationProp y;
            public AnimationProp scale;
            public AnimationProp opacity;

            public void recalculate(long newDuration) {
                newDuration = newDuration < 50 ? 50 : newDuration;

                x.delay = (x.delay / (float) duration) * newDuration;
                x.interrupt = (x.interrupt / (float) duration) * newDuration;

                y.delay = (y.delay / (float) duration) * newDuration;
                y.interrupt = (y.interrupt / (float) duration) * newDuration;

                opacity.delay = (opacity.delay / (float) duration) * newDuration;
                opacity.interrupt = (opacity.interrupt / (float) duration) * newDuration;

                scale.delay = (scale.delay / (float) duration) * newDuration;
                scale.interrupt = (scale.interrupt / (float) duration) * newDuration;

                duration = newDuration;
            }
        }

        public static class AnimationProp implements Serializable {
            public AnimationProp(float delay, float interrupt, Point cubic1, Point cubic2) {
                this.delay = delay;
                this.interrupt = interrupt;
                this.cubic1 = cubic1;
                this.cubic2 = cubic2;
            }

            public ValueAnimator fillValueAnimator(ValueAnimator valueAnimator) {
                if (cubic1.x == 1 && cubic2.x == 0) {
                    //to avoid shacking with bezier.
                    valueAnimator.setInterpolator(new LinearInterpolator());
                } else {
                    valueAnimator.setInterpolator(new CubicBezierInterpolator(cubic1.toPointF(), cubic2.toPointF()));
                }

                valueAnimator.setDuration((long) (interrupt - delay));
                valueAnimator.setStartDelay((long) delay);

                return valueAnimator;
            }

            public float delay;
            public float interrupt;
            public Point cubic1;
            public Point cubic2;
        }
    }
}
