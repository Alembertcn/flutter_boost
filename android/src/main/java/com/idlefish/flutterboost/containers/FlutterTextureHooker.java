// Copyright (c) 2019 Alibaba Group. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.idlefish.flutterboost.containers;

import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;

import com.idlefish.flutterboost.FlutterBoost;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.flutter.embedding.android.FlutterTextureView;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.renderer.FlutterRenderer;


class FlutterTextureHooker {
    private SurfaceTexture restoreSurface;
    private FlutterTextureView flutterTextureView;
    private boolean isNeedRestoreState = false;
    private String cachedEngineId;

    /**
     * Release surface when Activity.onDestroy / Fragment.onDestroy.
     * Stop render when finish the last flutter boost container.
     */
    public void onFlutterTextureViewRelease() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            int containerSize = FlutterContainerManager.instance().getContainerSize(this.cachedEngineId);
            if (containerSize == 1) {
                FlutterEngine engine = FlutterEngineCache.getInstance().get(this.cachedEngineId);
                if (engine != null) {
                    FlutterRenderer renderer = engine.getRenderer();
                    renderer.stopRenderingToSurface();
                }
            }
            if (restoreSurface != null) {
                restoreSurface.release();
                restoreSurface = null;
            }
        }
    }

    /**
     * Restore last surface for os version below Android.M.
     * Call from Activity.onResume / Fragment.didFragmentShow.
     */
    public void onFlutterTextureViewRestoreState() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            if (restoreSurface != null && flutterTextureView != null && isNeedRestoreState) {
                try {
                    Class<? extends FlutterTextureView> aClass = flutterTextureView.getClass();

                    Field isSurfaceAvailableForRendering = aClass.getDeclaredField(
                            "isSurfaceAvailableForRendering");
                    isSurfaceAvailableForRendering.setAccessible(true);
                    isSurfaceAvailableForRendering.set(flutterTextureView, true);
                    boolean next = false;
                    try {
                        Field isAttachedToFlutterRenderer = aClass.getDeclaredField(
                                "isAttachedToFlutterRenderer");
                        isAttachedToFlutterRenderer.setAccessible(true);
                        next = isAttachedToFlutterRenderer.getBoolean(flutterTextureView);
                    } catch (NoSuchFieldException ignore) {
                        Method shouldNotify = aClass.getDeclaredMethod("shouldNotify");
                        shouldNotify.setAccessible(true);
                        next = (Boolean) shouldNotify.invoke(flutterTextureView);
                    }
                    if (next) {
                        FlutterEngine engine = FlutterEngineCache.getInstance().get(cachedEngineId);
                        if (engine != null) {

                            FlutterRenderer flutterRenderer = engine.getRenderer();
                            Surface surface = new Surface(restoreSurface);
                            flutterRenderer.startRenderingToSurface(surface, false);

                            try {
                                flutterTextureView.setSurfaceTexture(restoreSurface);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        restoreSurface = null;
                        isNeedRestoreState = false;
                    }
                } catch (Exception e) {
                    // https://github.com/alibaba/flutter_boost/issues/1560
                    throw new RuntimeException("You *SHOULD* keep FlutterTextureView: -keep class io.flutter.embedding.android.FlutterTextureView { *; }.", e);
                }
            }
        }
    }

    /**
     * Hook FlutterTextureView for os version below Android.M.
     */
    public void hookFlutterTextureView(FlutterTextureView flutterTextureView, String cachedEngineId) {
        this.cachedEngineId = cachedEngineId;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            if (flutterTextureView != null) {
                TextureView.SurfaceTextureListener surfaceTextureListener = flutterTextureView.getSurfaceTextureListener();
                this.flutterTextureView = flutterTextureView;
                this.flutterTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        surfaceTextureListener.onSurfaceTextureAvailable(surface, width, height);

                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                        surfaceTextureListener.onSurfaceTextureSizeChanged(surface, width, height);
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        try {
                            Class<? extends FlutterTextureView> aClass =
                                    flutterTextureView.getClass();
                            Field isSurfaceAvailableForRendering = aClass.getDeclaredField(
                                    "isSurfaceAvailableForRendering");
                            isSurfaceAvailableForRendering.setAccessible(true);
                            isSurfaceAvailableForRendering.set(flutterTextureView, false);
                        } catch (Exception e) {
                            // https://github.com/alibaba/flutter_boost/issues/1560
                            throw new RuntimeException("You *SHOULD* keep FlutterTextureView: -keep class io.flutter.embedding.android.FlutterTextureView { *; }.", e);
                        }
                        isNeedRestoreState = true;
                        //return false, handle the last frame of surfaceTexture ourselves;
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                        surfaceTextureListener.onSurfaceTextureUpdated(surface);
                        restoreSurface = surface;
                    }
                });
            }
        }
    }
}
