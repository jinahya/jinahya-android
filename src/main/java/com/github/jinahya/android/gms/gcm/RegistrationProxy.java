/*
 * Copyright 2014 Jin Kwon <onacit at gmail.com>.
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


package com.github.jinahya.android.gms.gcm;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;


/**
 *
 * @author Jin Kwon <onacit at gmail.com>
 */
public final class RegistrationProxy {


    private static final String tag = "jinahya.android.gms.gcm";


    public static final String CONFIGURATION_ASSET_FILE_NAME
        = "jinahya.android.gms.gcm.proxy.configuration.xml";


    private static final String REGISTRATION_SHARED_PREFERENCES_NAME
        = "jinahya.android.gms.gcm.proxy.registration";


    private static final String KEY_SENDER_IDS = "sender.ids";


    private static final String KEY_VERSION_CODE = "version.code";


    private static final String KEY_REGISTRATION_ID = "registration.id";


    private static int versionCode(final Context context) {

        if (context == null) {
            throw new NullPointerException("null context");
        }

        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (final NameNotFoundException nnfe) {
            throw new RuntimeException(nnfe);
        }
    }


    private static Set<String> senderIdSet(final String senderIds) {

        if (senderIds == null) {
            throw new NullPointerException("null senderIds");
        }

        final Set<String> senderIdSet = new TreeSet<String>();

        for (String senderId : senderIds.split(",")) {
            if ((senderId = senderId.trim()).isEmpty()) {
                continue;
            }
            senderIdSet.add(senderId);
        }

        return senderIdSet;
    }


    private static String senderIds(final Set<String> senderIdSet) {

        if (senderIdSet == null) {
            throw new NullPointerException("null senderIds");
        }

        final StringBuilder builder = new StringBuilder();

        boolean first = true;
        for (final Iterator<String> i = senderIdSet.iterator(); i.hasNext();) {
            String senderId = i.next();
            if (senderId == null || (senderId = senderId.trim()).isEmpty()) {
                continue;
            }
            if (!first) {
                builder.append(",");
            }
            builder.append(senderId);
        }

        return builder.toString();
    }


    public static interface Callback {


        /**
         * Notifies with an cached registration id.
         *
         * @param registrationId the registration id which is never
         * {@code null}.
         */
        void cached(String registrationId);


        /**
         * Notifies with an restored registration id.
         *
         * @param registrationId the registration id which is never
         * {@code null}.
         */
        void restored(String registrationId);


        /**
         * Notifies with registered with optional exception.
         *
         * @param registrationId the registration id; {@code null} if failed to
         * register.
         * @param thrown the exception thrown while registering or {@code null}
         * if registered successfully.
         */
        void registered(String registrationId, IOException thrown);


        /**
         * Notifies unregistered with optional exception.
         *
         * @param thrown the exception thrown while unregistering or
         * {@code null} if unregistered successfully.
         */
        void unregistered(IOException thrown);


    }


    private static void register(final Context context,
                                 final Set<String> senderIdSet,
                                 final int versionCode, final Callback callback,
                                 final boolean unregister) {

        if (context == null) {
            throw new NullPointerException("null context");
        }

        if (senderIdSet == null) {
            throw new NullPointerException("null senderIds");
        }
        if (senderIdSet.isEmpty()) {
            throw new IllegalArgumentException("empty senderIdSet");
        }
        for (final String senderId : senderIdSet) {
            if (senderId == null) {
                throw new IllegalArgumentException("null senderId included");
            }
            if (senderId.trim().isEmpty()) {
                throw new IllegalArgumentException("blank senderId included");
            }
        }

        if (callback == null) {
            throw new NullPointerException("null callback");
        }

        final GoogleCloudMessaging gcm
            = GoogleCloudMessaging.getInstance(context);

        new AsyncTask<Void, Void, Void>() {


            @Override
            protected Void doInBackground(final Void... params) {

                if (unregister) {
                    try {
                        gcm.unregister();
                        callback.unregistered(null);
                    } catch (final IOException ioe) {
                        callback.unregistered(ioe);
                    }
                }

                final String registrationId;
                try {
                    registrationId = gcm.register(
                        senderIdSet.toArray(new String[senderIdSet.size()]));
                } catch (final IOException ioe) {
                    callback.registered(null, ioe);
                    return null;
                }

                final SharedPreferences preferences
                    = context.getSharedPreferences(
                        REGISTRATION_SHARED_PREFERENCES_NAME,
                        Context.MODE_PRIVATE);
                final Editor editor = preferences.edit();
                editor.putString(KEY_SENDER_IDS, senderIds(senderIdSet));
                editor.putInt(KEY_VERSION_CODE, versionCode);
                editor.putString(KEY_REGISTRATION_ID, registrationId);

                callback.registered(registrationId, null);

                return null;
            }


        }.execute();
    }


