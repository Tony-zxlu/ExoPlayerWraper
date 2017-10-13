package com.tony.exoplayerdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.util.Util;

/**
 * Created by tony on 2017/10/12.
 * 1、MediaSource - 负责装载 media，装载到MediaSource 的 media 可以被读取，MediaSource 在调用 ExoPlayer.prepare 方法时被注入。
 * 2、Render S - 用于渲染 media 的部件，在创建播放器时被注入。
 * 3、TrackSelector - 从MediaSource 中选出 media 提供给可用的 Render S 来渲染，在创建播放器时被注入。
 * 4、LoadControl - 控制 MediaSource 缓存更多的 media，有多少 media 被缓冲。在创建播放器时被注入。
 */

public class PlayerActivity extends Activity {

    private SimpleExoPlayerViewWrapper simpleExoPlayerViewWrapper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_activity);
        simpleExoPlayerViewWrapper = findViewById(R.id.player_view);
        String url = getIntent().getStringExtra("url");
        simpleExoPlayerViewWrapper.setUp(url, "http://video.miuapp.com/video/20170807/2/1206066_95de71407a7c203dc4e9a8cde57f00af.jpg", true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            simpleExoPlayerViewWrapper.initializePlayer();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || simpleExoPlayerViewWrapper.getPlayer() == null)) {
            simpleExoPlayerViewWrapper.initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            simpleExoPlayerViewWrapper.releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            simpleExoPlayerViewWrapper.releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


}
