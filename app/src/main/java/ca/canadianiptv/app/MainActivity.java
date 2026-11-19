package ca.canadianiptv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(13, 16, 22);
    private final int PANEL = Color.rgb(25, 30, 39);
    private final int PANEL_LIGHT = Color.rgb(35, 42, 53);
    private final int RED = Color.rgb(220, 48, 58);
    private final int WHITE = Color.rgb(245, 247, 250);
    private final int MUTED = Color.rgb(157, 167, 181);
    private LinearLayout content;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }

    private void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(16), dp(14), dp(16), dp(20));
        rail.setBackgroundColor(Color.rgb(18, 22, 29));
        root.addView(rail, new LinearLayout.LayoutParams(dp(205), -1));

        TextView brand = text("CANADIAN\nIPTV", 23, WHITE, true);
        brand.setLetterSpacing(0.06f);
        rail.addView(brand);
        TextView edition = text("YOUR TV. YOUR WAY.", 10, MUTED, true);
        LinearLayout.LayoutParams ep = params(-2, -2);
        ep.topMargin = dp(8);
        rail.addView(edition, ep);

        View divider = new View(this);
        divider.setBackgroundColor(PANEL_LIGHT);
        LinearLayout.LayoutParams divp = params(-1, 1);
        divp.topMargin = dp(18);
        divp.bottomMargin = dp(14);
        rail.addView(divider, divp);

        addRailItem(rail, "⌂   HOME", true, () -> showHome());
        addRailItem(rail, "▣   LIVE TV", false, () -> comingSoon("Live TV"));
        addRailItem(rail, "▤   MOVIES", false, () -> comingSoon("Movies"));
        addRailItem(rail, "▥   SERIES", false, () -> comingSoon("Series"));
        addRailItem(rail, "★   FAVOURITES", false, () -> comingSoon("Favourites"));

        View spacer = new View(this);
        rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView version = text("VERSION 0.1  •  PREVIEW", 10, MUTED, true);
        rail.addView(version);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(16), dp(28), dp(16));
        scroll.addView(content);

        TextView top = text("HOME", 13, MUTED, true);
        top.setLetterSpacing(0.16f);
        content.addView(top);

        TextView heading = text("Welcome to Canadian IPTV", 26, WHITE, true);
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(8);
        content.addView(heading, hp);

        TextView intro = text("Your channels and entertainment, all in one place.", 16, MUTED, false);
        LinearLayout.LayoutParams ip = params(-1, -2);
        ip.topMargin = dp(5);
        content.addView(intro, ip);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = params(-1, dp(92));
        sp.topMargin = dp(18);
        content.addView(stats, sp);
        addStat(stats, "PLAYLISTS", "0", "Add a source to begin");
        addStat(stats, "CHANNELS", "0", "Waiting for playlist");
        addStat(stats, "CONNECTION", "READY", "App is ready");

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(22), dp(16), dp(22), dp(16));
        hero.setBackground(round(PANEL, 18));
        LinearLayout.LayoutParams bp = params(-1, -2);
        bp.topMargin = dp(16);
        content.addView(hero, bp);

        TextView eyebrow = text("GET STARTED", 11, RED, true);
        eyebrow.setLetterSpacing(0.14f);
        hero.addView(eyebrow);

        TextView heroTitle = text("Bring your TV lineup with you.", 24, WHITE, true);
        LinearLayout.LayoutParams htp = params(-1, -2);
        htp.topMargin = dp(6);
        hero.addView(heroTitle, htp);

        TextView heroBody = text("Connect a supported M3U playlist or Xtream Codes account. Your provider supplies the playlist and access details.", 14, MUTED, false);
        LinearLayout.LayoutParams hbp = params(-1, -2);
        hbp.topMargin = dp(5);
        hero.addView(heroBody, hbp);

        TextView add = actionCard("＋   ADD PLAYLIST", true);
        LinearLayout.LayoutParams ap = params(220, 44);
        ap.topMargin = dp(12);
        hero.addView(add, ap);
        add.setOnClickListener(v -> showAddPlaylist());

        TextView note = text("No playlist connected yet. Add one to populate your library.", 13, MUTED, false);
        LinearLayout.LayoutParams np = params(-1, -2);
        np.topMargin = dp(12);
        content.addView(note, np);

        setContentView(root);
        add.requestFocus();
    }

    private void addRailItem(LinearLayout rail, String label, boolean selected, Runnable action) {
        TextView item = actionCard(label, selected);
        item.setTextSize(13);
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = params(-1, dp(36));
        p.bottomMargin = dp(4);
        rail.addView(item, p);
        item.setOnClickListener(v -> action.run());
    }

    private void addStat(LinearLayout row, String label, String value, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(12), dp(10));
        card.setBackground(round(PANEL, 14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        cp.rightMargin = dp(10);
        row.addView(card, cp);
        card.addView(text(label, 10, MUTED, true));
        TextView val = text(value, 22, WHITE, true);
        LinearLayout.LayoutParams vp = params(-1, -2);
        vp.topMargin = dp(7);
        card.addView(val, vp);
        TextView desc = text(detail, 10, MUTED, false);
        LinearLayout.LayoutParams dp = params(-1, -2);
        dp.topMargin = dp(3);
        card.addView(desc, dp);
    }

    private TextView actionCard(String label, boolean primary) {
        TextView view = text(label, 14, WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(round(primary ? RED : PANEL_LIGHT, 10));
        view.setOnFocusChangeListener((v, focused) ->
            v.setBackground(round(focused ? RED : (primary ? RED : PANEL_LIGHT), 10)));
        return view;
    }

    private void showAddPlaylist() {
        String[] choices = {"M3U / M3U8 playlist", "Xtream Codes login"};
        new AlertDialog.Builder(this)
            .setTitle("Add a playlist")
            .setItems(choices, (dialog, which) -> {
                if (which == 0) showInfo("M3U playlist", "M3U playlist setup is the next development step. You'll be able to enter your playlist URL here.");
                else showInfo("Xtream Codes", "Xtream Codes setup is coming next. You'll enter your server address, username and password here.");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void comingSoon(String section) {
        showInfo(section, section + " will appear here once a playlist is connected.");
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Got it", null)
            .show();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
