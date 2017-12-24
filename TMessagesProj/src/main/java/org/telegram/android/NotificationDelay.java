/*
 * This is the source code of گلد گرام for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.گلد گرام.android;

import android.app.IntentService;
import android.content.Intent;

public class NotificationDelay extends IntentService {

    public NotificationDelay() {
        super("NotificationDelay");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationsController.getInstance().notificationDelayReached();
            }
        });
    }
}
