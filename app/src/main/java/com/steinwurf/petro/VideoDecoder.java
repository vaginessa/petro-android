package com.steinwurf.petro;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by jpihl on 11/27/15.
 */
public class VideoDecoder extends Thread {
    private static final int TIMEOUT_US = 10000;

    private static final String TAG = "VideoDecoder";
    private static final String MIME = "video/avc";

    private MediaCodec mDecoder;

    private boolean mEosReceived;

    public boolean init(Surface surface, byte[] sps, byte[] pps)
    {
        int width = NativeInterface.getVideoWidth();
        int height = NativeInterface.getVideoHeight();
        try {
            mDecoder = MediaCodec.createDecoderByType(MIME);
            MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);

            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
            format.setInteger("durationUs", Integer.MAX_VALUE);

            mDecoder.configure(format, surface, null, 0 /* Decoder */);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
    @Override
    public void run() {
        mDecoder.start();
        BufferInfo info = new BufferInfo();
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        mDecoder.getOutputBuffers();

        boolean isInput = true;
        boolean first = true;
        long startWhen = 0;
        long sampleTime =  0;
        int i = 0;
        while (!mEosReceived) {
            if (isInput) {
                int inputIndex = mDecoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    // fill inputBuffers[inputBufferIndex] with valid data
                    ByteBuffer inputBuffer = inputBuffers[inputIndex];
                    int sampleIndex = i % NativeInterface.getVideoSampleCount();
                    byte[] data = NativeInterface.getVideoSample(sampleIndex);
                    i++;
                    inputBuffer.clear();
                    inputBuffer.put(data);
                    inputBuffer.clear();
                    int sampleSize = data.length;

                    if (sampleSize > 0) {

                        sampleTime += NativeInterface.getVideoTimeToSample(sampleIndex) * 1000;
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);

                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, TIMEOUT_US);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mDecoder.getOutputBuffers();
                    break;

                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:
    				Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                    break;

                default:
                    if (first) {
                        startWhen = System.currentTimeMillis();
                        first = false;
                    }
                    try {
                        long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startWhen);
                        Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 1000) + " playTime: " + (System.currentTimeMillis() - startWhen) + " sleepTime : " + sleepTime);

                        if (sleepTime > 0)
                            Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    mDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }

        mDecoder.stop();
        mDecoder.release();
    }

    public void close()
    {
        mEosReceived = true;
    }
}
