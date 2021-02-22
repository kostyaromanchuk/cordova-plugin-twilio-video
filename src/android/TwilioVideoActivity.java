package org.apache.cordova.twiliovideo;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twilio.video.CameraCapturer;
import com.twilio.video.CameraCapturer.CameraSource;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoRenderer;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.squareup.picasso.Picasso;


//RELEASE - comment in before release no MainActivity in POC
import live.sea.chat.MainActivity;

import static android.media.AudioManager.MODE_CURRENT;
import static android.media.AudioManager.MODE_INVALID;
import static android.media.AudioManager.MODE_IN_CALL;
import static android.media.AudioManager.MODE_IN_COMMUNICATION;
import static android.media.AudioManager.MODE_NORMAL;
import static android.media.AudioManager.MODE_RINGTONE;


//DONT USE it works in this POC but when they integrate with the main sea/chat app theres no capacitor so it breaks
//import capacitor.android.plugins.R;


public class TwilioVideoActivity extends AppCompatActivity implements CallActionObserver {
    public static final String TAG = "TwilioVideoActivity";


    //----------------------------------------------------------------------------------------------
    //Is TwilioVideoActivity running?
    //----------------------------------------------------------------------------------------------
    //uses by onAnswer to switch to this Activity if it active if not then switch to MainActivity
    //need for when app is stopped completely, you get call.
    //onAnswer executes quickly so MainActivity may not have init the call
    //MainActivity AND TwilioActivity should be init before you open TwilioVideoActivity from onAnswer
    //https://stackoverflow.com/questions/5446565/android-how-do-i-check-if-activity-is-running
    static int activeInstances = 0;
    static boolean onResumeCompletedAtLeastOnce = false;


    public static boolean isActive() {
        return (activeInstances > 0);
    }


    //USED BY MyConnectionService [if(TwilioVideoActivity.onResumeHasCompletedAtLeastOnce())]
    public static boolean onResumeHasCompletedAtLeastOnce() {
        return onResumeCompletedAtLeastOnce;
    }
    //set and removed in onStart onStop


    public static TwilioVideoActivity twilioVideoActivity = null;
    //public static boolean endCall_can_disconnect = true;





    /*
     * Audio and video tracks can be created with names. This feature is useful for categorizing
     * tracks of participants. For example, if one participant publishes a video track with
     * ScreenCapturer and CameraCapturer with the names "screen" and "camera" respectively then
     * other participants can use RemoteVideoTrack#getName to determine which video track is
     * produced from the other participant's screen or camera.
     */
    private static final String LOCAL_AUDIO_TRACK_NAME = "microphone";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    private static final int PERMISSIONS_REQUEST_CODE = 1;

    private static FakeR FAKE_R;

    /*
     * Access token used to connect. This field will be set either from the console generated token
     * or the request to the token server.
     */
    private String accessToken;
    private String roomId;

    private String action_current;

    private CallConfig config;

    private String local_user_name;
    private String local_user_photo_url;
    private String remote_user_name;
    private String remote_user_photo_url;

    /*
     * A Room represents communication between a local participant and one or more participants.
     */
    private Room room;
    private LocalParticipant localParticipant;

    /*
     * A VideoView receives frames from a local or remote video track and renders them
     * to an associated view.
     * //in android the video feed is swapped from fullScreenVideoView to thumbnailVideoView so both can be remote
     * //in iOS we resize the large one into the smaller
     */
    private VideoView fullScreenVideoView;
    private VideoView thumbnailVideoView;
    private android.widget.FrameLayout thumbnailVideoViewFrameLayout;

    private boolean thumbnailVideoViewWasInitialised;

    //----------------------------------------------------------------------------------------------
    //APPLYING BLUR
    private android.widget.FrameLayout video_container;
    private androidx.coordinatorlayout.widget.CoordinatorLayout activity_video_coordinatorlayout;
//    private android.widget.LinearLayout blurredviewgroup;

    //----------------------------------------------------------------------------------------------
    //margins for zoom
    private android.widget.LinearLayout bottom_buttons_linearlayout;

    /*
     * Android application UI elements
     */
    private CameraCapturerCompat cameraCapturer;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;

    private FloatingActionButton button_fab_localvideo_onoff;
    private FloatingActionButton button_fab_localaudio_onoff;
    private FloatingActionButton button_fab_switchaudio;
    private FloatingActionButton button_fab_switchcamera;
    private FloatingActionButton button_fab_disconnect;


    private AudioManager audioManager;

    HeadsetIntentReceiver headsetIntentReceiver = new HeadsetIntentReceiver();
    SpeakerPhoneChangeIntentReceiver speakerPhoneChangeIntentReceiver = new SpeakerPhoneChangeIntentReceiver();


    private String participantIdentity;

    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private boolean disconnectedFromOnDestroy;


    private boolean roomListener_onDisconnectedCalled = false;


    private VideoRenderer localVideoView;

    //play ringing.mp3
    private MediaPlayer mediaPlayer;

    //if hone near ear - turn of local camera - else remote user sees your ear
    SensorManager mSensorManager;
    Sensor mSensor;

    private boolean localVideoTrack_wasOnBeforeMovedPhoneToEar;


    //remote participant image
    //To pulse the border we has a circle view behind it with border and animate its alpha
    private ImageView imageViewRemoteParticipantWhilstCalling;
    private ImageView imageViewRemoteParticipantWhilstCallingToAnimate;

    private ImageView imageViewRemoteParticipantInCall;

    private ImageView imageViewLocalParticipant;

    private TextView textViewRemoteParticipantName;
    private TextView textViewRemoteParticipantConnectionState;


    private TextView textViewInCallRemoteName;
    private ImageView imageViewInCallRemoteMicMuteState;

    private Button buttonHiddenSwitchVideo;

    private ImageView imageViewSwitchVideo;

    private boolean previewIsFullScreen;

    boolean runAnimation = true;

    private LinearLayout viewAlert;

    private ImageButton buttonBackToCall;

    private boolean is_Activity_hiding_because_buttonBackToCall_tapped;

    //----------------------------------------------------------------------------------------------
    //DOUBLE TAP ON CAMERA CAN CAUSE CLICK
    //prevents double taps - can cause crashes
    boolean button_localVideo_OnClickListener_tapped = false;
    Handler handler_onClick_camera_doubletap = null;
    long delayMillis_onClick_camera_doubletap  = 1000; //1 sec - can only tap the button once a second

    Runnable delayRunnable_onClick_camera_doubletap = null;
    //----------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onCreate: STARTED:" + this);


        TwilioVideoActivity.twilioVideoActivity = this;


        TwilioVideoManager.getInstance().setActionListenerObserver(this);

        FAKE_R = new FakeR(this);

        publishEvent(CallEvent.OPENED);

        //------------------------------------------------------------------------------------------
        //ContentView
        //------------------------------------------------------------------------------------------
        setContentView(FAKE_R.getLayout("activity_video"));


        activity_video_coordinatorlayout = findViewById(FAKE_R.getId("activity_video_coordinatorlayout"));
        video_container = findViewById(FAKE_R.getId("video_container"));
//        blurredviewgroup = findViewById(FAKE_R.getId("blurredviewgroup"));
        bottom_buttons_linearlayout = findViewById(FAKE_R.getId("bottom_buttons_linearlayout"));

        //------------------------------------------------------------------------------------------
        //ALERT  - hidden covers all controls
        // triggered by cordova.showOffline() >> action_showOffline > VISIBLE
        // hiddden   by cordova.showOnline() >> action_showOnline   > INVISIBLE
        //------------------------------------------------------------------------------------------
        viewAlert = findViewById(FAKE_R.getId("viewAlert"));
        //------------------------------------------------------------------------------------------
        //DEFAULT TO HIDDEN AS BLOCK THE WHOLE WINDOW
        //    <LinearLayout
        //        android:id="@+id/viewAlert"
        //        ...
        //        android:visibility="gone"

        //------------------------------------------------------------------------------------------
        this.viewAlert.setVisibility(View.INVISIBLE);
        //------------------------------------------------------------------------------------------

        //openRoom local camera is in  fullScreenVideoView
        //startACall local moves to  thumbnailVideoView
        //Remote video feed  is in fullScreenVideoView
        fullScreenVideoView = findViewById(FAKE_R.getId("fullscreen_video_view"));

        thumbnailVideoView = findViewById(FAKE_R.getId("thumbnail_video_view"));
        thumbnailVideoViewFrameLayout = findViewById(FAKE_R.getId("thumbnail_video_view_framelayout"));




        //------------------------------------------------------------------------------------------
        //BOTTOM BUTTONS
        //------------------------------------------------------------------------------------------
        //CALLER VIDEO ON/OFF
        button_fab_localvideo_onoff = findViewById(FAKE_R.getId("local_video_action_fab"));

        //MUTE AUDIO
        button_fab_localaudio_onoff = findViewById(FAKE_R.getId("local_audio_action_fab"));

        //FLIP CAMERA
        button_fab_switchcamera = findViewById(FAKE_R.getId("switch_camera_action_fab"));

        //SWITCH AUDIO - AUDIO/HEADPHONES
        button_fab_switchaudio = findViewById(FAKE_R.getId("switch_audio_action_fab"));

        //RED DISCONNECT BUTTON
        button_fab_disconnect = findViewById(FAKE_R.getId("connect_action_fab"));
        //------------------------------------------------------------------------------------------

        //filled below from url passed in from cordova via intent
        imageViewRemoteParticipantWhilstCalling = findViewById(FAKE_R.getId("imageViewRemoteParticipantWhilstCalling"));
        imageViewRemoteParticipantWhilstCallingToAnimate = findViewById(FAKE_R.getId("imageViewRemoteParticipantWhilstCallingToAnimate"));
        imageViewRemoteParticipantInCall        = findViewById(FAKE_R.getId("imageViewRemoteParticipantInCall"));
        imageViewLocalParticipant = findViewById(FAKE_R.getId("imageViewLocalParticipant"));

        textViewRemoteParticipantName = findViewById(FAKE_R.getId("textViewRemoteParticipantName"));
        textViewRemoteParticipantConnectionState = findViewById(FAKE_R.getId("textViewRemoteParticipantConnectionState"));


        imageViewInCallRemoteMicMuteState = findViewById(FAKE_R.getId("imageViewInCallRemoteMicMuteState"));
        textViewInCallRemoteName = findViewById(FAKE_R.getId("textViewInCallRemoteName"));

        imageViewSwitchVideo = findViewById(FAKE_R.getId("imageViewSwitchVideo"));
        //dont show in fullscreen
        imageViewSwitchVideo.setVisibility(View.INVISIBLE);


        //hidden button no text over thrumbnail tap to flip camera
        buttonHiddenSwitchVideo = findViewById(FAKE_R.getId("buttonHiddenSwitchVideo"));
        buttonHiddenSwitchVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "[VIDEOPLUGIN] onClick: buttonHiddenSwitchVideo tapped - flip camera if not in full screen");
                //--------------------------------------------------------------------------------------
//RELEASE - put back
                //dont flip  if in full screen in openRoom - can be trigger too easily when you tap on 4 main buttons
                if(previewIsFullScreen){
                    Log.d(TAG, "[VIDEOPLUGIN] previewIsFullScreen is true - dont flip camera - too near the big buttons");
                }else{
                    //------------------------------------------------------------------------------
                    //FLIP  FRONT / BACK CAMERA
                    //------------------------------------------------------------------------------
                    if (cameraCapturer != null) {
                        CameraSource cameraSource = cameraCapturer.getCameraSource();
                        cameraCapturer.switchCamera();

                        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                            thumbnailVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                        } else {
                            fullScreenVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                        }
                    }else{
                        Log.e(TAG, "[VIDEOPLUGIN] cameraCapturer is null");
                    }
                    //----------------------------------------------------------------------------
                }
                //----------------------------------------------------------------------------------
//RELEASE - comment out
//                publishEvent(CallEvent.DEBUGCLOSEROOM);


            }
        });

        //------------------------------------------------------------------------------------------
        //BUTTON - Back to Call (back arrow)
        //------------------------------------------------------------------------------------------
        buttonBackToCall = findViewById(FAKE_R.getId("buttonBackToCall"));
        buttonBackToCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e(TAG, "[VIDEOPLUGIN] onClick: buttonBackToCall tapped - TODO");
                //------------------------------------------------------------------------------------------
                //
                //                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                //
                //                // get the info from the currently running task
                //
                //                List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);
                //
                //                Log.d("topActivity", "CURRENT Activity ::"
                //
                //                        + taskInfo.get(0).topActivity.getClassName());
                //
                ////                for (task : taskInfo )
                ////                ComponentName componentInfo = taskInfo.get(0).topActivity;
                ////
                ////                componentInfo.getPackageName();
                //
                //                //----------------------------------------------------------------------------------
                //                //WRONG triggers DISCONNECT
                //                //finish();
                //                //----------------------------------------------------------------------------------
                //                //DIDNT WORK - opened phots?
                //                //Intent setIntent = new Intent(Intent.ACTION_MAIN);
                //                //setIntent.addCategory(Intent.CATEGORY_HOME);
                //                //setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                //                //startActivity(setIntent);
                //                //----------------------------------------------------------------------------------
                //
                ////                Intent mIntent=new Intent(TwilioVideoActivity.this, MainActivity.class);
                ////                mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                ////                mIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ////                startActivity(mIntent);

                //----------------------------------------------------------------------------------
                //AndroidManifest.xml - the main activity bundle id for this POC should match the main sea/chat app exactly
                //android:name="com.clarksons.cchat.MainActivity"


                //we dont want to disconnect in onDestroy
                //default set below and in onResume
                is_Activity_hiding_because_buttonBackToCall_tapped = false;

                //----------------------------------------------------------------------------------
                //v2 - call MainActivity the flags we launched
                //DIDNT WORK moveTaskToBack(..) worked so kept it
                // TODO: 19/01/21 CLEANUP after testing
                //----------------------------------------------------------------------------------
                //                String activityToStart = "com.clarksons.cchat.MainActivity";
                //                try {
                //                    Log.e(TAG, "onClick: CALL INTENT:" + activityToStart );
                //
                //                    Class<?> classForName = Class.forName(activityToStart);
                //                    Intent intent = new Intent(TwilioVideoActivity.this, classForName);
                //                    //------------------------------------------------------------------------------
                //                    //onDestroy() getting called [onPause > onStop > onDestroy]
                //                    //we only want               [onPause > onStop            ] - TwilioVideoActivity stays in background
                //                    //https://stackoverflow.com/questions/10435678/hide-activity-in-android?answertab=votes#tab-top
                //                    //intent_TwilioVideoActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                //                    //intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                //                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                //
                //                    //------------------------------------------------------------------------------
                //                    startActivity(intent);
                //
                //                }
                //                catch (ClassNotFoundException classNotFoundException) {
                //                    Log.e(TAG, "onClick: activityToStart:'" + activityToStart + "' - classNotFoundException:", classNotFoundException );
                //                }
                ///DONT WORK kept calling onPause() ... onStop() and onDestroy()
                //onDestroy was disconnecting Twilio
                //moveTaskToBack works - much simpler

                //----------------------------------------------------------------------------------
                //MOVE Activity to the back
                //----------------------------------------------------------------------------------
                Log.e(TAG, "onClick: CALLING moveTaskToBack...");

                //BEWARE - in AppManifest the TwilioVideActivity MUST be marked as singleInstance
                //android:launchMode="singleTask"     - create one task but allow it to spawn sub taks - ISSUE when you hit back it closes the whole app
                //android:launchMode="singleInstance" - create one task but dont allow it to spawn sub task -
                //USE singleInstance when you hit BACK it only closes this Activity
                //Note using singleInstance affects task I spawn from singleInstance - may
                //https://stackoverflow.com/questions/25773928/setting-launchmode-singletask-vs-setting-activity-launchmode-singletop#:~:text=It's%20at%20the%20root%20of,only%20activity%20in%20the%20task.

                //<activity android:configChanges="orientation|screenSize" android:name="org.apache.cordova.twiliovideo.TwilioVideoActivity" android:theme="@style/Theme.AppCompat.Light.Translucent" android:launchMode="singleInstance"/>
                //----------------------------------------------------------------------------------


//FOR RELEASE - COMMENT IN
                //----------------------------------------------------------------------------------
                //works - doesnt create new instance of MainActivity
                 ///but you need sharedInstance on TwilioVideoActivity
                Intent mIntent=new Intent(TwilioVideoActivity.this, MainActivity.class);
                startActivity(mIntent);
                //----------------------------------------------------------------------------------


                //----------------------------------------------------------------------------------
//FOR RELEASE - COMMENT OUT - DEBUG triggers startCall
//                publishEvent(CallEvent.DEBUGSTARTACALL);
                //----------------------------------------------------------------------------------

            }
        });
