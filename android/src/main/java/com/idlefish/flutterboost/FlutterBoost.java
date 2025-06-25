// Copyright (c) 2019 Alibaba Group. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.idlefish.flutterboost;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.idlefish.flutterboost.containers.FlutterContainerManager;
import com.idlefish.flutterboost.containers.FlutterViewContainer;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.android.FlutterEngineProvider;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.FlutterJNI;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.view.FlutterMain;
import io.flutter.embedding.engine.FlutterEngineGroup;
import io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint;
import java.util.Arrays;
import java.util.ArrayList;

public class FlutterBoost {
    public static final String ENGINE_ID = "flutter_boost_default_engine";
    private LinkedList<Activity> activityQueue = null;
    private FlutterBoostPlugin plugin;
    private boolean isBackForegroundEventOverridden = false;
    private boolean isAppInBackground = false;
    private FlutterEngineGroup mFGroup = null;
    private LinkedList<String> idealEngines = new LinkedList<>();

    private FlutterBoost() {
    }

    private static class LazyHolder {
        static final FlutterBoost INSTANCE = new FlutterBoost();
    }

    public static FlutterBoost instance() {
        return LazyHolder.INSTANCE;
    }

    public interface Callback {
        void onStart(FlutterEngine engine);
    }

    /**
     * Initializes engine and plugin.
     *
     * @param application the application
     * @param delegate    the FlutterBoostDelegate
     * @param callback    Invoke the callback when the engine was started.
     */
    public void setup(Application application, FlutterBoostDelegate delegate, Callback callback) {
        setup(application, delegate, callback, FlutterBoostSetupOptions.createDefault());
    }

    public void setup(Application application, FlutterBoostDelegate delegate, Callback callback, FlutterBoostSetupOptions options) {
        if (options == null) {
            options = FlutterBoostSetupOptions.createDefault();
        }
        isBackForegroundEventOverridden = options.shouldOverrideBackForegroundEvent();
        FlutterBoostUtils.setDebugLoggingEnabled(options.isDebugLoggingEnabled());

        if(mFGroup ==null) {
            // Initialize the FlutterEngineGroup if it is not already initialized.
            mFGroup = new FlutterEngineGroup(application);
        }
        // 1. initialize default engine
        FlutterEngine engine = getEngine();
        if (engine == null) {
            // First, get engine from option.flutterEngineProvider
            if (options.flutterEngineProvider() != null) {
                FlutterEngineProvider provider = options.flutterEngineProvider();
                engine = provider.provideFlutterEngine(application);
                if (engine != null) {
                    allPlugins.add(FlutterBoostUtils.getPlugin(engine));
                }
            }

            if (engine == null) {
                engine = initFlutterEngine(ENGINE_ID, application);
//                engine = new FlutterEngine(application, null, null, new FBPlatformViewsController(), options.shellArgs(), true);
            }

            // Cache the created FlutterEngine in the FlutterEngineCache.
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine);
        }

        if (!engine.getDartExecutor().isExecutingDart()) {
            // Pre-warm the cached FlutterEngine.
            engine.getNavigationChannel().setInitialRoute(options.initialRoute());
            engine.getDartExecutor().executeDartEntrypoint(new DartExecutor.DartEntrypoint(
                    FlutterMain.findAppBundlePath(), options.dartEntrypoint()), options.dartEntrypointArgs());
        }
        if (callback != null) callback.onStart(engine);

        // 2. set delegate
        getPlugin().setDelegate(delegate);

