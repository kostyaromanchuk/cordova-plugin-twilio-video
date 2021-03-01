package org.apache.cordova.twiliovideo;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import org.json.JSONObject;

public class TwilioVideo extends CordovaPlugin {

    public static final String TAG = "TwilioVideo";
    public static final String[] PERMISSIONS_REQUIRED = new String[] {
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO
    };

    private static final int PERMISSIONS_REQUIRED_REQUEST_CODE = 1;

    private CallbackContext callbackContext;
    private CordovaInterface cordova;
    private String roomId;
    private String token;
    private CallConfig config = new CallConfig();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        Log.d(TAG, "[VIDEOPLUGIN] JSMESSAGE IN > execute(): action received:'" + action + "'" );
        switch (action) {
            case "openRoom":
                this.registerCallListener(callbackContext);
                this.openRoom(args);
                break;
            case "startCall":
                this.registerCallListener(callbackContext);
                this.startCall(args);
                break;
            case "answerCall":
                this.registerCallListener(callbackContext);
                this.answerCall(args);
                break;
            case "showOffline":
                this.showOffline(args, callbackContext);
                break;
            case "showOnline":
                this.showOnline(args, callbackContext);
                break;
            case "show_twiliovideo":
                this.show_twiliovideo(args, callbackContext);
                break;
            case "hide_twiliovideo":
                this.hide_twiliovideo(args, callbackContext);
                break;
            case "closeRoom":
                this.registerCallListener(callbackContext);
                this.closeRoom(callbackContext);
                break;
            case "hasRequiredPermissions":
                this.hasRequiredPermissions(callbackContext);
                break;
            case "requestPermissions":
                this.requestRequiredPermissions();
                break;
        }
        return true;
    }

    public void openRoom(final JSONArray args) {
        Log.d(TAG, "[VIDEOPLUGIN] openRoom(args) CALLED" );
        try {
            this.token = args.getString(0);
            this.roomId = args.getString(1);

            //Added by BC
            String local_user_name = args.getString(2);
            String local_user_photo_url = args.getString(3);
            String remote_user_name = args.getString(4);
            String remote_user_photo_url = args.getString(5);

            //--------------------------------------------------------------------------------------
            //JS null gets mapped to String "null" not JAVA null - set it back to java null
            //--------------------------------------------------------------------------------------
            //    let global_local_user_photo_url = null;
            //    let global_local_user_name = null;
            //
            //    let global_remote_user_photo_url = null;
            //    let global_remote_user_name = null;
            //------------------------------------------------------------
            //    String local_user_name >>> "null" NOT null;
            //    String local_user_photo_url >>> "null" NOT null;
            //    String remote_user_name >>> "null" NOT null;
            //    String remote_user_photo_url >>> "null" NOT null;
            //------------------------------------------------------------
            if(null != local_user_name){
                if(local_user_name.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    local_user_name = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
            	Log.e(TAG, "[VIDEOPLUGIN][openRoom()] local_user_name is null");
            }
            //------------------------------------------------------------
            if(null != local_user_photo_url){
                if(local_user_photo_url.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    local_user_photo_url = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][openRoom()] local_user_photo_url is null");
            }
            //------------------------------------------------------------
            if(null != remote_user_name){
                if(remote_user_name.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    remote_user_name = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][openRoom()] remote_user_name is null");
            }
            //------------------------------------------------------------
            if(null != remote_user_photo_url){
                if(remote_user_photo_url.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    remote_user_photo_url = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][openRoom()] remote_user_photo_url is null");
            }
            //--------------------------------------------------------------------------------------

            final CordovaPlugin that = this;
            final String room = this.token;
            final String roomId = this.roomId;

            final String final_local_user_name = local_user_name;
            final String final_local_user_photo_url = local_user_photo_url;
            final String final_remote_user_name = remote_user_name;
            final String final_remote_user_photo_url = remote_user_photo_url;


            //param:config moved to position 6
            if (args.length() > 6) {
                this.config.parse(args.optJSONObject(6));
            }

            //LOG.e(TAG, "TOKEN: " + this.getClass());
            LOG.d(TAG, "TOKEN: " + token);
            LOG.d(TAG, "ROOMID: " + roomId);
            LOG.d(TAG, "local_user_name: " + local_user_name);
            LOG.d(TAG, "local_user_photo_url: " + local_user_photo_url);
            LOG.d(TAG, "remote_user_name: " + remote_user_name);
            LOG.d(TAG, "remote_user_photo_url: " + remote_user_photo_url);

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());

                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_openRoom;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for startCall only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);

                    //--------------------------------------------------------------------------------------
                    //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
                    //so copy values into final versions of same
                    //--------------------------------------------------------------------------------------
                    intent_TwilioVideoActivity.putExtra("token", token);
                    intent_TwilioVideoActivity.putExtra("roomId", roomId);
                    intent_TwilioVideoActivity.putExtra("config", config);

                    intent_TwilioVideoActivity.putExtra("local_user_name", final_local_user_name);
                    intent_TwilioVideoActivity.putExtra("local_user_photo_url", final_local_user_photo_url);
                    intent_TwilioVideoActivity.putExtra("remote_user_name", final_remote_user_name);
                    intent_TwilioVideoActivity.putExtra("remote_user_photo_url", final_remote_user_photo_url);

                    //single instance - reuse existing one
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    // that.cordova.getActivity() is MainActivity

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (JSONException e) {
            Log.e(TAG, "[VIDEOPLUGIN][openRoom()] Couldn't open room. No valid input params", e);
        }
    }

    public void startCall(final JSONArray args) {
        Log.d(TAG, "[VIDEOPLUGIN] startCall(args) CALLED" );
        try {
            this.token = args.getString(0);
            this.roomId = args.getString(1);

            //--------------------------------------------------------------------------------------
            //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
            //so copy values into final versions of same
            //--------------------------------------------------------------------------------------

            final CordovaPlugin that = this;
            final String room = this.token;
            final String roomId = this.roomId;

            //param:config moved to position 6
            if (args.length() > 6) {
                this.config.parse(args.optJSONObject(6));
            }

            LOG.d(TAG, "TOKEN: " + token);
            LOG.d(TAG, "ROOMID: " + roomId);


            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_startCall;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for startCall only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);
                    //------------------------------------------------------------------------------

                    intent_TwilioVideoActivity.putExtra("token", token);
                    intent_TwilioVideoActivity.putExtra("roomId", roomId);
                    intent_TwilioVideoActivity.putExtra("config", config);

                    //------------------------------------------------------------------------------
                    //prevent startCall > startActivity from creating new instance
                    //------------------------------------------------------------------------------
                    //openRoom() and startCall() both call startActivity on  TwilioVideoActivity
                    //    - openRoom on P1 and answerCall on P2 SHOULD CREATE NEW INSTANCE
                    //        - startActivity
                    //            - onCreate
                    //                - instance 699
                    //    - startCall
                    //        - startActivity
                    //            - onResume  NOT onCreate so add FLAG_ACTIVITY_REORDER_TO_FRONT
                    //                - same instance
                    //------------------------------------------------------------------------------
                    //but startCall should use existing instance
                    //single instance - reuse existing one
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);


                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (JSONException e) {
            Log.e(TAG, "[VIDEOPLUGIN][startCall() ] Couldn't open room. No valid input params", e);
        }
    }

    public void answerCall(final JSONArray args) {
        Log.d(TAG, "[VIDEOPLUGIN] answerCall(args) CALLED" );
        try {
            this.token = args.getString(0);
            this.roomId = args.getString(1);


            //Added by BC
            String local_user_name = args.getString(2);
            String local_user_photo_url = args.getString(3);
            String remote_user_name = args.getString(4);
            String remote_user_photo_url = args.getString(5);

            //param:config moved to position 6
            //parsed below


            //--------------------------------------------------------------------------------------
            //JS null gets mapped to String "null" not JAVA null - set it back to java null
            //--------------------------------------------------------------------------------------
            //    let global_local_user_photo_url = null;
            //    let global_local_user_name = null;
            //
            //    let global_remote_user_photo_url = null;
            //    let global_remote_user_name = null;
            //------------------------------------------------------------
            //    String local_user_name >>> "null" NOT null;
            //    String local_user_photo_url >>> "null" NOT null;
            //    String remote_user_name >>> "null" NOT null;
            //    String remote_user_photo_url >>> "null" NOT null;
            //------------------------------------------------------------
            if(null != local_user_name){
                if(local_user_name.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    local_user_name = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][answerCall()] local_user_name is null");
            }
            //------------------------------------------------------------
            if(null != local_user_photo_url){
                if(local_user_photo_url.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    local_user_photo_url = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][answerCall()] local_user_photo_url is null");
            }
            //------------------------------------------------------------
            if(null != remote_user_name){
                if(remote_user_name.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    remote_user_name = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][answerCall()] remote_user_name is null");
            }
            //------------------------------------------------------------
            if(null != remote_user_photo_url){
                if(remote_user_photo_url.equals("null")){
                    //JS null gets mapped to string "null" not java null
                    remote_user_photo_url = null;

                }else{
                    //OK string is not null or "null"
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN][answerCall()] remote_user_photo_url is null");
            }

            //--------------------------------------------------------------------------------------
            //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
            //so copy values into final versions of same
            //--------------------------------------------------------------------------------------

            final CordovaPlugin that = this;

            final String room = this.token;
            final String roomId = this.roomId;

            final String final_local_user_name = local_user_name;
            final String final_local_user_photo_url = local_user_photo_url;
            final String final_remote_user_name = remote_user_name;
            final String final_remote_user_photo_url = remote_user_photo_url;




            //config : {"startWithAudioOff":false,"startWithVideoOff":true}
            //param:config moved to position 6
            if (args.length() > 6) {
                this.config.parse(args.optJSONObject(6));
            }

            LOG.d(TAG, "TOKEN: " + token);
            LOG.d(TAG, "ROOMID: " + roomId);


            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //Android same activity
                    intent_TwilioVideoActivity.putExtra("action", "answerCall");

                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_answerCall;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for startCall only onResume is called so getIntent still shows "openRoom"
                    //this is only issue for startCall - for openRoom/ answercall startActivity with create new Activity
                    //so getIntent() will have correct "action"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);
                    //------------------------------------------------------------------------------

                    intent_TwilioVideoActivity.putExtra("token", token);
                    intent_TwilioVideoActivity.putExtra("roomId", roomId);
                    intent_TwilioVideoActivity.putExtra("config", config);
                    intent_TwilioVideoActivity.putExtra("isStartWithAudioOff", config.isStartWithAudioOff());
                    intent_TwilioVideoActivity.putExtra("isStartWithVideoOff", config.isStartWithVideoOff());

                    //--------------------------------------------------------------------------------------
                    //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
                    //so copy values into final versions of same
                    //--------------------------------------------------------------------------------------

                    intent_TwilioVideoActivity.putExtra("local_user_name", final_local_user_name);
                    intent_TwilioVideoActivity.putExtra("local_user_photo_url", final_local_user_photo_url);
                    intent_TwilioVideoActivity.putExtra("remote_user_name", final_remote_user_name);
                    intent_TwilioVideoActivity.putExtra("remote_user_photo_url", final_remote_user_photo_url);

                    //------------------------------------------------------------------------------
                    //startActivity - answerCall on P2 SHOULD CREATE NEW INSTANCE
                    //------------------------------------------------------------------------------
                    //    - openRoom on P1 and answerCall on P2 SHOULD CREATE NEW INSTANCE
                    //    - openRoom
                    //        - startActivity
                    //            - onCreate
                    //                - instance 699
                    //    - startCall() / answerCall()
                    //        - startActivity
                    //            - onResume  NOT onCreate so add FLAG_ACTIVITY_REORDER_TO_FRONT
                    //                - same instance
                    //------------------------------------------------------------------------------
                    //but answerCall() should use existing instance
                    //intentTwilioVideo.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); used in startCall only


                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    //that.cordova.getActivity() is MainActivity
                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);
                }

            });
        } catch (JSONException e) {
            Log.e(TAG, "[VIDEOPLUGIN][answerCall()] Couldn't open room. No valid input params", e);
        }
    }

    public void showOffline(final JSONArray args, CallbackContext callbackContext) {
        Log.d(TAG, "[VIDEOPLUGIN] showOffline CALLED");
        //no params try catch not needed
        //--------------------------------------------------------------------------------------
        //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
        //so copy values into final versions of same
        //--------------------------------------------------------------------------------------

        final CordovaPlugin that = this;
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_showOffline;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for showOffline only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);
                    //------------------------------------------------------------------------------



                    //------------------------------------------------------------------------------
                    //prevent showOffline > startActivity from creating new instance
                    //------------------------------------------------------------------------------
                    //openRoom() and showOffline() both call startActivity on  TwilioVideoActivity
                    //    - openRoom on P1 and answerCall on P2 SHOULD CREATE NEW INSTANCE
                    //        - startActivity
                    //            - onCreate
                    //                - instance 699
                    //    - showOffline/showOnline / startCall - SHOULD REUSE EXISTING Activity
                    //        - startActivity
                    //            - onResume  NOT onCreate so add FLAG_ACTIVITY_REORDER_TO_FRONT
                    //                - same instance
                    //------------------------------------------------------------------------------
                    //but showOffline should use existing instance

                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);



                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (Exception exception) {
            Log.e(TAG, "[VIDEOPLUGIN] showOffline failed", exception);
            this.sendPluginErrorResult(callbackContext);
            return;
        }
        this.sendPluginOkResult(callbackContext);
    }


    public void showOnline(final JSONArray args, CallbackContext callbackContext) {
        Log.d(TAG, "[VIDEOPLUGIN] showOnline CALLED");

        //no params try catch not needed
        //--------------------------------------------------------------------------------------
        //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
        //so copy values into final versions of same
        //--------------------------------------------------------------------------------------

        final CordovaPlugin that = this;
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_showOnline;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for showOnline only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);
                    //------------------------------------------------------------------------------



                    //------------------------------------------------------------------------------
                    //prevent showOnline > startActivity from creating new instance
                    //------------------------------------------------------------------------------
                    //openRoom() and showOnline() both call startActivity on  TwilioVideoActivity
                    //    - openRoom on P1 and answerCall on P2 SHOULD CREATE NEW INSTANCE
                    //        - startActivity
                    //            - onCreate
                    //                - instance 699
                    //    - showOnline/showOnline / startCall - SHOULD REUSE EXISTING Activity
                    //        - startActivity
                    //            - onResume  NOT onCreate so add FLAG_ACTIVITY_REORDER_TO_FRONT
                    //                - same instance
                    //------------------------------------------------------------------------------
                    //but showOnline should use existing instance
                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);


                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (Exception exception) {
            Log.e(TAG, "[VIDEOPLUGIN] showOnline failed", exception);
            this.sendPluginErrorResult(callbackContext);
            return;
        }
        this.sendPluginOkResult(callbackContext);
    }


    public void show_twiliovideo(final JSONArray args, CallbackContext callbackContext) {
        Log.e(TAG, "[VIDEOPLUGIN][TwilioVideo.java] show_twiliovideo CALLED");

        //no params try catch not needed
        //--------------------------------------------------------------------------------------
        //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
        //so copy values into final versions of same
        //--------------------------------------------------------------------------------------

        final CordovaPlugin that = this;
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_show_twiliovideo;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for showOnline only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);

                    //------------------------------------------------------------------------------
                    //prevent action_show_twiliovideo > startActivity from creating new instance
                    //------------------------------------------------------------------------------

                    //but showOnline should use existing instance
                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);


                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (Exception exception) {
            Log.e(TAG, "[VIDEOPLUGIN] show_twiliovideo failed", exception);
            this.sendPluginErrorResult(callbackContext);
            return;
        }
        this.sendPluginOkResult(callbackContext);
    }

    public void hide_twiliovideo(final JSONArray args, CallbackContext callbackContext) {
        Log.e(TAG, "[VIDEOPLUGIN][TwilioVideo.java] hide_twiliovideo CALLED");

        //no params try catch not needed
        //--------------------------------------------------------------------------------------
        //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
        //so copy values into final versions of same
        //--------------------------------------------------------------------------------------

        final CordovaPlugin that = this;
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_hide_twiliovideo;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for hide_twiliovideo only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);

                    //------------------------------------------------------------------------------
                    //prevent hide_twiliovideo > startActivity from creating new instance
                    //------------------------------------------------------------------------------

                    //but showOnline should use existing instance
                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);


                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (Exception exception) {
            Log.e(TAG, "[VIDEOPLUGIN] hide_twiliovideo failed", exception);
            this.sendPluginErrorResult(callbackContext);
            return;
        }
        this.sendPluginOkResult(callbackContext);
    }





    //**********************************************************************************************

    private void registerCallListener(final CallbackContext callbackContext) {
        if (callbackContext == null) {
            return;
        }
        TwilioVideoManager.getInstance().setEventObserver(new CallEventObserver() {
            @Override
            public void onEvent(String event, JSONObject data) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] Event received: '%s' with data: %s", event, data));

                if(null != event){
                    if(event.equals("CLOSED")){
                        Log.d(TAG, "[VIDEOPLUGIN] onEvent: CLOSED received - room.disconnect() was called by TVA");

                    }else if(event.equals("DISCONNECTED")){
                        Log.d(TAG, "[VIDEOPLUGIN] onEvent: DISCONNECTED received - TWA finish; called");

                    }else{
                        Log.d(TAG, "[VIDEOPLUGIN] onEvent: UNHANDLED EVENT - ignore passed back to twilio.js:" + event);
                    }
                }else{
                	Log.e(TAG, "event is null");
                }

                //----------------------------------------------------------------------------------
                //RETURN RESPONSE to twilio.js > index.js
                JSONObject eventData = new JSONObject();
                try {
                    eventData.putOpt("event", event);
                    eventData.putOpt("data", data);
                } catch (JSONException e) {
                    Log.e(TAG, "[VIDEOPLUGIN][registerCallListener()] Failed to create event: " + event);
                    return;
                }
                //----------------------------------------------------------------------------------
                PluginResult result = new PluginResult(PluginResult.Status.OK, eventData);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
                //----------------------------------------------------------------------------------
            }
        });
    }



