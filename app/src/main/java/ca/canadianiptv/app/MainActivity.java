package ca.canadianiptv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.InputType;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.PlayerView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import org.json.JSONObject;
import org.json.JSONArray;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import android.util.Base64;

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
    private final java.util.Map<String, String> favouriteUrls = new java.util.LinkedHashMap<>();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private String providerServer = "";
    private String providerUser = "";
    private String providerPass = "";
    private ExoPlayer activePlayer;
    private PlayerView activePlayerView;
    private boolean playerOpen = false;
    private String providerSelectedCategory = "";
    private int providerCurrentPage = 0;
    private String lastPlayedChannelName = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        android.content.SharedPreferences saved = getSharedPreferences("canadian_iptv", MODE_PRIVATE);
        providerServer = decrypt(saved.getString("provider_server", ""));
        providerUser = decrypt(saved.getString("provider_user", ""));
        providerPass = decrypt(saved.getString("provider_pass", ""));
        favourites.addAll(saved.getStringSet("favourites", new LinkedHashSet<>()));
        for (String name : favourites) {
            String url = saved.getString("url_" + name, "");
            if (!url.isEmpty()) favouriteUrls.put(name, url);
        }
        // Automatically resume the saved provider session when the app launches.
        // If no saved credentials are available, show the normal home screen.
        if (!providerServer.isEmpty() && !providerUser.isEmpty() && !providerPass.isEmpty()) {
            showProviderLiveTv();
        } else {
            showHome();
        }
    }


    private void showProviderLogin() {
        LinearLayout root = createRoot("PROVIDER");
        LinearLayout page = pageContent(root);
        addPageHeader(page, "XTREAM CODES", "Connect your IPTV provider",
            "Enter the server address, username and password supplied by your provider. Your details are encrypted on this device.");
        android.content.SharedPreferences prefs = getSharedPreferences("canadian_iptv", MODE_PRIVATE);
        EditText server = inputField("Server address (example: https://provider.com)");
        EditText username = inputField("Username");
        EditText password = inputField("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String savedServer = decrypt(prefs.getString("provider_server", ""));
        String savedUser = decrypt(prefs.getString("provider_user", ""));
        String savedPass = decrypt(prefs.getString("provider_pass", ""));
        if (!savedServer.isEmpty()) server.setText(savedServer);
        if (!savedUser.isEmpty()) username.setText(savedUser);
        if (!savedPass.isEmpty()) password.setText(savedPass);
        page.addView(server, fieldParams());
        page.addView(username, fieldParams());
        page.addView(password, fieldParams());
        TextView connect = actionCard("CONNECT AND LOAD CHANNELS", true);
        LinearLayout.LayoutParams cp = params(280, 48);
        cp.topMargin = dp(14);
        page.addView(connect, cp);
        TextView status = text("Your provider login is kept on this TV, not sent to Canadian IPTV servers.", 13, MUTED, false);
        LinearLayout.LayoutParams stp = params(-1, -2);
        stp.topMargin = dp(14);
        page.addView(status, stp);
        connect.setOnClickListener(v -> {
            String address = server.getText().toString().trim();
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (address.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                showInfo("Missing details", "Enter all three details from your IPTV provider.");
                return;
            }
            if (!address.startsWith("http://") && !address.startsWith("https://")) address = "http://" + address;
            while (address.endsWith("/")) address = address.substring(0, address.length() - 1);
            final String finalAddress = address;
            connect.setText("CONNECTING…");
            connect.setEnabled(false);
            status.setText("Checking your provider account…");
            playlistExecutor.execute(() -> {
                String problem = null;
                JSONObject account = null;
                try {
                    String api = finalAddress + "/player_api.php?username=" + enc(user) + "&password=" + enc(pass);
                    account = new JSONObject(httpGet(api));
                    JSONObject info = account.optJSONObject("user_info");
                    if (info == null || info.optInt("auth", 0) != 1) {
                        problem = "The provider did not accept those login details. Check the server address, username and password.";
                    }
                } catch (Exception e) {
                    problem = "Could not connect: " + safeError(e);
                }
                final String error = problem;
                final JSONObject result = account;
                runOnUiThread(() -> {
                    connect.setText("CONNECT AND LOAD CHANNELS");
                    connect.setEnabled(true);
                    if (error != null) {
                        status.setText(error);
                        return;
                    }
                    prefs.edit()
                        .putString("provider_server", encrypt(finalAddress))
                        .putString("provider_user", encrypt(user))
                        .putString("provider_pass", encrypt(pass))
                        .apply();
                    providerServer = finalAddress;
                    providerUser = user;
                    providerPass = pass;
                    JSONObject info = result.optJSONObject("user_info");
                    String expiry = info == null ? "" : info.optString("exp_date", "");
                    status.setText("Connected successfully" + (expiry.isEmpty() || "null".equals(expiry) ? "" : " • Account expiry: " + expiry) + ". Loading your live channels…");
                    showProviderLiveTv();
                });
            });
        });
        setContentView(root);
        focusFirst(root);
    }

    private EditText inputField(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(WHITE);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setTextSize(16);
        field.setPadding(dp(14), dp(8), dp(14), dp(8));
        field.setBackground(round(PANEL_LIGHT, 10));
        field.setFocusable(true);
        field.setSelectAllOnFocus(false);
        return field;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = params(520, 52);
        p.topMargin = dp(12);
        return p;
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", "CanadianIPTV/0.4 AndroidTV");
        try {
            int code = c.getResponseCode();
            java.io.InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new java.io.IOException("HTTP " + code);
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line);
            }
            if (code < 200 || code >= 400) throw new java.io.IOException("Provider returned HTTP " + code);
            return out.toString();
        } finally { c.disconnect(); }
    }

    private String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null) return "Network or server error";
        return message.replaceAll("(?i)(username|password)=[^& ]+", "$1=[hidden]");
    }

    private SecretKey providerKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias = "CanadianIPTVProviderKey";
        if (store.containsAlias(alias)) return ((SecretKey) store.getKey(alias, null));
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return generator.generateKey();
    }

    private String encrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, providerKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    private String decrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(value, Base64.NO_WRAP);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, providerKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private void showProviderLiveTv() {
        LinearLayout root = createRoot("LIVE TV");
        LinearLayout page = pageContent(root);
        addPageHeader(page, "YOUR PROVIDER", "Live TV", "Browse every channel from your provider by category. Channels are shown 50 at a time to keep the TV responsive.");

        TextView status = text("Loading your provider categories and channel list…", 14, MUTED, false);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(14);
        page.addView(status, sp);

        android.widget.HorizontalScrollView categoryScroll = new android.widget.HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout categoryBar = new LinearLayout(this);
        categoryBar.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroll.addView(categoryBar);
        LinearLayout.LayoutParams catScrollParams = params(-1, 48);
        catScrollParams.topMargin = dp(12);
        page.addView(categoryScroll, catScrollParams);

        LinearLayout channelContainer = new LinearLayout(this);
        channelContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams channelParams = params(-1, -2);
        channelParams.topMargin = dp(4);
        page.addView(channelContainer, channelParams);

        LinearLayout pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        pager.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams pagerParams = params(-1, 48);
        pagerParams.topMargin = dp(14);
        page.addView(pager, pagerParams);
        TextView previous = actionCard("◀  PREVIOUS", false);
        TextView pageLabel = text("Page 1", 13, MUTED, true);
        pageLabel.setGravity(Gravity.CENTER);
        TextView next = actionCard("NEXT  ▶", true);
        pager.addView(previous, params(150, 42));
        pager.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        pager.addView(next, params(150, 42));
        previous.setEnabled(false);
        previous.setAlpha(0.45f);
        next.setEnabled(false);
        next.setAlpha(0.45f);

        setContentView(root);
        focusFirst(root);

        playlistExecutor.execute(() -> {
            ArrayList<String[]> channels = new ArrayList<>();
            java.util.LinkedHashMap<String, String> categories = new java.util.LinkedHashMap<>();
            String problem = null;
            try {
                String credentials = "?username=" + enc(providerUser) + "&password=" + enc(providerPass);
                try {
                    JSONArray categoryList = new JSONArray(httpGet(providerServer + "/player_api.php" + credentials + "&action=get_live_categories"));
                    for (int i = 0; i < categoryList.length(); i++) {
                        JSONObject category = categoryList.optJSONObject(i);
                        if (category == null) continue;
                        String id = category.optString("category_id", "");
                        String name = category.optString("category_name", "Other");
                        if (!id.isEmpty()) categories.put(id, name);
                    }
                } catch (Exception ignored) {
                    // Some providers do not expose category metadata. The all-channels view still works.
                }

                JSONArray list = new JSONArray(httpGet(providerServer + "/player_api.php" + credentials + "&action=get_live_streams"));
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("stream_id", "");
                    if (id.isEmpty()) continue;
                    String name = item.optString("name", "Unnamed channel");
                    String ext = item.optString("container_extension", "ts");
                    if (ext == null || ext.isEmpty() || "null".equalsIgnoreCase(ext)) ext = "ts";
                    String logo = item.optString("stream_icon", "");
                    String categoryId = item.optString("category_id", "");
                    String categoryName = categories.containsKey(categoryId) ? categories.get(categoryId) : "Other / Uncategorised";
                    String stream = providerServer + "/live/" + pathPart(providerUser) + "/" + pathPart(providerPass) + "/" + id + "." + ext;
                    channels.add(new String[]{name, categoryName, stream, logo, categoryId, id});
                }
            } catch (Exception e) { problem = safeError(e); }

            final String error = problem;
            runOnUiThread(() -> {
                if (page.getParent() == null) return;
                if (error != null) {
                    status.setText("Could not load channels: " + error);
                    return;
                }
                if (channels.isEmpty()) {
                    status.setText("Login worked, but the provider returned no live channels.");
                    return;
                }

                final String[] selectedCategory = new String[]{providerSelectedCategory};
                final int[] currentPage = new int[]{providerCurrentPage};
                final int pageSize = 50;
                java.util.LinkedHashMap<String, String> availableCategories = new java.util.LinkedHashMap<>();
                availableCategories.put("", "ALL CHANNELS");
                availableCategories.putAll(categories);
                if (categories.isEmpty()) availableCategories.put("uncategorised", "OTHER / UNCATEGORISED");

                Runnable render = new Runnable() {
                    @Override public void run() {
                        ArrayList<String[]> filtered = new ArrayList<>();
                        for (String[] channel : channels) {
                            if (selectedCategory[0].isEmpty() || selectedCategory[0].equals(channel[4])) filtered.add(channel);
                        }
                        int totalPages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
                        currentPage[0] = Math.max(0, Math.min(currentPage[0], totalPages - 1));
                        int from = currentPage[0] * pageSize;
                        int to = Math.min(from + pageSize, filtered.size());
                        channelContainer.removeAllViews();
                        for (int i = from; i < to; i++) {
                            String[] channel = filtered.get(i);
                            TextView playButton = addMediaRowReturningPlay(channelContainer, channel[0], channel[1], channel[2], channel[3], channel[5]);
                            if (channel[0].equals(lastPlayedChannelName)) playButton.requestFocus();
                        }
                        status.setText(filtered.size() + " channels • " + categories.size() + " provider categories • 50 channels per page");
                        pageLabel.setText("Page " + (currentPage[0] + 1) + " of " + totalPages);
                        previous.setEnabled(currentPage[0] > 0);
                        previous.setAlpha(currentPage[0] > 0 ? 1f : 0.45f);
                        next.setEnabled(currentPage[0] < totalPages - 1);
                        next.setAlpha(currentPage[0] < totalPages - 1 ? 1f : 0.45f);
                    }
                };

                categoryBar.removeAllViews();
                for (java.util.Map.Entry<String, String> entry : availableCategories.entrySet()) {
                    String categoryId = entry.getKey();
                    TextView chip = actionCard(entry.getValue(), categoryId.equals(selectedCategory[0]));
                    chip.setTextSize(12);
                    LinearLayout.LayoutParams chipParams = params(-2, 40);
                    chipParams.rightMargin = dp(8);
                    categoryBar.addView(chip, chipParams);
                    chip.setOnClickListener(v -> {
                        selectedCategory[0] = categoryId;
                        providerSelectedCategory = categoryId;
                        currentPage[0] = 0;
                        providerCurrentPage = 0;
                        for (int j = 0; j < categoryBar.getChildCount(); j++) {
                            View child = categoryBar.getChildAt(j);
                            if (child instanceof TextView) {
                                boolean selected = categoryId.equals(availableCategories.keySet().toArray(new String[0])[j]);
                                child.setBackground(round(selected ? RED : PANEL_LIGHT, 10));
                            }
                        }
                        render.run();
                    });
                }
                previous.setOnClickListener(v -> { currentPage[0]--; providerCurrentPage = currentPage[0]; render.run(); });
                next.setOnClickListener(v -> { currentPage[0]++; providerCurrentPage = currentPage[0]; render.run(); });
                render.run();
            });
        });
    }

    private String pathPart(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return value; }
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
        TextView provider = actionCard("▣   CONNECT YOUR IPTV PROVIDER", true);
        LinearLayout.LayoutParams providerParams = params(300, 44);
        providerParams.topMargin = dp(14);
        page.addView(provider, providerParams);
        provider.setOnClickListener(v -> showProviderLogin());
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
        addRailItem(rail, "⌂   HOME", selected.equals("HOME"), this::showHome);
        addRailItem(rail, "▣   PROVIDER LOGIN", selected.equals("PROVIDER"), this::showProviderLogin);
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
                                channels.add(new String[]{name, detail, line, logo});
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
                for (String[] channel : channels) addMediaRow(page, channel[0], channel[1], channel[2], channel.length > 3 ? channel[3] : "");
                if (channels.isEmpty()) status.setText("The playlist loaded, but no entries were tagged Canada.");
            });
        });
    }

    private String m3uAttribute(String line, String key) {
        String marker = key + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = line.indexOf('"', start);
        return end < 0 ? "" : line.substring(start, end);
    }

    private void showMovies() {
        loadProviderCatalog(false);
    }

    private void showSeries() {
        loadProviderCatalog(true);
    }

    private void loadProviderCatalog(boolean series) {
        String section = series ? "SERIES" : "MOVIES";
        LinearLayout root = createRoot(section);
        LinearLayout page = pageContent(root);
        addPageHeader(page, section, "Loading " + section.toLowerCase() + "…",
            "Connecting to your IPTV provider catalogue.");
        TextView status = text("Loading provider catalogue…", 15, MUTED, false);
        LinearLayout.LayoutParams sp = params(-1, -2);
        sp.topMargin = dp(18);
        page.addView(status, sp);
        setContentView(root);
        playlistExecutor.execute(() -> {
            ArrayList<String[]> items = new ArrayList<>();
            String problem = null;
            try {
                String credentials = "?username=" + enc(providerUser) + "&password=" + enc(providerPass);
                String action = series ? "get_series" : "get_vod_streams";
                JSONArray list = new JSONArray(httpGet(providerServer + "/player_api.php" + credentials + "&action=" + action));
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString(series ? "series_id" : "stream_id", "");
                    if (id.isEmpty()) continue;
                    String name = item.optString("name", series ? "Unnamed series" : "Unnamed movie");
                    String category = item.optString("category_id", "");
                    String logo = item.optString(series ? "cover" : "stream_icon", "");
                    String ext = item.optString("container_extension", "mp4");
                    if (ext.isEmpty() || "null".equalsIgnoreCase(ext)) ext = "mp4";
                    String url = series ? id : providerServer + "/movie/" + pathPart(providerUser) + "/"
                        + pathPart(providerPass) + "/" + id + "." + ext;
                    items.add(new String[]{name, category, url, logo, ext});
                }
            } catch (Exception e) {
                problem = safeError(e);
            }
            final String error = problem;
            runOnUiThread(() -> {
                if (isFinishing() || page.getParent() == null) return;
                page.removeView(status);
                if (error != null) {
                    status.setText("Could not load " + section.toLowerCase() + ": " + error);
                    page.addView(status, sp);
                    return;
                }
                if (items.isEmpty()) {
                    status.setText("Your provider returned no " + section.toLowerCase() + ".");
                    page.addView(status, sp);
                    return;
                }
                addPageHeader(page, section, series ? "Your series library" : "Your movie library",
                    items.size() + " titles loaded from your provider.");
                final int pageSize = 50;
                final int[] current = {0};
                LinearLayout rows = new LinearLayout(this);
                rows.setOrientation(LinearLayout.VERTICAL);
                page.addView(rows, params(-1, -2));
                LinearLayout pager = new LinearLayout(this);
                pager.setGravity(Gravity.CENTER_VERTICAL);
                pager.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams pp = params(-1, 48);
                pp.topMargin = dp(14);
                page.addView(pager, pp);
                TextView previous = actionCard("◀  PREVIOUS", false);
                TextView label = text("", 13, MUTED, true);
                label.setGravity(Gravity.CENTER);
                TextView next = actionCard("NEXT  ▶", true);
                pager.addView(previous, params(150, 42));
                pager.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                pager.addView(next, params(150, 42));
                Runnable render = new Runnable() {
                    @Override public void run() {
                        rows.removeAllViews();
                        int totalPages = Math.max(1, (items.size() + pageSize - 1) / pageSize);
                        current[0] = Math.max(0, Math.min(current[0], totalPages - 1));
                        int from = current[0] * pageSize;
                        int to = Math.min(items.size(), from + pageSize);
                        for (int i = from; i < to; i++) {
                            String[] item = items.get(i);
                            String category = item[1].isEmpty() ? "Provider catalogue" : "Category ID: " + item[1];
                            if (series) {
                                TextView open = addMediaRowReturningPlay(rows, item[0], category, "", item[3]);
                                open.setText("EPISODES");
                                open.setOnClickListener(v -> loadSeriesEpisodes(item[0], item[2]));
                            } else {
                                addMediaRow(rows, item[0], category, item[2], item[3]);
                            }
                        }
                        label.setText("Page " + (current[0] + 1) + " of " + totalPages);
                        previous.setEnabled(current[0] > 0);
                        previous.setAlpha(current[0] > 0 ? 1f : 0.45f);
                        next.setEnabled(current[0] < totalPages - 1);
                        next.setAlpha(current[0] < totalPages - 1 ? 1f : 0.45f);
                    }
                };
                previous.setOnClickListener(v -> { current[0]--; render.run(); });
                next.setOnClickListener(v -> { current[0]++; render.run(); });
                render.run();
            });
        });
    }

    private void loadSeriesEpisodes(String seriesName, String seriesId) {
        showInfo("Series", "Loading seasons and episodes for " + seriesName + "…");
        playlistExecutor.execute(() -> {
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<String> urls = new ArrayList<>();
            String problem = null;
            try {
                String credentials = "?username=" + enc(providerUser) + "&password=" + enc(providerPass);
                JSONObject response = new JSONObject(httpGet(providerServer + "/player_api.php" + credentials
                    + "&action=get_series_info&series_id=" + enc(seriesId)));
                JSONObject episodes = response.optJSONObject("episodes");
                if (episodes != null) {
                    JSONArray seasons = response.optJSONArray("seasons");
                    if (seasons != null) {
                        for (int s = 0; s < seasons.length(); s++) {
                            JSONObject season = seasons.optJSONObject(s);
                            if (season == null) continue;
                            String seasonNumber = season.optString("season_number", season.optString("name", String.valueOf(s + 1)));
                            JSONArray list = episodes.optJSONArray(seasonNumber);
                            if (list == null) continue;
                            for (int e = 0; e < list.length(); e++) {
                                JSONObject episode = list.optJSONObject(e);
                                if (episode == null) continue;
                                String id = episode.optString("id", "");
                                if (id.isEmpty()) continue;
                                String title = episode.optString("title", episode.optString("name", "Episode " + (e + 1)));
                                String ext = episode.optString("container_extension", "mp4");
                                if (ext.isEmpty() || "null".equalsIgnoreCase(ext)) ext = "mp4";
                                labels.add("Season " + seasonNumber + " • " + title);
                                urls.add(providerServer + "/series/" + pathPart(providerUser) + "/"
                                    + pathPart(providerPass) + "/" + id + "." + ext);
                            }
                        }
                    } else {
                        java.util.Iterator<String> keys = episodes.keys();
                        while (keys.hasNext()) {
                            String seasonNumber = keys.next();
                            JSONArray list = episodes.optJSONArray(seasonNumber);
                            if (list == null) continue;
                            for (int e = 0; e < list.length(); e++) {
                                JSONObject episode = list.optJSONObject(e);
                                if (episode == null) continue;
                                String id = episode.optString("id", "");
                                if (id.isEmpty()) continue;
                                String ext = episode.optString("container_extension", "mp4");
                                labels.add("Season " + seasonNumber + " • " + episode.optString("title", "Episode " + (e + 1)));
                                urls.add(providerServer + "/series/" + pathPart(providerUser) + "/"
                                    + pathPart(providerPass) + "/" + id + "." + ext);
                            }
                        }
                    }
                }
                if (urls.isEmpty()) problem = "Your provider returned no playable episodes for this series.";
            } catch (Exception e) {
                problem = safeError(e);
            }
            final String error = problem;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (error != null) {
                    showInfo(seriesName, error);
                    return;
                }
                String[] choices = labels.toArray(new String[0]);
                new AlertDialog.Builder(this).setTitle(seriesName).setItems(choices, (dialog, which) ->
                    playDemo(labels.get(which), urls.get(which)))
                    .setNegativeButton("CLOSE", null).show();
            });
        });
    }

    private void showFavourites() {
        java.util.ArrayList<String[]> items = new java.util.ArrayList<>();
        for (String name : favourites) {
            String url = favouriteUrls.get(name);
            if (url != null && !url.isEmpty()) items.add(new String[]{name, "Saved favourite", url});
        }
        showCatalog("FAVOURITES", "Your favourites",
            items.isEmpty() ? "Nothing saved yet. Use the star beside a channel or title to save it." : "Your saved channels and videos.",
            items.toArray(new String[0][]));
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
        addMediaRow(page, name, detail, url, "");
    }

    private void addMediaRow(LinearLayout page, String name, String detail, String url, String logoUrl) {
        addMediaRowReturningPlay(page, name, detail, url, logoUrl);
    }

    private TextView addMediaRowReturningPlay(LinearLayout page, String name, String detail, String url, String logoUrl) {
        return addMediaRowReturningPlay(page, name, detail, url, logoUrl, "");
    }

    private TextView addMediaRowReturningPlay(LinearLayout page, String name, String detail, String url, String logoUrl, String epgStreamId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackground(round(PANEL, 12));
        LinearLayout.LayoutParams rp = params(-1, 76);
        rp.topMargin = dp(10);
        page.addView(row, rp);

        TextView logoFallback = text(channelInitials(name), 17, WHITE, true);
        logoFallback.setGravity(Gravity.CENTER);
        logoFallback.setBackground(round(PANEL_LIGHT, 8));
        LinearLayout.LayoutParams logoParams = params(54, 54);
        logoParams.rightMargin = dp(14);
        row.addView(logoFallback, logoParams);

        if (logoUrl != null && !logoUrl.trim().isEmpty()) {
            logoFallback.setTag(logoUrl);
            imageExecutor.execute(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(logoUrl).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(12000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 CanadianIPTV/0.4");
                    android.graphics.Bitmap bitmap;
                    try (java.io.InputStream stream = conn.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(stream);
                    } finally { conn.disconnect(); }
                    if (bitmap != null) runOnUiThread(() -> {
                        if (logoUrl.equals(logoFallback.getTag())) {
                            logoFallback.setText("");
                            logoFallback.setBackgroundColor(Color.TRANSPARENT);
                            logoFallback.setCompoundDrawables(null, null, null, null);
                            logoFallback.setBackground(round(PANEL_LIGHT, 8));
                            logoFallback.setText("");
                            logoFallback.setCompoundDrawablePadding(0);
                            logoFallback.setPadding(0, 0, 0, 0);
                            logoFallback.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
                        }
                    });
                } catch (Exception ignored) { }
            });
        }

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(text(name, 16, WHITE, true));
        TextView sub = text(detail, 12, MUTED, false);
        LinearLayout.LayoutParams subp = params(-1, -2);
        subp.topMargin = dp(4);
        info.addView(sub, subp);

        TextView fav = actionCard(favourites.contains(name) ? "★" : "☆", false);
        fav.setTextSize(23);
        fav.setContentDescription("Add " + name + " to favourites");
        LinearLayout.LayoutParams fp = params(52, 46);
        fp.rightMargin = dp(8);
        row.addView(fav, fp);
        fav.setOnClickListener(v -> {
            if (favourites.contains(name)) {
                favourites.remove(name);
                favouriteUrls.remove(name);
            } else {
                favourites.add(name);
                favouriteUrls.put(name, url);
            }
            getSharedPreferences("canadian_iptv", MODE_PRIVATE).edit()
                .putStringSet("favourites", new LinkedHashSet<>(favourites))
                .putString("url_" + name, favourites.contains(name) ? url : "")
                .apply();
            fav.setText(favourites.contains(name) ? "★" : "☆");
        });

        if (epgStreamId != null && !epgStreamId.isEmpty()) {
            TextView details = actionCard("DETAILS", false);
            details.setTextSize(11);
            LinearLayout.LayoutParams detailsParams = params(82, 42);
            detailsParams.rightMargin = dp(6);
            row.addView(details, detailsParams);
            details.setOnClickListener(v -> loadProgrammeDetails(name, epgStreamId));
        }

        TextView play = actionCard("▶  PLAY", true);
        row.addView(play, params(92, 42));
        play.setOnClickListener(v -> playDemo(name, url));
        row.setFocusable(false);
        row.setClickable(false);
        return play;
    }

    private void loadProgrammeDetails(String channelName, String streamId) {
        AlertDialog loading = new AlertDialog.Builder(this)
            .setTitle("Programme details")
            .setMessage("Loading programme information for " + channelName + "…")
            .setCancelable(false)
            .create();
        loading.show();
        playlistExecutor.execute(() -> {
            String heading = null;
            String problem = null;
            StringBuilder details = new StringBuilder();
            try {
                String credentials = "?username=" + enc(providerUser) + "&password=" + enc(providerPass);
                String address = providerServer + "/player_api.php" + credentials
                    + "&action=get_short_epg&stream_id=" + enc(streamId) + "&limit=5";
                JSONObject response = new JSONObject(httpGet(address));
                JSONArray programmes = response.optJSONArray("epg_list");
                if (programmes == null) programmes = response.optJSONArray("epg");
                if (programmes == null) programmes = response.optJSONArray("epg_listings");
                if (programmes == null || programmes.length() == 0) {
                    problem = "Your provider returned no EPG listings for this channel. The channel can still play normally, but programme information may not be included in your IPTV subscription.";
                } else {
                    for (int i = 0; i < programmes.length(); i++) {
                        JSONObject programme = programmes.optJSONObject(i);
                        if (programme == null) continue;
                        String programmeTitle = programme.optString("title", "Untitled programme");
                        String description = programme.optString("description", "");
                        if (description.isEmpty() || "null".equalsIgnoreCase(description)) {
                            description = programme.optString("desc", "No description supplied.");
                        }
                        String start = epgTime(programme, "start", "start_timestamp");
                        String end = epgTime(programme, "end", "stop_timestamp");
                        if (details.length() > 0) details.append("\n\n");
                        details.append(programmeTitle);
                        if (!start.isEmpty() || !end.isEmpty()) {
                            details.append("\n").append(start);
                            if (!start.isEmpty() && !end.isEmpty()) details.append(" - ");
                            details.append(end);
                        }
                        details.append("\n").append(description);
                    }
                    heading = channelName;
                    if (details.length() == 0) {
                        problem = "The provider returned EPG data, but it was in an unreadable format.";
                    }
                }
            } catch (Exception e) {
                problem = "Could not retrieve programme information: " + safeError(e);
            }
            final String dialogTitle = heading;
            final String message = problem == null ? details.toString() : problem;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                loading.dismiss();
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(dialogTitle == null ? "Programme details" : dialogTitle)
                    .setMessage(message)
                    .setPositiveButton("CLOSE", null)
                    .show();
            });
        });
    }

    private String epgTime(JSONObject programme, String textField, String timestampField) {
        String value = programme.optString(textField, "");
        if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        long timestamp = programme.optLong(timestampField, 0L);
        if (timestamp <= 0L) return "";
        if (timestamp < 100000000000L) timestamp *= 1000L;
        return new java.text.SimpleDateFormat("EEE, MMM d • h:mm a", java.util.Locale.getDefault())
            .format(new java.util.Date(timestamp));
    }

    private String channelInitials(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9 ]", " ").trim();
        String[] words = clean.split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "TV";
        if (words.length == 1) return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase(java.util.Locale.ROOT);
        return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT);
    }

    private void playDemo(String title, String url) {
        lastPlayedChannelName = title;
        // Keep hardware acceleration as the normal path, but allow Media3 to try
        // alternate available decoders when the device's preferred codec fails.
        if (activePlayerView != null) activePlayerView.setPlayer(null);
        if (activePlayer != null) {
            activePlayer.release();
            activePlayer = null;
        }

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true);
        ExoPlayer player = new ExoPlayer.Builder(this, renderers).build();
        activePlayer = player;
        PlayerView playerView = new PlayerView(this);
        activePlayerView = playerView;
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setBackgroundColor(Color.BLACK);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        playerOpen = true;
        setContentView(frame);

        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                String detail = error.getMessage();
                if (detail == null || detail.trim().isEmpty()) detail = "No additional decoder or network details were provided.";
                String message = "Channel: " + title
                    + "\\nError: " + error.getErrorCodeName()
                    + "\\n\\n" + detail
                    + "\\n\\nThis can happen when a stream is damaged, offline, geo-restricted, or uses a video profile the TV cannot decode. Decoder fallback is enabled.";
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Playback issue")
                    .setMessage(message)
                    .setPositiveButton("RETRY", (dialog, which) -> playDemo(title, url))
                    .setNegativeButton("BACK TO CHANNELS", (dialog, which) -> {
                        playerOpen = false;
                        if (activePlayerView != null) activePlayerView.setPlayer(null);
                        if (activePlayer != null) {
                            activePlayer.release();
                            activePlayer = null;
                        }
                        activePlayerView = null;
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        if (!providerServer.isEmpty() && !providerUser.isEmpty()) showProviderLiveTv();
                        else showHome();
                    })
                    .show();
            }
        });

        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();
    }

    @Override public void onBackPressed() {
        if (playerOpen) {
            playerOpen = false;
            if (activePlayerView != null) activePlayerView.setPlayer(null);
            if (activePlayer != null) {
                activePlayer.release();
                activePlayer = null;
            }
            activePlayerView = null;
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (!providerServer.isEmpty() && !providerUser.isEmpty()) showProviderLiveTv();
            else showHome();
            return;
        }
        super.onBackPressed();
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
        view.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable background = round(primary ? RED : (focused ? RED : PANEL_LIGHT), 10);
            if (focused) background.setStroke(dp(3), WHITE);
            view.setBackground(background);
        });
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
