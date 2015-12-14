package com.steinwurf.petro;

import android.util.Log;

public class NativeInterface {

    private static final String TAG = "NativeInterface";

    private static long native_context;

    static {
        Log.d(TAG, "Loading petro android library");
        System.loadLibrary("petro_android");
    }

    interface NativeInterfaceListener
    {
        void onInitialized();
    }

    private static NativeInterfaceListener mListener;

    public static void setNativeInterfaceListener(NativeInterfaceListener listener)
    {
        mListener = listener;
    }

    public static void onInitialized()
    {
        if (mListener != null)
            mListener.onInitialized();
    }

    public static native void nativeInitialize(String mp4_file);
    public static native byte[] getVideoSample(int index);
    public static native byte[] getAudioSample(int index);
    public static native byte[] getVideoPPS();
    public static native byte[] getVideoSPS();
    public static native int getVideoTimeToSample(int index);
    public static native int getAudioSampleRate();
    public static native int getAudioChannelCount();
}