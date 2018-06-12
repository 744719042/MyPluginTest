package com.example.mytest;

import android.content.Context;
import android.content.res.Resources;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class PluginManager {
    private Map<String, String> mPrefixPlugin = new HashMap<>();
    private Map<String, Resources> mResources = new HashMap<>();
    private Map<String, String> mResourcePath = new HashMap<>();

    private PluginManager() {

    }

    public void init(Context context) {
        String json = readPluginJson(context);
        try {
            JSONArray jsonArray = new JSONArray(json);
            int size = jsonArray.length();
            for (int i = 0; i < size; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.optString("name");
                String prefix = jsonObject.optString("prefix");
                mPrefixPlugin.put(name, prefix);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getPlugin(String className) {
        for (Map.Entry<String, String> entry : mPrefixPlugin.entrySet()) {
            if (className.startsWith(entry.getValue())) {
                return entry.getKey();
            }
        }

        return "";
    }

    public void addResources(String key, Resources resources) {
        mResources.put(key, resources);
    }

    public Resources getResources(String key) {
        return mResources.get(key);
    }

    private String readPluginJson(Context context) {
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            InputStream inputStream = context.getAssets().open("plugins.json");
            int length;
            byte[] bytes = new byte[1024];
            while ((length = inputStream.read(bytes)) != -1) {
                bos.write(bytes, 0, length);
            }
            return bos.toString();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return "";
    }

    public static PluginManager getInstance() {
        return PluginManagerHolder.INSTANCE;
    }

    public void addPluginPath(String plugin, String pluginPath) {
        mResourcePath.put(plugin, pluginPath);
    }

    public String getPluginPath(String key) {
        return mResourcePath.get(key);
    }

    private static class PluginManagerHolder {
        private static final PluginManager INSTANCE = new PluginManager();
    }
}
