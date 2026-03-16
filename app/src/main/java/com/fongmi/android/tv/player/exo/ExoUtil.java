package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExoUtil {

    /**
     * 获取 User-Agent
     */
    public static String getUa() {
        return Util.getUserAgent(App.get(), BuildConfig.APPLICATION_ID);
    }

    /**
     * 构建 LoadControl（缓冲控制）
     */
    public static LoadControl buildLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * Setting.getBuffer(),
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * Setting.getBuffer(),
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build();
    }

    /**
     * 构建 TrackSelector（轨道选择器）
     */
    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (Setting.isPreferAAC()) {
            builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        }
        builder.setPreferredTextLanguage(Locale.getDefault().getISO3Language());
        builder.setTunnelingEnabled(Setting.isTunnel());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    /**
     * 构建 RenderersFactory（渲染器工厂）
     */
    public static RenderersFactory buildRenderersFactory(int renderMode) {
        return new DefaultRenderersFactory(App.get())
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(renderMode);
    }

    /**
     * 构建 MediaSource Factory
     */
    public static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    /**
     * 获取字幕样式
     */
    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption()
                ? CaptionStyleCompat.createFromCaptionStyle(
                        ((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle())
                : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    /**
     * 设置字幕视图样式
     */
    public static void setSubtitleView(PlayerView exo) {
        exo.getSubtitleView().setStyle(getCaptionStyle());
        exo.getSubtitleView().setApplyEmbeddedStyles(true);
        exo.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (Setting.getSubtitleTextSize() != 0) {
            exo.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
        }
    }

    /**
     * 根据文件路径获取 MIME 类型
     */
    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    /**
     * 根据错误码返回 fallback MIME 类型
     * 修复点：MimeTypes.APPLICATION_OCTET_STREAM 已移除，直接返回字符串 "application/octet-stream"
     */
    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) {
            return "application/octet-stream";  // 修复在这里：使用字符串字面量
        }
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
            return MimeTypes.APPLICATION_M3U8;
        }
        return null;
    }

    /**
     * 构建 MediaItem
     */
    public static MediaItem getMediaItem(Map<String, String> headers, Uri uri, String mimeType, Drm drm, List<Sub> subs, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri));
        builder.setSubtitleConfigurations(getSubtitleConfigs(subs));
        if (drm != null) builder.setDrmConfiguration(drm.get());
        if (mimeType != null) builder.setMimeType(mimeType);
        builder.setMediaId(uri.toString());
        builder.setImageDurationMs(15000);
        return builder.build();
    }

    private static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri) {
        Bundle extras = new Bundle();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            extras.putString(header.getKey(), header.getValue());
        }
        return new MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .setExtras(extras)
                .build();
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) {
            for (Sub sub : subs) {
                configs.add(sub.config());
            }
        }
        return configs;
    }
}
