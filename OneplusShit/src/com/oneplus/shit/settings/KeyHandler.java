/*
 * Copyright (C) 2017 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oneplus.shit.settings;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

import java.util.Arrays;

import com.oneplus.shit.settings.SliderControllerBase;
import com.oneplus.shit.settings.slider.NotificationController;
import com.oneplus.shit.settings.slider.FlashlightController;
import com.oneplus.shit.settings.slider.BrightnessController;
import com.oneplus.shit.settings.slider.RotationController;
import com.oneplus.shit.settings.slider.RingerController;
import com.oneplus.shit.settings.slider.CaffeineController;
import com.oneplus.shit.settings.slider.NotificationRingerController;

import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.service.notification.ZenModeConfig;
import com.oneplus.shit.settings.ScreenOffGesture;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.gzosp.ActionConstants;
import com.android.internal.util.gzosp.Action;
import android.view.Gravity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import com.oneplus.shit.settings.R;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = "KeyHandler";

    private static final int GESTURE_REQUEST = 1;

    // Supported scancodes
    private static final int GESTURE_CIRCLE_SCANCODE = 250;
    private static final int GESTURE_SWIPE_DOWN_SCANCODE = 251;
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int GESTURE_LTR_SCANCODE = 253;
    private static final int GESTURE_GTR_SCANCODE = 254;
    private static final int GESTURE_V_UP_SCANCODE = 255;

    private static final int[] sSupportedGestures = new int[]{
        GESTURE_CIRCLE_SCANCODE,
        GESTURE_SWIPE_DOWN_SCANCODE,
        GESTURE_V_SCANCODE,
        GESTURE_V_UP_SCANCODE,
        GESTURE_LTR_SCANCODE,
        GESTURE_GTR_SCANCODE
    };

    private final Context mContext;

    private final AudioManager mAudioManager;
    private final NotificationManager mNotificationManager;
    private final PowerManager mPowerManager;
    private Context mGestureContext = null;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    private final NotificationController mNotificationController;
    private final FlashlightController mFlashlightController;
    private final BrightnessController mBrightnessController;
    private final RotationController mRotationController;
    private final RingerController mRingerController;
    private final CaffeineController mCaffeineController;
    private final NotificationRingerController mNotificationRingerController;

    private SliderControllerBase mSliderController;

    private final BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int usage = intent.getIntExtra(ButtonConstants.EXTRA_SLIDER_USAGE, 0);
            int[] actions = intent.getIntArrayExtra(ButtonConstants.EXTRA_SLIDER_ACTIONS);
            handleSliderUpdate(usage, actions);
        }
    };

    public KeyHandler(Context context) {
        mContext = context;

        mNotificationController = new NotificationController(context);
        mFlashlightController = new FlashlightController(context);
        mBrightnessController = new BrightnessController(context);
        mRotationController = new RotationController(context);
        mRingerController = new RingerController(context);
        mCaffeineController = new CaffeineController(context);
        mNotificationRingerController = new NotificationRingerController(context);

        mContext.registerReceiver(mUpdateReceiver,
                new IntentFilter(ButtonConstants.ACTION_UPDATE_SLIDER_SETTINGS));

        mEventHandler = new EventHandler();
        mPowerManager = context.getSystemService(PowerManager.class);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mSensorManager = context.getSystemService(SensorManager.class);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "ProximityWakeLock");

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        try {
            mGestureContext = mContext.createPackageContext(
                    "com.oneplus.shit.settings", Context.CONTEXT_IGNORE_SECURITY);
        } catch (NameNotFoundException e) {
        }
    }

    private boolean hasSetupCompleted() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    private void handleSliderUpdate(int usage, int[] actions) {
        Log.d(TAG, "update usage " + usage + " with actions " +
                Arrays.toString(actions));

        if (mSliderController != null) {
            mSliderController.reset();
        }

        switch (usage) {
            case NotificationController.ID:
                mSliderController = mNotificationController;
                break;
            case FlashlightController.ID:
                mSliderController = mFlashlightController;
                break;
            case BrightnessController.ID:
                mSliderController = mBrightnessController;
                break;
            case RotationController.ID:
                mSliderController = mRotationController;
                break;
            case RingerController.ID:
                mSliderController = mRingerController;
                break;
            case CaffeineController.ID:
                mSliderController = mCaffeineController;
                break;
            case NotificationRingerController.ID:
                mSliderController = mNotificationRingerController;
                break;
        }

        if (mSliderController != null) {
            mSliderController.update(actions);
            mSliderController.restoreState();
        }
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            String action = null;
            switch(event.getScanCode()) {
            case GESTURE_CIRCLE_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_CIRCLE,
                        ActionConstants.ACTION_CAMERA);
                        doHapticFeedback();
                break;
            case GESTURE_SWIPE_DOWN_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_DOUBLE_SWIPE,
                        ActionConstants.ACTION_MEDIA_PLAY_PAUSE);
                        doHapticFeedback();
                break;
            case GESTURE_V_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_DOWN,
                        ActionConstants.ACTION_VIB_SILENT);
                        doHapticFeedback();
                break;
            case GESTURE_V_UP_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_UP,
                        ActionConstants.ACTION_TORCH);
                        doHapticFeedback();
                break;
            case GESTURE_LTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_LEFT,
                        ActionConstants.ACTION_MEDIA_PREVIOUS);
                        doHapticFeedback();
                break;
            case GESTURE_GTR_SCANCODE:
                action = getGestureSharedPreferences()
                        .getString(ScreenOffGesture.PREF_GESTURE_ARROW_RIGHT,
                        ActionConstants.ACTION_MEDIA_NEXT);
                        doHapticFeedback();
                break;
			}

            if (action == null || action != null && action.equals(ActionConstants.ACTION_NULL)) {
                return;
            }
            if (action.equals(ActionConstants.ACTION_CAMERA)
                    || !action.startsWith("**")) {
                Action.processAction(mContext, ActionConstants.ACTION_WAKE_DEVICE, false);
            }
            Action.processAction(mContext, action, false);
        }
    }

    private void doHapticFeedback() {
        if (mVibrator == null) {
            return;
        }
            mVibrator.vibrate(50);
    }

    private SharedPreferences getGestureSharedPreferences() {
        return mGestureContext.getSharedPreferences(
                ScreenOffGesture.GESTURE_SETTINGS,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }


    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return event;
        }

        int scanCode = event.getScanCode();
        if (mSliderController != null &&
                mSliderController.processEvent(scanCode)) {
            return null;
        }

        if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(event);
            if (mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, 200);
                processEvent(event);
            } else {
                mEventHandler.sendMessage(msg);
            }
        }

        return event;
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }

    private void processEvent(final KeyEvent keyEvent) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {

            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(keyEvent);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }
}

