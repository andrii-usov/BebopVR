package com.ericsson.bebopvr.dron.video;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.parrot.arsdk.arcontroller.ARCONTROLLER_STREAM_CODEC_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BebopVideoCodec {

    private static final String TAG = "BebopVideoCodec";
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final int VIDEO_DEQUEUE_TIMEOUT = 33000;

    private Surface surface;
    private MediaCodec mMediaCodec;
    private Lock mReadyLock;
    private boolean mIsCodecConfigured = false;

    private ByteBuffer mSpsBuffer;
    private ByteBuffer mPpsBuffer;
    private ByteBuffer[] mBuffers;

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 368;

    public BebopVideoCodec() {
        mReadyLock = new ReentrantLock();
    }

    public void displayFrame(ARFrame frame) {
        mReadyLock.lock();

        if ((mMediaCodec != null && this.surface != null)) {
            if (mIsCodecConfigured) {
                // Here we have either a good PFrame, or an IFrame
                int index = -1;

                try {
                    index = mMediaCodec.dequeueInputBuffer(VIDEO_DEQUEUE_TIMEOUT);
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error while dequeue input buffer");
                }
                if (index >= 0) {
                    ByteBuffer b;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        b = mMediaCodec.getInputBuffer(index);
                    } else {
                        b = mBuffers[index];
                        b.clear();
                    }

                    if (b != null) {
                        b.put(frame.getByteData(), 0, frame.getDataSize());
                    }

                    try {
                        mMediaCodec.queueInputBuffer(index, 0, frame.getDataSize(), 0, 0);
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Error while queue input buffer");
                    }
                }
            }

            // Try to display previous frame
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outIndex;
            try {
                outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);

                while (outIndex >= 0) {
                    mMediaCodec.releaseOutputBuffer(outIndex, true);
                    outIndex = mMediaCodec.dequeueOutputBuffer(info, 0);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error while dequeue input buffer (outIndex)");
            }
        }


        mReadyLock.unlock();
    }

    public void configureDecoder(ARControllerCodec codec) {
        mReadyLock.lock();

        if (this.surface != null && !this.mIsCodecConfigured) {
            if (codec.getType() == ARCONTROLLER_STREAM_CODEC_TYPE_ENUM.ARCONTROLLER_STREAM_CODEC_TYPE_H264) {
                ARControllerCodec.H264 codecH264 = codec.getAsH264();

                mSpsBuffer = ByteBuffer.wrap(codecH264.getSps().getByteData());
                mPpsBuffer = ByteBuffer.wrap(codecH264.getPps().getByteData());
            }

            if ((mMediaCodec != null) && (mSpsBuffer != null)) {
                configureMediaCodec();
            }
        }

        mReadyLock.unlock();
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
        initMediaCodec(VIDEO_MIME_TYPE);
    }

    private void configureMediaCodec() {
        mMediaCodec.stop();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, VIDEO_WIDTH, VIDEO_HEIGHT);
        format.setByteBuffer("csd-0", mSpsBuffer);
        format.setByteBuffer("csd-1", mPpsBuffer);

        mMediaCodec.configure(format, this.surface, null, 0);
        mMediaCodec.start();

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            mBuffers = mMediaCodec.getInputBuffers();
        }
        mIsCodecConfigured = true;
    }

    private void initMediaCodec(String type) {
        if ( !this.mIsCodecConfigured ) {
            try {
                mMediaCodec = MediaCodec.createDecoderByType(type);
            } catch (IOException e) {
                Log.e(TAG, "Exception", e);
            }

            if ((mMediaCodec != null) && (mSpsBuffer != null)) {
                configureMediaCodec();
            }
        }
    }

    public void releaseMediaCodec() {
        if (mMediaCodec != null) {
            if (mIsCodecConfigured) {
                mMediaCodec.stop();
                mMediaCodec.release();
            }
            mIsCodecConfigured = false;
            mMediaCodec = null;
        }
    }

}