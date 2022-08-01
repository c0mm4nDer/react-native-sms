package com.tkporter.sendsms;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.facebook.jni.HybridData;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class SendSMSModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int REQUEST_CODE = 5235;
    private final ReactApplicationContext reactContext;
    private Callback callback = null;

    public SendSMSModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "SendSMS";
    }

    //    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        //System.out.println("in module onActivityResult() request " + requestCode + " result " + resultCode);
//        //canceled intent
//        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
//            sendCallback(false, true, false);
//        }
//    }
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        //System.out.println("in module onActivityResult() request " + requestCode + " result " + resultCode);
        //canceled intent
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_CANCELED) {
            sendCallback(false, true, false);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    public void sendCallback(Boolean completed, Boolean cancelled, Boolean error) {
        if (callback != null) {
            callback.invoke(completed, cancelled, error);
            callback = null;
        }
    }

    @ReactMethod
    public void getSims(Promise promise) {
        WritableMap map = Arguments.createMap();
        WritableArray array = new WritableNativeArray();
        if (Build.VERSION.SDK_INT >= 22) {
            if (ActivityCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                promise.resolve(map);
                return ;
            }
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(getReactApplicationContext());

            final List<SubscriptionInfo> activeSubscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();


            for (int i = 0; i < activeSubscriptionInfoList.size(); i++) {
                WritableMap m =  Arguments.createMap();
                m.putString("subscriptionId", String.valueOf(activeSubscriptionInfoList.get(i).getSubscriptionId()));
                m.putString("simSlotIndex", String.valueOf(activeSubscriptionInfoList.get(i).getSimSlotIndex()));
                m.putString("carrierName", String.valueOf(activeSubscriptionInfoList.get(i).getCarrierName()));
                m.putString("displayName", String.valueOf(activeSubscriptionInfoList.get(i).getDisplayName()));
                array.pushMap(m);
            }
            map.putArray("SIMs", array);

            Log.i("tag",  map.toString());
//            simCount = activeSubscriptionInfoList.size();
        }
//        Log.d("TAG", "getSims:"+ simCount);
        promise.resolve(map);
    }

    @ReactMethod
    public void send(ReadableMap options, final Callback callback) {
        try {
            this.callback = callback;

            if (options.hasKey("direct_send") ? options.getBoolean("direct_send") : false) {
                sendDirect(options, callback);
                return;
            }

            new SendSMSObserver(reactContext, this, options).start();

            String body = options.hasKey("body") ? options.getString("body") : "";
            ReadableArray recipients = options.hasKey("recipients") ? options.getArray("recipients") : null;

            Intent sendIntent;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(reactContext);
                sendIntent = new Intent(Intent.ACTION_SEND);
                if (defaultSmsPackageName != null) {
                    sendIntent.setPackage(defaultSmsPackageName);
                }
                sendIntent.setType("text/plain");
            } else {
                sendIntent = new Intent(Intent.ACTION_VIEW);
                sendIntent.setType("vnd.android-dir/mms-sms");
            }

            sendIntent.putExtra("sms_body", body);
            sendIntent.putExtra(sendIntent.EXTRA_TEXT, body);
            sendIntent.putExtra("exit_on_sent", true);

            //if recipients specified
            if (recipients != null) {
                //Samsung for some reason uses commas and not semicolons as a delimiter
                String separator = ";";
                if (android.os.Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                    separator = ",";
                }
                String recipientString = "";
                for (int i = 0; i < recipients.size(); i++) {
                    recipientString += recipients.getString(i);
                    recipientString += separator;
                }
                sendIntent.putExtra("address", recipientString);
            }

            reactContext.startActivityForResult(sendIntent, REQUEST_CODE, sendIntent.getExtras());
        } catch (Exception e) {
            //error!
            sendCallback(false, false, true);
            throw e;
        }
    }

    /**
     * todo: do it in a background process
     *
     * @param options
     * @param callback
     */
    private void sendDirect(ReadableMap options, Callback callback) {

        String msg = options.hasKey("body") ? options.getString("body") : "";
        int sim = options.hasKey("sim") ? options.getInt("sim") : -1;


        ReadableArray recipients = options.hasKey("recipients") ? options.getArray("recipients") : null;
        for (int i = 0; i < recipients.size(); i++) {
            String phoneNo = recipients.getString(i);

            try {
                SmsManager smsManager;
                Log.d("test", String.valueOf(sim));
                if (Build.VERSION.SDK_INT >= 22 && sim > -1 ) {
                    smsManager   = SmsManager.getSmsManagerForSubscriptionId(sim);
                }else{
                    smsManager = SmsManager.getDefault();
                }
                smsManager.sendTextMessage(phoneNo, null, msg, null, null);
            } catch (Exception ex) {
                ex.printStackTrace();
                sendCallback(false, false, true);
                return;
            }
        }

        sendCallback(true, false, false);

    }

}
