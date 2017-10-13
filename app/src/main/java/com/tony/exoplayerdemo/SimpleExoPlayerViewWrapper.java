package com.tony.exoplayerdemo;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extension.LocalCacheDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * Created by tony on 2017/10/13.
 * SimpleExoPlayerView包装类
 * 1、MediaSource - 负责装载 media，装载到MediaSource 的 media 可以被读取，MediaSource 在调用 ExoPlayer.prepare 方法时被注入。
 * 2、Render S - 用于渲染 media 的部件，在创建播放器时被注入。
 * 3、TrackSelector - 从MediaSource 中选出 media 提供给可用的 Render S 来渲染，在创建播放器时被注入。
 * 4、LoadControl - 控制 MediaSource 缓存更多的 media，有多少 media 被缓冲。在创建播放器时被注入。
 */

public class SimpleExoPlayerViewWrapper extends FrameLayout implements ExoplayerLog, View.OnClickListener {

    ///////////////////////////////////////////////////////////////////////////
    // Cookie
    ///////////////////////////////////////////////////////////////////////////
    private static final CookieManager DEFAULT_COOKIE_MANAGER;

    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter(); //网络带宽检测计

    private static final long MAX_CACHE_SIZE = 1000 * 1024 * 1024;//缓存最大空间
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;//缓存最大文件数

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SimpleExoPlayerView simpleExoPlayerView;
    private ProgressBar loadingView;
    private LinearLayout errorLayout;
    private RelativeLayout rootView;
    private TextView retryTxt;
    private ImageView playIv;
    private TextView noWifiTxt;

    private SimpleExoPlayer player;//播放器对象
    private DefaultTrackSelector trackSelector;
    private boolean inErrorState;
    private EventLogger eventLogger;
    private int resumeWindow;
    private long resumePosition; //记住播放的位置
    private DataSource.Factory mediaDataSourceFactory; //MediaSource工厂，视频本地缓存通过工厂实现

    public SimpleExoPlayerViewWrapper(@NonNull Context context) {
        this(context, null);
    }

    public SimpleExoPlayerViewWrapper(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SimpleExoPlayerViewWrapper(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.exo_simple_player_wrapper_view, this);
        rootView = findViewById(R.id.root);
        simpleExoPlayerView = findViewById(R.id.simpleExoPlayerView);
        loadingView = findViewById(R.id.loading_view);
        errorLayout = findViewById(R.id.error);
        retryTxt = findViewById(R.id.retry);
        playIv = findViewById(R.id.play_iv);
        noWifiTxt = findViewById(R.id.no_wifi_tv);

        rootView.setOnClickListener(this);
        retryTxt.setOnClickListener(this);
        playIv.setOnClickListener(this);
        clearResumePosition();
        //Cookie相关
        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }
        mediaDataSourceFactory = new LocalCacheDataSourceFactory(getContext(), MAX_CACHE_SIZE, MAX_FILE_SIZE);
        simpleExoPlayerView.getUseController();
    }

    private String videoUrl;
    private String coverUrl;
    private boolean shouldAutoPlay;

    public void setUp(String videoUrl, String coverUrl, boolean shouldAutoPlay) {
        this.videoUrl = videoUrl;
        this.coverUrl = coverUrl;
        this.shouldAutoPlay = shouldAutoPlay;
        if (DEBUG) {
            Log.d(TAG, "setUp[videoUrl:" + videoUrl + ",coverUrl:" + coverUrl + ",shouldAutoPlay:" + shouldAutoPlay + "]");
        }
    }

    public SimpleExoPlayer getPlayer() {
        return player;
    }

    /**
     * 初始化播放器
     */
    public void initializePlayer() {
        simpleExoPlayerView.setCoverView(coverUrl);
        errorLayout.setVisibility(View.GONE);
        boolean needNewPlayer = (player == null);
        if (DEBUG) {
            Log.d(TAG, "initializePlayer--coverUrl:" + coverUrl + ",videoUrl:" + videoUrl + ",needNewPlayer:" + needNewPlayer);
        }
        if (needNewPlayer) {
            TrackSelection.Factory adaptiveTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(adaptiveTrackSelectionFactory);

            eventLogger = new EventLogger(trackSelector);

            @DefaultRenderersFactory.ExtensionRendererMode
            int extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(getContext(), null, extensionRendererMode);

            player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
            player.addListener(mEventListener);
            player.addListener(eventLogger);
            player.addMetadataOutput(eventLogger);
            player.setAudioDebugListener(eventLogger);
            player.setVideoDebugListener(eventLogger);

            simpleExoPlayerView.setPlayer(player);
            player.setPlayWhenReady(shouldAutoPlay);
        }
        Uri uri = Uri.parse(videoUrl);
//        if (Util.maybeRequestReadExternalStoragePermission(this, uri)) {
//            // The player will be reinitialized if the permission is granted.
//            return;
//        }

        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
        if (haveResumePosition) {
            player.seekTo(resumeWindow, resumePosition);
        }

        MediaSource mediaSource = new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                mainHandler, eventLogger);
        player.prepare(new LoopingMediaSource(mediaSource), !haveResumePosition, false);
        inErrorState = false;
    }

    /**
     * 更新播放进度
     */
    private void updateResumePosition() {
        resumeWindow = player.getCurrentWindowIndex();
        resumePosition = Math.max(0, player.getContentPosition());
    }

    /**
     * 清除播放记忆
     */
    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    //播放过程中的回调
    private Player.EventListener mEventListener = new Player.EventListener() {
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
            // Do nothing.
            Log.d(TAG, "onTimelineChanged---[timeline:" + timeline + ",manifest:" + manifest + "]");
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            Log.d(TAG, "onLoadingChanged---[isLoading:" + isLoading + "]");
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            Log.d(TAG, "onPlayerStateChanged---[playWhenReady:" + playWhenReady + ",playbackState:" + playbackState + "]");
        }

        @Override
        public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            Log.d(TAG, "onRepeatModeChanged---[repeatMode:" + repeatMode + "]");
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.d(TAG, "onPlayerError---[error:" + error + "]");
            //
            errorLayout.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPositionDiscontinuity() {
            Log.d(TAG, "onPositionDiscontinuity---[inErrorState:" + inErrorState + "]");
            if (inErrorState) {
                // This will only occur if the user has performed a seek whilst in the error state. Update the
                // resume position so that if the user then retries, playback will resume from the position to
                // which they seeked.
                updateResumePosition();
            }
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            // Do nothing.
            Log.d(TAG, "onPlaybackParametersChanged---[playbackParameters:" + playbackParameters + "]");
        }
    };

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.retry:
                initializePlayer();
                break;
            case R.id.root:
            case R.id.play_iv:
                boolean loading = player.isLoading();
                int playbackState = player.getPlaybackState();
                boolean playWhenReady = player.getPlayWhenReady();
                if (DEBUG) {
                    Log.d(TAG, "点击卡片[loading:" + loading + ",playbackState:" + playbackState + ",playWhenReady:" + playWhenReady + "]");
                }
                if (playWhenReady) {
                    pause();
                } else {
                    resumePlay();
                }
                break;
        }
    }

    public void pause() {
        playIv.setVisibility(VISIBLE);
        player.setPlayWhenReady(false);
    }

    public void resumePlay() {
        playIv.setVisibility(GONE);
        player.setPlayWhenReady(true);
    }

    public void releasePlayer() {
        if (player != null) {
            shouldAutoPlay = player.getPlayWhenReady();
            updateResumePosition();
            player.release();
            player = null;
            trackSelector = null;
            eventLogger = null;
        }
    }

}