//FOR RELEASE - COMMENT IN
        hide_buttonBackToCall();

        //default is to disconnect when user his RED button > finish() > onPause() onDestroy()
        //note if we use moveTaskToBack() then onDestroy shouldnt be called
        //so I only use this in onPause
        is_Activity_hiding_because_buttonBackToCall_tapped = true;

        //------------------------------------------------------------------------------------------
        //AUDIO
        //------------------------------------------------------------------------------------------
        onCreate_configureAudio();


        //------------------------------------------------------------------------------------------
        //PROXIMITY SENSOR
        //------------------------------------------------------------------------------------------
        onCreate_configureProximitySensor();

        //------------------------------------------------------------------------------------------
        //LOCAL CAMERA VIEW
        //------------------------------------------------------------------------------------------
        //must call before handling Intents
        setupPreviewView();

        //------------------------------------------------------------------------------------------
        //REMOTE USER - name and muted status - hide at the start
        hide_inCall_remoteUserNameAndMic();

        this.imageViewInCallRemoteMicMuteState.setVisibility(View.INVISIBLE);
        //------------------------------------------------------------------------------------------

        //initializeUI requires config - the other Intent values are handle in onResume as thats always called
        Intent intent = getIntent();
        this.config = (CallConfig) intent.getSerializableExtra("config");

        //requires config to be parsed in parse_Intents()
        initializeUI();

        //------------------------------------------------------------------------------------------
        //FILL USER INFO but dont show yet
        this.hide_imageViewRemoteParticipantWhilstCalling();
        this.hide_textViewRemoteParticipantName();
        this.hide_textViewRemoteParticipantConnectionState();


        textViewRemoteParticipantName.setText("");

        //------------------------------------------------------------------------------------------
        IntentFilter receiverFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetIntentReceiver, receiverFilter);
        //unregisterReceiver is in onDestroy()

        //------------------------------------------------------------------------------------------

        IntentFilter receiverFilter_speakerphone = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            receiverFilter_speakerphone = new IntentFilter(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED);
            registerReceiver(speakerPhoneChangeIntentReceiver, receiverFilter_speakerphone);
        }else{
            Log.e(TAG, "onCreate: SDK not Q - cant register for ACTION_SPEAKERPHONE_STATE_CHANGED");
        }

        //unregisterReceiver is in onDestroy()


    }

    private void parse_Intents(){
        Intent intent = getIntent();

        //doesnt work for startCall - getIntent() is values set when Activity created
        //for startCall values passed to startActivity are ignored action stuck on openRoom
        //String action = intent.getStringExtra("action");

        String action = TwilioVideoActivityNextAction.getNextAction();

        if(null != action){
            if(action.equals(TwilioVideoActivityNextAction.action_openRoom)){

                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_openRoom;

                //----------------------------------------------------------------------------------
                //CORDOVA > openRoom()
                //----------------------------------------------------------------------------------
                String token = intent.getStringExtra("token");
                String roomId = intent.getStringExtra("roomId");

                //----------------------------------------------------------------------------------
                //config
                //parsed in onCreate as intitalizeUI requires it
                //in iOS the config is passed in by TwilioPlugin
                //this.config = (CallConfig) intent.getSerializableExtra("config");
                //----------------------------------------------------------------------------------

                //CHECK FOR JS NULL > "null" done in TwilioPlugin
                String local_user_name = intent.getStringExtra("local_user_name");
                String local_user_photo_url = intent.getStringExtra("local_user_photo_url");

                String remote_user_name = intent.getStringExtra("remote_user_name");
                String remote_user_photo_url = intent.getStringExtra("remote_user_photo_url");

                Log.d(TAG, "[VIDEOPLUGIN] parse_Intents: INTENT > action:'"  + action + "' >> CALL this.openRoom()");
                this.action_openRoom(roomId, token, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url);

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_startCall))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > startCall()
                //----------------------------------------------------------------------------------
                String token = intent.getStringExtra("token");
                String roomId = intent.getStringExtra("roomId");

                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_startCall;

                Log.d(TAG, "[VIDEOPLUGIN] onCreate: INTENT > action:'"  + action + "' >> CALL this.startCall()");
                this.action_startCall(roomId, token);

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_answerCall))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > answerCall()
                //----------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] onCreate: INTENT > action:'"  + action + "' >> CALL this.answerCall()");
                String token = intent.getStringExtra("token");
                String roomId = intent.getStringExtra("roomId");

                //CHECK FOR JS NULL > "null" done in TwilioPlugin
                String local_user_name = intent.getStringExtra("local_user_name");
                String local_user_photo_url = intent.getStringExtra("local_user_photo_url");

                String remote_user_name = intent.getStringExtra("remote_user_name");
                String remote_user_photo_url = intent.getStringExtra("remote_user_photo_url");



                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_answerCall;

                this.action_answerCall(roomId, token, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url);

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_showOffline))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > showOffline()
                //----------------------------------------------------------------------------------
                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_showOffline;

                Log.d(TAG, "[VIDEOPLUGIN] onCreate: INTENT > action:'"  + action + "' >> CALL this.showOffline()");
                this.action_showOffline();

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_showOnline))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > showOnline()
                //----------------------------------------------------------------------------------

                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_showOnline;

                Log.d(TAG, "[VIDEOPLUGIN] onCreate: INTENT > action:'"  + action + "' >> CALL this.showOnline()");
                this.action_showOnline();

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_closeRoom))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > closeRoom()
                //----------------------------------------------------------------------------------

                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_closeRoom;

                Log.d(TAG, "[VIDEOPLUGIN] onCreate: INTENT > action:'"  + action + "' >> CALL this.closeRoom()");
                this.action_closeRoom();

            }
            else{
                Log.e(TAG, "[VIDEOPLUGIN] UNHANDLED action:" + action);

                //needed for onRequestPermissionsResult
                this.action_current = null;
            }
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] action is null");
        }
    }

    private void dump_audioManager_Mode(){
        if(null != audioManager){
            //android.media.AudioSystem.modeToString(audioManager.getMode());
            int mode = audioManager.getMode();
            switch (mode) {
                case MODE_CURRENT:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_CURRENT" );
                    break;
                case MODE_IN_CALL:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_IN_CALL" );
                    break;
                case MODE_IN_COMMUNICATION:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_IN_COMMUNICATION" );
                    break;
                case MODE_INVALID:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_INVALID" );
                    break;
                case MODE_NORMAL:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_NORMAL" );
                    break;
                case MODE_RINGTONE:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode():MODE_RINGTONE" );
                    break;
                default:
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager.getMode(): - default" );
                    break;
            }
        }else{
        	Log.e(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager is null");
        }

    }
    private void onCreate_configureAudio(){

        //Enable changing the volume using the up/down keys during a conversation
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        //------------------------------------------------------------------------------------------
        //Needed for setting/abandoning audio focus during call
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO] onCreate_configureAudio: audioManager CREATE");


        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        dump_audioManager_Mode();



        //Samsung S9 - setSpeakerphoneOn works / doesnt on S10
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] BEFORE isSpeakeraudioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());
//RELEASE

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            //ANDROID 10 - setSpeakerphoneOn not working
            //even added
            //<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
            audioManager.setMode(MODE_IN_CALL);
            audioManager.setSpeakerphoneOn(true);

        }else{
            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] onCreate_configureAudio: SDK not Q - cant register for ACTION_SPEAKERPHONE_STATE_CHANGED");
            //works on S9 but not Android 10
            audioManager.setSpeakerphoneOn(true);
        }
        
        dump_audioManager_Mode();

        Log.e(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] AFTER isSpeakeraudioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());

        updateSpeakerButton();

        //------------------------------------------------------------------------------------------
        //PLAY RING TONE - ringing.mp3
        //------------------------------------------------------------------------------------------

        int id_ringing =  FAKE_R.getResourceId( TwilioVideoActivity.this, "raw", "ringing");

        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] mediaPlayer CREATE");

        mediaPlayer = MediaPlayer.create(TwilioVideoActivity.this, id_ringing);

        if (mediaPlayer != null) {
            //--------------------------------------------------------------------------------------
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] mediaPlayer.setLooping(true);");
            //--------------------------------------------------------------------------------------
            mediaPlayer.setLooping(true);
            //--------------------------------------------------------------------------------------
            //use .play + pause
            //.play() + .stop() seems to kill it next .play() fails
            //--------------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] methodName: mediaPlayer is null");
        }
        //------------------------------------------------------------------------------------------
    }

    //turn of local camera if phone near your ear
    //REQUIRES <uses-permission android:name="android.hardware.sensor.proximity"/>
    private void onCreate_configureProximitySensor(){

        SensorEventListener proximitySensorEventListener
                = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // TODO Auto-generated method stub
            }
            @Override
            public void onSensorChanged(SensorEvent event) {

                // TODO Auto-generated method stub
                if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                    if (event.values[0] == 0) {
                        //--------------------------------------------------------------------------
                        //PHONE is NEAR to ear - ALWAYS turn off local camera
                        //--------------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] onSensorChanged: NEAR");

                        //--------------------------------------------------------------------------
                        //during openRoom we havent
                        if(null != localVideoTrack){

                            if(localVideoTrack.isEnabled()){

                                //Camera was ON when user moved phone to their ear.
                                //later when user moves phone away from ear
                                //and onSensorChanged triggered again
                                //we should turn camera back on
                                localVideoTrack_wasOnBeforeMovedPhoneToEar = true;



                                //TURN IF OFF
                                localVideoTrack.enable(false);


                                //------------------------------------------------------------------
                                //WRAPPER - ALWAYS VISIBLE
                                ///thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);
                                //thumbnailVideoViewFrameLayout.bringToFront();

                                thumbnailVideoView.setVisibility(View.INVISIBLE);
                                imageViewLocalParticipant.setVisibility(View.VISIBLE);
                                //imageViewLocalParticipant1.bringToFront();

                                //BACKGROUND is the solid fill in border.xml
                                //BORDER is stroke in border.xml
                                //------------------------------------------------------------------

                            }else{
                                //------------------------------------------------------------------
                                //Camera was OFF when user moved phone to their ear.
                                //later when user moves phone away from ear >  proximityStateDidChange called again
                                //we should NOT turn camera back on
                                //------------------------------------------------------------------
                                localVideoTrack_wasOnBeforeMovedPhoneToEar = false;
                                //------------------------------------------------------------------
                            	Log.d(TAG, "[VIDEOPLUGIN] PROXIMITY:NEAR - localVideoTrack.isEnabled(): false - Video already off - DO NOTHING");
                                //------------------------------------------------------------------
                            }
                        }else{
                        	Log.e(TAG, "[VIDEOPLUGIN] localVideoTrack is null - cant enable or disable it for PROXIMITY:NEAR");
                        }

                    } else {
                        //--------------------------------------------------------------------------
                        //PROXIMITY: FALSE - phone is not near face
                        //turn on video ONLY IF it had been previously ON
                        //--------------------------------------------------------------------------
                        //PHONE is AWAY from  ear
                        //--------------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] onSensorChanged: AWAY");
                        //--------------------------------------------------------------------------

                        //NOTE - localVideoTrack_wasOnBeforeMovedPhoneToEar normally set in this closure
                        //but is also set in preventProximityTurningCameraOn - if cordova sets config.startWithVideoOff
                        //when proximity sensor in init in android it triggers an AWAY (ios doesnt)
                        //so it can overide startWithVideoOff

                        if(localVideoTrack_wasOnBeforeMovedPhoneToEar){
                            //----------------------------------------------------------------------
                            //CAMERA was ON > proximity:NEAR > turn camera off > proximity:FAR > turn camera back on
                            //----------------------------------------------------------------------
                            //turn it back on when you move phone away from ear
                            if(null != localVideoTrack){
                                localVideoTrack.enable(true);
                            }else{
                                Log.e(TAG, "[VIDEOPLUGIN] localVideoTrack is null");
                            }
                            //----------------------------------------------------------------------
                            //WRAPPER - ALWAYS VISIBLE
                            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

                            thumbnailVideoView.setVisibility(View.VISIBLE);
                            imageViewLocalParticipant.setVisibility(View.INVISIBLE);
                            //imageViewLocalParticipant.bringToFront();
                            //----------------------------------------------------------------------
                        }else{
                            //----------------------------------------------------------------------
                            //CAMERA was OFF > proximity:NEAR > camera stays off > proximity:FAR > DO NOT turn camera back on
                            //-------------- --------------------------------------------------------

                            //user moved phone away from ear but they had VIDEO off before
                            //so dont automatically turn it back on

                        	Log.d(TAG, "[VIDEOPLUGIN] onSensorChanged: PROXIMITY:AWAY - localVideoTrack_wasOnBeforeMovedPhoneToEar is false - DONT TURN CAMERA BACK ON");
                            //----------------------------------------------------------------------

                            //----------------------------------------------------------------------
                            //WRAPPER - ALWAYS VISIBLE
                            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

                            thumbnailVideoView.setVisibility(View.INVISIBLE);
                            imageViewLocalParticipant.setVisibility(View.VISIBLE);
                            //imageViewLocalParticipant.bringToFront();
                            //----------------------------------------------------------------------
                        }
                    }
                }
            }
        };

        //------------------------------------------------------------------------------------------
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        //------------------------------------------------------------------------------------------
        if (mSensor == null) {
            Log.e(TAG, "[VIDEOPLUGIN] onCreate_configureProximitySensor: mSensor is null - cant access Phone Proximity Sensor");
        } else {
            //--------------------------------------------------------------------------------------
            mSensorManager.registerListener(proximitySensorEventListener,
                                            mSensor,
                                            SensorManager.SENSOR_DELAY_NORMAL);
            //--------------------------------------------------------------------------------------
        }
    }


    //----------------------------------------------------------------------------------------------
    private void action_openRoom(  String room,
                            String token,
                            String localUserName,
                            String localUserPhotoURL,
                            String remoteUserName,
                            String remoteUserPhotoURL)
    {
        //------------------------------------------------------------------------------------------
        Log.d(TAG, "[VIDEOPLUGIN] action_openRoom: STARTED");
        //------------------------------------------------------------------------------------------

        this.roomId = room;
        this.accessToken = token;

// TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
        this.local_user_name = localUserName;
        this.local_user_photo_url = localUserPhotoURL;

        this.remote_user_name = remoteUserName;
        this.remote_user_photo_url = remoteUserPhotoURL;


        showRoomUI();

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------
        Log.d(TAG, "[VIDEOPLUGIN] hasPermissionForCameraAndMicrophone?");
        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TAG, "[VIDEOPLUGIN] REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TAG, "[VIDEOPLUGIN] hasPermissionForCameraAndMicrophone(): TRUE");
            permissionOk();
        }
        //------------------------------------------------------------------------------------------
    }


    private void action_startCall(  String room,
                                    String token)
    {
        Log.d(TAG, "[VIDEOPLUGIN] action_startCall: STARTED");

        this.roomId = room;
        this.accessToken = token;

        //------------------------------------------------------------------------------------------
        //enable mic button
        this.showRoomUI();

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------

        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TAG, "[VIDEOPLUGIN] REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TAG, "[VIDEOPLUGIN] PERMISSIONS OK");
            permissionOk();
        }
        //------------------------------------------------------------------------------------------

    }

    private void action_showOffline()
    {
        Log.d(TAG, "[VIDEOPLUGIN] action_showOffline: show viewAlert");

        if(null != this.viewAlert){
            this.viewAlert.setVisibility(View.VISIBLE);
        }else{
        	Log.e(TAG, "[VIDEOPLUGIN] [action_showOffline] this.viewAlert is null");
        }
    }

    private void action_showOnline()
    {
        Log.d(TAG, "[VIDEOPLUGIN] action_showOnline: hide viewAlert");

        if(null != this.viewAlert){
            this.viewAlert.setVisibility(View.INVISIBLE);
        }else {
            Log.e(TAG, "[VIDEOPLUGIN] [action_showOnline] this.viewAlert is null");
        }
    }

    private void action_closeRoom()
    {
        Log.d(TAG, "[VIDEOPLUGIN] action_closeRoom: CALL onDisconnect");

        //closeRoom in TwilioVideo.java call
        //TwilioVideoManager.getInstance().publishDisconnection()
        //which calls onDisconnect
        onDisconnect();
        //closes room > CLOSED
        //finish > DISCONNECTED
    }



    private void action_answerCall( String room,
                                    String token,
                                    String localUserName,
                                    String localUserPhotoURL,
                                    String remoteUserName,
                                    String remoteUserPhotoURL)
    {
        Log.d(TAG, "[VIDEOPLUGIN] action_answerCall: STARTED");

        this.roomId = room;
        this.accessToken = token;


        // TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
        this.local_user_name = localUserName;
        this.local_user_photo_url = localUserPhotoURL;

        this.remote_user_name = remoteUserName;
        this.remote_user_photo_url = remoteUserPhotoURL;

        //------------------------------------------------------------------------------------------
        //enable mic button
        this.showRoomUI();

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------

        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TAG, "[VIDEOPLUGIN] REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TAG, "[VIDEOPLUGIN] PERMISSIONS OK");
            permissionOk();
        }
        //------------------------------------------------------------------------------------------

    }

    //----------------------------------------------------------------------------------------------
    //CAMERA PERMISSIONS
    //----------------------------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean permissionsGranted = true;

            for (int grantResult : grantResults) {
                permissionsGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (permissionsGranted) {
                //----------------------------------------------------------------------------------
                permissionOk();
                //----------------------------------------------------------------------------------
            } else {
                //----------------------------------------------------------------------------------
                publishEvent(CallEvent.PERMISSIONS_REQUIRED);


                if(null != config){
                    TwilioVideoActivity.this.handleConnectionError(config.getI18nConnectionError());

                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] onRequestPermissionsResult: config is null - cant get config.getI18nConnectionError() - ok PERMISSIONS_REQUIRED still published");
                }


                //----------------------------------------------------------------------------------
            }
        }else{
            Log.e(TAG, "onRequestPermissionsResult: UNHANDLED requestCode:" + requestCode );
        }
    }

    private void preventProximityTurningCameraOn(){
        //ISSUE - the proximity sensor is triggered as AWAY when it starts
        //This turn local camera on
        //but we might have wanted to startWithVideoOff:true
        //localVideoTrack_wasOnBeforeMovedPhoneToEar prevents the camera turning back on if it was off
        //camera off > Proximity:near > leave camera off > Proximity: Away > DONT turn camera back on if it was off before NEAR triggered
        //camera on  > Proximity:near > turn camera off  > Proximity: Away > turn camera back on

        //default to on
        localVideoTrack_wasOnBeforeMovedPhoneToEar = true;

        if(null != config){
            if(config.isStartWithVideoOff()){
                localVideoTrack_wasOnBeforeMovedPhoneToEar = false;

            }else{
                Log.d(TAG, "[VIDEOPLUGIN] preventProximityTurningCameraOn: config.isStartWithVideoOff() is false >> localVideoTrack_wasOnBeforeMovedPhoneToEar = true;");
            }
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] config is null - ok FALLBACK TO localVideoTrack_wasOnBeforeMovedPhoneToEar = true;");
        }
    }

    private void permissionOk(){


        preventProximityTurningCameraOn();


        if(this.action_current.equals(TwilioVideoActivityNextAction.action_openRoom)){



            //--------------------------------------------------------------------------------------
            //SETUP LOCAL CAMERA AND AUDIO
            //--------------------------------------------------------------------------------------
            setup_createAudioAndVideoTracks();

            //--------------------------------------------------------------------------------------
            //p1 - wait for P2 to call room.connect() then backend will send startCall() to P1
            //--------------------------------------------------------------------------------------
            displayCallWaiting();

            //if localCameraTrack.enable is true so still on here even though config.startWithVideoOff:true
            // may be the proximity sensor


        }
        else if(this.action_current.equals(TwilioVideoActivityNextAction.action_startCall))
        {
            setupLocalCamera_ifnull();
            connectToRoom();

        }
        else if(this.action_current.equals(TwilioVideoActivityNextAction.action_answerCall))
        {
            setup_createAudioAndVideoTracks();
            setupLocalCamera_ifnull();

            //showHideBlurView(true);

            connectToRoom();

        }
        else{
            Log.e(TAG, "[VIDEOPLUGIN] UNHANDLED this.action_current:" + this.action_current);
        }
    }




    //----------------------------------------------------------------------------------------------
    //UI
    //----------------------------------------------------------------------------------------------
    // Reset the client ui status
    private void showRoomUI() {
        this.fillIn_viewRemoteParticipantInfo();
    }

    private void setupPreviewView(){
        //HIDE till my camera connected
        this.hide_viewRemoteParticipantInfo();

        //set it always to Fill so it looks ok in fullscreen
        //I tried changing it to Fit/Fill but jumps at the end when it zooms in
        //this.previewView.contentMode = UIViewContentModeScaleAspectFill;

        //DEBUG - when video is full screen is may have wrong AspectFit or AspectFil

        //DIFF to iOS - we show blur in onResume so View is ready - doesnt work from onCreate
        //this.update_PreviewView_showInFullScreen(true, false, true);
        this.update_PreviewView_showInFullScreen(true, false, false);
    }



    //Disconnected... just hide the animation is not stopped
    private void animateAlphaBorderForViews_HideBorder(){
        Log.d(TAG, "[VIDEOPLUGIN] animateAlphaBorderForViews_HideBorder" );

        hide_imageViewRemoteParticipantWhilstCallingToAnimate();
    }

    private void animateAlphaBorderForViews(){

        if(null != this.imageViewRemoteParticipantWhilstCallingToAnimate){
            animateView_start(this.imageViewRemoteParticipantWhilstCallingToAnimate);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] this.imageViewRemoteParticipantWhilstCallingToAnimate is null - cant animate");
        }
    }



    public void animateView_start(View view){
        runAnimation = true;
        animateView_fadeOut(view, 1000);
    }

    public void animateView_toggle(){
        runAnimation = !runAnimation;
    }
    public void animateView_stop(){
        runAnimation = false;
    }


    public void animateView_fadeOut(View view, long duration){
        //In transition: (alpha from 0 to 0.5)
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(runAnimation){
                            //Log.d(TAG, "[VIDEOPLUGIN] animateView_fadeOut - runAnimation is true - CALL animateView_fadeIn");
                            animateView_fadeIn(view, duration);
                        }else{
                            //Log.d(TAG, "[VIDEOPLUGIN] animateView_fadeOut - runAnimation is false - STOP ANIMATION");
                        }

                    }
                });
    }

    public void animateView_fadeIn(View view, long duration){
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(runAnimation){
                            //Log.d(TAG, "animateView_fadeIn - runAnimation is true - CALL animateView_fadeOut");
                            animateView_fadeOut(view,duration);
                        }else{
                            //Log.d(TAG, "[VIDEOPLUGIN] animateView_fadeIn - runAnimation is false - STOP ANIMATION");
                        }
                    }
                });
    }








    private void fillIn_viewRemoteParticipantInfo(){

        if (this.remote_user_name != null) {
            this.textViewRemoteParticipantName.setText(this.remote_user_name);
            this.textViewInCallRemoteName.setText(this.remote_user_name);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] this.remoteUserName is NULL");
            this.textViewRemoteParticipantName.setText("");
            this.textViewInCallRemoteName.setText("");

        }

        fill_imageViewRemoteParticipantWhilstCalling();
        fill_imageViewRemoteParticipantInCall();

        fill_imageView_LocalParticipant();

        //text set in didConnectToRoom_StartACall*
        this.textViewRemoteParticipantConnectionState.setText("");
    }


    private void fill_imageViewRemoteParticipantWhilstCalling(){
        //DEBUG self.remoteUserPhotoURL = NULL;

        this.loadUserImageInBackground_async(this.remote_user_photo_url, this.imageViewRemoteParticipantWhilstCalling);
        this.loadUserImageInBackground_async(this.remote_user_photo_url, this.imageViewRemoteParticipantWhilstCallingToAnimate);

    }
    private void fill_imageViewRemoteParticipantInCall(){
        //DEBUG self.remoteUserPhotoURL = NULL;

        this.loadUserImageInBackground_async(this.remote_user_photo_url, this.imageViewRemoteParticipantInCall);

    }

    //when LOCAL user is offline we show their image over the disable camera view
    //when LOCAL user is offline we show their image over the disable camera view
    private void fill_imageView_LocalParticipant(){
        //DEBUG DO NOT RELEASE - this.local_user_photo_url = null;

        this.loadUserImageInBackground_async(this.local_user_photo_url, this.imageViewLocalParticipant);

    }
    
    private void loadUserImageInBackground_async(String userPhotoURL, ImageView imageView){
        //TODO add border around image

        //------------------------------------------------------------------------------------------
        //Fill imageView async
        //------------------------------------------------------------------------------------------
        //"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"
        //------------------------------------------------------------------------------------------
        int defaultImage = FAKE_R.getDrawable("ic_account_circle_white_24dp");


        if (imageView != null) {
            if (userPhotoURL != null) {
                Picasso.get().load(userPhotoURL).placeholder(defaultImage).into(imageView);

            }else{
                Log.d(TAG, "[VIDEOPLUGIN] loadUserImageInBackground_async: userPhotoURL is null - use default image");

                imageView.setImageResource(defaultImage);
            }
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] loadUserImageInBackground_async: imageView is null - cant set image to default or otherwise");
        }
    }


    //------------------------------------------------------------------------------------------
    //SHOW REMOTE USER PANEL with image name and state e.g. Dialing..

    private void show_viewRemoteParticipantInfoWithState(String state){
        show_imageViewRemoteParticipantWhilstCalling();
        show_textViewRemoteParticipantName();
        show_textViewRemoteParticipantConnectionState();

        textViewRemoteParticipantConnectionState_setText(state);
    }

    private void hide_viewRemoteParticipantInfo(){
        hide_imageViewRemoteParticipantWhilstCalling();
        hide_textViewRemoteParticipantName();
        hide_textViewRemoteParticipantConnectionState();
        //clear it
        textViewRemoteParticipantConnectionState_setText("");
    }

    //------------------------------------------------------------------------------------------
    //SHOW/HIDE each control
    private void show_imageViewRemoteParticipantWhilstCalling(){

        if (this.imageViewRemoteParticipantWhilstCalling != null) {
            this.imageViewRemoteParticipantWhilstCalling.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] show_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCalling is null");
        }

        if (this.imageViewRemoteParticipantWhilstCallingToAnimate != null) {
            animateAlphaBorderForViews();
            this.imageViewRemoteParticipantWhilstCallingToAnimate.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] show_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCallingToAnimate is null");
        }
    }
    private void hide_imageViewRemoteParticipantWhilstCalling(){
        if (this.imageViewRemoteParticipantWhilstCalling != null) {
            this.imageViewRemoteParticipantWhilstCalling.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCalling is null");
        }

        this.hide_imageViewRemoteParticipantWhilstCallingToAnimate();
    }

    //called by hide_imageViewRemoteParticipantWhilstCalling
    //but we also stop the animation whin answerCall() > Disconnected
    //we show the remote phot but hide the animate circle beihnd it
    //animateAlphaBorderForViews_HideBorder
    private void hide_imageViewRemoteParticipantWhilstCallingToAnimate(){

        if (this.imageViewRemoteParticipantWhilstCallingToAnimate != null) {
            animateView_stop();
            this.imageViewRemoteParticipantWhilstCallingToAnimate.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCallingToAnimate is null");
        }
    }


    //----------------------------------------------------------------------------------------------
    //SHOW/HIDE each control
    private void show_imageViewRemoteParticipantInCall(){
        if (this.imageViewRemoteParticipantInCall != null) {
            this.imageViewRemoteParticipantInCall.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] show_imageViewRemoteParticipantInCall: imageViewRemoteParticipant is null");
        }

    }
    private void hide_imageViewRemoteParticipantInCall(){
        if (this.imageViewRemoteParticipantInCall != null) {
            this.imageViewRemoteParticipantInCall.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_imageViewRemoteParticipantInCall: imageViewRemoteParticipant is null");
        }
    }

    //--------------------------------------------------------------------------------------
    //REMOTE use has toggle camera ON so show them in the full screen view
    private void show_fullScreenVideoView(){
        if (this.fullScreenVideoView != null) {
            this.fullScreenVideoView.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] fullScreenVideoView: remoteVideoView is null");
        }
    }
    //REMOTE use has toggle camera OFF so fullscreenview is frozen so hide it
    private void hide_fullScreenVideoView(){
        if (this.fullScreenVideoView != null) {
            this.fullScreenVideoView.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_remoteVideoView: remoteVideoView is null");
        }
    }


    //----------------------------------------------------------------------------------------------
    //show_textViewRemoteParticipantName
    private void show_textViewRemoteParticipantName(){
        if (this.textViewRemoteParticipantName != null) {
            this.textViewRemoteParticipantName.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] show_textViewRemoteParticipantName: textViewRemoteParticipantName is null");
        }
    }
    private void hide_textViewRemoteParticipantName(){
        if (this.textViewRemoteParticipantName != null) {
            this.textViewRemoteParticipantName.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_textViewRemoteParticipantName: textViewRemoteParticipantName is null");
        }
    }

    private void show_textViewRemoteParticipantConnectionState(){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] show_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
    }
    private void hide_textViewRemoteParticipantConnectionState(){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
    }
    private void textViewRemoteParticipantConnectionState_setText(String state){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setText(state);
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] hide_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
    }


    //if proximity triggered
    //if camera button set to off
    private void thumbnailVideoViewFrameLayout_show(){
        this.thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

    }

    private void thumbnailVideoViewFrameLayout_hide(){
        this.thumbnailVideoViewFrameLayout.setVisibility(View.INVISIBLE);

    }



    //----------------------------------------------------------------------------------------------
    //REMOTE USER - NAME and MUTED STATUS
    //----------------------------------------------------------------------------------------------

    //In Call - NAME and MIC state
    private void hide_inCall_remoteUserNameAndMic(){

        //DONT use GONE - it moves the buttons down then jumps when it reappears

        this.textViewInCallRemoteName.setVisibility(View.INVISIBLE);


    }

    private void show_inCall_remoteUserNameAndMic_isMuted(boolean micIsMuted){


        this.textViewInCallRemoteName.setVisibility(View.VISIBLE);

    
        this.update_imageViewInCallRemoteMicMuteState_isMuted(micIsMuted);
    }

    private void update_imageViewInCallRemoteMicMuteState_isMuted(boolean micIsMuted){
        if(micIsMuted){
            //--------------------------------------------------------------------------------------
            //REMOTE USER IS - MUTED - show icon
            //--------------------------------------------------------------------------------------

            //DONT USE R its //import capacitor.android.plugins.R;
            //will break in sea/chat as theres no capacitor
            //imageViewInCallRemoteMicMuteState.setImageResource(R.drawable.ic_mic_off_red_24px);
            //--------------------------------------------------------------------------------------
            //NOTE - main buttons use ic_mic_off_red_24px - this uses white
            imageViewInCallRemoteMicMuteState.setImageResource(FAKE_R.getDrawable("ic_mic_off_white_24px"));
            //ONLY icon SHOW WHEN MUTED
            this.imageViewInCallRemoteMicMuteState.setVisibility(View.VISIBLE);
        }else{

            //--------------------------------------------------------------------------------------
            //REMOTE USER IS - UNMUTED - hide icon
            //--------------------------------------------------------------------------------------
            //DONT USE R its //import capacitor.android.plugins.R;
            //will break in sea/chat as theres no capacitor
            //imageViewInCallRemoteMicMuteState.setImageResource(R.drawable.ic_mic_green_24px);
            //--------------------------------------------------------------------------------------
            imageViewInCallRemoteMicMuteState.setImageResource(FAKE_R.getDrawable("ic_mic_green_24px"));
            //--------------------------------------------------------------------------------------
            this.imageViewInCallRemoteMicMuteState.setVisibility(View.INVISIBLE);

        }
    }

    /**
     * SHOW / HIDE - BACK TO CALL arrow button
     */

    private void show_buttonBackToCall(){

        if(null != this.buttonBackToCall){
            buttonBackToCall.setVisibility(View.VISIBLE);

        }else{
            Log.e(TAG, "this.buttonBackToCall is null - show failed");
        }
    }

    //when app start the Callin.. panel will be at the top
    // but BACK arrow wont
    // so remove the blank space too - use GONE not INVISIBLE
    private void hide_buttonBackToCall(){
        if(null != this.buttonBackToCall){
            //FOR RELEASE - COMMENT IN
            buttonBackToCall.setVisibility(View.GONE);

        }else{
        	Log.e(TAG, "this.buttonBackToCall is null - hide failed");
        }
    }



    //------------------------------------------------------------------------------------------
    //DELEGATE > UPDATE UI
    //------------------------------------------------------------------------------------------

    //On the ANSWERING PHONE it will trigger
    //didConnectToRoom_AnswerACall only
    private void didConnectToRoom_AnswerACall(){
        Log.d(TAG, "[VIDEOPLUGIN] didConnectToRoom_AnswerACall: START");
        
        if(this.previewIsFullScreen){
            this.show_viewRemoteParticipantInfoWithState("Connecting...");
            this.hide_buttonBackToCall();

            this.animateAlphaBorderForViews();

        }else{
            Log.e(TAG, "[VIDEOPLUGIN] didConnectToRoom_AnswerACall: new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }


    private void showHideBlurView(boolean showBlurView){

//        if(showBlurView){
//            if(null != this.blurredviewgroup){
//                //if alpha is 0.0 will still be hidden - a;pha is animated below
//                this.blurredviewgroup.setVisibility(View.VISIBLE);
//            }else{
//            	Log.e(TAG, "[VIDEOPLUGIN] this.blurredviewgroup is null");
//            }
//
//            //--------------------------------------------------------------------------------------
//            //TODO - works but needs tweaking
//            //--------------------------------------------------------------------------------------
//            //                        if(null != blurredviewgroup){
//            //                            Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(this.blurredviewgroup);
//            //
//            ////                            Blurry.with(TwilioVideoActivity.this).radius(10)
//            ////                                    .sampling(8)
//            ////                                    .color(Color.argb(66, 255, 255, 0))
//            ////                                    .async()
//            ////                                    .animate(500)
//            ////                                    .onto(blurredviewgroup);
//            //                        }else{
//            //                            Log.e(TAG, "blurredviewgroup is null");
//            //                        }
//            //--------------------------------------------------------------------------------------
//        }else{
//            if(null != this.blurredviewgroup){
//                //if alpha is 0.0 will still be hidden - a;pha is animated below
//                this.blurredviewgroup.setVisibility(View.GONE);
//            }else{
//                Log.e(TAG, "[VIDEOPLUGIN] this.blurredviewgroup is null");
//            }
//        }
    }

    private void update_PreviewView_showInFullScreen(boolean changeToFullScreen, boolean isAnimated, boolean showBlurView) {

        //animation in and out should be same number of secs
        //        NSTimeInterval duration = 1.0;

        //------------------------------------------------------------------------------------------
        //When you show "Disconnected.." you dont show the blur - other use has hung up

        showHideBlurView(showBlurView);
        //------------------------------------------------------------------------------------------

        if(changeToFullScreen){
            if(isAnimated){
                //------------------------------------------------------------------
                //FULL SCREEN + ANIMATED
                //------------------------------------------------------------------
                //            [UIView animateWithDuration:duration
                //                delay:0
                //                options:UIViewAnimationOptionCurveEaseInOut
                //                animations:^{
                //                    //--------------------------------------------------
                //                                this.update_PreviewView_toFullScreen: TRUE];
                //                    //--------------------------------------------------
                //                    //will resize but animate without this
                //                                [this.view layoutIfNeeded];
                //                    //--------------------------------------------------
                //                }
                //                completion:^(BOOL finished) {
                //                    //ANIMATION DONE
                //
                //                }
                //            ];
                //----------------------------------------------------------------------------------
                this.update_PreviewView_toFullScreen(true);
                //----------------------------------------------------------------------------------

            }else{
                //------------------------------------------------------------------
                //FULL SCREEN + UNANIMATED (when app starts)
                //------------------------------------------------------------------
                this.update_PreviewView_toFullScreen(true);

            }
        }else{
            if(isAnimated){
                //------------------------------------------------------------------
                //NOT FULL SCREEN + ANIMATED - (dialing ends shrink preview to bottom right)
                //------------------------------------------------------------------
                //            [UIView animateWithDuration:duration
                //                delay:0
                //                options:UIViewAnimationOptionCurveEaseInOut
                //                animations:^{
                //                    //--------------------------------------------------
                //                this.update_PreviewView_toFullScreen: FALSE];
                //                    //--------------------------------------------------
                //                    //will resize but animate without this
                //                [this.view layoutIfNeeded];
                //                    //--------------------------------------------------
                //                }
                //                completion:^(BOOL finished) {
                //                    //DONE
                //                }
                //             ];
                //----------------------------------------------------------------------------------
                this.update_PreviewView_toFullScreen(false);
                //----------------------------------------------------------------------------------

            }else{
                //------------------------------------------------------------------
                //NOT FULL SCREEN + UNANIMATED (preview size jumps to bottom right - unused)
                //------------------------------------------------------------------
                this.update_PreviewView_toFullScreen(false);

            }
        }
    }

    private void update_PreviewView_toFullScreen(boolean fullScreen) {
        Log.d(TAG, "[VIDEOPLUGIN] update_PreviewView_toFullScreen: START");
        if(fullScreen){

            //--------------------------------------------------------------------------------------
            //FULLSCREEN VIEW
            //--------------------------------------------------------------------------------------

            if (thumbnailVideoViewFrameLayout != null) {
                ViewGroup.LayoutParams thumbnailVideoViewFrameLayout_layoutParams = thumbnailVideoViewFrameLayout.getLayoutParams();

                thumbnailVideoViewFrameLayout_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                thumbnailVideoViewFrameLayout_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

                thumbnailVideoViewFrameLayout.setLayoutParams(thumbnailVideoViewFrameLayout_layoutParams);

                thumbnail_video_view_framelayout_setmargins(0, 0, 0, 0);


                thumbnailVideoViewFrameLayout.requestLayout();
                //imageViewLocalParticipant.requestLayout();
            } else {
                Log.e(TAG, "[VIDEOPLUGIN] onClick: thumbnailVideoViewFrameLayout is null");
            }

            imageViewSwitchVideo.setVisibility(View.INVISIBLE);

            this.previewIsFullScreen = true;
        }else{
            //------------------------------------------------------------------------------------------
            //MINI VIEW
            //------------------------------------------------------------------------------------------

            int screen_width_pixels = Resources.getSystem().getDisplayMetrics().widthPixels;
            int screen_height_pixels = Resources.getSystem().getDisplayMetrics().heightPixels;

            Log.d(TAG, "[VIDEOPLUGIN] onClick: screen_width_pixels:" + screen_width_pixels + ",screen_height_pixels:"+ screen_height_pixels );
            //1080, 2076

            //Motorola Android one - Android 10 - 720x1362
            //dont hard code looked huge on Android 1
            //int preview_mini_width = 350;
            //int preview_mini_height = 580;

            int preview_mini_width = screen_width_pixels / 3 ;
            int preview_mini_height = screen_height_pixels / 3;


            //int margin = 64;
            //int margin_bottom = 364;
            int margin = 32;
            //int margin_bottom = 300; //too low on s7
            //int margin_bottom = 320; //too low on s7
            int margin_bottom = 340; //too low on s7

            if(null != this.bottom_buttons_linearlayout){
                int[] location = new int[2];
                this.bottom_buttons_linearlayout.getLocationOnScreen(location);

                //----------------------------------------------------------------------------------
                //    int bottom_buttons_linearlayout_x = location[0];
                //    int bottom_buttons_linearlayout_y = location[1];
                //
                //    int bottom_buttons_linearlayout_top = bottom_buttons_linearlayout_y;
                //----------------------------------------------------------------------------------
                int leftMargin = screen_width_pixels - preview_mini_width - margin;
                int topMargin  = screen_height_pixels - preview_mini_height - margin_bottom;
                //----------------------------------------------------------------------------------
                //thumbnail_video_view_setmargins(leftMargin, topMargin, margin, margin * 4);

                //thumbnail_video_view_setmargins(leftMargin, topMargin, margin, margin_bottom);
                thumbnail_video_view_framelayout_setmargins(leftMargin, topMargin, margin, margin_bottom);
                //----------------------------------------------------------------------------------
            }else{
            	Log.e(TAG, "[VIDEOPLUGIN] bottom_buttons_linearlayout is null");
            }

            imageViewSwitchVideo.setVisibility(View.VISIBLE);

            this.previewIsFullScreen = false;
        }
    }

    //----------------------------------------------------------------------------------------------

    private void thumbnail_video_view_framelayout_setmargins(int leftmargin, int topMargin, int rightMargin, int bottomMargin){

        FrameLayout thumbnail_video_view_frameLayout = findViewById(FAKE_R.getId("thumbnail_video_view_framelayout"));

        ViewGroup.LayoutParams layoutParams = thumbnail_video_view_frameLayout.getLayoutParams();


        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;

            //OK but sets all
            marginLayoutParams.setMargins(leftmargin, topMargin, rightMargin, bottomMargin); // left, top, right, bottom

            //https://developer.android.com/reference/android/view/ViewGroup.MarginLayoutParams
            //((ViewGroup.MarginLayoutParams) layoutParams).topMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).leftMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).rightMargin = 0;

            //video_container.requestLayout();
            thumbnail_video_view_frameLayout.requestLayout();
        } else{
            Log.e(TAG, "[VIDEOPLUGIN] Attempted to set the margins on a class that doesn't support margins: video_container");
        }
    }
    //----------------------------------------------------------------------------------------------

    //------------------------------------------------------------------------------------------
    //MEDIA PLAYER - ringing.mp3
    //------------------------------------------------------------------------------------------
    //use .play + pause 
    //.play() + .stop() seems to kill it next .play() fails
    
    private void dialing_sound_start(){
        //------------------------------------------------------------------------------------------
        //use .play + pause 
        //.play() + .stop() seems to kill it next .play() fails
        //------------------------------------------------------------------------------------------
        if (mediaPlayer != null) {
            //Plays ringing tone
//RELEASE PUT BACK
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO][dialing_sound_start] mediaPlayer.start();");
            mediaPlayer.start();
            //--------------------------------------------------------------------------------------
            //use .start() + pause()
            //not .start() + .stop() seems to kill it, next .play() fails
            //--------------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "[VIDEOPLUGIN][AUDIO] dialing_sound_start: mediaPlayer is null");
        }
    }

    private void dialing_sound_pause(){
        if (mediaPlayer != null) {

            Log.w(TAG, "[VIDEOPLUGIN][AUDIO][dialing_sound_start] mediaPlayer.pause();");
            mediaPlayer.pause();
            //use .start() + pause()
            //not .start() + .stop() seems to kill it, next .play() fails

        }else{
            Log.e(TAG, "[VIDEOPLUGIN] methodName: mediaPlayer is null");
        }
    }

    private void dialing_sound_stop(){
        if (mediaPlayer != null) {
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO][dialing_sound_stop] mediaPlayer.stop();");
            mediaPlayer.stop();
            //use .start() + pause()
            //not .start() + .stop() seems to kill it, next .play() fails

        }else{
            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][dialing_sound_stop]: mediaPlayer is null");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Log.d(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onNewIntent: CALLED - because launchMode is singleInstance:" + this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE]  onResume: STARTED:" + this);

        //reset to default - changed in buttonBackToCall handler in onCreate
        is_Activity_hiding_because_buttonBackToCall_tapped = true;

        this.hide_imageViewRemoteParticipantInCall();