        //3. register ActivityLifecycleCallbacks
        setupActivityLifecycleCallback(application, isBackForegroundEventOverridden);
    }

    ArrayList<FlutterBoostPlugin> allPlugins= new ArrayList<>();
    public FlutterEngine initFlutterEngine(String engineId, Application context) {
        if (FlutterEngineCache.getInstance().get(engineId) == null) {
            FlutterEngine engine = mFGroup.createAndRunEngine(
                    new FlutterEngineGroup.Options(context)
                            .setDartEntrypoint(DartEntrypoint.createDefault())
                            .setInitialRoute("/")
                            .setDartEntrypointArgs(Arrays.asList(
                                    "--verbose"
                            ))
            );
            // 缓存引擎
            FlutterEngineCache.getInstance().put(engineId, engine);
            allPlugins.add(FlutterBoostUtils.getPlugin(engine));
        }
        return FlutterEngineCache.getInstance().get(engineId);
    }

    public void tearDownPendingNow(){
        for (String engineId : idealEngines) {
            tearDown(engineId, false);
        }
        idealEngines.clear();
    }
    void tearDown(String engineId,boolean pending) {
        if(pending){
            if(!idealEngines.contains(engineId)){
                idealEngines.add(engineId);
            }
            return;
        }
        if (engineId == null || engineId.isEmpty()) {
            tearDown();
        }
        FlutterEngine engine = FlutterEngineCache.getInstance().get(engineId);
        if (engine != null) {
            allPlugins.remove(FlutterBoostUtils.getPlugin(engine));
            engine.destroy();
            FlutterEngineCache.getInstance().remove(engineId);
        }
    }

    public void removePendingTearDown(String engineId) {
        if(idealEngines.contains(engineId)){
            idealEngines.remove(engineId);
        }
    }

    /**
     *  Releases the engine resource.
     */
    public void tearDown() {
        FlutterEngine engine = getEngine();
        if (engine != null) {
            engine.destroy();
            FlutterEngineCache.getInstance().remove(ENGINE_ID);
        }
        activityQueue = null;
        plugin = null;
        isBackForegroundEventOverridden = false;
        isAppInBackground = false;
    }

    /**
     * Gets the FlutterBoostPlugin.
     *
     * @return the FlutterBoostPlugin.
     */
    public FlutterBoostPlugin getPlugin() {
        if (plugin == null) {
            FlutterEngine engine = getEngine();
            if (engine == null) {
                throw new RuntimeException("FlutterBoost might *not* have been initialized yet!!!");
            }
            plugin = FlutterBoostUtils.getPlugin(engine);
        }
        return plugin;
    }
     /**
     * Gets the FlutterBoostPlugin.
     *
     * @return the FlutterBoostPlugin.
     */
    public FlutterBoostPlugin getPlugin(String engineId) {
        return FlutterBoostUtils.getPlugin(FlutterEngineCache.getInstance().get(engineId));
    }


    /**
     * Gets the FlutterEngine in use.
     *
     * @return the FlutterEngine
     */
    public FlutterEngine getEngine() {
        return FlutterEngineCache.getInstance().get(ENGINE_ID);
    }

    /**
     * Gets the current activity.
     *
     * @return the current activity
     */
    public Activity currentActivity() {
        if (activityQueue != null && !activityQueue.isEmpty()) {
            return activityQueue.peek();
        } else {
            return null;
        }
    }

    /**
     * Informs FlutterBoost of the back/foreground state.
     *
     * @param background a boolean indicating if the app goes to background
     *                   or foreground.
     */
    public void dispatchBackForegroundEvent(boolean background) {
        if (!isBackForegroundEventOverridden) {
            throw new RuntimeException("Oops! You should set override enable first by FlutterBoostSetupOptions.");
        }

        if (background) {
            getPlugin().onBackground();
        } else {
            getPlugin().onForeground();
        }
        setAppIsInBackground(background);
    }

    /**
     * Gets the FlutterView container with uniqueId.
     * <p>
     * This is a legacy API for backwards compatibility.
     *
     * @param uniqueId The uniqueId of the container
     * @return a FlutterView container
     */
    public FlutterViewContainer findFlutterViewContainerById(String uniqueId) {
        return FlutterContainerManager.instance().findContainerById(uniqueId);
    }

    /**
     * Gets the topmost container
     * <p>
     * This is a legacy API for backwards compatibility.
     *
     * @return the topmost container
     */
    public FlutterViewContainer getTopContainer() {
        return FlutterContainerManager.instance().getTopContainer();
    }

    /**
     * @param name      The Flutter route name.
     * @param arguments The bussiness arguments.
     * @deprecated use open(FlutterBoostRouteOptions options) instead
     * Open a Flutter page with name and arguments.
     */
    public void open(String name, Map<String, Object> arguments) {
        FlutterBoostRouteOptions options = new FlutterBoostRouteOptions.Builder()
                .pageName(name)
                .arguments(arguments)
                .build();
        getPlugin().getDelegate().pushFlutterRoute(options);
    }

    /**
     * Use FlutterBoostRouteOptions to open a new Page
     *
     * @param options FlutterBoostRouteOptions object
     */
    public void open(FlutterBoostRouteOptions options) {
        getPlugin().getDelegate().pushFlutterRoute(options);
    }

    /**
     * Close the Flutter page with uniqueId.
     *
     * @param uniqueId The uniqueId of the Flutter page
     */
    public void close(String uniqueId) {
        Messages.CommonParams params = new Messages.CommonParams();
        params.setUniqueId(uniqueId);
        getPlugin().popRoute(params,new Messages.Result<Void>(){
            @Override
            public void success(Void result) {
            }

            @Override
            public void error(Throwable t) {
            }
        });
    }

    /**
     * Change the application life cycle of Flutter
     */
    public void changeFlutterAppLifecycle(int state) {
        getPlugin().changeFlutterAppLifecycle(state);
    }

    /**
     * Add a event listener
     *
     * @param listener
     * @return ListenerRemover, you can use this to remove this listener
     */
    public ListenerRemover addEventListener(String key, EventListener listener) {
        return addEventListener(ENGINE_ID, key, listener);
    }
    public ListenerRemover addEventListener(String engineId, String key, EventListener listener) {
        FlutterEngine flutterEngine = FlutterEngineCache.getInstance().get(engineId);
        if (flutterEngine != null && FlutterBoostUtils.getPlugin(flutterEngine) != null) {
            FlutterBoostPlugin plugin = FlutterBoostUtils.getPlugin(flutterEngine);
            plugin.addEventListener(key, listener);
            return plugin.addEventListener(key, listener);
        }
        return null;
    }

    /**
     * Send the event to flutter
     *
     * @param key  the key of this event
     * @param args the arguments of this event
     */
    public void sendEventToFlutter(String key, Map<String, Object> args) {
        sendEventToFlutter(key, args, true);
    }

    /**
     * Send the event to flutter
     *
     * @param key  the key of this event
     * @param args the arguments of this event
     */
    void sendEventToFlutter(String key, Map<String, Object> args, boolean isAll) {
        if(isAll){
            allPlugins.forEach(plugin -> plugin.sendEventToFlutter(key, args));
        }else{
          getPlugin().sendEventToFlutter(key, args);
        }
    }

    public boolean isAppInBackground() {
        return isAppInBackground;
    }

    /*package*/ void setAppIsInBackground(boolean inBackground) {
        isAppInBackground = inBackground;
    }

    private void setupActivityLifecycleCallback(Application application, boolean isBackForegroundEventOverridden) {
        application.registerActivityLifecycleCallbacks(new BoostActivityLifecycle(isBackForegroundEventOverridden));
    }

    private class BoostActivityLifecycle implements Application.ActivityLifecycleCallbacks {
        private int activityReferences = 0;
        private boolean isActivityChangingConfigurations = false;
        private boolean isBackForegroundEventOverridden = false;

        public BoostActivityLifecycle(boolean isBackForegroundEventOverridden) {
            this.isBackForegroundEventOverridden = isBackForegroundEventOverridden;
        }

        private void dispatchForegroundEvent() {
            if (isBackForegroundEventOverridden) {
                return;
            }

            FlutterBoost.instance().setAppIsInBackground(false);
            FlutterBoost.instance().getPlugin().onForeground();
        }

        private void dispatchBackgroundEvent() {
            if (isBackForegroundEventOverridden) {
                return;
            }

            FlutterBoost.instance().setAppIsInBackground(true);
            FlutterBoost.instance().getPlugin().onBackground();
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            if (activityQueue == null) {
                activityQueue = new LinkedList<Activity>();
            }
            activityQueue.addFirst(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                // App enters foreground
                dispatchForegroundEvent();
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (activityQueue == null) {
                activityQueue  = new LinkedList<Activity>();
                activityQueue.addFirst(activity);
            } else if(activityQueue.isEmpty()) {
                activityQueue.addFirst(activity);
            } else if (activityQueue.peek() != activity) {
                //针对多tab且每个tab都为Activity，在切换时并不会走remove，所以先从队列中删除再加入
                activityQueue.removeFirstOccurrence(activity);
                activityQueue.addFirst(activity);
            }
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
            isActivityChangingConfigurations = activity.isChangingConfigurations();
            if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                // App enters background
                dispatchBackgroundEvent();
            }

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activityQueue != null && !activityQueue.isEmpty()) {
                activityQueue.remove(activity);
            }
        }
    }
}
