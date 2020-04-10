/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.filament.pagecurl;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.Filament;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.Material;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.Texture;
import com.google.android.filament.Viewport;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.android.UiHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

public class MainActivity extends Activity
        implements Choreographer.FrameCallback, UiHelper.RendererCallback {
    static {
        Filament.init();
    }
    private UiHelper mUiHelper;
    private Choreographer mChoreographer;
    private Engine mEngine;
    private SwapChain mSwapChain;
    private com.google.android.filament.View mView;
    private Renderer mRenderer;
    private Camera mCamera;

    @Override
    public void onNativeWindowChanged(Surface surface) {
        if (mSwapChain != null) {
            mEngine.destroySwapChain(mSwapChain);
        }
        mSwapChain = mEngine.createSwapChain(surface);
    }

    @Override
    public void onDetachedFromSurface() {
        if (mSwapChain != null) {
            mEngine.destroySwapChain(mSwapChain);
            // Required to ensure we don't return before Filament is done executing the
            // destroySwapChain command, otherwise Android might destroy the Surface
            // too early
            mEngine.flushAndWait();
            mSwapChain = null;
        }
    }

    @Override
    public void onResized(int width, int height) {
        float aspect = (float)width / (float)height;
        mCamera.setProjection(90.0, aspect, 0.1, 1000.0, Camera.Fov.HORIZONTAL);
        mView.setViewport(new Viewport(0, 0, width, height));
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mChoreographer.postFrameCallback(this);
        if (mUiHelper.isReadyToRender()) {
            mRenderer.beginFrame(mSwapChain);
            mRenderer.render(mView);
            mRenderer.endFrame();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView surface = findViewById(R.id.render_surface);
        mChoreographer = Choreographer.getInstance();
        mUiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        mUiHelper.setRenderCallback(this);
        // call this to set a specific size for the underlying surace
        // mUiHelper.setDesiredSize(1280, 720);
        mUiHelper.attachTo(surface);
        mEngine = Engine.create();
        mRenderer = mEngine.createRenderer();
        Scene scene = mEngine.createScene();
        mView = mEngine.createView();
        mCamera = mEngine.createCamera();
        mView.setClearColor(1, 0,0,1);
        mView.setCamera(mCamera);
        mView.setScene(scene);
//        EntityManager em = EntityManager.get();
//        @Entity int entity = em.create();
//        em.destroy(entity);
//        TransformManager tm = mEngine.getTransformManager();
//        tm.create(entity, 0, null);
        int[] cm = new int[2 * 2 * 6];
        cm[ 0] = 0x200000FF;
        cm[ 1] = 0x20000000;
        cm[ 2] = 0x20000000;
        cm[ 3] = 0x200000FF;
        cm[ 4] = 0x2000FF00;
        cm[ 5] = 0x20000000;
        cm[ 6] = 0x20000000;
        cm[ 7] = 0x2000FF00;
        cm[ 8] = 0x20FF0000;
        cm[ 9] = 0x20000000;
        cm[10] = 0x20000000;
        cm[11] = 0x20FF0000;
        cm[12] = 0x20FFFF00;
        cm[13] = 0x20000000;
        cm[14] = 0x20000000;
        cm[15] = 0x20FFFF00;
        cm[16] = 0x20FF00FF;
        cm[17] = 0x20000000;
        cm[18] = 0x20000000;
        cm[19] = 0x20FF00FF;
        cm[20] = 0x2000FFFF;
        cm[21] = 0x20000000;
        cm[22] = 0x20000000;
        cm[23] = 0x2000FFFF;
        int[] faceOffsets = {0, 4*4, 8*4, 12*4, 16*4, 20*4};
        Texture cubemap = new Texture.Builder()
                .width(2)
                .height(2)
                .format(Texture.InternalFormat.RGBM)
                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                .build(mEngine);
        Texture.PixelBufferDescriptor desc = new Texture.PixelBufferDescriptor(
                IntBuffer.wrap(cm), Texture.Format.RGBM, Texture.Type.UBYTE);
        cubemap.setImage(mEngine, Texture.BASE_LEVEL, desc, faceOffsets);
        Skybox skybox = new Skybox.Builder()
                .environment(cubemap)
                .showSun(true)
                .build(mEngine);
        scene.setSkybox(skybox);
        ByteBuffer asset = readAsset("test.filamat");
        if (asset != null) {
            Material material = new Material.Builder()
                    .payload(asset, asset.remaining())
                    .build(mEngine);
            List<Material.Parameter> parameters = material.getParameters();
            for (Material.Parameter p : parameters) {
                android.util.Log.d("Filament", p.precision.toString().toLowerCase() + " "
                        + p.name + " = " + p.type.toString().toLowerCase() + "[" + p.count + "]");
            }
        }
        int vertexCount = 10000;
        FloatBuffer bufferVertices = FloatBuffer.allocate(vertexCount * 3);
        VertexBuffer vb = new VertexBuffer.Builder()
                .bufferCount(1)
                .vertexCount(vertexCount)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3)
                .build(mEngine);
        vb.setBufferAt(mEngine, 0, bufferVertices);
        IndirectLight ibl = new IndirectLight.Builder().intensity(3000).build(mEngine);
        scene.setIndirectLight(ibl);
    }

    @Nullable
    private ByteBuffer readAsset(@NonNull String assetName) {
        ByteBuffer dst = null;
        try (AssetFileDescriptor fd = getAssets().openFd(assetName)) {
            InputStream in = fd.createInputStream();
            dst = ByteBuffer.allocate((int) fd.getLength());
            final ReadableByteChannel src = Channels.newChannel(in);
            //noinspection StatementWithEmptyBody
            src.read(dst);
            src.close();
            dst.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dst;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mChoreographer.postFrameCallback(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mChoreographer.removeFrameCallback(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUiHelper.detach(); // call this before destroying the Engine (it could call back)
        mEngine.destroyRenderer(mRenderer);
        mEngine.destroyView(mView);
        mEngine.destroy();
    }
}