package com.jakedaltrui.quiethome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class HomeRedirectService extends AccessibilityService {
    private static final String AMAZON_HOME_PACKAGE = "com.amazon.tv.launcher";
    private long lastRedirectAt;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!AMAZON_HOME_PACKAGE.contentEquals(event.getPackageName())) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirectAt < 750L) return;
        lastRedirectAt = now;

        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(home);
    }

    @Override
    public void onInterrupt() {
    }
}
