package com.tony.exoplayerdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created by tony on 2017/10/12.
 */

public class MainActivity extends Activity {

    private EditText urlTxt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        urlTxt = findViewById(R.id.media_url_txt);
    }


    public void play(View view) {
        String url = urlTxt.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(this, "请填写url", Toast.LENGTH_SHORT).show();
        } else {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("url", url);
            startActivity(intent);
        }
    }
}
