// Copyright (c) 2014 Steinwurf ApS
// All Rights Reserved
//
// Distributed under the "BSD License". See the accompanying LICENSE.rst file.

package com.steinwurf.petro;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class AudioExtractorActivity extends AppCompatActivity
{
    private static final String TAG = "AudioExtractorActivity";

    private AudioExtractorDecoder mAudioDecoder;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String filePath = intent.getStringExtra(MainActivity.FILEPATH);

        mAudioDecoder = new AudioExtractorDecoder();
        mAudioDecoder.init(filePath);
        mAudioDecoder.start();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        if (mAudioDecoder != null)
        {
            mAudioDecoder.close();
        }
    }
}
