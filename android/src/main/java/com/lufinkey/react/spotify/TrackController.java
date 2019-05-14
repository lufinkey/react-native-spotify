package com.lufinkey.react.spotify;

import android.annotation.TargetApi;
import android.media.AudioTrack;
import android.os.Build;

import com.spotify.sdk.android.player.AudioController;
import com.spotify.sdk.android.player.AudioRingBuffer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class TrackController implements AudioController {
    private final AudioRingBuffer mAudioBuffer = new AudioRingBuffer(81920);
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final Object mPlayingMutex = new Object();
    private AudioTrack mAudioTrack;
    private float volume = 1;
    private int mSampleRate;
    private int mChannels;
    private final Runnable mAudioRunnable = new Runnable() {
        final short[] pendingSamples = new short[4096];

        public void run() {
            int itemsRead = TrackController.this.mAudioBuffer.peek(this.pendingSamples);
            if (itemsRead > 0) {
                int itemsWritten = TrackController.this.writeSamplesToAudioOutput(this.pendingSamples, itemsRead);
                TrackController.this.mAudioBuffer.remove(itemsWritten);
            }

        }
    };

    public TrackController() {
    }

    public AudioTrack getAudioTrack() {
        return mAudioTrack;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (this.mAudioTrack != null) {
            if (Build.VERSION.SDK_INT >= 21) {
                this.mAudioTrack.setVolume(this.volume);
            } else {
                this.mAudioTrack.setStereoVolume(this.volume, this.volume);
            }
        }
    }

    public int onAudioDataDelivered(short[] samples, int sampleCount, int sampleRate, int channels) {
        if (this.mAudioTrack != null && (this.mSampleRate != sampleRate || this.mChannels != channels)) {
            synchronized(this.mPlayingMutex) {
                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }
        }

        this.mSampleRate = sampleRate;
        this.mChannels = channels;
        if (this.mAudioTrack == null) {
            this.createAudioTrack(sampleRate, channels);
        }

        try {
            this.mExecutorService.execute(this.mAudioRunnable);
        } catch (RejectedExecutionException e) { }

        return this.mAudioBuffer.write(samples, sampleCount);
    }

    public void onAudioFlush() {
        this.mAudioBuffer.clear();
        if (this.mAudioTrack != null) {
            synchronized(this.mPlayingMutex) {
                this.mAudioTrack.pause();
                this.mAudioTrack.flush();
                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }
        }

    }

    public void onAudioPaused() {
        if (this.mAudioTrack != null) {
            this.mAudioTrack.pause();
        }

    }

    public void onAudioResumed() {
        if (this.mAudioTrack != null) {
            this.mAudioTrack.play();
        }

    }

    public void start() {
    }

    public void stop() {
        this.mExecutorService.shutdown();
    }

    @TargetApi(21)
    private void createAudioTrack(int sampleRate, int channels) {
        byte channelConfig;
        switch(channels) {
            case 0:
                throw new IllegalStateException("Input source has 0 channels");
            case 1:
                channelConfig = 4;
                break;
            case 2:
                channelConfig = 12;
                break;
            default:
                throw new IllegalArgumentException("Unsupported input source has " + channels + " channels");
        }

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, 2) * 2;
        synchronized(this.mPlayingMutex) {
            this.mAudioTrack = new AudioTrack(3, sampleRate, channelConfig, 2, bufferSize, 1);
            if (this.mAudioTrack.getState() == 1) {
                setVolume(this.volume);
                this.mAudioTrack.play();
            } else {
                this.mAudioTrack.release();
                this.mAudioTrack = null;
            }

        }
    }

    private int writeSamplesToAudioOutput(short[] samples, int samplesCount) {
        if (this.isAudioTrackPlaying()) {
            int itemsWritten = this.mAudioTrack.write(samples, 0, samplesCount);
            if (itemsWritten > 0) {
                return itemsWritten;
            }
        }

        return 0;
    }

    private boolean isAudioTrackPlaying() {
        return this.mAudioTrack != null && this.mAudioTrack.getPlayState() == 3;
    }
}