//        imageViewLocalParticipant.setVisibility(View.INVISIBLE);

        //------------------------------------------------------------------------------------------
        //PARSE PARAMS FROM Intent
        //------------------------------------------------------------------------------------------
        //    P1 CALLER
        //        - openRoom
        //            - startActivity
        //                - onCreate
        //                    - instance 699
        //                    - get Intents
        //                        - action > openRoom
        //                - onResume
        //        - startCall
        //            - startActivity
        //                - doesnt call onCreate due to FLAG_ACTIVITY_REORDER_TO_FRONT
        //                - onResume
        //                    - same instance
        //                    - ISSUE - Intents parsed in onCreate
        //    P2 - PERSON BEING CALLED
        //            - answerCall()
        //                - startActivity
        //                    - onCreate
        //                        - instance 699
        //                        - get Intents
        //                        - action > openRoom
        //                      - onResume
        //------------------------------------------------------------------------------------------
        parse_Intents();

        //------------------------------------------------------------------------------------------
        // TODO: 20/01/21 CLEANUP
        //        setupLocalCamera_ifnull();
        //        publishTrack_video(); //needed room.connect not set yet so cant publish??

        setupAndPublish_localVideo();
        //------------------------------------------------------------------------------------------

    }

    private void setupAndPublish_localVideo(){
        //------------------------------------------------------------------------------------------
        setupLocalCamera_ifnull();
        publishTrack_video(); //needed room.connect not set yet so cant publish??
        //------------------------------------------------------------------------------------------
    }


    //1 - openRooom - setup local camera to show full screen
    //2 - startCall > room.connect > room.localParticipant > publishtrack
    private void setupLocalCamera_ifnull(){
        //If the local video track was released when the app was put in the background, recreate.
        if (localVideoTrack == null) {

            Log.d(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: localVideoTrack == null - we need to recreate it / check permissions" );

            if (hasPermissionForCameraAndMicrophone()) {

                Log.d(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: hasPermissionForCameraAndMicrophone(): TRUE" );

                if(null != cameraCapturer){
                    //------------------------------------------------------------------------------
                    boolean enableVideoAtStart = true;

                    if(null != config){
                        if(config.isStartWithVideoOff()){
                            Log.i(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: isStartWithAudioOff is TRUE - AUDIO disabled at start");
                            enableVideoAtStart = false;

                        }else{
                            Log.i(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: isStartWithAudioOff is FALSE - enableVideoAtStart = true;");
                            enableVideoAtStart = true;

                        }
                    }else{
                        Log.e(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: config is null - FALLBACK TO enableVideoAtStart = true;");
                        enableVideoAtStart = true;

                    }

                    //------------------------------------------------------------------------------
                    //NOTE - proximity sensor in android triggers a single proximity:AWAY when its starts
                    //proximity:AWAY sets localVideoTrack.enabled to true so overwrites what you set here
                    // you need to make sure it doesnt start the camera if config.startWithVideoOff:true
                    // done in preventProximityTurningCameraOn()
                    //------------------------------------------------------------------------------
                    Log.d(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: CALLING - LocalVideoTrack.create(..)" );
                    localVideoTrack = LocalVideoTrack.create(this,
                            enableVideoAtStart,
                            cameraCapturer.getVideoCapturer(),
                            LOCAL_VIDEO_TRACK_NAME);
                    //------------------------------------------------------------------------------
                    localVideoTrack.addRenderer(thumbnailVideoView);
                    //------------------------------------------------------------------------------
                    //                    /*
                    //                     * If connected to a Room then share the local video track.
                    //                     */
                    //                    if (localParticipant != null) {
                    //                        localParticipant.publishTrack(localVideoTrack);
                    //                    }else{
                    //                        Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");
                    //                    }
                    //------------------------------------------------------------------------------
                    //publishTrack_video
                    //------------------------------------------------------------------------------
                    //publishTrack_video();
                    //------------------------------------------------------------------------------
                }else{
                    //happened when openRoom and StartCall both did startActivity
                    Log.e(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: cameraCapturer is null");
                }

            }else{
                //can happen when openRoom and StartCall both did startActivity
                Log.e(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: hasPermissionForCameraAndMicrophone() setupLocalCamera_ifnull FAILED");
            }
        }else{
            //can happen when openRoom and StartCall both did startActivity
            Log.d(TAG, "[VIDEOPLUGIN] setupLocalCamera_ifnull: setupLocalCamera_ifnull - localVideoTrack is NOT NULL - OK created earlier");

        }
    }
    //----------------------------------------------------------------------------------------------
    //TO PUBLISH a VIDEO TRACK to REMOTE USER
    //----------------------------------------------------------------------------------------------
    //you need room to be connected - onConnected listener returned
    //room.localParticipant
    //localVideoTrack - which can be setup in openRoom > setupLocalCamera
    //but then null for startCall so need to call setupLocalCamera again

    private void publishTrack_video(){
        if (localVideoTrack == null) {

            if (hasPermissionForCameraAndMicrophone()) {

                Log.d(TAG, "[VIDEOPLUGIN] publishTrack_video: hasPermissionForCameraAndMicrophone(): TRUE - recreate" );

                if (localParticipant != null) {
                    localParticipant.publishTrack(localVideoTrack);
                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] publishTrack_video: localParticipant is null >> publishTrack(localVideoTrack) FAILED");

                    if(null != room){
                        localParticipant = room.getLocalParticipant();

                        if (localParticipant != null) {
                            localParticipant.publishTrack(localVideoTrack);
                        }else{
                            Log.e(TAG, "[VIDEOPLUGIN] publishTrack_video: localParticipant is null >> publishTrack(localVideoTrack) FAILED");

                        }
                    }else{
                        Log.e(TAG, "[VIDEOPLUGIN] publishTrack_video:  room is null");
                    }
                }

            }else{
                //can happen when openRoom and StartCall both did startActivity
                Log.e(TAG, "[VIDEOPLUGIN] publishTrack_video: hasPermissionForCameraAndMicrophone() FAILED");
            }
        }else{
            //localVideoTrack NOT null - can happen when openRoom and StartCall both did startActivity

            if(null != room){
                localParticipant = room.getLocalParticipant();

                if (localParticipant != null) {
                    localParticipant.publishTrack(localVideoTrack);
                }else{
                    Log.d(TAG, "[VIDEOPLUGIN] publishTrack_video: localVideoTrack is OK BUT localParticipant is null >> wait for room.connect");

                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN] publishTrack_video: room is null >> wait for room.connect to trigger Room.Listener onConnected:");
            }

        }
    }




    @Override
    protected void onPause() {

        if(this.is_Activity_hiding_because_buttonBackToCall_tapped){
            Log.d(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onPause CALLED because_buttonBackToCall_tapped - dont unpublish videoTrack  [" + this + "]");
        }else{
            Log.d(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onPause CALLED finish() called in DISCONNECT button - must unpublish videoTrack  [" + this + "]");Log.e(TAG, " is null");

            //--------------------------------------------------------------------------------------
            unpublishTrack_localVideoTrack();
            //--------------------------------------------------------------------------------------
        }

        Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onPause CALLED [" + this + "]");
        super.onPause();
    }

    private void unpublishTrack_localAudioTrack(){
        Log.e(TAG, "[VIDEOPLUGIN] unpublishTrack_localAudioTrack: CALLED >> localParticipant.unpublishTrack(localAudioTrack)");

        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localAudioTrack != null) {
            /*
             * If this local video track is being shared in a Room, unpublish from room before
             * releasing the video track. Participants will be notified that the track has been
             * unpublished.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localAudioTrack);
            }else{
                Log.e(TAG, "onPause: localParticipant is null - cant unpublishTrack");
            }
            //--------------------------------------------------------------------------------------
            //dont release when you unpublish
            // when audio enabled again it will crate a new localTrack instance
            // but it doesnt trigger delegate on remote side - I think because we pass audio track in to room.connect
            //
            //            localAudioTrack.release();
            //            localAudioTrack = null;
            //--------------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "onPause: localAudioTrack is null - cant unpublishTrack");
        }
    }
    private void publishTrack_localAudioTrack(){
        Log.e(TAG, "[VIDEOPLUGIN] publishTrack_localAudioTrack: CALLED >> localParticipant.publishTrack(localAudioTrack)");
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localAudioTrack != null) {
            /*
             * If this local video track is being shared in a Room, publish from room before
             * releasing the video track. Participants will be notified that the track has been
             * published.
             */
            if (localParticipant != null) {
                localParticipant.publishTrack(localAudioTrack);
            }else{
                Log.e(TAG, "onPause: localParticipant is null - cant publishTrack");
            }

        }else{
            Log.e(TAG, "onPause: localAudioTrack is null - cant publishTrack");
        }
    }

    private void unpublishTrack_localVideoTrack(){
        Log.e(TAG, "[VIDEOPLUGIN] unpublishTrack_localVideoTrack: CALLED >> localParticipant.unpublishTrack(localVideoTrack)");

        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, unpublish from room before
             * releasing the video track. Participants will be notified that the track has been
             * unpublished.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }else{
                Log.e(TAG, "onPause: localParticipant is null - cant unpublishTrack");
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }else{
            Log.e(TAG, "onPause: localVideoTrack is null - cant unpublishTrack");
        }
    }
    private void publishTrack_localVideoTrack(){
        Log.e(TAG, "[VIDEOPLUGIN] publishTrack_localVideoTrack: CALLED >> localParticipant.publishTrack(localVideoTrack)");
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, publish from room before
             * releasing the video track. Participants will be notified that the track has been
             * published.
             */
            if (localParticipant != null) {
                localParticipant.publishTrack(localVideoTrack);
            }else{
                Log.e(TAG, "onPause: localParticipant is null - cant publishTrack");
            }

        }else{
            Log.e(TAG, "onPause: localVideoTrack is null - cant publishTrack");
        }
    }

    private void stopCapture(){
        if(null != cameraCapturer){
            if(null != cameraCapturer.getVideoCapturer()){
                cameraCapturer.getVideoCapturer().stopCapture();

                cameraCapturer = null;

            }else{
                Log.e(TAG, "cameraCapturer.getVideoCapturer() is null - cant stopCapture");
            }
        }else{
            Log.e(TAG, "cameraCapturer is null - cant stopCapture");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.e(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onBackPressed CALLED [" + this + "]");

        overridePendingTransition(0, 0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onStart CALLED [" + this + "]");
        //static var to track if thisActivity is running - used in onAnswer
        activeInstances++;

        onResumeCompletedAtLeastOnce = true;
    }

    @Override
    protected void onStop() {
        Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onStop CALLED - does nothing [" + this + "]");
        super.onStop();
        //static var to track if thisActivity is running - used in onAnswer
        activeInstances--;
    }


    @Override
    protected void onDestroy() {
        //BACK TO CALL button - calls moveToBack(..) this should not be called

        if(this.isFinishing()){
            Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onDestroy CALLED - isFinishing:TRUE - finish() called somewhere [" + this + "]");
        }else{
            Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onDestroy CALLED - isFinishing:FALSE - system is temporarily destroying this instance [" + this + "]");
        }

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
            Log.w(TAG, "[VIDEOPLUGIN][TwilioVideoActivity][LIFECYCLE] onDestroy CALLED - CALLING room.disconnect();");
            room.disconnect();

            disconnectedFromOnDestroy = true;
        }

        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }

        Log.w(TAG, "[VIDEOPLUGIN] onDestroy() >> publishEvent(CLOSED) (Activity started with FLAG_ACTIVITY_REORDER_TO_FRONT so may not be destroyed)");
        publishEvent(CallEvent.CLOSED);

        TwilioVideoManager.getInstance().setActionListenerObserver(null);

        unregisterReceiver(headsetIntentReceiver);
        unregisterReceiver(speakerPhoneChangeIntentReceiver);


        //------------------------------------------------------------------------------------------
        super.onDestroy();
    }

    private boolean hasPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPermissionForMicrophone() {

        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasPermissionForCamera() {

        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        return resultCamera == PackageManager.PERMISSION_GRANTED ;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                TwilioVideo.PERMISSIONS_REQUIRED,
                PERMISSIONS_REQUEST_CODE);

    }

    private void setup_createAudioAndVideoTracks() {

        //------------------------------------------------------------------------------------------
        //VIDEO - CALLER'S LOCAL AUDIO
        //------------------------------------------------------------------------------------------
        setup_local_audio();
        setup_localaudio_button();

        //------------------------------------------------------------------------------------------
        //VIDEO - CALLER'S LOCAL CAMERA
        //------------------------------------------------------------------------------------------
        setup_local_camera();
        setup_localvideo_button();
        setup_localvideo_moveLocalVideoToThumbnailView();

    }

    private void setup_local_audio(){
        //------------------------------------------------------------------------------------------
        //AUDIO
        //------------------------------------------------------------------------------------------
        //cordova can start call with audio off config.startWithAudioOff:true
        boolean enableAudioAtStart = true;

        //------------------------------------------------------------------------------------------
        //onResume can be called twice for a clean install
        // first when call started from web to android > void: answer >
        // show permission alerts
        // after permission request alerts press ok app comes to fireground and onResume called
        //localAudioTrack may be not nil - dont create it again else thrumnail can be blank (but camera looks ok on the web)
        //------------------------------------------------------------------------------------------
        if(null != localAudioTrack){
            Log.i(TAG, "localAudioTrack is not null - resume can be called twice after permission request alerts");

        }else{
            Log.w(TAG, "localAudioTrack is null - CREATE IT");

            if(null != config){
                if(config.isStartWithAudioOff()){
                    Log.i(TAG, "[VIDEOPLUGIN] setup_local_audio: TRUE - enableAudioAtStart = false;");
                    enableAudioAtStart = false;

                }else{
                    Log.d(TAG, "[VIDEOPLUGIN] setup_local_audio: FALSE - enableAudioAtStart = true;");
                    enableAudioAtStart = true;

                }
            }else{
                Log.e(TAG, "setup_local_audio: config is null FALLBACK TO - enableAudioAtStart = true;");
                enableAudioAtStart = true;
            }

            //--------------------------------------------------------------
            //was crashing but I do ask for permission above
            // Caused by: java.lang.IllegalStateException: RECORD_AUDIO permission must be granted to create audio track
            if(hasPermissionForMicrophone()){
                localAudioTrack = LocalAudioTrack.create(this,
                        enableAudioAtStart,
                        LOCAL_AUDIO_TRACK_NAME);
            }else{
                Log.e(TAG, "hasPermissionForMicrophone is false - localAudioTrack.create failed");
            }
            //--------------------------------------------------------------
        }
    }
    private void setup_localaudio_button(){

        Log.d(TAG, "[VIDEOPLUGIN] setup_localaudio_button CALLED");

        if(null != localAudioTrack){

            //--------------------------------------------------------------------------------------
            //UPDATE BUTTON STATE TO MATCH VIDEO ON/OFF
            //WHEN you TAP on a button to turn OFF video or AUDIO the button is SELECTED -
            //so if video/audio is enabled then button is unselected
            //--------------------------------------------------------------------------------------
            update_button_fab_localaudio_onoff(localAudioTrack.isEnabled());
            //--------------------------------------------------------------------------------------

        }else{
            Log.e(TAG, "[VIDEOPLUGIN] localAudioTrack is null - update_button_fab_localaudio_onoff to default OFF");
            update_button_fab_localaudio_onoff(false);
        }
    }

    private void setup_localvideo_button(){
        if(null != cameraCapturer){

            //--------------------------------------------------------------------------------------
            //DOUBLE CHECK - localVideo track was set at least once and once only - onResume can be called two after permissions alerts tapped
            if(null != localVideoTrack){
                //--------------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] localVideoTrack is created ok from camera capture");

                //--------------------------------------------------------------------------------------
                //UPDATE BUTTON STATE TO MATCH VIDEO ON/OFF
                //WHEN you TAP on a button to turn OFF video or AUDIO the button is SELECTED -
                //so if video/audio is enabled then button is unselected
                //--------------------------------------------------------------------------------------
                update_button_fab_localvideo_onoff(localVideoTrack.isEnabled());
                //--------------------------------------------------------------------------------------

            }else{
                Log.e(TAG, "[VIDEOPLUGIN] localVideoTrack is null - failed to create from camera capture");
            }
        }else{
            Log.i(TAG, "cameraCapturer FAILED - cant create VIDEOTRACK");
        }
    }
    private void setup_localvideo_moveLocalVideoToThumbnailView(){
        if(null != cameraCapturer){

            //--------------------------------------------------------------------------------------
            //DOUBLE CHECK - localVideo track was set at least once and once only - onResume can be called two after permissions alerts tapped
            if(null != localVideoTrack){
                //--------------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] localVideoTrack is created ok from camera capture");

                //--------------------------------------------------------------------------------------
                this.moveLocalVideoToThumbnailView();
                //--------------------------------------------------------------------------------------

            }else{
                Log.e(TAG, "[VIDEOPLUGIN] localVideoTrack is null - failed to create from camera capture");
            }
        }else{
            Log.i(TAG, "cameraCapturer FAILED - cant create VIDEOTRACK");
        }
    }

    private void setup_local_camera(){
        setup_cameraCapturer();
        setup_localVideoTrack();
    }

    private void setup_cameraCapturer() {
        if (null != cameraCapturer) {
            Log.e(TAG, "cameraCapturer is not null - DONT RECREATE IT - resume can be called twice after permission request alerts");

        } else {
            Log.e(TAG, "cameraCapturer is null - CREATE IT ONCE");
            cameraCapturer = new CameraCapturerCompat(this, getAvailableCameraSource());
        }
    }

    private void setup_localVideoTrack(){
        if(null != cameraCapturer) {
            Log.i(TAG, "DOUBLE CHECK cameraCapturer CREATED OK - cant create VIDEOTRACK");

            //------------------------------------------------------------------------------------------
            boolean enableVideoAtStart = true;


            if(null != config){
                if (config.isStartWithVideoOff()) {
                    Log.d(TAG, "[VIDEOPLUGIN] setup_localVideoTrack: isStartWithVideoOff: TRUE - AUDIO disabled at start");
                    enableVideoAtStart = false;

                } else {
                    Log.w(TAG, "[VIDEOPLUGIN] setup_localVideoTrack: isStartWithVideoOff: FALSE - AUDIO enabled at start");
                    enableVideoAtStart = true;
                }
            }else{
                Log.e(TAG, "[VIDEOPLUGIN] setup_localVideoTrack: config is null - FALLBACK TO isStartWithVideoOff: TRUE");
                enableVideoAtStart = true;
            }
            //------------------------------------------------------------------------------------------
            //NOTE - proximity sensor in android triggers a single proximity:AWAY when its starts
            //proximity:AWAY sets localVideoTrack.enabled to true so overwrites what you set here
            // you need to make sure it doesnt start the camera if config.startWithVideoOff:true
            // done in preventProximityTurningCameraOn()

            //--------------------------------------------------------------------------------------
            if (null != localVideoTrack) {
                Log.e(TAG, "localVideoTrack is not null - DONT RECREATE IT - resume can be called twice after permission request alerts");

            } else {
                //----------------------------------------------------------------------------------
                Log.d(TAG, "localVideoTrack is null - CREATE IT");
                //----------------------------------------------------------------------------------
                if(hasPermissionForCamera()){
                    localVideoTrack = LocalVideoTrack.create(this,
                            enableVideoAtStart,
                            cameraCapturer.getVideoCapturer(),
                            LOCAL_VIDEO_TRACK_NAME);
                }else{
                    Log.e(TAG, "hasPermissionForCamera is null - request permissions should show alert - to test do clean install");
                }

                //----------------------------------------------------------------------------------
            }
        }
    }

    private CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraSource.FRONT_CAMERA)) ?
                (CameraSource.FRONT_CAMERA) :
                (CameraSource.BACK_CAMERA);
    }


    //P1  - openRoom > dont connect to room > show Calling...
    //backend will send push to p2 to connect to room
    //once p2 has joined to room webhood will send startCall to P1
    //till then just show Calling...
    private void displayCallWaiting(){
        Log.d(TAG, "[VIDEOPLUGIN]  displayCallWaiting");


        if(this.previewIsFullScreen){
            //----------------------------------------------------------------------
            //Show the dialing panel

            this.show_viewRemoteParticipantInfoWithState("Calling...");
            this.hide_buttonBackToCall();

            this.animateAlphaBorderForViews();

            //----------------------------------------------------------------------
            // show LOCAL USER full screen while waiting for othe ruser to answer
            this.update_PreviewView_showInFullScreen(true, false, true);

            //----------------------------------------------------------------------
            //FIRST TIME VERY LOUD - cant set volume to 0
            //NEXT TIMESupdate_PreviewView_showInFullScreen too quiet
            //will start it before room connect in viewDidLoad
            this.dialing_sound_start();
            //----------------------------------------------------------------------

        }else{
            Log.e(TAG, "[VIDEOPLUGIN] [displayCallWaiting] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }





    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 0 remote - LOCAL USER is person dialing REMOTE participant.
    //Remote hasnt joined the room yet so hasnt answered so show 'Dialing..'
    //On the CALLING PHONE it will trigger
    //participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking
    private void participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom(){
        Log.d(TAG, "[VIDEOPLUGIN] [participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] START");

        this.dialing_sound_stop();


        if(this.previewIsFullScreen){

            //hide Waiting... / show back arrow
            this.hide_viewRemoteParticipantInfo();
            this.show_buttonBackToCall();

            //------------------------------------------------------------------------------------------
            //INCALL remote users name and muted icon
            //------------------------------------------------------------------------------------------
            //default ot muted - AUDIO_TRACK_ADDED will set it to unmuted
            this.show_inCall_remoteUserNameAndMic_isMuted(true);

            //------------------------------------------------------------------------------------------
            //REMOTE user is visible in full screen
            //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
            //ANSWER this.update_PreviewView_showInFullScreen(false, true, true);
            this.update_PreviewView_showInFullScreen(false, true, false);

        }else{
            Log.w(TAG, "[VIDEOPLUGIN] [participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

    private void participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking(){
        Log.d(TAG, "[VIDEOPLUGIN] [participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] START");

        this.dialing_sound_stop();

        if(this.previewIsFullScreen){

            this.hide_viewRemoteParticipantInfo();
            this.show_buttonBackToCall();

            //------------------------------------------------------------------------------------------
            //INCALL remote users name and muted icon
            //------------------------------------------------------------------------------------------
            //default ot muted - AUDIO_TRACK_ADDED will set it to unmuted
            this.show_inCall_remoteUserNameAndMic_isMuted(true);

            //------------------------------------------------------------------------------------------
            //REMOTE user is visible in full screen
            //------------------------------------------------------------------------------------------
            //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
            //this.update_PreviewView_showInFullScreen(false, true, true);
            this.update_PreviewView_showInFullScreen(false, true, false);


        }else{
            Log.w(TAG, "[VIDEOPLUGIN] [participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 1 remote - REMOTE user in room is the other person who started the call
    //LOCAL USER is answering a call so dont show 'Dialing..'
    private void participantDidConnect(){
        Log.w(TAG, "[VIDEOPLUGIN] [participantDidConnect] Unused in 1..1 - use for GROUP");
    }

    private void participantDidDisconnect(String remoteParticipant_identity){

        if(this.previewIsFullScreen){
            Log.w(TAG, "[VIDEOPLUGIN] [participantDidDisconnect] new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL");

        }else{
            //REMOTE USER DISCONNECTED
            //show the remote user panel with state 'Disconnected'

            //if app running on REMOTE photo will just show white circle no photo
            //this is so Disconnected isnt off center

            this.show_viewRemoteParticipantInfoWithState("Disconnected");
            this.hide_buttonBackToCall();

            //pulse animation is on repeat forever - just hide the fake border view - I think when in full sea/chat disconnect will close this whole VC
            this.animateAlphaBorderForViews_HideBorder();

            //Zoom the preview from MINI to FULL SCREEN
            //ONLY show BLUR when dialing
            //Here remote has disconnected so dont show blur

            this.update_PreviewView_showInFullScreen(true,true, false);
        }
    }



    private void connectToRoom() {

        //onResume can be called twice (if permissions alert appear) - dont connect to the room again
        if(null != room){
            Log.d(TAG, "[VIDEOPLUGIN] connectToRoom: room is NOT null - DONT RECREATE - onResume can be called twice (if permissions alert appear) - dont connect to the room again");
        }else{
            Log.d(TAG, "[VIDEOPLUGIN] connectToRoom: room is null - CONNECT AND CREATE IT");

            configureAudio(true);
            //--------------------------------------------------------------------------------------
            ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                                                                .roomName(this.roomId)
                                                                .enableIceGatheringOnAnyAddressPorts(true);

            //--------------------------------------------------------------------------------------
            /*
             * Add local audio track to connect options to share with participants.
             */
            if (localAudioTrack != null) {
                connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
            }else{
                Log.e(TAG, "[VIDEOPLUGIN] connectToRoom: localAudioTrack is null");
            }

            //--------------------------------------------------------------------------------------
            //Add local video track to connect options to share with participants.
            if (localVideoTrack != null) {
                connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
            }else{
                Log.e(TAG, "[VIDEOPLUGIN] connectToRoom: localVideoTrack is null");
            }

            //--------------------------------------------------------------------------------------
            Log.d(TAG, "[VIDEOPLUGIN] connectToRoom: CREATE ROOM: room = Video.connect(...)");
            room = Video.connect(this, connectOptionsBuilder.build(), roomListener());

            //--------------------------------------------------------------------------------------
        }


    }

    /*
     * The initial state when there is no active conversation.
     */
    private void initializeUI() {
        //------------------------------------------------------------------------------------------
        //unused config params - dont remove primaryColor is used in styles.xml and can break main sea/chat app
        //------------------------------------------------------------------------------------------
        //        if(null != this.config){
        //            if (config.getPrimaryColorHex() != null) {
        //                int primaryColor = Color.parseColor(config.getPrimaryColorHex());
        //                ColorStateList color = ColorStateList.valueOf(primaryColor);
        //                button_fab_disconnect.setBackgroundTintList(color);
        //            }
        //
        //            if (config.getSecondaryColorHex() != null) {
        //                int secondaryColor = Color.parseColor(config.getSecondaryColorHex());
        //                ColorStateList color = ColorStateList.valueOf(secondaryColor);
        //                if(null != button_fab_switchcamera){
        //                    button_fab_switchcamera.setBackgroundTintList(color);
        //                }else{
        //                	Log.e(TAG, "[VIDEOPLUGIN] switchCameraActionFab is null");
        //                }
        //
        //                button_fab_localvideo_onoff.setBackgroundTintList(color);
        //                button_fab_audio_onoff.setBackgroundTintList(color);
        //                button_fab_switchaudio.setBackgroundTintList(color);
        //            }
        //        }else{
        //            Log.e(TAG, "[VIDEOPLUGIN] this.config is null");
        //        }
        //------------------------------------------------------------------------------------------



        //------------------------------------------------------------------------------------------
        //DISCONNECT - always RED
        //------------------------------------------------------------------------------------------
        int colorButtonDisconnect = ContextCompat.getColor(this, FAKE_R.getColor("colorButtonDisconnect"));

        button_fab_disconnect.setBackgroundColor(colorButtonDisconnect);

        //------------------------------------------------------------------------------------------
        //OTHER BUTTONS
        //------------------------------------------------------------------------------------------
        //DONT USE R. doesnt work
        //        int colorButtonSelected   = ContextCompat.getColor(this, R.color.colorButtonSelected);
        //        int colorButtonUnselected = ContextCompat.getColor(this, R.color.colorButtonUnselected);

        int colorButtonSelected = ContextCompat.getColor(this, FAKE_R.getColor("colorButtonSelected"));
        int colorButtonUnselected = ContextCompat.getColor(this, FAKE_R.getColor("colorButtonUnselected"));


        //------------------------------------------------------------------------------------------
        //FABS
        //------------------------------------------------------------------------------------------
        //Doesnt work properly with FABs
        //        button_fab_localvideo_onoff.setBackgroundColor(colorButtonOn);

        //------------------------------------------------------------------------------------------
        ColorStateList colorStateList = createColorStateList(colorButtonSelected, colorButtonUnselected);

        //------------------------------------------------------------------------------------------
        button_fab_localvideo_onoff.setBackgroundTintList(colorStateList);
        button_fab_localvideo_onoff.show();
        button_fab_localvideo_onoff.setOnClickListener(button_localVideo_OnClickListener());

        //------------------------------------------------------------------------------------------
        button_fab_localaudio_onoff.setBackgroundTintList(colorStateList);
        button_fab_localaudio_onoff.show();
        button_fab_localaudio_onoff.setOnClickListener(button_mute_OnClickListener());

        //------------------------------------------------------------------------------------------
        //dont use ColorStateList - same color for all states
        //button_fab_switchaudio.setBackgroundTintList(colorStateList);

        //tint is always blue - if tapped switches to speaker or headphones

        //DONT USE R its capacitor...R
        //button_fab_switchaudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorButtonUnselected));
        button_fab_switchaudio.setBackgroundTintList(ContextCompat.getColorStateList(this, FAKE_R.getColor("colorButtonUnselected")));

        button_fab_switchaudio.show();
        button_fab_switchaudio.setOnClickListener(button_switchAudio_OnClickListener());


        //show speaker or headset icon
        update_icon_button_fab_switchaudio();


        //------------------------------------------------------------------------------------------
        //DISCONNECT
        //------------------------------------------------------------------------------------------
        button_fab_disconnect.setImageDrawable(ContextCompat.getDrawable(this, FAKE_R.getDrawable("ic_call_end_white_24px")));
        button_fab_disconnect.show();
        button_fab_disconnect.setOnClickListener(button_disconnect_OnClickListener());


        //------------------------------------------------------------------------------------------
        //        if(null != button_fab_switchcamera){
        //            button_fab_switchcamera.show();
        //            button_fab_switchcamera.setOnClickListener(button_switchCamera_OnClickListener());
        //        }else{
        //            Log.e(TAG, "[VIDEOPLUGIN] switchCameraActionFab is null");
        //        }
        //------------------------------------------------------------------------------------------
    }

    private ColorStateList createColorStateList(int selectedColor, int unselectedIconColor) {
        int[][] states = new int[][] {
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_selected},
                new int[]{android.R.attr.state_enabled},
                new int[]{android.R.attr.state_focused, android.R.attr.state_pressed},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
        };
        int[] colors = new int[]{
                selectedColor,
                selectedColor,
                unselectedIconColor,
                unselectedIconColor,
                unselectedIconColor,
                unselectedIconColor
        };
        return new ColorStateList(states, colors);
    }






    /***********************************************************************************************
     *  BUTTONS
     **********************************************************************************************/

    //----------------------------------------------------------------------------------------------
    //BUTTON STATES for VIDEO/MIC
    //----------------------------------------------------------------------------------------------
    //change icon
    //set selected - colors in ColorStateList

    //called by createAudioAndVideoTracks - cordova can pass in config.startWithAudioOff:true
    //called by button listener

    private void update_button_fab_localvideo_onoff( boolean enabled){
        //------------------------------------------------------------------------------------------
        //BUTTON BACKGROUND TINT
        //------------------------------------------------------------------------------------------
        //this doesnt work properly for FABs - use ColorStateList + setSelected(..)
        //  button_fab_localvideo_onoff.setBackgroundColor(colorButton);
        //------------------------------------------------------------------------------------------
        //colors setup in ColorStateList above
        //we just need to set setSelected
        //------------------------------------------------------------------------------------------
        int icon;

        if (enabled) {
            button_fab_localvideo_onoff.setSelected(false);
            icon = FAKE_R.getDrawable("ic_videocam_green_24px");
        }
        else {
            button_fab_localvideo_onoff.setSelected(true);
            icon = FAKE_R.getDrawable("ic_videocam_off_red_24px"); //bad name its grey
        }

        //------------------------------------------------------------------------------------------
        //BUTTON ICON
        button_fab_localvideo_onoff.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
        //------------------------------------------------------------------------------------------
    }

    //called by createAudioAndVideoTracks - cordova can pass in config.startWithAudioOff:true
    //called by button listener
    private void update_button_fab_localaudio_onoff( boolean enabled){
        //------------------------------------------------------------------------------------------
        //BUTTON BACKGROUND TINT
        //------------------------------------------------------------------------------------------
        //doesnt work properly for FABs
        //button_fab_localaudio_onoff.setBackgroundColor(colorButton);
        //------------------------------------------------------------------------------------------
        //colors setup in ColorStateList above
        //we just need to set state
        //------------------------------------------------------------------------------------------
        int icon;

        if (enabled) {
            button_fab_localaudio_onoff.setSelected(false);
            icon = FAKE_R.getDrawable("ic_mic_green_24px");
        }
        else {
            button_fab_localaudio_onoff.setSelected(true);
            icon = FAKE_R.getDrawable("ic_mic_off_red_24px"); //bad name its grey
        }

        //------------------------------------------------------------------------------------------
        //BUTTON ICON
        button_fab_localaudio_onoff.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
        //------------------------------------------------------------------------------------------
    }




    //----------------------------------------------------------------------------------------------
    //BUTTON - CAMERA ON/OFF
    //----------------------------------------------------------------------------------------------
    private void button_fab_localvideo_onoff_disable(){

        if(null != button_fab_localvideo_onoff){
            button_fab_localvideo_onoff.setEnabled(false);

            button_fab_localvideo_onoff.setVisibility(View.INVISIBLE);
        }else{
            Log.e(TAG, "button_fab_localvideo_onoff  is null");
        }
    }
    private void button_fab_localvideo_onoff_enable(){
        if(null != button_fab_localvideo_onoff){
            button_fab_localvideo_onoff.setVisibility(View.VISIBLE);
            button_fab_localvideo_onoff.setEnabled(true);

        }else{
            Log.e(TAG, "button_fab_localvideo_onoff  is null");
        }
    }

    private View.OnClickListener button_localVideo_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "[VIDEOPLUGIN] button_localVideo_OnClickListener.onClick: ADDED DELAY TIMER AS IT CRASHES ");

                //----------------------------------------------------------------------------------
                //PREVENT DOUBLE TAP

                if( handler_onClick_camera_doubletap == null){

                    Log.e(TAG, "handler is null - CREATE ONCE");

                    handler_onClick_camera_doubletap = new Handler();

                    delayRunnable_onClick_camera_doubletap = new Runnable() {

                        @Override
                        public void run() {
                            Log.e(TAG, "onCLick > run: WAIT COMPLETE - RESET  button_localVideo_OnClickListener_tapped TO false;");
                            button_localVideo_OnClickListener_tapped = false;

                            button_fab_localvideo_onoff_enable();

                        }
                    };

                }else{
                    Log.e(TAG, "handler_onClick_camera_doubletap is not null - DONT CREATE ON EVERY CLICK");
                }
                //----------------------------------------------------------------------------------


                //IF YOU TAP TOO FAST then app can crash - you need to wait for local camera to be fully enabled or fully disabled before allowing another tap
                if(button_localVideo_OnClickListener_tapped){
                    Log.e(TAG, "onClick: button_localVideo_OnClickListener_tapped:TRUE - SKIP DOUBLE TAP CAN CAUSE APP TO CRASH IF CAMMAER NOT FULLY ON OR FULLY OFF" );

                }else{
                    //PREVENT DOUBLE TAPS
                    button_localVideo_OnClickListener_tapped = true;

                    //disable the button else you it looks clickable but does nothing
                    button_fab_localvideo_onoff_disable();

                    //Enable/disable the local video track
                    if (localVideoTrack != null) {


                        if(localVideoTrack.isEnabled()){
                            Log.e(TAG, "[VIDEOPLUGIN] onClick: a1" );

                            Log.e(TAG, "button_localVideo_OnClickListener onClick: change LOCAL VIDEO to enabled:FALSE - call unpublishTrack + stopCapture()");
                            //--------------------------------------------------------------------------
                            //DISABLED LOCAL VIDEO
                            //--------------------------------------------------------------------------
                            localVideoTrack.enable(false);

                            //--------------------------------------------------------------------------
                            //CHANGE ICON and COLOR
                            //--------------------------------------------------------------------------
                            show_hide_localcamera(false);
                            //------------------------------------------------
                            update_button_fab_localvideo_onoff(false);
                            //--------------------------------------------------------------------------

                            //--------------------------------------------------------------------------
                            //BUG on JS sdk - just disabling localTrack isnt enough you have to UNPUBLISH too
                            //else web sdk wont get delegate
                            //--------------------------------------------------------------------------
                            unpublishTrack_localVideoTrack();
                            stopCapture();
                            //--------------------------------------------------------------------------

                        }else{

                            //--------------------------------------------------------------------------
                            //ENABLE LOCAL VIDEO - localVideoTrack != null is created above we only need to reenable it
                            //--------------------------------------------------------------------------

                            Log.e(TAG, "button_localVideo_OnClickListener onClick: change LOCAL VIDEO to enabled:TRUE - only call localVideoTrack.enable(true) DONT REPUBLISH HERE");

                            localVideoTrack.enable(true);


                            //--------------------------------------------------------------------------
                            //CHANGE ICON and COLOR
                            //--------------------------------------------------------------------------
                            show_hide_localcamera(true);
                            //------------------------------------------------
                            update_button_fab_localvideo_onoff(true);
                            //--------------------------------------------------------------------------
                        }

                        //------------------------------------------------------------------------------
                    }else{
                        Log.e(TAG, "[VIDEOPLUGIN] onClick: localVideoTrack is null - CREATE IT" );

                        setup_local_camera();

                        //------------------------------------------------------------------------------
                        //CHANGE ICON and COLOR
                        //------------------------------------------------------------------------------
                        //MOVED DOWN I had issue when local camera is off when WEB calls ANDROID it gets stuck showing thumbnail
                        // show_hide_localcamera(true);
                        //------------------------------------------------------------------------------
                        update_button_fab_localvideo_onoff(true);
                        //------------------------------------------------------------------------------
                        setup_localvideo_moveLocalVideoToThumbnailView();

                        //------------------------------------------------------------------------------
                        //BUG on JS sdk - just enabling localTrack isnt enough you have to PUBLISH too
                        //else web sdk wont get delegate

                        publishTrack_localVideoTrack();


                        //bug - I start call from web, camera off, tap once enables it,
                        // tap again disables + unpublish, (first else clause above)
                        // third tap black screen (this clause) , (here i enable it after recreating it)
                        // 4th tap enables it
                        localVideoTrack.enable(true);

                        //i had to move down to AFTER localVideoTrack.enable
                        show_hide_localcamera(true);


                    }

                    //------------------------------------------------------------------------------
                    //WAIT 1 sec before user can tap the button again - low level crash if we turn local camera on and off too quickly
                    //------------------------------------------------------------------------------

                    handler_onClick_camera_doubletap.postDelayed(delayRunnable_onClick_camera_doubletap,
                            delayMillis_onClick_camera_doubletap);
                    //------------------------------------------------------------------------------
                }
            }//onClick
        };
    }
    private void show_hide_localcamera(boolean enabled){
        if (enabled) {
            //--------------------------------------------------------------------------
            //LOCAL CAMERA IS ON
            //--------------------------------------------------------------------------
            //WRAPPER - ALWAYS VISIBLE
            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

            thumbnailVideoView.setVisibility(View.VISIBLE);
            imageViewLocalParticipant.setVisibility(View.INVISIBLE);
            //imageViewLocalParticipant.bringToFront();
            //--------------------------------------------------------------------------

        } else {
            //--------------------------------------------------------------------------
            //LOCAL CAMERA IS OFF
            //--------------------------------------------------------------------------
            //WRAPPER - ALWAYS VISIBLE
            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);
            thumbnailVideoViewFrameLayout.bringToFront();

            thumbnailVideoView.setVisibility(View.INVISIBLE);
            imageViewLocalParticipant.setVisibility(View.VISIBLE);
            imageViewLocalParticipant.bringToFront();
            //--------------------------------------------------------------------------
        }
    }


    //----------------------------------------------------------------------------------------------
    //BUTTON - AUDIO ON/OFF
    //----------------------------------------------------------------------------------------------
    private View.OnClickListener button_mute_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[VIDEOPLUGIN] muteClickListener.onClick: ");

                /*
                 * Enable/disable the local audio track. The results of this operation are
                 * signaled to other Participants in the same Room. When an audio track is
                 * disabled, the audio is muted.
                 */
                if (localAudioTrack != null) {

                    //TOGGLE CURRENT AUDIO VALUE
                    boolean enable = !localAudioTrack.isEnabled();

                    //------------------------------------------------------------------------------
                    localAudioTrack.enable(enable);

                    //------------------------------------------------------------------------------
                    //BUTTON BACKGROUND and ICON for state
                    update_button_fab_localaudio_onoff(enable);
                    //------------------------------------------------------------------------------

                    if(enable){
                        Log.w(TAG, "button_mute_OnClickListener onClick: change to enabled:TRUE - CALL publishTrack_localAudioTrack()");
                        publishTrack_localAudioTrack();
                    }else{
                        Log.e(TAG, "button_mute_OnClickListener onClick: change to enabled:FALSE - CALL unpublishTrack_localAudioTrack()");
                        unpublishTrack_localAudioTrack();
                    }

                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] onClick: localAudioTrack is null - SHOULD NOT HAPPEN - DONT RECREATE IT its not connected to the room so delegate on other side will never be called");




//DIDNT WORK - it does recrate the localAudio track but doesnt connect it to the room
//instead in unpublish I call unpublish but I dont set localTrack to nil so in publsih Im using same instance
//this else clause should never happen
                    //                   setup_local_audio();
                    //                   setup_localaudio_button();
                    //
                    //                    publishTrack_localVideoTrack();

                    //DELEGATE on WEB and iOS wasnt being called
                    //                    localAudioTrack.enable(true);
                }

            }
        };
    }



    //----------------------------------------------------------------------------------------------
    //BUTTON - DISCONNECT
    //----------------------------------------------------------------------------------------------
    private View.OnClickListener button_disconnect_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != config){
                    if (config.isHangUpInApp()) {
                        Log.e(TAG, "[VIDEOPLUGIN][button_disconnect_OnClickListener] onClick: >> isHangUpInApp:TRUE >> publishEvent(HANG_UP)");

                        // Propagating the event to the web side in order to allow developers to do something else before disconnecting the room
                        publishEvent(CallEvent.HANG_UP);

                    } else {
                        Log.e(TAG, "[VIDEOPLUGIN][button_disconnect_OnClickListener] onClick: >> isHangUpInApp:FALSE >> onDisconnect");

                        onDisconnect();
                    }
                }else{
                    //happnes when you call show_twiliovideo before openRoom - disconnect button will crash cos config is null
                    Log.e(TAG, "button_disconnect_OnClickListener: config is null - activity created but config is null - FALLBACK call onDisconnect ");
                    onDisconnect();
                }

            }
        };
    }

    //----------------------------------------------------------------------------------------------
    //BUTTON - FLIP FRONT and BACk camera - tap on mini view
    //----------------------------------------------------------------------------------------------
    //CLEANUP - hidden button now
    private View.OnClickListener button_switchCamera_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "[VIDEOPLUGIN] onClick: switchCameraClickListener" );

                if (cameraCapturer != null) {
                    CameraSource cameraSource = cameraCapturer.getCameraSource();
                    cameraCapturer.switchCamera();
                    if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                        thumbnailVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    } else {
                        fullScreenVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    }
                }


            }//onclick

        };//return
    }


    //----------------------------------------------------------------------------------------------
    //BUTTON - AUDIO
    //----------------------------------------------------------------------------------------------

    private View.OnClickListener button_switchAudio_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //----------------------------------------------------------------------------------
                Log.w(TAG, "[VIDEOPLUGIN] switchAudioClickListener.onClick: BEFORE audioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());
                //----------------------------------------------------------------------------------


                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    //ANDROID 10 - setSpeakerphoneOn not working
                    //even added
                    //<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

                    if (ActivityCompat.checkSelfPermission(TwilioVideoActivity.this, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(TwilioVideoActivity.this, new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS}, 123);
                        // if the permission is granted then ok
                        Log.e(TAG, "onClick: MODIFY_AUDIO_SETTINGS permission is missing" );

                    } else {
                        Log.e(TAG, "onClick: MODIFY_AUDIO_SETTINGS is granted " );

                        if (audioManager.isSpeakerphoneOn()) {
                            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][button_switchAudio_OnClickListener] switchAudioClickListener.onClick: CALL audioManager.setSpeakerphoneOn(false);");
                            audioManager.setMode(AudioManager.MODE_NORMAL);
                            audioManager.setSpeakerphoneOn(false);
                        } else {
                            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][button_switchAudio_OnClickListener]  switchAudioClickListener.onClick: CALL audioManager.setSpeakerphoneOn(true);");
                            audioManager.setSpeakerphoneOn(true);

                        }
                    }

                }else{
                    Log.e(TAG, "[VIDEOPLUGIN][AUDIO][onCreate_configureAudio] onCreate_configureAudio: SDK not Q - cant register for ACTION_SPEAKERPHONE_STATE_CHANGED");
                    //works on S9 but not Android 10

                    if (audioManager.isSpeakerphoneOn()) {

                        Log.e(TAG, "[VIDEOPLUGIN][AUDIO][button_switchAudio_OnClickListener] switchAudioClickListener.onClick: CALL audioManager.setSpeakerphoneOn(false);");
                        audioManager.setSpeakerphoneOn(false);
                    } else {

                        Log.e(TAG, "[VIDEOPLUGIN][AUDIO][button_switchAudio_OnClickListener]  switchAudioClickListener.onClick: CALL audioManager.setSpeakerphoneOn(true);");
                        audioManager.setSpeakerphoneOn(true);

                    }
                }

                //----------------------------------------------------------------------------------
                Log.w(TAG, "[AUDIO][button_switchAudio_OnClickListener] AFTER audioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());
                //----------------------------------------------------------------------------------
                updateSpeakerButton();
                //----------------------------------------------------------------------------------



                //----------------------------------------------------------------------------------
//FOR RELEASE - COMMENT OUT - DEBUG triggers startCall
//                publishEvent(CallEvent.DEBUGSTARTACALL);
                //----------------------------------------------------------------------------------
            }
        };
    }

    private void updateSpeakerButton(){
        Log.e(TAG, "updateSpeakerButton: TODO" );
        //RELEASE put back
//        if(audioManager.isSpeakerphoneOn()){
//
//
//            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][updateSpeakerButton] AFTER isSpeakeraudioManager.isSpeakerphoneOn(): TRUE - CHANGE TO PHONE ICON");
//            //------------------------------------------------------------------------------
//            int icon = FAKE_R.getDrawable("ic_phonelink_ring_white_24dp");
//
//
//            button_fab_switchaudio.setImageDrawable(ContextCompat.getDrawable(
//                    TwilioVideoActivity.this, icon));
//
//            //------------------------------------------------------------------------------
//        }else{
//            Log.e(TAG, "[VIDEOPLUGIN][AUDIO][updateSpeakerButton] AFTER isSpeakeraudioManager.isSpeakerphoneOn(): FALSE - CHANGE TO HEADSET ICON");
//            int icon = FAKE_R.getDrawable("ic_volume_headhphones_white_24dp");
//
//            button_fab_switchaudio.setImageDrawable(ContextCompat.getDrawable( TwilioVideoActivity.this, icon));
//        }

    }
    private void configureAudio(boolean enable) {
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO] configureAudio: enable:" + enable);

        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             *
             * BC - REDIRECTS SOUND TO COME OUT OF SPEAKERPHONE not top call speaker
             * see also isSpeakerPhoneOn()/setSpeakerPhoneOn(t/f)
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();

            Log.w(TAG, "[VIDEOPLUGIN][AUDIO] configureAudio: audioManager.setMicrophoneMute(false);");
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO] configureAudio: audioManager.setMicrophoneMute(previousMicrophoneMute):" + previousMicrophoneMute);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void requestAudioFocus() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO] requestAudioFocus: Build.VERSION.SDK_INT >= Build.VERSION_CODES.O");

            //AUDIO FOCUS - if another app needs the volume dim yours
            //https://developer.android.com/guide/topics/media-apps/audio-focus
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) {
                                            Log.w(TAG, "onAudioFocusChange: ");

                                        }
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO] requestAudioFocus: NOT  Build.VERSION.SDK_INT >= Build.VERSION_CODES.O");
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }




    /*
     * Called when participant joins the room
     */
    private void addRemoteParticipant(RemoteParticipant participant) {
        participantIdentity = participant.getIdentity();


        /*
         * Add participant renderer
         */
        if (participant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    participant.getRemoteVideoTracks().get(0);

            /*
             * Only render video tracks that are subscribed to
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        /*
         * Start listening for participant media events
         */
        participant.setListener(remoteParticipantListener());
    }

    /*
     * Set primary view as renderer for participant video track
     */
    private void addRemoteParticipantVideo(VideoTrack videoTrack) {
        fullScreenVideoView.setVisibility(View.VISIBLE);
        fullScreenVideoView.setMirror(false);
        videoTrack.addRenderer(fullScreenVideoView);
    }

    private void moveLocalVideoToThumbnailView() {

        Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: STARTED" );

        //we need to setup the thumbnailVideoView at least once
        //we had issue with inResume being called twice after the Permission alerts were accepted
        //so we added code to int the UI only once
        //but we need this flag because this method also responds to user turning thumbnailVideoView on and off
        boolean refreshthumbnailVideoView = false;

        if (thumbnailVideoViewWasInitialised){
            //--------------------------------------------------------------------------------------
            //then use GONE to toggle it
            if (thumbnailVideoView.getVisibility() == View.GONE) {
                //----------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: thumbnailVideoViewWasInitialised is TRUE but View.GONE set refreshthumbnailVideoView to TRUE" );

                refreshthumbnailVideoView = true;
                //----------------------------------------------------------------------------------

            }else{
                //----------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: thumbnailVideoViewWasInitialised is TRUE but View.GONE set refreshthumbnailVideoView to FALSE" );
                refreshthumbnailVideoView = false;
                //----------------------------------------------------------------------------------
            }
            //--------------------------------------------------------------------------------------
        }else{
            Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: thumbnailVideoViewWasInitialised is FALSE set refreshthumbnailVideoView to TRUE" );

            //thumbnailVideoViewWasInitialised is false then always refresh it
            //had issue with onResume being called twice and this not being called
            refreshthumbnailVideoView = true;
        }

        if (refreshthumbnailVideoView) {
            Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: refreshthumbnailVideoView is TRUE - thumbnailVideoView.setVisibility(View.VISIBLE)" );

            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

            //IF YOU COMMENT THIS OUT YOU GET THAT CYAN BACKGROUND I THINK thumbnailVideoView is fullscreen on startup
            thumbnailVideoView.setVisibility(View.VISIBLE);

            imageViewLocalParticipant.setVisibility(View.INVISIBLE);

            //--------------------------------------------------------------------------------------
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(fullScreenVideoView);
                localVideoTrack.addRenderer(thumbnailVideoView);
            }else{
                Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: localVideoTrack is null");
            }
            //--------------------------------------------------------------------------------------
            if (localVideoView != null && thumbnailVideoView != null) {
                localVideoView = thumbnailVideoView;
            }else{
                Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: localVideoView != null && thumbnailVideoView != null FAILED OK IF LOCAL VIDEO IS OFF WHEN CALL STARTS");

            }
            //--------------------------------------------------------------------------------------
            //flip camera on horizontal axis?
            thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() == CameraSource.FRONT_CAMERA);
            //--------------------------------------------------------------------------------------



            if(null != localVideoTrack){
                if(localVideoTrack.isEnabled()){
                    Log.e(TAG, "localVideoTrack.isEnabled(): true");
                }else{
                    Log.e(TAG, "localVideoTrack.isEnabled(): false - Call started with video off so hide the black locla video with locla users photo ");
                    //IF LOCAL VIDEO IS OFF WHEN CALL STARTS then localVideo is null and can be black
                    //this put local users phot in its place
                    show_hide_localcamera(false);
                }
            }else{
                Log.e(TAG, "localVideoTrack is null");
            }

        }else{
            Log.d(TAG, "[VIDEOPLUGIN] moveLocalVideoToThumbnailView: refreshthumbnailVideoView is FALSE -- do nothing");
        }
    }

    /*
     * Called when participant leaves the room
     */
    private void removeRemoteParticipant(RemoteParticipant participant) {
        if (!participant.getIdentity().equals(participantIdentity)) {
            return;
        }

        /*
         * Remove participant renderer
         */
        if (participant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    participant.getRemoteVideoTracks().get(0);

            /*
             * Remove video only if subscribed to participant track
             */
            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }
    }

    private void removeParticipantVideo(VideoTrack videoTrack) {
        fullScreenVideoView.setVisibility(View.GONE);
        videoTrack.removeRenderer(fullScreenVideoView);
    }

    //called by DISCONNECT                                      > dismiss > finish()
    //called by DISCONNECTED_WITH_ERROR > handleConnectionError > dismiss > finish()
    public void dismiss(){
        Log.w(TAG, "[VIDEOPLUGIN] dismiss: CALLING finish()");
        finish();
    }

    /*
     * Room events listener
     */
    private Room.Listener roomListener() {
        return new Room.Listener() {

            @Override
            public void onConnected(Room room) {
                //----------------------------------------------------------------------------------
                //ROOM - onConnected
                //----------------------------------------------------------------------------------
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onConnected: ");


                //SINGLE CALL - if remote hangs up then Room listener onDisconnected called
                //MAIN APP will send endCall so we must process it
                //RESET
                roomListener_onDisconnectedCalled = false;


                //--------------------------------------------------------------------------------------
                localParticipant = room.getLocalParticipant();

                publishEvent(CallEvent.CONNECTED);

                final List<RemoteParticipant> remoteParticipants = room.getRemoteParticipants();
                if (remoteParticipants != null && !remoteParticipants.isEmpty()) {
                    addRemoteParticipant(remoteParticipants.get(0));
                }

                if (remoteParticipants != null) {
                    if (remoteParticipants.isEmpty()) {
                        //--------------------------------------------------------------------------
                        //1..1 CALL - no remote users so I am DIALING the REMOTE USER
                        //--------------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onConnected: room.remoteParticipants count is 0 >> LOCAL USER is STARTING A 1..1 CALL");

                        //--------------------------------------------------------------------------
                        didConnectToRoom_AnswerACall();

                        //--------------------------------------------------------------------------

                    }else if (remoteParticipants.size() == 1) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
                        //----------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onConnected: room.remoteParticipants count:%lu >> LOCAL USER is CONNECTING TO ROOM AFTER REMOTE for A 1..1 CALL");
                        //----------------------------------------------------------------------
                        participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking();
                    }
                    else {
                        Log.e(TAG, "[VIDEOPLUGIN] remoteParticipants.size() > 1 - GROUP CALL NOT HANDLED IN v1");
                    }
                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] methodName: remoteParticipants is null");
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onConnectFailure: ");

                publishEvent(CallEvent.CONNECT_FAILURE, TwilioVideoUtils.convertToJSON(e));

                if(null != config){
                    TwilioVideoActivity.this.handleConnectionError(config.getI18nConnectionError());

                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] onConnectFailure: config is null - cant get config.getI18nConnectionError()");
                }

            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException e) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onReconnecting: ");

                publishEvent(CallEvent.RECONNECTING, TwilioVideoUtils.convertToJSON(e));
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onReconnected: ");

                publishEvent(CallEvent.RECONNECTED);
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                //----------------------------------------------------------------------------------
                //ROOM - onDisconnected - dont mix up with onDisconnect
                //----------------------------------------------------------------------------------

                //SINGLE CALL - if remote hangs up then Room listener onDisconnected called
                //MAIN APP will send endCall so we must process it
                roomListener_onDisconnectedCalled = true;


                localParticipant = null;
                TwilioVideoActivity.this.room = null;

                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy && e != null) {

                    Log.w(TAG, "[VIDEOPLUGIN] Room.Listener >> onDisconnected: >> publishEvent(DISCONNECTED_WITH_ERROR) error:" + e);

                    publishEvent(CallEvent.DISCONNECTED_WITH_ERROR, TwilioVideoUtils.convertToJSON(e));


                    if(null != config){
                        TwilioVideoActivity.this.handleConnectionError(config.getI18nDisconnectedWithError());

                    }else{
                        Log.e(TAG, "[VIDEOPLUGIN] onConnectFailure: config is null - cant get config.getI18nDisconnectedWithError()");
                    }

                } else {
                    Log.w(TAG, "[VIDEOPLUGIN] Room.Listener >> onDisconnected: >> publishEvent(DISCONNECTED)");
                    publishEvent(CallEvent.DISCONNECTED);
                    dismiss();
                }
            }



            @Override
            public void onParticipantConnected(Room room, RemoteParticipant participant) {
                //----------------------------------------------------------------------------------
                //PARTICIPANT - onParticipantConnected
                //----------------------------------------------------------------------------------

                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onParticipantConnected: ");

                publishEvent(CallEvent.PARTICIPANT_CONNECTED);
                addRemoteParticipant(participant);

                //----------------------------------------------------------------------------------
                final List<RemoteParticipant> remoteParticipants = room.getRemoteParticipants();

                if (remoteParticipants != null) {

                    if (remoteParticipants.isEmpty()) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - no remote users so I an STARTING A CALL
                        //----------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] onParticipantConnected: oom.remoteParticipants count:0 >> LOCAL USER is STARTING A 1..1 CALL");
                        //----------------------------------------------------------------------
                        //this.participantDidConnect_AnswerACall];
                        //used didConnectToRoom_AnswerACall instead
                        //for GROUP participantDidConnect will do thinks like inc particpant count
                        //show list of users etc
                        //----------------------------------------------------------------------

                    }else if (remoteParticipants.size() == 1) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
                        //----------------------------------------------------------------------
                        Log.d(TAG, "[VIDEOPLUGIN] onParticipantConnected: room.remoteParticipants count:1 >>  room.remoteParticipants count:%lu >> REMOTE USER is ANSWERING A 1..1 CALL >> participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom");
                        //----------------------------------------------------------------------
                        participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom();
                    }
                    else {
                        Log.e(TAG, "[VIDEOPLUGIN] remoteParticipants.size() > 1 - GROUP CALL NOT HANDLED IN v1");
                    }
                }else{
                    Log.e(TAG, "[VIDEOPLUGIN] remoteParticipants is null");
                }
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onParticipantDisconnected: ");

                publishEvent(CallEvent.PARTICIPANT_DISCONNECTED);
                removeRemoteParticipant(participant);

                participantDidDisconnect(participant.getIdentity());

                //P1 calls p2
                //P2 turns off camera - remote phot appears in center of P1
                //P2 disconnects > onParticipantDisconnected > hide the remote photo if visible
                hide_imageViewRemoteParticipantInCall();
                //see also Room.Listener onVideoTrackEnabled/onVideoTrackDisabled:
            }

            @Override
            public void onRecordingStarted(Room room) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onRecordingStarted: ");
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                Log.d(TAG, "[VIDEOPLUGIN] Room.Listener onRecordingStopped: ");
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "[VIDEOPLUGIN] onRecordingStopped");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {

            @Override
            public void onAudioTrackPublished(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.w(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onAudioTrackPublished: ");

                Log.w(TAG, String.format("[VIDEOPLUGIN] onAudioTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackUnpublished(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.w(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onAudioTrackUnpublished: ");

                Log.w(TAG, String.format("[VIDEOPLUGIN] onAudioTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.isTrackEnabled(),
                        remoteAudioTrackPublication.isTrackSubscribed(),
                        remoteAudioTrackPublication.getTrackName()));
            }

            @Override
            public void onAudioTrackSubscribed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, RemoteAudioTrack remoteAudioTrack) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onAudioTrackSubscribed: ");

                Log.d(TAG, String.format("[VIDEOPLUGIN] onAudioTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));
                publishEvent(CallEvent.AUDIO_TRACK_ADDED);

                update_imageViewInCallRemoteMicMuteState_isMuted(false);
            }

            @Override
            public void onAudioTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, TwilioException twilioException) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onAudioTrackSubscriptionFailed: ");


                Log.i(TAG, String.format("[VIDEOPLUGIN] onAudioTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrackPublication.getTrackSid(),
                        remoteAudioTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onAudioTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication, RemoteAudioTrack remoteAudioTrack) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onAudioTrackUnsubscribed: ");


                Log.i(TAG, String.format("[VIDEOPLUGIN] onAudioTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteAudioTrack: enabled=%b, playbackEnabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteAudioTrack.isEnabled(),
                        remoteAudioTrack.isPlaybackEnabled(),
                        remoteAudioTrack.getName()));
                publishEvent(CallEvent.AUDIO_TRACK_REMOVED);

                update_imageViewInCallRemoteMicMuteState_isMuted(true);
            }

            @Override
            public void onVideoTrackPublished(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onVideoTrackPublished: ");


                Log.i(TAG, String.format("[VIDEOPLUGIN] onVideoTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackUnpublished(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onVideoTrackUnpublished: ");


                Log.i(TAG, String.format("[VIDEOPLUGIN] onVideoTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.isTrackEnabled(),
                        remoteVideoTrackPublication.isTrackSubscribed(),
                        remoteVideoTrackPublication.getTrackName()));
            }

            @Override
            public void onVideoTrackSubscribed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, RemoteVideoTrack remoteVideoTrack) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onVideoTrackSubscribed: ");

                Log.i(TAG, String.format("[VIDEOPLUGIN] onVideoTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                publishEvent(CallEvent.VIDEO_TRACK_ADDED);
                addRemoteParticipantVideo(remoteVideoTrack);

                //p1 calls p2
                //if p2 answers with their camera off then show their photo
                //see also onVideoTrackEnabled/onVideoTrackDisabled
                if(remoteVideoTrack.isEnabled()){
                    show_fullScreenVideoView();
                    hide_imageViewRemoteParticipantInCall();
                }else{
                    Log.d(TAG, "[VIDEOPLUGIN] remoteVideoTrack.isEnabled():TRUE");
                    hide_fullScreenVideoView();
                    show_imageViewRemoteParticipantInCall();
                }

            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, TwilioException twilioException) {
                Log.d(TAG, "[VIDEOPLUGIN] RemoteParticipant.Listener onVideoTrackSubscriptionFailed: ");

                Log.e(TAG, String.format("[VIDEOPLUGIN] onVideoTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrackPublication.getTrackSid(),
                        remoteVideoTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onVideoTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, RemoteVideoTrack remoteVideoTrack) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] onVideoTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteVideoTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteVideoTrack.isEnabled(),
                        remoteVideoTrack.getName()));
                publishEvent(CallEvent.VIDEO_TRACK_REMOVED);
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onDataTrackPublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] onDataTrackPublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackUnpublished(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] onDataTrackUnpublished: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%s, enabled=%b, " +
                                "subscribed=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.isTrackEnabled(),
                        remoteDataTrackPublication.isTrackSubscribed(),
                        remoteDataTrackPublication.getTrackName()));
            }

            @Override
            public void onDataTrackSubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, RemoteDataTrack remoteDataTrack) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] onDataTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, TwilioException twilioException) {
                Log.e(TAG, String.format("[VIDEOPLUGIN] onDataTrackSubscriptionFailed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrackPublication: sid=%b, name=%s]" +
                                "[TwilioException: code=%d, message=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrackPublication.getTrackSid(),
                        remoteDataTrackPublication.getTrackName(),
                        twilioException.getCode(),
                        twilioException.getMessage()));
            }

            @Override
            public void onDataTrackUnsubscribed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, RemoteDataTrack remoteDataTrack) {
                Log.d(TAG, String.format("[VIDEOPLUGIN] onDataTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "[VIDEOPLUGIN] onAudioTrackEnabled: CALLED" );

                show_inCall_remoteUserNameAndMic_isMuted(false);
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "[VIDEOPLUGIN] onAudioTrackDisabled: CALLED" );

                show_inCall_remoteUserNameAndMic_isMuted(true);
            }
            //--------------------------------------------------------------------------------------
            //OTHER USER TURNS VIDEO / ON OFF - hide the video feed and show remote users photo
            //--------------------------------------------------------------------------------------
            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.d(TAG, "[VIDEOPLUGIN] onVideoTrackEnabled: CALLED" );

                show_fullScreenVideoView();

                hide_imageViewRemoteParticipantInCall();
                //see also Room.Listener onParticipantDisconnected:
                //see also onVideoTrackSubscribed
            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.d(TAG, "[VIDEOPLUGIN] onVideoTrackDisabled: CALLED" );

                hide_fullScreenVideoView();

                show_imageViewRemoteParticipantInCall();
                //see also onVideoTrackSubscribed
            }
        };
    }





    /*
     * handleConnectionError
     * */
    private void handleConnectionError(String message) {

        //if connection cant be made must stop audio or else it stays rinning till you likk the app
        dialing_sound_stop();

        //------------------------------------------------------------------------------------------
        // TODO: 08/02/21 NEEDED? we call dismiss() anyway
        if(null != config){
            if (config.isHandleErrorInApp()) {
                Log.i(TAG, "[VIDEOPLUGIN] handleConnectionError: config.isHandleErrorInApp(): TRUE - Error handling disabled for the plugin. This error should be handled in the hybrid app");
                dismiss();
                return;
            }
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] config is null");
        }

        Log.i(TAG, "[VIDEOPLUGIN] Connection error handled by the plugin");

        //------------------------------------------------------------------------------------------
        //close video ui - return to cordova
        dismiss();
        //------------------------------------------------------------------------------------------
    }


    //onDisconnect() - disconnect and close TVA
    //triggers Room delegate onDisconnected() which returns DISCONNECTED to Cordova
    //TVA.finish > onDestroy                 which returns  CLOSED       to Cordova

    @Override
    public void onDisconnect() {
        Log.e(TAG, "[VIDEOPLUGIN] TwilioVideoActivity.onDisconnect()] CALLED");
        /*
         * Disconnect from room
         */
        if (room != null) {
            Log.d(TAG, "[VIDEOPLUGIN] TwilioVideoActivity.onDisconnect()] CALL room.disconnect()");
            room.disconnect();
        }else{
            Log.e(TAG, "[VIDEOPLUGIN] TwilioVideoActivity.onDisconnect()]  room is null cant call room.disconnect()");
        }

        //if user pressed RED disconnect button while mp3 is playing it will keep playing till app killed
        dialing_sound_stop();

        //------------------------------------------------------------------------------------------
//RELEASE - doesnt work in POC but comment in before release as it builds ok there
        //in older Android MainActivity may not be right behind TwilioVideoActivity
        //sometime InCallActivity is
        //so need to switch to MainA here before finish;
        //------------------------------------------------------------------------------------------
        Log.e(TAG, "[VIDEOPLUGIN][onDisconnect()] startActivity: >> MainActivity BEFORE finish");
        Intent mIntent=new Intent(TwilioVideoActivity.this, MainActivity.class);
        startActivity(mIntent);

        //------------------------------------------------------------------------------------------
        Log.e(TAG, "[VIDEOPLUGIN][onDisconnect()] onClick: >> finish()");
        finish();

        //------------------------------------------------------------------------------------------
        //when you end a call - null this
        //its used for incoming calls while your in a video call
        //to check if TVA is running
        //but was showing as not null even when TVA not showing in activityes list
        ///Users/gbxc/Library/Android/sdk/platform-tools/adb shell dumpsys activity activities | sed -En -e '/Stack #/p' -e '/Running activities/,/Run #0/p'
//        TwilioVideoActivity.twilioVideoActivity = null;

        //------------------------------------------------------------------------------------------
        //BUG - openRoom() > startCall() we need Activity to stay alive so we added FLAG_ACTIVITY_REORDER_TO_FRONT
        //but restarting a call is causing issues so when we hit RED disconnect button CLOSED should tell TwilioVideo.java to remove the Activity fully from memory
        //triggers onDestroy() which wil send CLOSED
        //TwilioPlugin.java CLOSED will remove the Activity
        //------------------------------------------------------------------------------------------

    }

    @Override
    public void finish() {
        Log.w(TAG, "[VIDEOPLUGIN][finish()] TURN AUDIO OFF >> super.finish()");

        configureAudio(false);
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void publishEvent(CallEvent event) {
        TwilioVideoManager.getInstance().publishEvent(event);
    }

    private void publishEvent(CallEvent event, JSONObject data) {
        TwilioVideoManager.getInstance().publishEvent(event, data);
    }



    //ACTION_SPEAKERPHONE_STATE_CHANGED


    //https://github.com/Spark-Rom/packages_services_Telecomm/blob/1ae5295c6e727df8687bf1137343b614ca607cbc/src/com/android/server/telecom/CallAudioRouteStateMachine.java
    public class SpeakerPhoneChangeIntentReceiver extends BroadcastReceiver {

        public SpeakerPhoneChangeIntentReceiver() {

        }

        @Override
        public void onReceive(Context context, Intent intent) {

            //----------------------------------------------------------------------------------
            Log.e(TAG, "[VIDEOPLUGIN][AUDIO] SpeakerPhoneChangeIntentReceiver.onReceive: CALLED");
            try {
                if (AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED.equals(intent.getAction())) {
                    if (null != audioManager) {
                        //--------------------------------------------------------------------------
                        //    if (audioManager.isSpeakerphoneOn()) {
                        //        //sendInternalMessage(SPEAKER_ON);
                        //
                        //        Log.e(TAG, "[VIDEOPLUGIN][AUDIO] audioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());
                        //    } else {
                        //        //sendInternalMessage(SPEAKER_OFF);
                        //    }
                        //--------------------------------------------------------------------------
                        Log.w(TAG, "[VIDEOPLUGIN][AUDIO] SpeakerPhoneChangeIntentReceiver.onReceive audioManager.isSpeakerphoneOn():" + audioManager.isSpeakerphoneOn());
                        //--------------------------------------------------------------------------
                    }
                } else {
                    Log.w(TAG, "[VIDEOPLUGIN][AUDIO] SpeakerPhoneChangeIntentReceiver.onReceive not ACTION_SPEAKERPHONE_STATE_CHANGED - skip");
                }
            } finally {
                Log.w(TAG, "[VIDEOPLUGIN][AUDIO] SpeakerPhoneChangeIntentReceiver.onReceive: END");
            }
        }
    }

    //https://github.com/Lennowarweg/Plugin/blob/2d1566049d6ffbe89a6327c1ae127c67e90494d7/app/src/main/java/lenno/plugin/plugin/MainActivity.java

    public class HeadsetIntentReceiver extends BroadcastReceiver {

        public HeadsetIntentReceiver() {

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            //https://developer.android.com/reference/android/content/Intent#ACTION_HEADSET_PLUG

            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                //----------------------------------------------------------------------------------
                Log.w(TAG, "HeadsetIntentReceiver onReceive: state:" + state);
                //----------------------------------------------------------------------------------
                if(0 == state){
                    Log.w(TAG, "HeadsetIntentReceiver onReceive: state:0 - headphone plugged OUT > change icon to SPEAKERPHONE");
                    updateicon_button_fab_switchaudio_tospeaker();

                }
                else if(1 == state){
                    Log.w(TAG, "HeadsetIntentReceiver onReceive: state:1 - headphone plugged IN > change icon to HEADPHONES");
                    updateicon_button_fab_switchaudio_toheadphones();
                }
                else{
                	Log.e(TAG, "ACTION_HEADSET_PLUG >> state is null cant ICON FOR updateicon_button_fab_switchaudio");
                }
                //----------------------------------------------------------------------------------
            }else {
                Log.w(TAG, "[VIDEOPLUGIN][AUDIO] HeadsetIntentReceiver.onReceive Intent not ACTION_HEADSET_PLUG - skip");
            }
        }
    }

    private void update_icon_button_fab_switchaudio(){
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][updateSpeakerButtonIcon]");

        logAudioDeviceInfo();

        if(isHeadphonesPlugged()){
            updateicon_button_fab_switchaudio_toheadphones();

        }else{
            updateicon_button_fab_switchaudio_tospeaker();
        }

    }
    private void updateicon_button_fab_switchaudio_tospeaker(){
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][updateSpeakerButtonIconToSpeakerPhone]");

        int icon = FAKE_R.getDrawable("ic_phonelink_ring_white_24dp");

        button_fab_switchaudio.setImageDrawable(ContextCompat.getDrawable( TwilioVideoActivity.this, icon));

        //fab look n feel managed
        //works but I need grey ic
        //button_fab_switchaudio.setSelected(true);

        button_fab_switchaudio.setEnabled(true);

    }

    private void updateicon_button_fab_switchaudio_toheadphones(){
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][updateSpeakerButtonIconToHeadphones]");

        int icon = FAKE_R.getDrawable("ic_volume_headhphones_white_24dp");

        button_fab_switchaudio.setImageDrawable(ContextCompat.getDrawable( TwilioVideoActivity.this, icon));

        //disable
        //button_fab_switchaudio.setSelected(false);
        button_fab_switchaudio.setEnabled(false);



    }

    private boolean isHeadphonesPlugged(){
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for(AudioDeviceInfo deviceInfo : audioDevices){
            if(deviceInfo.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    deviceInfo.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET)
            {
                return true;
            }
        }
        return false;
    }

    //Debug as we plug in wired and unwrired headset/ bluetooth headphones
    //https://github.com/dengjianzhong/OddPoint/blob/2296ebc93392f8f8c98fa94bc30a4e049bf917b4/libwebrtc/src/main/java/sdk/android/src/java/org/webrtc/audio/WebRtcAudioUtils.java
    private void logAudioDeviceInfo() {
        Log.w(TAG, "[VIDEOPLUGIN][AUDIO][logAudioDeviceInfo]");

        if (Build.VERSION.SDK_INT < 23) {
            Log.w(TAG, "[VIDEOPLUGIN][AUDIO][logAudioDeviceInfo] Build.VERSION.SDK_INT < 23 - SKIP");

        }else{
            if(null != audioManager){
                final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);


                Log.w(TAG, "[VIDEOPLUGIN][AUDIO][logAudioDeviceInfo] Audio Devices:" + devices.length);

                for (AudioDeviceInfo device : devices) {
                    //----------------------------------------------------------------------------------
                    StringBuilder info = new StringBuilder();

                    info.append("  ").append(deviceTypeToString(device.getType()));
                    info.append(device.isSource() ? "(in): " : "(out): ");

                    //----------------------------------------------------------------------------------
                    // An empty array indicates that the device supports arbitrary channel counts.
                    if (device.getChannelCounts().length > 0) {
                        info.append("channels=").append(Arrays.toString(device.getChannelCounts()));
                        info.append(", ");
                    }
                    if (device.getEncodings().length > 0) {
                        // Examples: ENCODING_PCM_16BIT = 2, ENCODING_PCM_FLOAT = 4.
                        info.append("encodings=").append(Arrays.toString(device.getEncodings()));
                        info.append(", ");
                    }
                    if (device.getSampleRates().length > 0) {
                        info.append("sample rates=").append(Arrays.toString(device.getSampleRates()));
                        info.append(", ");
                    }

                    info.append("id=").append(device.getId());

                    String str = info.toString();
                    Log.e(TAG, str );
                }
            }else{
            	Log.e(TAG, "[VIDEOPLUGIN][AUDIO][logAudioDeviceInfo] audioManager is null");
            }

        }


    }
    // Converts AudioDeviceInfo types to local string representation.
    private static String deviceTypeToString(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_UNKNOWN:
                return "TYPE_UNKNOWN";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "TYPE_BUILTIN_EARPIECE";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "TYPE_BUILTIN_SPEAKER";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "TYPE_WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "TYPE_WIRED_HEADPHONES";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "TYPE_LINE_ANALOG";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "TYPE_LINE_DIGITAL";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "TYPE_BLUETOOTH_SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "TYPE_BLUETOOTH_A2DP";
            case AudioDeviceInfo.TYPE_HDMI:
                return "TYPE_HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "TYPE_HDMI_ARC";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "TYPE_USB_DEVICE";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "TYPE_USB_ACCESSORY";
            case AudioDeviceInfo.TYPE_DOCK:
                return "TYPE_DOCK";
            case AudioDeviceInfo.TYPE_FM:
                return "TYPE_FM";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "TYPE_BUILTIN_MIC";
            case AudioDeviceInfo.TYPE_FM_TUNER:
                return "TYPE_FM_TUNER";
            case AudioDeviceInfo.TYPE_TV_TUNER:
                return "TYPE_TV_TUNER";
            case AudioDeviceInfo.TYPE_TELEPHONY:
                return "TYPE_TELEPHONY";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "TYPE_AUX_LINE";
            case AudioDeviceInfo.TYPE_IP:
                return "TYPE_IP";
            case AudioDeviceInfo.TYPE_BUS:
                return "TYPE_BUS";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "TYPE_USB_HEADSET";
            default:
                return "TYPE UNHANDLED ";
        }
    }
}
