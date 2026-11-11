package com.jakedaltrui.quiethome;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int WIDGET_HOST_ID = 4817;
    private static final String PIA_PACKAGE = "com.privateinternetaccess.android";
    private static final String PIA_WIDGET = "com.kape.widget.WidgetReceiver";
    private AppWidgetHost widgetHost;
    private HomeView homeView;
    private BroadcastReceiver packageReceiver;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this);
        homeView = new HomeView(this);
        root.addView(homeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final FrameLayout loading = new FrameLayout(this);
        loading.setBackgroundColor(Color.argb(150, 0, 0, 0));
        LaunchSpinnerView spinner = new LaunchSpinnerView(this);
        FrameLayout.LayoutParams spinnerLayout = new FrameLayout.LayoutParams(48, 48, Gravity.CENTER);
        loading.addView(spinner, spinnerLayout);
        root.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        homeView.setOnFirstFrame(new Runnable() {
            @Override public void run() {
                loading.postDelayed(new Runnable() {
                    @Override public void run() {
                        loading.animate().alpha(0f).setDuration(220L).withEndAction(new Runnable() {
                            @Override public void run() {
                                ViewGroup parent = (ViewGroup) loading.getParent();
                                if (parent != null) parent.removeView(loading);
                            }
                        }).start();
                    }
                }, 120L);
            }
        });

        widgetHost = new AppWidgetHost(this, WIDGET_HOST_ID);
        AppWidgetHostView piaWidget = createPiaWidget();
        if (piaWidget != null) {
            piaWidget.setAlpha(.01f);
            FrameLayout.LayoutParams hidden = new FrameLayout.LayoutParams(480, 160, Gravity.TOP | Gravity.LEFT);
            hidden.leftMargin = -700;
            root.addView(piaWidget, hidden);
            homeView.setPiaWidget(piaWidget);
        }
        setContentView(root);
    }

    private static final class LaunchSpinnerView extends View {
        private final Paint spinnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator animator;
        private float rotation;

        LaunchSpinnerView(Context context) {
            super(context);
            spinnerPaint.setColor(Color.WHITE);
            spinnerPaint.setStyle(Paint.Style.STROKE);
            spinnerPaint.setStrokeWidth(3f);
            spinnerPaint.setStrokeCap(Paint.Cap.ROUND);
            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(850L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator animation) {
                    rotation = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animator.start();
        }

        @Override protected void onDetachedFromWindow() {
            animator.cancel();
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = 7f;
            RectF ring = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(ring, rotation, 255f, false, spinnerPaint);
        }
    }

    private AppWidgetHostView createPiaWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        int widgetId = getPreferences(MODE_PRIVATE).getInt("pia_widget_id", -1);
        if (widgetId < 0 || manager.getAppWidgetInfo(widgetId) == null) {
            widgetId = widgetHost.allocateAppWidgetId();
            ComponentName provider = new ComponentName(PIA_PACKAGE, PIA_WIDGET);
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 72);
            if (!manager.bindAppWidgetIdIfAllowed(widgetId, provider, options)) {
                widgetHost.deleteAppWidgetId(widgetId);
                return null;
            }
            getPreferences(MODE_PRIVATE).edit().putInt("pia_widget_id", widgetId).apply();
        }
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(widgetId);
        if (info == null) return null;
        AppWidgetHostView view = widgetHost.createView(this, widgetId, info);
        view.setAppWidget(widgetId, info);
        return view;
    }

    @Override protected void onStart() {
        super.onStart();
        if (widgetHost != null) widgetHost.startListening();
        if (packageReceiver == null) {
            packageReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (homeView != null) homeView.refreshApps();
                }
            };
            IntentFilter packagesChanged = new IntentFilter();
            packagesChanged.addAction(Intent.ACTION_PACKAGE_ADDED);
            packagesChanged.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packagesChanged.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packagesChanged.addDataScheme("package");
            registerReceiver(packageReceiver, packagesChanged);
        }
        if (homeView != null) homeView.refreshApps();
    }

    @Override protected void onResume() {
        super.onResume();
        if (homeView != null) homeView.refreshApps();
    }

    @Override protected void onStop() {
        if (widgetHost != null) widgetHost.stopListening();
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }
        super.onStop();
    }

    private static final class AppItem {
        final String name;
        final String packageName;
        final int color;
        boolean installed;
        boolean hasTvBanner;
        Bitmap artwork;

        AppItem(String name, String packageName, int color) {
            this.name = name;
            this.packageName = packageName;
            this.color = color;
        }

        AppItem(AppItem other) {
            this(other.name, other.packageName, other.color);
        }
    }

    private static final class HomeView extends View {
        private static final int COLUMNS = 5;
        private static final AppItem[] BASE_APPS = new AppItem[] {
                new AppItem("Netflix", "com.netflix.ninja", Color.rgb(188, 18, 29)),
                new AppItem("Hulu", "com.hulu.plus", Color.rgb(28, 187, 109)),
                new AppItem("Disney+", "com.disney.disneyplus", Color.rgb(23, 71, 155)),
                new AppItem("Prime Video", "com.amazon.firebat", Color.rgb(0, 168, 225)),
                new AppItem("YouTube", "com.amazon.firetv.youtube", Color.rgb(230, 33, 23)),

                new AppItem("Max", "com.hbo.hbonow", Color.rgb(91, 45, 214)),
                new AppItem("Apple TV+", "com.apple.atve.amazon.appletv", Color.rgb(120, 126, 136)),
                new AppItem("Peacock", "com.peacock.peacockfiretv", Color.rgb(243, 194, 40)),
                new AppItem("Paramount+", "com.cbs.ott", Color.rgb(39, 97, 196)),
                new AppItem("Tubi", "com.tubitv.ott", Color.rgb(123, 67, 232)),

                new AppItem("Pluto TV", "tv.pluto.android", Color.rgb(244, 119, 40)),
                new AppItem("ESPN", "com.espn.gtv", Color.rgb(210, 28, 36)),
                new AppItem("Amazon Music", "com.amazon.bueller.music", Color.rgb(37, 163, 213))
        };

        private static final int UTILITY_NONE = -1;
        private static final int UTILITY_SETTINGS = 0;
        private static final int UTILITY_APPSTORE = 1;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap background;
        private final Bitmap blurredBackground;
        private final PackageManager packages;
        private final ConnectivityManager connectivity;
        private final WifiManager wifiManager;
        private final ArrayList<AppItem> apps = new ArrayList<AppItem>();
        private final Handler clockHandler = new Handler();
        private final ScoreService scoreService;
        private final long tickerStartedAt = System.currentTimeMillis();
        private String tickerText;
        private String favoriteLive;
        private String favoriteNext;
        private long favoriteNextTime;
        private boolean scoresLoaded;
        private AppWidgetHostView piaWidget;
        private boolean vpnFocused = false;
        private int utilityFocused = UTILITY_NONE;
        private boolean vpnConnected = false;
        private int selected = 0;
        private int previousSelected = 0;
        private long selectionChangedAt = 0L;
        private Runnable onFirstFrame;
        private boolean firstFrameDelivered;

        HomeView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
            background = BitmapFactory.decodeResource(getResources(), R.drawable.forest_background);
            blurredBackground = Bitmap.createScaledBitmap(background, 80, 45, true);
            packages = context.getPackageManager();
            connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            scoreService = new ScoreService(context, clockHandler);
            tickerText = scoreService.cachedText();
            favoriteLive = scoreService.favoriteLive();
            favoriteNext = scoreService.favoriteNext();
            favoriteNextTime = scoreService.favoriteNextTime();
            scoresLoaded = tickerText.length() > 0;
            reloadApps();
            clockHandler.post(new Runnable() {
                @Override public void run() {
                    vpnConnected = readVpnState();
                    invalidate();
                    clockHandler.postDelayed(this, 1000L);
                }
            });
        }

        private final Runnable scoreRefresh = new Runnable() {
            @Override public void run() {
                scoreService.refresh(new ScoreService.Listener() {
                    @Override public void onScores(String text, boolean fresh) {
                        if (fresh || tickerText.length() == 0) {
                            tickerText = text;
                            scoresLoaded = true;
                            favoriteLive = scoreService.favoriteLive();
                            favoriteNext = scoreService.favoriteNext();
                            favoriteNextTime = scoreService.favoriteNextTime();
                            invalidate();
                        }
                    }
                });
                clockHandler.postDelayed(this, 60000L);
            }
        };

        private final Runnable tickerAnimator = new Runnable() {
            @Override public void run() {
                invalidate();
                clockHandler.postDelayed(this, 33L);
            }
        };

        void setPiaWidget(AppWidgetHostView widget) {
            piaWidget = widget;
        }

        void setOnFirstFrame(Runnable callback) {
            onFirstFrame = callback;
        }

        void refreshApps() {
            reloadApps();
            invalidate();
        }

        private boolean readVpnState() {
            if (connectivity == null) return false;
            try {
                for (Network network : connectivity.getAllNetworks()) {
                    NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
                }
            } catch (RuntimeException ignored) {
            }
            return false;
        }

        private void reloadApps() {
            apps.clear();
            Set<String> knownPackages = new HashSet<String>();
            for (AppItem base : BASE_APPS) {
                AppItem item = new AppItem(base);
                loadArtwork(item);
                apps.add(item);
                knownPackages.add(item.packageName);
            }

            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            List<ResolveInfo> launchable = packages.queryIntentActivities(query, 0);
            ArrayList<AppItem> discovered = new ArrayList<AppItem>();
            for (ResolveInfo info : launchable) {
                if (info.activityInfo == null || info.activityInfo.applicationInfo == null) continue;
                ApplicationInfo application = info.activityInfo.applicationInfo;
                String packageName = info.activityInfo.packageName;
                if (knownPackages.contains(packageName)
                        || getContext().getPackageName().equals(packageName)
                        || PIA_PACKAGE.equals(packageName)
                        || (application.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                CharSequence label = info.loadLabel(packages);
                String name = label == null ? packageName : label.toString();
                int seed = packageName.hashCode();
                int color = Color.rgb(35 + Math.abs(seed & 0x5f),
                        35 + Math.abs((seed >> 8) & 0x5f),
                        35 + Math.abs((seed >> 16) & 0x5f));
                AppItem item = new AppItem(name, packageName, color);
                loadArtwork(item);
                discovered.add(item);
                knownPackages.add(packageName);
            }
            Collections.sort(discovered, new Comparator<AppItem>() {
                @Override public int compare(AppItem left, AppItem right) {
                    return left.name.compareToIgnoreCase(right.name);
                }
            });
            apps.addAll(discovered);
            if (selected >= apps.size()) selected = Math.max(0, apps.size() - 1);
        }

        private void loadArtwork(AppItem item) {
            Drawable artwork = null;
            try {
                Intent launch = packages.getLeanbackLaunchIntentForPackage(item.packageName);
                if (launch != null) {
                    ResolveInfo resolved = packages.resolveActivity(launch, 0);
                    if (resolved != null && resolved.activityInfo != null) {
                        artwork = resolved.activityInfo.loadBanner(packages);
                    }
                }
                if (artwork == null) artwork = packages.getApplicationBanner(item.packageName);
                item.hasTvBanner = artwork != null;
                if (artwork == null) artwork = packages.getApplicationIcon(item.packageName);
                item.artwork = renderArtwork(artwork, item.hasTvBanner ? 960 : 512,
                        item.hasTvBanner ? 540 : 512);
                item.installed = true;
            } catch (PackageManager.NameNotFoundException ignored) {
                item.installed = false;
                item.hasTvBanner = false;
                item.artwork = null;
            }
        }

        private Bitmap renderArtwork(Drawable drawable, int width, int height) {
            if (drawable == null) return null;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas iconCanvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(iconCanvas);
            return bitmap;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            reloadApps();
            clockHandler.removeCallbacks(scoreRefresh);
            clockHandler.post(scoreRefresh);
            clockHandler.removeCallbacks(tickerAnimator);
            clockHandler.post(tickerAnimator);
        }

        @Override
        protected void onDetachedFromWindow() {
            clockHandler.removeCallbacks(scoreRefresh);
            clockHandler.removeCallbacks(tickerAnimator);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float h = getHeight();
            if (w <= 0 || h <= 0) return;

            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            boolean allAppsView = selected >= COLUMNS && !vpnFocused;
            canvas.drawBitmap(allAppsView ? blurredBackground : background,
                    null, new RectF(0, 0, w, h), paint);

            // All Apps becomes a separate, calm layer while controls stay crisp.
            paint.setColor(Color.argb(allAppsView ? 132 : 48, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[] { Color.argb(25, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.argb(210, 0, 0, 0) },
                    new float[] { 0f, .48f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, w, 0,
                    new int[] { Color.argb(55, 0, 0, 0), Color.TRANSPARENT, Color.argb(90, 0, 0, 0) },
                    new float[] { 0f, .55f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            if (favoriteLive.length() > 0) {
                paint.setShader(new RadialGradient(w * .52f, h * .70f, w * .60f,
                        new int[] { Color.argb(38, 200, 30, 30), Color.TRANSPARENT },
                        null, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            }

            drawTicker(canvas, w, h);
            drawHeader(canvas, w, h);
            drawApps(canvas, w, h);
            drawUtilityButtons(canvas, w, h);
            drawVpnPill(canvas, w, h);
            deliverFirstFrame();
        }

        private void drawTicker(Canvas canvas, float w, float h) {
            final int amber = Color.rgb(255, 181, 48);
            RectF frame = new RectF(w * .042f, h * .018f, w * .738f, h * .122f);
            RectF screen = new RectF(frame.left + h * .010f, frame.top + h * .010f,
                    frame.right - h * .010f, frame.bottom - h * .010f);

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(235, 5, 5, 5));
            canvas.drawRoundRect(new RectF(frame.left + 8f, frame.top + 10f,
                    frame.right + 8f, frame.bottom + 12f), h * .011f, h * .011f, paint);
            paint.setColor(Color.rgb(20, 19, 17));
            canvas.drawRoundRect(frame, h * .011f, h * .011f, paint);
            paint.setColor(Color.rgb(3, 4, 3));
            canvas.drawRoundRect(screen, h * .005f, h * .005f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.rgb(48, 45, 38));
            canvas.drawRoundRect(screen, h * .005f, h * .005f, paint);

            // Unlit LEDs give the bar the physical dot-matrix texture in the references.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(21, 22, 19));
            float grid = h * .0065f;
            for (float y = screen.top + grid; y < screen.bottom - grid; y += grid) {
                for (float x = screen.left + grid; x < screen.right - grid; x += grid) {
                    canvas.drawCircle(x, y, h * .00125f, paint);
                }
            }

            float labelWidth = w * .125f;
            float labelLeft = screen.left + w * .012f;
            paint.setColor(Color.rgb(58, 54, 43));
            canvas.drawRect(screen.left + labelWidth, screen.top + h * .013f,
                    screen.left + labelWidth + 3f, screen.bottom - h * .013f, paint);

            String text = tickerText.length() > 0 ? tickerText
                    : (scoresLoaded ? "NO GAMES ON THE BOARD" : "CONNECTING TO THE SCOREBOARD...");
            ArrayList<TickerSegment> segments = parseTicker(text);
            float step = h * .00435f;
            float clipLeft = screen.left + labelWidth + w * .014f;
            float clipRight = screen.right - w * .010f;
            float textWidth = tickerWidth(segments, step);
            float gap = w * .075f;
            float cycle = textWidth + gap;
            float offset = (((System.currentTimeMillis() - tickerStartedAt) / 1000f)
                    * w * .050f) % cycle;
            String currentLeague = leagueAtOffset(segments, offset, step);
            float labelStep = h * .00325f;
            LedFont.drawText(canvas, paint, tickerLabel(currentLeague), labelLeft, screen.centerY(),
                    labelStep, h * .00125f, ledLeagueColor(currentLeague),
                    screen.left, screen.right);
            float firstX = clipLeft - offset;
            int save = canvas.save();
            canvas.clipRect(clipLeft, screen.top, clipRight, screen.bottom);
            drawLedTickerPass(canvas, segments, firstX, screen.centerY(), step,
                    h * .00165f, clipLeft, clipRight);
            drawLedTickerPass(canvas, segments, firstX + cycle, screen.centerY(), step,
                    h * .00165f, clipLeft, clipRight);
            canvas.restoreToCount(save);
        }

        private void drawLedTickerPass(Canvas canvas, List<TickerSegment> segments,
                                       float x, float centerY,
                                       float step, float radius, float clipLeft, float clipRight) {
            float cursor = x;
            String divider = "     |     ";
            for (int i = 0; i < segments.size(); i++) {
                TickerSegment segment = segments.get(i);
                LedFont.drawText(canvas, paint, segment.text, cursor, centerY, step, radius,
                        ledLeagueColor(segment.league), clipLeft, clipRight);
                cursor += LedFont.textWidth(segment.text, step);
                if (i + 1 < segments.size()) {
                    LedFont.drawText(canvas, paint, divider, cursor, centerY, step, radius,
                            Color.rgb(255, 181, 48), clipLeft, clipRight);
                    cursor += LedFont.textWidth(divider, step);
                }
            }
        }

        private ArrayList<TickerSegment> parseTicker(String text) {
            ArrayList<TickerSegment> segments = new ArrayList<TickerSegment>();
            for (String raw : text.split("\\|", -1)) {
                String segment = raw.trim();
                if (segment.length() == 0) continue;
                String league = "SCORES";
                int separator = segment.indexOf("::");
                if (separator > 0) {
                    league = segment.substring(0, separator);
                    segment = segment.substring(separator + 2);
                } else {
                    String[] legacy = new String[] { "NCAA BB", "NCAA FB", "NFL", "MLB" };
                    for (String prefix : legacy) {
                        if (segment.startsWith(prefix + "  ")) {
                            league = "NCAA BB".equals(prefix) ? "NCAA BSB" : prefix;
                            segment = segment.substring(prefix.length()).trim();
                            break;
                        }
                    }
                }
                segments.add(new TickerSegment(league, segment));
            }
            if (segments.isEmpty()) segments.add(new TickerSegment("SCORES", text));
            return segments;
        }

        private float tickerWidth(List<TickerSegment> segments, float step) {
            float width = 0f;
            float dividerWidth = LedFont.textWidth("     |     ", step);
            for (int i = 0; i < segments.size(); i++) {
                width += LedFont.textWidth(segments.get(i).text, step);
                if (i + 1 < segments.size()) width += dividerWidth;
            }
            return width;
        }

        private String leagueAtOffset(List<TickerSegment> segments, float offset, float step) {
            float cursor = 0f;
            float dividerWidth = LedFont.textWidth("     |     ", step);
            for (int i = 0; i < segments.size(); i++) {
                TickerSegment segment = segments.get(i);
                cursor += LedFont.textWidth(segment.text, step);
                if (offset < cursor) return segment.league;
                if (i + 1 < segments.size()) {
                    cursor += dividerWidth;
                    if (offset < cursor) return segment.league;
                }
            }
            return segments.get(0).league;
        }

        private String tickerLabel(String league) {
            if ("NCAA FB".equals(league)) return "NCAA F";
            return league;
        }

        private int ledLeagueColor(String league) {
            if ("MLB".equals(league)) return Color.rgb(255, 78, 65);
            if ("NFL".equals(league)) return Color.rgb(86, 166, 255);
            if ("NCAA FB".equals(league)) return Color.rgb(255, 181, 48);
            if ("NCAA BSB".equals(league)) return Color.rgb(65, 231, 111);
            return Color.rgb(240, 240, 226);
        }

        private static final class TickerSegment {
            final String league;
            final String text;

            TickerSegment(String league, String text) {
                this.league = league;
                this.text = text;
            }
        }

        private void drawHeader(Canvas canvas, float w, float h) {
            if (selected >= COLUMNS && !vpnFocused) return;
            sansText(h * .071f, Color.WHITE, true);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()),
                    w * .055f, h * .49f, paint);
            sansText(h * .020f, Color.argb(188, 255, 255, 255), false);
            canvas.drawText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(new Date()),
                    w * .058f, h * .532f, paint);
            if (favoriteLive.length() > 0) {
                RectF liveChip = new RectF(w * .058f, h * .548f, w * .112f, h * .578f);
                paint.setColor(Color.rgb(211, 42, 45));
                canvas.drawRoundRect(liveChip, h * .015f, h * .015f, paint);
                sansText(h * .012f, Color.WHITE, true);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("LIVE", liveChip.centerX(), liveChip.centerY() + h * .004f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
                sansText(h * .015f, Color.WHITE, true);
                canvas.drawText(fitText(favoriteLive, w * .42f), w * .122f, h * .571f, paint);
            }
            if (favoriteNext.length() > 0 && favoriteNextTime > System.currentTimeMillis()) {
                sansText(h * .0135f, Color.argb(190, 255, 255, 255), false);
                String next = "NEXT  " + countdown(favoriteNextTime) + "  •  " + favoriteNext;
                canvas.drawText(fitText(next, w * .53f), w * .058f,
                        favoriteLive.length() > 0 ? h * .605f : h * .571f, paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void drawVpnPill(Canvas canvas, float w, float h) {
            int green = Color.rgb(48, 209, 88);
            float pillWidth = w * .118f;
            float pillHeight = h * .050f;
            float left = w * .822f;
            float top = h * .044f;
            float grow = vpnFocused ? h * .0035f : 0f;
            left -= grow;
            top -= grow;
            pillWidth += grow * 2f;
            pillHeight += grow * 2f;
            RectF pill = new RectF(left, top, left + pillWidth, top + pillHeight);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(vpnFocused ? 205 : 165, 18, 18, 20));
            canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, paint);

            sansText(h * .016f, Color.WHITE, true);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("VPN", left + pillHeight * .38f, pill.centerY() + h * .006f, paint);

            float switchW = pillHeight * .72f;
            float switchH = pillHeight * .40f;
            float switchRight = pill.right - pillHeight * .25f;
            RectF toggle = new RectF(switchRight - switchW, pill.centerY() - switchH / 2f,
                    switchRight, pill.centerY() + switchH / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(vpnConnected ? green : Color.rgb(82, 82, 87));
            canvas.drawRoundRect(toggle, switchH / 2f, switchH / 2f, paint);
            float knobX = vpnConnected ? toggle.right - switchH / 2f : toggle.left + switchH / 2f;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(knobX, pill.centerY(), switchH * .40f, paint);
        }

        private void drawUtilityButtons(Canvas canvas, float w, float h) {
            float size = h * .050f;
            float top = h * .044f;
            float firstLeft = w * .750f;
            float gap = w * .008f;
            drawUtilityButton(canvas, new RectF(firstLeft, top, firstLeft + size, top + size),
                    UTILITY_SETTINGS, true, h);
            float secondLeft = firstLeft + size + gap;
            drawUtilityButton(canvas, new RectF(secondLeft, top, secondLeft + size, top + size),
                    UTILITY_APPSTORE, false, h);
        }

        private void drawUtilityButton(Canvas canvas, RectF base, int utility,
                                       boolean settingsIcon, float h) {
            boolean focused = utilityFocused == utility;
            float grow = focused ? h * .0035f : 0f;
            RectF button = new RectF(base.left - grow, base.top - grow,
                    base.right + grow, base.bottom + grow);
            if (focused) {
                paint.setColor(Color.argb(70, 255, 255, 255));
                canvas.drawRoundRect(new RectF(button.left - 5f, button.top - 5f,
                        button.right + 5f, button.bottom + 5f), h * .016f, h * .016f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(focused ? 215 : 165, 18, 18, 20));
            canvas.drawRoundRect(button, h * .014f, h * .014f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(h * .0030f);
            paint.setColor(Color.WHITE);
            if (settingsIcon) {
                float cx = button.centerX();
                float cy = button.centerY();
                float inner = button.height() * .115f;
                float outer = button.height() * .245f;
                canvas.drawCircle(cx, cy, inner, paint);
                canvas.drawCircle(cx, cy, outer * .72f, paint);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4.0;
                    float x1 = cx + (float) Math.cos(angle) * outer * .78f;
                    float y1 = cy + (float) Math.sin(angle) * outer * .78f;
                    float x2 = cx + (float) Math.cos(angle) * outer;
                    float y2 = cy + (float) Math.sin(angle) * outer;
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            } else {
                float cx = button.centerX();
                float cy = button.centerY();
                float r = button.height() * .235f;
                canvas.drawLine(cx, cy - r, cx - r, cy + r, paint);
                canvas.drawLine(cx, cy - r, cx + r, cy + r, paint);
                canvas.drawLine(cx - r * .58f, cy + r * .28f,
                        cx + r * .58f, cy + r * .28f, paint);
            }
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }

        private String networkLabel() {
            if (connectivity == null) return "Offline";
            try {
                Network active = connectivity.getActiveNetwork();
                NetworkCapabilities caps = active == null ? null
                        : connectivity.getNetworkCapabilities(active);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return "Offline";
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    int level = 2;
                    if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
                        level = WifiManager.calculateSignalLevel(
                                wifiManager.getConnectionInfo().getRssi(), 4);
                    }
                    if (level >= 3) return "Wi-Fi Great";
                    if (level == 2) return "Wi-Fi Good";
                    return "Wi-Fi Weak";
                }
                return "Online";
            } catch (RuntimeException ignored) {
                return "Online";
            }
        }

        private String countdown(long target) {
            long minutes = Math.max(0L, (target - System.currentTimeMillis()) / 60000L);
            if (minutes < 60L) return "IN " + Math.max(1L, minutes) + "M";
            long hours = minutes / 60L;
            if (hours < 24L) return "IN " + hours + "H";
            long days = hours / 24L;
            return "IN " + days + "D";
        }

        private String fitText(String text, float maxWidth) {
            if (paint.measureText(text) <= maxWidth) return text;
            String suffix = "…";
            int count = paint.breakText(text, true,
                    Math.max(0f, maxWidth - paint.measureText(suffix)), null);
            return text.substring(0, Math.max(0, count)).trim() + suffix;
        }

        private void deliverFirstFrame() {
            if (firstFrameDelivered || onFirstFrame == null) return;
            firstFrameDelivered = true;
            final Runnable callback = onFirstFrame;
            post(new Runnable() {
                @Override public void run() {
                    callback.run();
                }
            });
        }

        private void drawApps(Canvas canvas, float w, float h) {
            int selectedRow = selected / COLUMNS;
            boolean favoritesView = selectedRow == 0 || vpnFocused
                    || utilityFocused != UTILITY_NONE;
            if (favoritesView) {
                drawFavoritesDock(canvas, w, h, h * .682f, w * .08f, w * .84f,
                        h * .188f, true);
                return;
            }

            drawFavoritesDock(canvas, w, h, h * .235f, w * .08f, w * .84f,
                    h * .135f, false);

            float horizontalGap = w * .025f;
            float rowWidth = w * .82f;
            float cardWidth = (rowWidth - horizontalGap * (COLUMNS - 1)) / COLUMNS;
            float cardHeight = cardWidth * 9f / 16f;
            float startX = (w - rowWidth) / 2f;
            float firstTop = h * .465f;
            float rowStep = h * .235f;

            int selectedAllAppsRow = Math.max(0, selected / COLUMNS - 1);
            int firstVisibleAllAppsRow = Math.max(0, selectedAllAppsRow - 1);
            int firstVisible = COLUMNS + firstVisibleAllAppsRow * COLUMNS;
            int lastVisible = Math.min(firstVisible + COLUMNS * 2, apps.size());
            for (int i = firstVisible; i < lastVisible; i++) {
                int row = (i - firstVisible) / COLUMNS;
                int column = (i - COLUMNS) % COLUMNS;
                float focus = focusAmount(i);
                boolean focused = !vpnFocused && i == selected;
                float grow = h * .014f * focus;
                float left = startX + column * (cardWidth + horizontalGap) - grow;
                float top = firstTop + row * rowStep - grow - h * .008f * focus;
                RectF card = new RectF(left, top,
                        left + cardWidth + grow * 2f, top + cardHeight + grow * 2f);
                drawAppCard(canvas, i, card, focus, h);

                if (focused) {
                    sansText(h * .017f, Color.WHITE, true);
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(apps.get(i).name, card.centerX(), card.bottom + h * .040f, paint);
                    paint.setTextAlign(Paint.Align.LEFT);
                }
            }
        }

        private void drawFavoritesDock(Canvas canvas, float w, float h, float top,
                                       float startX, float rowWidth, float cardHeight,
                                       boolean interactive) {
            float horizontalGap = interactive ? w * .018f : w * .012f;
            float cardWidth = (rowWidth - horizontalGap * (COLUMNS - 1)) / COLUMNS;
            cardHeight = cardWidth * 9f / 16f;
            float padX = interactive ? w * .028f : w * .020f;
            float padY = interactive ? h * .040f : h * .027f;
            RectF dock = new RectF(startX - padX, top - padY,
                    startX + rowWidth + padX, top + cardHeight + padY);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, dock.top, 0, dock.bottom,
                    new int[] { Color.argb(168, 70, 70, 72), Color.argb(145, 23, 23, 25) },
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(dock, h * .036f, h * .036f, paint);
            paint.setShader(null);

            for (int i = 0; i < COLUMNS; i++) {
                int column = i;
                float focus = interactive && !vpnFocused ? focusAmount(i) : 0f;
                float grow = h * .016f * focus;
                float left = startX + column * (cardWidth + horizontalGap) - grow;
                float right = left + cardWidth + grow * 2f;
                float cardTop = top - grow - h * .008f * focus;
                float bottom = cardTop + cardHeight + grow * 2f;
                RectF card = new RectF(left, cardTop, right, bottom);
                drawAppCard(canvas, i, card, focus, h);
            }
        }

        private void drawAppCard(Canvas canvas, int index, RectF card, float focus, float h) {
            AppItem app = apps.get(index);
            paint.setAlpha(255);
            if (focus > 0f) {
                paint.setColor(Color.argb((int) (68f * focus), 0, 0, 0));
                canvas.drawRoundRect(new RectF(card.left + 4f, card.top + 7f,
                        card.right + 4f, card.bottom + 10f), h * .030f, h * .030f, paint);
            }

            int tileColor = tileBackground(app);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tileColor);
            canvas.drawRoundRect(card, h * .023f, h * .023f, paint);

            Path clip = new Path();
            clip.addRoundRect(card, h * .023f, h * .023f, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            if (app.artwork != null && app.hasTvBanner) {
                drawCenterCrop(canvas, app.artwork, card);
            } else if (app.artwork != null) {
                drawIconFit(canvas, app.artwork, card);
            } else {
                drawPlaceholderLogo(canvas, app, card.centerX(),
                        card.centerY() - card.height() * .02f, card.height() * 1.12f, focus > .5f);
            }
            paint.setShader(new LinearGradient(0, card.top, 0, card.bottom,
                    new int[] { Color.argb(24, 255, 255, 255), Color.TRANSPARENT,
                            Color.argb((int) (30f - 24f * focus), 0, 0, 0) },
                    new float[] { 0f, .55f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawRect(card, paint);
            paint.setShader(null);
            canvas.restoreToCount(save);

            if (!app.installed) {
                paint.setColor(Color.argb(185, 0, 0, 0));
                RectF badge = new RectF(card.right - h * .058f, card.top + h * .012f,
                        card.right - h * .012f, card.top + h * .035f);
                canvas.drawRoundRect(badge, 10, 10, paint);
                sansText(h * .009f, Color.argb(215, 255, 255, 255), true);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("GET", badge.centerX(), badge.centerY() + h * .0035f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }

        private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF destination) {
            float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float destinationRatio = destination.width() / destination.height();
            int left = 0;
            int top = 0;
            int right = bitmap.getWidth();
            int bottom = bitmap.getHeight();
            if (sourceRatio < destinationRatio) {
                int height = Math.round(bitmap.getWidth() / destinationRatio);
                top = (bitmap.getHeight() - height) / 2;
                bottom = top + height;
            } else if (sourceRatio > destinationRatio) {
                int width = Math.round(bitmap.getHeight() * destinationRatio);
                left = (bitmap.getWidth() - width) / 2;
                right = left + width;
            }
            canvas.drawBitmap(bitmap, new android.graphics.Rect(left, top, right, bottom),
                    destination, paint);
        }

        private void drawIconFit(Canvas canvas, Bitmap bitmap, RectF card) {
            float size = Math.min(card.height() * .82f, card.width() * .72f);
            RectF destination = new RectF(card.centerX() - size / 2f,
                    card.centerY() - size / 2f,
                    card.centerX() + size / 2f,
                    card.centerY() + size / 2f);
            canvas.drawBitmap(bitmap, null, destination, paint);
        }

        private float focusAmount(int index) {
            if (vpnFocused || utilityFocused != UTILITY_NONE) return 0f;
            if (selectionChangedAt == 0L) return index == selected ? 1f : 0f;
            float progress = Math.min(1f,
                    (SystemClock.uptimeMillis() - selectionChangedAt) / 150f);
            float eased = progress * progress * (3f - 2f * progress);
            if (index == selected) return eased;
            if (index == previousSelected) return 1f - eased;
            return 0f;
        }

        private void changeSelection(int next) {
            if (next < 0 || next >= apps.size() || next == selected) return;
            previousSelected = selected;
            selected = next;
            selectionChangedAt = SystemClock.uptimeMillis();
            invalidate();
        }

        private void sansText(float size, int color, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setTextSize(size);
            paint.setColor(color);
        }

        private int tileBackground(AppItem app) {
            if ("YouTube".equals(app.name)) return Color.rgb(245, 245, 245);
            if ("Prime Video".equals(app.name)) return Color.rgb(22, 122, 244);
            if ("Disney+".equals(app.name)) return Color.rgb(10, 43, 79);
            if ("Paramount+".equals(app.name)) return Color.rgb(12, 45, 105);
            if ("Tubi".equals(app.name)) return Color.rgb(29, 10, 50);
            if ("Max".equals(app.name)) return Color.rgb(10, 6, 31);
            if ("Hulu".equals(app.name) || "Netflix".equals(app.name)
                    || "Apple TV+".equals(app.name) || "Peacock".equals(app.name)
                    || "ESPN".equals(app.name) || "Amazon Music".equals(app.name)) {
                return Color.rgb(6, 6, 7);
            }
            return Color.rgb(
                    Math.max(8, (int) (Color.red(app.color) * .26f)),
                    Math.max(8, (int) (Color.green(app.color) * .26f)),
                    Math.max(8, (int) (Color.blue(app.color) * .26f)));
        }

        private void terminalText(float size, int color, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setTextSize(size);
            paint.setColor(color);
        }

        private Path panelPath(float left, float top, float right, float bottom, float cut) {
            Path path = new Path();
            path.moveTo(left + cut, top);
            path.lineTo(right - cut, top);
            path.lineTo(right, top + cut);
            path.lineTo(right, bottom - cut);
            path.lineTo(right - cut, bottom);
            path.lineTo(left + cut, bottom);
            path.lineTo(left, bottom - cut);
            path.lineTo(left, top + cut);
            path.close();
            return path;
        }

        private void drawDashedLine(Canvas canvas, float start, float end, float y,
                                    int color, float dash, float gap) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(color);
            for (float x = start; x < end; x += dash + gap) {
                canvas.drawLine(x, y, Math.min(x + dash, end), y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawCornerTicks(Canvas canvas, RectF rect, int color, float length) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(color);
            canvas.drawLine(rect.left - 4, rect.top + length, rect.left - 4, rect.top - 3, paint);
            canvas.drawLine(rect.left - 4, rect.top - 3, rect.left + length, rect.top - 3, paint);
            canvas.drawLine(rect.right + 4, rect.top + length, rect.right + 4, rect.top - 3, paint);
            canvas.drawLine(rect.right + 4, rect.top - 3, rect.right - length, rect.top - 3, paint);
            canvas.drawLine(rect.left - 4, rect.bottom - length, rect.left - 4, rect.bottom + 3, paint);
            canvas.drawLine(rect.left - 4, rect.bottom + 3, rect.left + length, rect.bottom + 3, paint);
            canvas.drawLine(rect.right + 4, rect.bottom - length, rect.right + 4, rect.bottom + 3, paint);
            canvas.drawLine(rect.right + 4, rect.bottom + 3, rect.right - length, rect.bottom + 3, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawPlaceholderLogo(Canvas canvas, AppItem app, float cx, float cy,
                                         float cardHeight, boolean focused) {
            paint.setTextAlign(Paint.Align.CENTER);
            String mark = app.name;
            if ("Netflix".equals(app.name)) mark = "N";
            if ("YouTube".equals(app.name)) {
                paint.setColor(Color.rgb(240, 34, 35));
                canvas.drawRect(cx - cardHeight * .22f, cy - cardHeight * .14f,
                        cx + cardHeight * .22f, cy + cardHeight * .14f, paint);
                Path play = new Path();
                play.moveTo(cx - cardHeight * .045f, cy - cardHeight * .075f);
                play.lineTo(cx + cardHeight * .09f, cy);
                play.lineTo(cx - cardHeight * .045f, cy + cardHeight * .075f);
                play.close();
                paint.setColor(Color.WHITE);
                canvas.drawPath(play, paint);
                return;
            }
            if ("YouTube Music".equals(app.name)) {
                float radius = cardHeight * .23f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(255, 0, 45));
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cardHeight * .035f);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, radius * .58f, paint);
                paint.setStyle(Paint.Style.FILL);
                Path play = new Path();
                play.moveTo(cx - radius * .18f, cy - radius * .30f);
                play.lineTo(cx + radius * .34f, cy);
                play.lineTo(cx - radius * .18f, cy + radius * .30f);
                play.close();
                canvas.drawPath(play, paint);
                return;
            }
            else if ("Apple TV+".equals(app.name)) mark = "tv+";
            else if ("Prime Video".equals(app.name)) mark = "prime";
            else if ("Paramount+".equals(app.name)) mark = "P+";
            else if ("Pluto TV".equals(app.name)) mark = "P tv";
            else if ("ESPN".equals(app.name)) mark = "E";
            else if ("Peacock".equals(app.name)) mark = "P•";

            sansText(cardHeight * (mark.length() > 5 ? .20f : .27f), app.color, true);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(mark, cx, cy + cardHeight * .085f, paint);
        }

        private static String greeting() {
            int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(new Date()));
            if (hour < 12) return "Good morning";
            if (hour < 18) return "Good afternoon";
            return "Good evening";
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (!vpnFocused && utilityFocused == UTILITY_NONE) {
                    if (selected < COLUMNS) {
                        vpnFocused = true;
                    } else {
                        changeSelection(selected - COLUMNS);
                    }
                }
                invalidate();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (vpnFocused || utilityFocused != UTILITY_NONE) {
                    vpnFocused = false;
                    utilityFocused = UTILITY_NONE;
                } else {
                    int nextRowStart = (selected / COLUMNS + 1) * COLUMNS;
                    if (nextRowStart < apps.size()) {
                        changeSelection(Math.min(selected + COLUMNS, apps.size() - 1));
                    }
                }
                invalidate();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (vpnFocused) {
                    vpnFocused = false;
                    utilityFocused = UTILITY_APPSTORE;
                } else if (utilityFocused == UTILITY_APPSTORE) {
                    utilityFocused = UTILITY_SETTINGS;
                } else if (utilityFocused == UTILITY_NONE) {
                    int rowStart = (selected / COLUMNS) * COLUMNS;
                    int rowCount = Math.min(COLUMNS, apps.size() - rowStart);
                    changeSelection(rowStart + (selected - rowStart + rowCount - 1) % rowCount);
                }
                invalidate();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (utilityFocused == UTILITY_SETTINGS) {
                    utilityFocused = UTILITY_APPSTORE;
                } else if (utilityFocused == UTILITY_APPSTORE) {
                    utilityFocused = UTILITY_NONE;
                    vpnFocused = true;
                } else if (!vpnFocused) {
                    int rowStart = (selected / COLUMNS) * COLUMNS;
                    int rowCount = Math.min(COLUMNS, apps.size() - rowStart);
                    changeSelection(rowStart + (selected - rowStart + 1) % rowCount);
                }
                invalidate();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (vpnFocused) {
                    toggleVpn();
                } else if (utilityFocused != UTILITY_NONE) {
                    launchUtility(utilityFocused);
                } else {
                    launchSelected();
                }
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        private void launchUtility(int utility) {
            if (utility == UTILITY_SETTINGS) {
                Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(settings);
                return;
            }
            try {
                Intent store = new Intent("com.amazon.venezia.ade.FEATURED_LAUNCH");
                store.setClassName("com.amazon.venezia",
                        "com.amazon.venezia.ade.ADEHomeActivity");
                store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(store);
            } catch (ActivityNotFoundException explicitStoreMissing) {
                try {
                    Intent store = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("amzn://apps/android"));
                    store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(store);
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(getContext(), "Amazon Appstore is not available",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        private void toggleVpn() {
            View button = findClickable(piaWidget);
            if (button != null && button.performClick()) {
                Toast.makeText(getContext(), vpnConnected ? "Turning VPN off…" : "Turning VPN on…",
                        Toast.LENGTH_SHORT).show();
                clockHandler.postDelayed(new Runnable() {
                    @Override public void run() {
                        Intent stayHome = new Intent(getContext(), MainActivity.class);
                        stayHome.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        getContext().startActivity(stayHome);
                    }
                }, 700L);
                clockHandler.postDelayed(new Runnable() {
                    @Override public void run() {
                        vpnConnected = readVpnState();
                        invalidate();
                    }
                }, 1200L);
                return;
            }

            Intent intent = packages.getLaunchIntentForPackage(PIA_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                getContext().startActivity(intent);
            } else {
                Toast.makeText(getContext(), "PIA VPN is not available", Toast.LENGTH_SHORT).show();
            }
        }

        private View findClickable(View view) {
            if (view == null) return null;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    View found = findClickable(group.getChildAt(i));
                    if (found != null) return found;
                }
            }
            return view.isClickable() ? view : null;
        }

        private void launchSelected() {
            AppItem app = apps.get(selected);
            Intent intent = packages.getLeanbackLaunchIntentForPackage(app.packageName);
            if (intent == null) intent = packages.getLaunchIntentForPackage(app.packageName);
            if (intent == null) {
                try {
                    String storeAddress;
                    if ("com.tubitv.ott".equals(app.packageName)) {
                        storeAddress = "amzn://apps/android?asin=B075NTHVJW";
                    } else if ("com.espn.gtv".equals(app.packageName)) {
                        storeAddress = "amzn://apps/android?asin=B00E81O27Y";
                    } else {
                        storeAddress = "amzn://apps/android?p=" + app.packageName;
                    }
                    Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse(storeAddress));
                    store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(store);
                    Toast.makeText(getContext(), "Opening " + app.name + " in the Appstore",
                            Toast.LENGTH_SHORT).show();
                } catch (ActivityNotFoundException ignored) {
                    Toast.makeText(getContext(), app.name + " is not installed", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            getContext().startActivity(intent);
        }
    }
}
