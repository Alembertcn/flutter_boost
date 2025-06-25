// Copyright (c) 2019 Alibaba Group. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.idlefish.flutterboost.containers;

import android.app.Activity;
import android.text.TextUtils;

import com.idlefish.flutterboost.FlutterBoost;

import java.util.Map;

/**
 * A container which contains the FlutterView
 */
public interface FlutterViewContainer {
    Activity getContextActivity();
    String getUrl();
    Map<String, Object> getUrlParams();
    String getUniqueId();
    void finishContainer(Map<String, Object> result);
    default boolean isPausing() { return false; }
    default boolean isOpaque() { return true; }
    default void detachFromEngineIfNeeded() {}
    default boolean isCommonEngine() { return FlutterBoost.ENGINE_ID.equals(getCachedEngineId()); }
    default String getCachedEngineId() { return FlutterBoost.ENGINE_ID; }
}