//    private void closeRoom(CallbackContext callbackContext) {
//        Log.e(TAG, "[VIDEOPLUGIN] closeRoom: CALL TwilioVideoManager.getInstance().publishDisconnection() >> actionListener.onDisconnect(); >> TwilioVideoActivity.onDisconnect ");
//        if (TwilioVideoManager.getInstance().publishDisconnection()) {
//            Log.e(TAG, "[VIDEOPLUGIN] closeRoom: publishDisconnection: OK >> sendPluginErrorResult");
//
//            this.sendPluginOkResult(callbackContext);
//
//        } else {
//            Log.e(TAG, "[VIDEOPLUGIN] closeRoom: publishDisconnection: failed >> sendPluginErrorResult");
//            this.sendPluginErrorResult(callbackContext);
//        }
//    }

    public void closeRoom(CallbackContext callbackContext) {
        Log.e(TAG, "[VIDEOPLUGIN][TwilioVideo.java] closeRoom CALLED");

        //no params try catch not needed
        //--------------------------------------------------------------------------------------
        //putExtra can throw error: local variables referenced from an inner class must be final or effectively final
        //so copy values into final versions of same
        //--------------------------------------------------------------------------------------

        final CordovaPlugin that = this;
        try {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Intent intent_TwilioVideoActivity = new Intent(Intent.ACTION_VIEW);

                    intent_TwilioVideoActivity.setClass(that.cordova.getActivity().getBaseContext(), TwilioVideoActivity.class);
                    intent_TwilioVideoActivity.setPackage(that.cordova.getActivity().getApplicationContext().getPackageName());


                    //------------------------------------------------------------------------------
                    //Android same activity
                    String nextAction = TwilioVideoActivityNextAction.action_closeRoom;
                    intent_TwilioVideoActivity.putExtra("action", nextAction);

                    //getIntent() only gets the intent set startActivity > onCreate
                    //for showOnline only onResume is called so getIntent still shows "openRoom"

                    TwilioVideoActivityNextAction.setNextAction(nextAction);

                    //------------------------------------------------------------------------------
                    //prevent action_closeRoom > startActivity from creating new instance
                    //------------------------------------------------------------------------------

                    //but showOnline should use existing instance
                    //single instance - reuse existing one if available
                    intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);


                    //------------------------------------------------------------------------------
                    //DEBUG its MainActivity
                    //Activity cordova_activity = that.cordova.getActivity();

                    that.cordova.getActivity().startActivity(intent_TwilioVideoActivity);

                }

            });
        } catch (Exception exception) {
            Log.e(TAG, "[VIDEOPLUGIN] closeRoom failed", exception);
            this.sendPluginErrorResult(callbackContext);
            return;
        }
        this.sendPluginOkResult(callbackContext);
    }

    private void hasRequiredPermissions(CallbackContext callbackContext) {

        boolean hasRequiredPermissions = true;
        for (String permission : TwilioVideo.PERMISSIONS_REQUIRED) {
            hasRequiredPermissions = cordova.hasPermission(permission);
            if (!hasRequiredPermissions) { break; }
        }

        callbackContext.sendPluginResult(
            new PluginResult(PluginResult.Status.OK, hasRequiredPermissions)
        );
    }

    private void requestRequiredPermissions() {
        cordova.requestPermissions(this, PERMISSIONS_REQUIRED_REQUEST_CODE, PERMISSIONS_REQUIRED);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUIRED_REQUEST_CODE) {

            boolean requiredPermissionsGranted = true;
            for (int grantResult : grantResults) {
                requiredPermissionsGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, requiredPermissionsGranted);
            callbackContext.sendPluginResult(result);
        } else {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
        }
    }

    public Bundle onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putString("token", this.token);
        state.putString("roomId", this.roomId);
        state.putSerializable("config", this.config);
        return state;
    }

    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        this.token = state.getString("token");
        this.roomId = state.getString("roomId");
        this.config = (CallConfig) state.getSerializable("config");
        this.callbackContext = callbackContext;
    }

    private void sendPluginOkResult(CallbackContext callbackContext) {
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void sendPluginErrorResult(CallbackContext callbackContext) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }
}
