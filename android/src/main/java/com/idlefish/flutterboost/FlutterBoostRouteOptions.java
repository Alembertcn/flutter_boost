// Copyright (c) 2019 Alibaba Group. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.idlefish.flutterboost;

import java.util.Map;

public class FlutterBoostRouteOptions {
    static final String ARGS_SCOUCE_CONTINUED_ID = "sourceContainerId";
    private final String pageName;
    private final Map<String, Object> arguments;
    private final int requestCode;
    private final String uniqueId;

    private final String sourceContainerId;
    private final boolean opaque;

    private FlutterBoostRouteOptions(FlutterBoostRouteOptions.Builder builder) {
        this.pageName = builder.pageName;
        this.arguments = builder.arguments;
        this.requestCode = builder.requestCode;
        this.uniqueId = builder.uniqueId;
        this.opaque = builder.opaque;
        this.sourceContainerId = builder.sourceContainerId;
    }

    public String pageName() {
        return pageName;
    }

    public String getSourceContainerId() {
        return sourceContainerId;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    public int requestCode() {
        return requestCode;
    }

    public String uniqueId() {
        return uniqueId;
    }

    public boolean opaque() {
        return opaque;
    }

    public static class Builder {
        private String pageName;
        private Map<String, Object> arguments;
        private int requestCode;
        private String uniqueId;
        private String sourceContainerId;
        private boolean opaque = true;

        public Builder() {
        }

        public FlutterBoostRouteOptions.Builder pageName(String pageName) {
            this.pageName = pageName;
            return this;
        }

        public FlutterBoostRouteOptions.Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }

        public FlutterBoostRouteOptions.Builder requestCode(int requestCode) {
            this.requestCode = requestCode;
            return this;
        }

        public FlutterBoostRouteOptions.Builder uniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }
        public FlutterBoostRouteOptions.Builder sourceContainerId(String sourceContainerId) {
            this.sourceContainerId = sourceContainerId;
            return this;
        }

        public FlutterBoostRouteOptions.Builder opaque(boolean opaque) {
            this.opaque = opaque;
            return this;
        }

        public FlutterBoostRouteOptions build() {
            return new FlutterBoostRouteOptions(this);
        }
    }

}