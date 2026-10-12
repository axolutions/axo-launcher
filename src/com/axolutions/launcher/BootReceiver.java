package com.axolutions.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Bolha.setGray(c, true);
        ColorBubbleService.start(c);
    }
}
