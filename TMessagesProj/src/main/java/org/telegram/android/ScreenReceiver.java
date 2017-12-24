/*
 * This is the source code of گلد گرام for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.گلد گرام.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.goldgram.messenger.ConnectionsManager;
import org.goldgram.messenger.FileLog;
import org.goldgram.messenger.ApplicationLoader;

public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            FileLog.e("tmessages", "screen off");
            ConnectionsManager.getInstance().setAppPaused(true, true);
            ApplicationLoader.isScreenOn = false;
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            FileLog.e("tmessages", "screen on");
            ConnectionsManager.getInstance().setAppPaused(false, true);
            ApplicationLoader.isScreenOn = true;
        }
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.screenStateChanged);
    }
}
