/*
 * This is the source code of گلد گرام for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.goldgram.messenger;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.گلد گرام.android.AndroidUtilities;
import org.گلد گرام.android.ContactsController;
import org.گلد گرام.android.MediaController;
import org.گلد گرام.android.NotificationsService;
import org.گلد گرام.android.SendMessagesHelper;
import org.گلد گرام.android.LocaleController;
import org.گلد گرام.android.MessagesController;
import org.گلد گرام.android.NativeLoader;
import org.گلد گرام.android.ScreenReceiver;
import org.گلد گرام.ui.Components.ForegroundDetector;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class ApplicationLoader extends Application {

    private GoogleCloudMessaging gcm;
    private AtomicInteger msgId = new AtomicInteger();
    private String regid;
    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static Drawable cachedWallpaper;
    private static int selectedColor;
    private static boolean isCustomTheme;
    private static final Object sync = new Object();

    public static volatile Context applicationContext;
    public static volatile Handler applicationHandler;
    private static volatile boolean applicationInited = false;

    public static volatile boolean isScreenOn = false;
    public static volatile boolean mainInterfacePaused = true;

    public static boolean isCustomTheme() {
        return isCustomTheme;
    }

    public static int getSelectedColor() {
        return selectedColor;
    }

    public static void reloadWallpaper() {
        cachedWallpaper = null;
        loadWallpaper();
    }

    public static void loadWallpaper() {
        if (cachedWallpaper != null) {
            return;
        }
        Utilities.searchQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                synchronized (sync) {
                    int selectedColor = 0;
                    try {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        int selectedBackground = preferences.getInt("selectedBackground", 1000001);
                        selectedColor = preferences.getInt("selectedColor", 0);
                        int cacheColorHint = 0;
                        if (selectedColor == 0) {
                            if (selectedBackground == 1000001) {
                                cachedWallpaper = applicationContext.getResources().getDrawable(R.drawable.background_hd);
                                isCustomTheme = false;
                            } else {
                                File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                                if (toFile.exists()) {
                                    cachedWallpaper = Drawable.createFromPath(toFile.getAbsolutePath());
                                    isCustomTheme = true;
                                } else {
                                    cachedWallpaper = applicationContext.getResources().getDrawable(R.drawable.background_hd);
                                    isCustomTheme = false;
                                }
                            }
                        }
                    } catch (Throwable throwable) {
                        //ignore
                    }
                    if (cachedWallpaper == null) {
                        if (selectedColor == 0) {
                            selectedColor = -2693905;
                        }
                        cachedWallpaper = new ColorDrawable(selectedColor);
                    }
                }
            }
        });
    }

    public static Drawable getCachedWallpaper() {
        synchronized (sync) {
            return cachedWallpaper;
        }
    }

    public static void postInitApplication() {
        if (applicationInited) {
            return;
        }

        applicationInited = true;

        try {
            LocaleController.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            final BroadcastReceiver mReceiver = new ScreenReceiver();
            applicationContext.registerReceiver(mReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            PowerManager pm = (PowerManager)ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            isScreenOn = pm.isScreenOn();
            FileLog.e("tmessages", "screen state = " + isScreenOn);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        UserConfig.loadConfig();
        if (UserConfig.getCurrentUser() != null) {
            MessagesController.getInstance().putUser(UserConfig.getCurrentUser(), true);
            ConnectionsManager.getInstance().applyCountryPortNumber(UserConfig.getCurrentUser().phone);
            ConnectionsManager.getInstance().initPushConnection();
            MessagesController.getInstance().getBlockedUsers(true);
            SendMessagesHelper.getInstance().checkUnsentMessages();
        }

        ApplicationLoader app = (ApplicationLoader)ApplicationLoader.applicationContext;
        app.initPlayServices();
        FileLog.e("tmessages", "app initied");

        ContactsController.getInstance().checkAppAccount();
        MediaController.getInstance();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT < 11) {
            java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
            java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        }

        applicationContext = getApplicationContext();
        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);

        if (Build.VERSION.SDK_INT >= 14) {
            new ForegroundDetector(this);
        }

        applicationHandler = new Handler(applicationContext.getMainLooper());

        startPushService();
    }

    public static void startPushService() {
        SharedPreferences preferences = applicationContext.getSharedPreferences("Notifications", MODE_PRIVATE);

        if (preferences.getBoolean("pushService", true)) {
            applicationContext.startService(new Intent(applicationContext, NotificationsService.class));

            if (android.os.Build.VERSION.SDK_INT >= 19) {
//                Calendar cal = Calendar.getInstance();
//                PendingIntent pintent = PendingIntent.getService(applicationContext, 0, new Intent(applicationContext, NotificationsService.class), 0);
//                AlarmManager alarm = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
//                alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 30000, pintent);

                PendingIntent pintent = PendingIntent.getService(applicationContext, 0, new Intent(applicationContext, NotificationsService.class), 0);
                AlarmManager alarm = (AlarmManager)applicationContext.getSystemService(Context.ALARM_SERVICE);
                alarm.cancel(pintent);
            }
        } else {
            stopPushService();
        }
    }

    public static void stopPushService() {
        applicationContext.stopService(new Intent(applicationContext, NotificationsService.class));

        PendingIntent pintent = PendingIntent.getService(applicationContext, 0, new Intent(applicationContext, NotificationsService.class), 0);
        AlarmManager alarm = (AlarmManager)applicationContext.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pintent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
            AndroidUtilities.checkDisplaySize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPlayServices() {
        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId();

            if (regid.length() == 0) {
                registerInBackground();
            } else {
                sendRegistrationIdToBackend(false);
            }
        } else {
            FileLog.d("tmessages", "No valid Google Play Services APK found.");
        }
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
        /*if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("tmessages", "This device is not supported.");
            }
            return false;
        }
        return true;*/
    }

    private String getRegistrationId() {
        final SharedPreferences prefs = getGCMPreferences(applicationContext);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            FileLog.d("tmessages", "Registration not found.");
            return "";
        }
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion();
        if (registeredVersion != currentVersion) {
            FileLog.d("tmessages", "App version changed.");
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        return getSharedPreferences(ApplicationLoader.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    public static int getAppVersion() {
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private void registerInBackground() {
        AsyncTask<String, String, Boolean> task = new AsyncTask<String, String, Boolean>() {
            @Override
            protected Boolean doInBackground(String... objects) {
                if (gcm == null) {
                    gcm = GoogleCloudMessaging.getInstance(applicationContext);
                }
                int count = 0;
                while (count < 1000) {
                    try {
                        count++;
                        regid = gcm.register(BuildVars.GCM_SENDER_ID);
                        sendRegistrationIdToBackend(true);
                        storeRegistrationId(applicationContext, regid);
                        return true;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    try {
                        if (count % 20 == 0) {
                            Thread.sleep(60000 * 30);
                        } else {
                            Thread.sleep(5000);
                        }
                    } catch (InterruptedException e) {
                        FileLog.e("tmessages", e);
                    }
                }
                return false;
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
        } else {
            task.execute(null, null, null);
        }
    }

    private void sendRegistrationIdToBackend(final boolean isNew) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                UserConfig.pushString = regid;
                UserConfig.registeredForPush = !isNew;
                UserConfig.saveConfig(false);
                if (UserConfig.getClientUserId() != 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            MessagesController.getInstance().registerForPush(regid);
                        }
                    });
                }
            }
        });
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion();
        FileLog.e("tmessages", "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }
}
