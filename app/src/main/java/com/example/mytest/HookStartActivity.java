package com.example.mytest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

public class HookStartActivity {
    private static final String TAG = "HookStartActivity";

    public static void hook(Context context)  {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method method = activityThreadClass.getDeclaredMethod("currentActivityThread");
            method.setAccessible(true);
            Object activityThread = method.invoke(null);
            Log.d(TAG, activityThread.toString());
            Field instrumentation = activityThreadClass.getDeclaredField("mInstrumentation");
            instrumentation.setAccessible(true);
            Instrumentation old = (Instrumentation) instrumentation.get(activityThread);
            Log.d(TAG, old.toString());
            instrumentation.set(activityThread, new ProxyInstrumentation(context, old));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private static class ProxyInstrumentation extends Instrumentation {
        private Context context;
        private Instrumentation old;

        public ProxyInstrumentation(Context context, Instrumentation old) {
            this.context = context;
            this.old = old;
        }

        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, Bundle options) {
            PackageManager pm = who.getPackageManager();
            ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
            String className = resolveInfo.activityInfo.name;
            String plugin = PluginManager.getInstance().getPlugin(className);
            if (!TextUtils.isEmpty(plugin)) {
                initPlugin(context, plugin);
            }
            try {
                Method method = Instrumentation.class.getDeclaredMethod("execStartActivity",
                        Context.class, IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
                return (ActivityResult) method.invoke(old, who, contextThread, token, target, intent, requestCode, options);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle) {
            injectResource(activity);
            old.callActivityOnCreate(activity, icicle);
        }

        private void injectResource(Activity activity) {
            String plugin = PluginManager.getInstance().getPlugin(activity.getClass().getName());
            if (TextUtils.isEmpty(plugin)) {
                return;
            }

            AssetManager assetManager = PluginManager.getInstance().getAssetManager(plugin);
            if (assetManager == null) {
                assetManager = loadPluginResources(plugin);
            }
            if (assetManager == null) {
                return;
            }

            refreshResources(activity, assetManager);
        }

        private void refreshResources(Activity activity, AssetManager newAssetManager) {
            try {

                Resources resources = activity.getResources();
                try {
                    Field mAssets = Resources.class.getDeclaredField("mAssets");
                    mAssets.setAccessible(true);
                    mAssets.set(resources, newAssetManager);
                } catch (Throwable ignore) {
                    Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                    mResourcesImpl.setAccessible(true);
                    Object resourceImpl = mResourcesImpl.get(resources);
                    Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                    implAssets.setAccessible(true);
                    implAssets.set(resourceImpl, newAssetManager);
                }

                Resources.Theme theme = activity.getTheme();
                try {
                    Field ma = Resources.Theme.class.getDeclaredField("mAssets");
                    ma.setAccessible(true);
                    ma.set(theme, newAssetManager);
                } catch (NoSuchFieldException ignore) {
                    Field themeField = Resources.Theme.class.getDeclaredField("mThemeImpl");
                    themeField.setAccessible(true);
                    Object impl = themeField.get(theme);
                    Field ma = impl.getClass().getDeclaredField("mAssets");
                    ma.setAccessible(true);
                    ma.set(impl, newAssetManager);
                }
            } catch (Throwable e) {
                Log.e("InstantRun",
                        "Failed to update existing theme for activity " + activity, e);
            }

            updateSystemResources(newAssetManager);
        }

        private void updateSystemResources(AssetManager newAssetManager) {
            try {
                Collection<WeakReference> references;
                if (Build.VERSION.SDK_INT >= 19) {
                    Class resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                    Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance", new Class[0]);
                    mGetInstance.setAccessible(true);
                    Object resourcesManager = mGetInstance.invoke(null, new Object[0]);
                    try {
                        Field fMActiveResources = resourcesManagerClass
                                .getDeclaredField("mActiveResources");
                        fMActiveResources.setAccessible(true);

                        ArrayMap arrayMap = (ArrayMap) fMActiveResources.get(resourcesManager);

                        references = (Collection<WeakReference>) arrayMap.values();
                    } catch (NoSuchFieldException ignore) {
                        Field mResourceReferences = resourcesManagerClass
                                .getDeclaredField("mResourceReferences");
                        mResourceReferences.setAccessible(true);
                        references = (Collection) mResourceReferences.get(resourcesManager);
                    }
                } else {
                    Class activityThreadClass = Class.forName("android.app.ActivityThread");
                    Field fMActiveResources = activityThreadClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);
                    Method method = activityThreadClass.getDeclaredMethod("currentActivityThread");
                    method.setAccessible(true);
                    Object activityThread = method.invoke(null);
                    HashMap<Class<?>, WeakReference> map = (HashMap<Class<?>, WeakReference>) fMActiveResources.get(activityThread);
                    references = map.values();
                }

                for (WeakReference wr : references) {
                    Resources resources = (Resources) wr.get();
                    if (resources != null) {
                        try {
                            Field mAssets = Resources.class.getDeclaredField("mAssets");
                            mAssets.setAccessible(true);
                            mAssets.set(resources, newAssetManager);
                        } catch (Throwable ignore) {
                            Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                            mResourcesImpl.setAccessible(true);
                            Object resourceImpl = mResourcesImpl.get(resources);
                            Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                            implAssets.setAccessible(true);
                            implAssets.set(resourceImpl, newAssetManager);
                        }

                        resources.updateConfiguration(resources.getConfiguration(),
                                resources.getDisplayMetrics());
                    }
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        private AssetManager loadPluginResources(String plugin) {
            String pluginPath = PluginManager.getInstance().getPluginPath(plugin);
            // 开始加载资源
            AssetManager assetManager = null;
            try {
                assetManager = AssetManager.class.newInstance();
                Method method = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                method.setAccessible(true);
                method.invoke(assetManager, pluginPath);
                method.invoke(assetManager, context.getApplicationContext().getPackageResourcePath());
                Method strMethod = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
                strMethod.setAccessible(true);
                strMethod.invoke(assetManager);
                PluginManager.getInstance().addAssetManager(plugin, assetManager);
                return assetManager;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle,
                                         PersistableBundle persistentState) {
            injectResource(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                old.callActivityOnCreate(activity, icicle, persistentState);
            } else {
                old.callActivityOnCreate(activity, icicle);
            }
        }

        private void initPlugin(Context context, String plugin) {
            String pluginPath = copy(context, plugin + ".so", new File(context.getCacheDir(), plugin + ".apk"));
            if (TextUtils.isEmpty(pluginPath)) {
                return;
            }

            String dexOutput = context.getCacheDir() + File.separator + plugin;
            File file = new File(dexOutput);
            if (!file.exists()) file.mkdirs();
            // 从bugfix.dex文件加载修复bug的dex文件
            DexClassLoader dexClassLoader = new DexClassLoader(pluginPath, dexOutput, null, context.getClassLoader());
            PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();

            try {
                // 反射获取pathList成员变量Field
                Field dexPathList = BaseDexClassLoader.class.getDeclaredField("pathList");
                dexPathList.setAccessible(true);
                // 现获取两个类加载器内部的pathList成员变量
                Object pathList = dexPathList.get(pathClassLoader);
                Object fixPathList = dexPathList.get(dexClassLoader);

                // 反射获取DexPathList类的dexElements成员变量Field
                Field dexElements = pathList.getClass().getDeclaredField("dexElements");
                dexElements.setAccessible(true);
                // 反射获取pathList对象内部的dexElements成员变量
                Object originDexElements = dexElements.get(pathList);
                Object fixDexElements = dexElements.get(fixPathList);

                // 使用反射获取两个dexElements的长度
                int originLength = Array.getLength(originDexElements);
                int fixLength = Array.getLength(fixDexElements);
                int totalLength = originLength + fixLength;
                // 获取dexElements数组的元素类型
                Class<?> componentClass = originDexElements.getClass().getComponentType();
                // 将修复dexElements的元素放在前面，原始dexElements放到后面，这样就保证加载类的时候优先查找修复类
                Object[] elements = (Object[]) Array.newInstance(componentClass, totalLength);
                for (int i = 0; i < totalLength; i++) {
                    if (i < fixLength) {
                        elements[i] = Array.get(fixDexElements, i);
                    } else {
                        elements[i] = Array.get(originDexElements, i - fixLength);
                    }
                }
                // 将新生成的dexElements数组注入到PathClassLoader内部，
                // 这样App查找类就会先从fixdex查找，在从App安装的dex里查找
                dexElements.set(pathList, elements);
                PluginManager.getInstance().addPluginPath(plugin, pluginPath);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        private String copy(Context context, String path, File file) {
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                    return "";
                }
            } else {
                return file.getAbsolutePath();
            }

            FileOutputStream fos = null;
            InputStream inputStream = null;
            try {
                fos = new FileOutputStream(file);
                inputStream = context.getAssets().open(path);
                int length = -1;
                byte[] bytes = new byte[2048];
                while ((length = inputStream.read(bytes)) != -1) {
                    fos.write(bytes, 0, length);
                }
                return file.getAbsolutePath();
            } catch (Exception e) {
                return "";
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
