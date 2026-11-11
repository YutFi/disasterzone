package com.example.disasterzone.util;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Small, dependency-free animation helpers. They use ViewPropertyAnimator, so they automatically
 * respect the system "remove animations" / animator-duration-scale setting.
 */
public final class Anim {
    private Anim() {}

    /** Fades a list row in while sliding it up; rows further down start slightly later. */
    public static void listItem(View v, int position) {
        v.setAlpha(0f);
        v.setTranslationY(48f);
        v.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(Math.min(position, 6) * 45L)
                .setDuration(380)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /** Fades + rises a whole screen's content when it first appears. */
    public static void enter(View v, long delayMs) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(36f);
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(450)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    /** Pops a view in with a little overshoot (logos, badges). */
    public static void popIn(View v, long delayMs) {
        if (v == null) return;
        v.setScaleX(0.5f);
        v.setScaleY(0.5f);
        v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delayMs).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    /** Quick "heartbeat" bounce, used when a post is liked. */
    public static void bounce(final View v) {
        if (v == null) return;
        v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(110).withEndAction(new Runnable() {
            @Override public void run() {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(new OvershootInterpolator(3f)).start();
            }
        }).start();
    }
}
