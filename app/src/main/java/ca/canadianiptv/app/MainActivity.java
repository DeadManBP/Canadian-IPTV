package ca.canadianiptv.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32,32,32,32);
        root.setBackgroundColor(Color.rgb(20,22,27));
        TextView title = new TextView(this);
        title.setText("CANADIAN IPTV");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Version 0.1 • Android TV foundation");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);
        Button button = new Button(this);
        button.setText("Ready for setup");
        button.setFocusable(true);
        button.setOnClickListener(v -> subtitle.setText("Remote control input works!"));
        root.addView(button);
        setContentView(root);
        button.requestFocus();
    }
}
