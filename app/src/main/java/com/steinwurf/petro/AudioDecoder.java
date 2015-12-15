/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.steinwurf.petro;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

/**
 *
 * @author taehwan
 *
 */
public class AudioDecoder  extends Thread {
    private static final int TIMEOUT_US = 10000;
    private static final String TAG = "AudioDecoder";
    private MediaCodec mDecoder;

    private boolean mEosReceived;
    private int mSampleRate = 0;

    public boolean init(int audioProfile, int sampleRateIndex,
                        int channelConfig) {
        Log.d(TAG, "init(" + audioProfile + ", " + sampleRateIndex + ", " + channelConfig + ")");
        mEosReceived = false;

        try {
            mDecoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mSampleRate = new int[]{
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000}[sampleRateIndex];

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);
        //format.setInteger(MediaFormat.KEY_IS_ADTS, 1);

        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put((byte) ((audioProfile << 3) | (sampleRateIndex >> 1)));

        csd.position(1);
        csd.put((byte) ((byte) ((sampleRateIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        format.setByteBuffer("csd-0", csd); // add csd-0

        if (format == null) {
            Log.e(TAG, "Can't create format!");
            return false;
        }

        mDecoder.configure(format, null, null, 0);

        if (mDecoder == null) {
            Log.e(TAG, "Can't find audio info!");
            return false;
        }
        return true;
    }

    @Override
    public void run() {

        mDecoder.start();
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
        ByteBuffer[] outputBuffers = mDecoder.getOutputBuffers();

        BufferInfo info = new BufferInfo();

        // create an audiotrack object
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT),
                AudioTrack.MODE_STREAM);
        audioTrack.play();

        long sampleTime =  0;
        int i = 0;
        while (!mEosReceived) {
            int inputIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputIndex];
                int sampleIndex = i % NativeInterface.getAudioSampleCount();
                byte[] data = NativeInterface.getAudioSample(sampleIndex);
                i++;
                inputBuffer.clear();
                inputBuffer.put(data);
                inputBuffer.clear();
                int sampleSize = data.length;
                if (sampleSize < 0) {
                    // We shouldn't stop the playback at this point, just pass the EOS
                    // flag to mDecoder, we will get it again from the
                    // dequeueOutputBuffer
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {

                    sampleTime += 10000;//NativeInterface.getAudioTimeToSample(sampleIndex) * 1000;
                    mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                }

                int outIndex = mDecoder.dequeueOutputBuffer(info, TIMEOUT_US);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = mDecoder.getOutputBuffers();
                        break;

                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        MediaFormat format = mDecoder.getOutputFormat();
                        Log.d(TAG, "New format " + format);
                        audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer timed out!");
                        break;

                    default:
                        ByteBuffer outBuffer = outputBuffers[outIndex];
                        Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + outBuffer);

                        final byte[] chunk = new byte[info.size];
                        outBuffer.get(chunk); // Read the buffer all at once
                        outBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

                        audioTrack.write(chunk, info.offset, info.offset + info.size); // AudioTrack write data
                        mDecoder.releaseOutputBuffer(outIndex, false);
                        break;
                }

                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }
        }

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;

        audioTrack.stop();
        audioTrack.release();
    }

    public void close()
    {
        mEosReceived = true;
    }

}
