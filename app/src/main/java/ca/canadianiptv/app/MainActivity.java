package ca.canadianiptv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(13, 16, 22);
    private final int PANEL = Color.rgb(25, 30, 39);
    private final int PANEL_LIGHT = Color.rgb(35, 42, 53);
    private final int RED = Color.rgb(220, 48, 58);
    private final int WHITE = Color.rgb(245, 247, 250);
    private final int MUTED = Color.rgb(157, 167, 181);
    private final String BBB = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    private final String TEARS = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8";
    private final String LIVE_TEST = "https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8";
    private final String CANADA_PLAYLIST = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8";
    private final ExecutorService playlistExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> favourites = new LinkedHashSet<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }

    private void showHome() {
        LinearLayout root = createRoot("HOME");
        LinearLayout page = pageContent(root);
        addPageHeader(page, "HOME", "Welcome to Canadian IPTV", "Free Canadian channels, plus movies and series.");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sp = params(-1, 92);
        sp.topMargin = dp(18);
        page.addView(stats, sp);
        addStat(stats, "PLAYLISTS", "1", "Free Canadian playlist");
        addStat(stats, "CHANNELS", "CAN", "Canadian channels");
        addStat(stats, "CONNECTION", "DEMO", "No login required");

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(22), dp(16), dp(22), dp(16));
        hero.setBackground(round(PANEL, 18));
        LinearLayout.LayoutParams hp = params(-1, -2);
        hp.topMargin = dp(16);
        page.addView(hero, hp);
        TextView eyebrow = text("FREE CANADIAN TV", 11, RED, true);
        eyebrow.setLetterSpacing(0.14f);
        hero.addView(eyebrow);
        TextView title = text("Your Canadian channels", 24, WHITE, true);
        LinearLayout.LayoutParams tp = params(-1, -2);
        tp.topMargin = dp(6);
        hero.addView(title, tp);
        TextView body = text("Browse free Canadian streams from a community-maintained playlist. Demo streams remain available for playback testing.", 14, MUTED, false);
        LinearLayout.LayoutParams bp = params(-1, -2);
        bp.topMargin = dp(5);
        hero.addView(body, bp);
        TextView live = actionCard("▶   OPEN CANADIAN LIVE TV", true);
        LinearLayout.LayoutParams lp = params(250, 44);
        lp.topMargin = dp(12);
        hero.addView(live, lp);
        live.setOnClickListener(v -> showLiveTv());
        TextView note = text("Free streams can change, go offline or be restricted by location.", 12, MUTED, false);
        LinearLayout.LayoutParams np = params(-1, -2);
        np.topMargin = dp(12);
        page.addView(note, np);
        setContentView(root);
        focusFirst(root);
    }

    private LinearLayout createRoot(String selected) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(16), dp(14), dp(16), dp(20));
        rail.setBackgroundColor(Color.rgb(18, 22, 29));
        root.addView(rail, new LinearLayout.LayoutParams(dp(205), -1));
        TextView brand = text("CANADIAN\\nIPTV", 23, WHITE, true);
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
        addRailItem(rail, "⌂   HOME", selected.equals("HOME"), this::showHome);
        addRailItem(rail, "▣   LIVE TV", selected.equals("LIVE TV"), this::showLiveTv);
        addRailItem(rail, "▤   MOVIES", selected.equals("MOVIES"), this::showMovies);
        addRailItem(rail, "▥   SERIES", selected.equals("SERIES"), this::showSeries);
        addRailItem(rail, "★   FAVOURITES", selected.equals("FAVOURITES"), this::showFavourites);
        View spacer = new View(this);
        rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        rail.addView(text("FREE TV  •  0.3 PREVIEW", 9, MUTED, true));
        return root;
    }

    private LinearLayout pageContent(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(28), dp(16), dp(28), dp(16));
        scroll.addView(page);
        return page;
    }

    private void addPageHeader(LinearLayout page, String section, String heading, String subtitle) {
        TextView top = text(section, 13, MUTED, true);
        top.setLetterSpacing(0.16f);
        page.addView(top);
        TextView title = text(heading, 26, WHITE, true);
        LinearLayout.LayoutParams tp = params(-1, -2);
        tp.topMargin = dp(8);
        page.addView(title, tp);
        TextView sub = text(subtitle, 15, MUTED, false);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(5);
        page.addView(sub, sp);
    }

    private void showLiveTv() {
        LinearLayout root = createRoot("LIVE TV");
        LinearLayout page = pageContent(root);
        addPageHeader(page, "LIVE TV", "Free Canadian channels", "Official free streams listed by the Free-TV project. Some channels may be geo-blocked or temporarily offline.");
        TextView status = text("Loading Canadian channel list…", 14, MUTED, false);
        LinearLayout.LayoutParams statusParams = params(-1, -2);
        statusParams.topMargin = dp(16);
        page.addView(status, statusParams);
        TextView reload = actionCard("↻  RELOAD CHANNELS", true);
        LinearLayout.LayoutParams reloadParams = params(220, 42);
        reloadParams.topMargin = dp(10);
        page.addView(reload, reloadParams);
        reload.setOnClickListener(v -> loadCanadianChannels(page, status));
        TextView demo = actionCard("▶  OPEN DEMO STREAMS", false);
        LinearLayout.LayoutParams demoParams = params(220, 42);
        demoParams.topMargin = dp(8);
        page.addView(demo, demoParams);
        demo.setOnClickListener(v -> showDemoLiveTv());
        setContentView(root);
        focusFirst(root);
        loadCanadianChannels(page, status);
    }

    private void showDemoLiveTv() {
        showCatalog("LIVE TV", "Demo channels", "Public playback samples used as a fallback. These are not broadcast TV channels.",
            new String[][] {
                {"Big Buck Bunny • Test Channel", "HLS playback sample • On demand", BBB},
                {"Unified Streaming • Live Test", "Live test signal • Availability may vary", LIVE_TEST}
            });
    }

    private void loadCanadianChannels(LinearLayout page, TextView status) {
        status.setText("Loading Canadian channel list…");
        playlistExecutor.execute(() -> {
            ArrayList<String[]> channels = new ArrayList<>();
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(CANADA_PLAYLIST).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "CanadianIPTV/0.3 AndroidTV");
                connection.setInstanceFollowRedirects(true);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    String name = null;
                    String group = "";
                    String logo = "";
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("#EXTINF")) {
                            int comma = line.lastIndexOf(',');
                            name = comma >= 0 ? line.substring(comma + 1).trim() : "Unnamed channel";
                            group = m3uAttribute(line, "group-title");
                            logo = m3uAttribute(line, "tvg-logo");
                        } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                            if ("canada".equalsIgnoreCase(group.trim())) {
                                String detail = "Canada";
                                if (!logo.isEmpty()) detail += " • Logo available";
                                channels.add(new String[]{name, detail, line});
                            }
                            name = null;
                            group = "";
                            logo = "";
                        }
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Network error" : e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String problem = error;
            runOnUiThread(() -> {
                if (page.getParent() == null) return;
                int first = page.indexOfChild(status);
                while (page.getChildCount() > first + 3) page.removeViewAt(first + 3);
                if (problem != null) {
                    status.setText("Could not load playlist: " + problem + ". Check the TV's internet connection and try reload.");
                    return;
                }
                status.setText(channels.size() + " Canadian channels found • Select a channel to play");
                for (String[] channel : channels) addMediaRow(page, channel[0], channel[1], channel[2]);
                if (channels.isEmpty()) status.setText("The playlist loaded, but no entries were tagged Canada.");
            });
        });
    }

    private String m3uAttribute(String line, String key) {
        String marker = key + "=\\\"";
        int start = line.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = line.indexOf('"', start);
        return end < 0 ? "" : line.substring(start, end);
    }

    private void showMovies() {
        showCatalog("MOVIES", "Demo movie library", "Open sample videos published for player testing.",
            new String[][] {
                {"Big Buck Bunny", "Open movie playback sample", BBB},
                {"Tears of Steel", "Open movie playback sample", TEARS}
            });
    }

    private void showSeries() {
        showCatalog("SERIES", "Demo series", "Sample entries to test browsing. These are not a real TV catalogue.",
            new String[][] {
                {"Demo Series • Episode 1", "Playback sample • Big Buck Bunny", BBB},
                {"Demo Series • Episode 2", "Playback sample • Tears of Steel", TEARS}
            });
    }

    private void showFavourites() {
        java.util.ArrayList<String[]> items = new java.util.ArrayList<>();
        if (favourites.contains("Big Buck Bunny • Test Channel")) items.add(new String[]{"Big Buck Bunny • Test Channel", "Saved demo channel", BBB});
        if (favourites.contains("Big Buck Bunny")) items.add(new String[]{"Big Buck Bunny", "Saved demo movie", BBB});
        if (favourites.contains("Tears of Steel")) items.add(new String[]{"Tears of Steel", "Saved demo movie", TEARS});
        if (favourites.contains("Unified Streaming • Live Test")) items.add(new String[]{"Unified Streaming • Live Test", "Saved live test feed", LIVE_TEST});
        if (items.isEmpty()) {
            showCatalog("FAVOURITES", "Your favourites", "Nothing saved yet. Use the ☆ button beside a demo item to add it here.", new String[0][0]);
        } else {
            showCatalog("FAVOURITES", "Your favourites", "Your saved demo items.", items.toArray(new String[0][]));
        }
    }

    private void showCatalog(String section, String heading, String subtitle, String[][] items) {
        LinearLayout root = createRoot(section);
        LinearLayout page = pageContent(root);
        addPageHeader(page, section, heading, subtitle);
        TextView badge = text("DEMO CONTENT", 11, RED, true);
        badge.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams badgeParams = params(-1, -2);
        badgeParams.topMargin = dp(16);
        page.addView(badge, badgeParams);
        if (items.length == 0) {
            TextView empty = text("No favourites yet.", 18, MUTED, true);
            LinearLayout.LayoutParams ep = params(-1, -2);
            ep.topMargin = dp(18);
            page.addView(empty, ep);
        }
        for (String[] item : items) addMediaRow(page, item[0], item[1], item[2]);
        setContentView(root);
        focusFirst(root);
    }

    private void addMediaRow(LinearLayout page, String name, String detail, String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackground(round(PANEL, 12));
        LinearLayout.LayoutParams rp = params(-1, 76);
        rp.topMargin = dp(10);
        page.addView(row, rp);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(text(name, 16, WHITE, true));
        TextView sub = text(detail, 12, MUTED, false);
        LinearLayout.LayoutParams subp = params(-1, -2);
        subp.topMargin = dp(4);
        info.addView(sub, subp);
        TextView fav = actionCard(favourites.contains(name) ? "★" : "☆", false);
        LinearLayout.LayoutParams fp = params(44, 42);
        fp.rightMargin = dp(8);
        row.addView(fav, fp);
        fav.setOnClickListener(v -> {
            if (favourites.contains(name)) favourites.remove(name); else favourites.add(name);
            fav.setText(favourites.contains(name) ? "★" : "☆");
        });
        TextView play = actionCard("▶  PLAY", true);
        row.addView(play, params(105, 42));
        play.setOnClickListener(v -> playDemo(name, url));
        row.setFocusable(true);
        row.setOnClickListener(v -> playDemo(name, url));
    }

    private void playDemo(String title, String url) {
        VideoView video = new VideoView(this);
        video.setVideoURI(Uri.parse(url));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("DEMO  •  " + title).setView(frame)
            .setNegativeButton("Close", (d, w) -> video.stopPlayback()).create();
        dialog.setOnDismissListener(d -> video.stopPlayback());
        dialog.show();
        dialog.getWindow().setLayout(dp(900), dp(560));
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setOnErrorListener((mp, what, extra) -> {
            showInfo("Playback issue", "This public test stream could not be played right now. The host may be unavailable, or this TV may not support its format.");
            return true;
        });
        video.start();
    }

    private void addRailItem(LinearLayout rail, String label, boolean selected, Runnable action) {
        TextView item = actionCard(label, selected);
        item.setTextSize(13);
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = params(-1, 36);
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
        view.setOnFocusChangeListener((v, focused) -> v.setBackground(round(focused ? RED : (primary ? RED : PANEL_LIGHT), 10)));
        return view;
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Got it", null).show();
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

    private void focusFirst(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.isFocusable()) {
                    child.requestFocus();
                    return;
                }
                focusFirst(child);
            }
        }
    }
}