    private static void register(final Context context,
                                 final Set<String> senderIdSet,
                                 final int versionCode, final Callback callback)
        throws IOException {

        register(context, senderIdSet, versionCode, callback, false);
    }


    private static class InstanceHolder {


        private static final RegistrationProxy INSTANCE
            = new RegistrationProxy();


        private InstanceHolder() {

            super();
        }


    }


    public static RegistrationProxy getInstance() {

        return InstanceHolder.INSTANCE;
    }


    private RegistrationProxy() {

        super();
    }


    public void getRegistrationId(final Context context,
                                  final Callback callback)
        throws IOException {

        if (context == null) {
            throw new NullPointerException("null context");
        }

        if (callback == null) {
            throw new NullPointerException("null callback");
        }

        if (cachedRegistrationId != null) {
            callback.cached(cachedRegistrationId);
            return;
        }

        final int currentVersionCode = versionCode(context);

        final Properties properties = new Properties();
        {
            final AssetManager manager = context.getResources().getAssets();
            final InputStream stream = manager.open(
                CONFIGURATION_ASSET_FILE_NAME, AssetManager.ACCESS_BUFFER);
            try {
                properties.loadFromXML(stream);
            } finally {
                stream.close();
            }
        }

        final SharedPreferences preferences
            = context.getSharedPreferences(REGISTRATION_SHARED_PREFERENCES_NAME,
                                           Context.MODE_PRIVATE);

        final String configuredSenderIds
            = properties.getProperty(KEY_SENDER_IDS);
        if (configuredSenderIds == null) {
            throw new RuntimeException("no property for " + KEY_SENDER_IDS);
        }
        Log.d(tag, "configured sender ids: " + configuredSenderIds);
        final Set<String> configuredSenderIdSet
            = senderIdSet(configuredSenderIds);
        Log.d(tag, "configured sender id set: " + configuredSenderIdSet);
        if (configuredSenderIdSet.isEmpty()) {
            throw new RuntimeException("empty sender id set");
        }

        final String storedSenderIds
            = preferences.getString(KEY_SENDER_IDS, null);
        if (storedSenderIds == null) {
            Log.d(tag, "no stored sender ids");
            register(context, configuredSenderIdSet, currentVersionCode,
                     callback);
            return;
        }
        final Set<String> storedSenderIdSet = senderIdSet(storedSenderIds);
        if (!storedSenderIdSet.equals(configuredSenderIdSet)) {
            Log.d(tag, "stored sender id set is not equal to configured");
            register(context, configuredSenderIdSet, currentVersionCode,
                     callback, true);
            return;
        }

        final int storedVersionCode
            = preferences.getInt(KEY_VERSION_CODE, Integer.MIN_VALUE);
        if (storedVersionCode == Integer.MIN_VALUE) {
            Log.d(tag, "no stored version code");
            register(context, configuredSenderIdSet, currentVersionCode,
                     callback);
            return;
        }
        if (storedVersionCode != currentVersionCode) {
            Log.d(tag, "version codes don't match");
            register(context, configuredSenderIdSet, currentVersionCode,
                     callback);
            return;
        }

        final String storedRegistrationId
            = preferences.getString(KEY_REGISTRATION_ID, null);
        if (storedRegistrationId == null) {
            Log.d(tag, "no stored registration id");
            register(context, configuredSenderIdSet, currentVersionCode,
                     callback);
            return;
        }

        callback.restored(storedRegistrationId);

        cachedRegistrationId = storedRegistrationId;
    }


    private static volatile String cachedRegistrationId;


}

