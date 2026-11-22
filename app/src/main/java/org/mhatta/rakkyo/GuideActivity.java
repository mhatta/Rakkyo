package org.mhatta.rakkyo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** "Using it from other apps": how to point a browser or the Wi-Fi proxy setting at the proxy. */
public final class GuideActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        UiUtil.edgeToEdge(this, findViewById(R.id.root), findViewById(R.id.scroll));
        ((TextView) findViewById(R.id.header_title)).setText(R.string.guide_title);
        findViewById(R.id.header_back).setOnClickListener(v -> finish());
        findViewById(R.id.guide_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The ports as they are set now, which is what the user will enter.
        final Prefs prefs = new Prefs(this);
        final String host = TorController.LISTEN_ADDRESS;
        final boolean password = prefs.isPasswordRequired();
        ((TextView) findViewById(R.id.guide_socks_body)).setText(getString(R.string.guide_socks_body, host, prefs.getSocksPort()));
        UiUtil.show(findViewById(R.id.guide_socks_password), password);
        final TextView http = findViewById(R.id.guide_http_body);
        if (!prefs.isHttpEnabled()) {
            http.setText(R.string.guide_http_off);
        } else if (password) {
            http.setText(getString(R.string.guide_http_body, host, prefs.getHttpPort()) + "\n\n" + getString(R.string.guide_http_password));
        } else {
            http.setText(getString(R.string.guide_http_body, host, prefs.getHttpPort()));
        }
    }
}
