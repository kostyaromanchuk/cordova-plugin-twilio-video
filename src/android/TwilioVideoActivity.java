package org.apache.cordova.twiliovideo;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
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
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

import java.util.Collections;
import java.util.List;

import com.squareup.picasso.Picasso;
//DONT USE it works in this POC but when they integrate with the main sea/chat app theres no capacitor so it breaks
//import capacitor.android.plugins.R;


public class TwilioVideoActivity extends AppCompatActivity implements CallActionObserver {
    public static final String TAG = "TwilioVideoActivity";
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

    //----------------------------------------------------------------------------------------------
    //APPLYING BLUR
    private android.widget.FrameLayout video_container;
    private androidx.coordinatorlayout.widget.CoordinatorLayout activity_video_coordinatorlayout;
    private android.widget.LinearLayout blurredviewgroup;

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
    private FloatingActionButton button_fab_audio_onoff;
    private FloatingActionButton button_fab_switchaudio;
    private FloatingActionButton button_fab_switchcamera;
    private FloatingActionButton button_fab_disconnect;


    private AudioManager audioManager;

    private String participantIdentity;

    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private boolean disconnectedFromOnDestroy;
    private VideoRenderer localVideoView;

    //play ringing.mp3
    private MediaPlayer mediaPlayer;

    //if hone near ear - turn of local camera - else remote user sees your ear
    SensorManager mSensorManager;
    Sensor mSensor;

    //remote participant image
    //To pulse the border we has a circle view behind it with border and animate its alpha
    private ImageView imageViewRemoteParticipantWhilstCalling;
    private ImageView imageViewRemoteParticipantWhilstCallingToAnimate;

    private ImageView imageViewRemoteParticipantInCall;

    private ImageView imageViewLocalParticipant1;

    private TextView textViewRemoteParticipantName;
    private TextView textViewRemoteParticipantConnectionState;


    private TextView textViewInCallRemoteName;
    private ImageView imageViewInCallRemoteMicMuteState;

    private Button buttonHiddenSwitchVideo;

    private boolean previewIsFullScreen;

    boolean runAnimation = true;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TwilioVideoManager.getInstance().setActionListenerObserver(this);

        FAKE_R = new FakeR(this);

        publishEvent(CallEvent.OPENED);

        //------------------------------------------------------------------------------------------
        //ContentView
        //------------------------------------------------------------------------------------------
//        setContentView(R.layout.activity_video);
        setContentView(FAKE_R.getLayout("activity_video"));


        activity_video_coordinatorlayout = findViewById(FAKE_R.getId("activity_video_coordinatorlayout"));
        video_container = findViewById(FAKE_R.getId("video_container"));
        blurredviewgroup = findViewById(FAKE_R.getId("blurredviewgroup"));
        bottom_buttons_linearlayout = findViewById(FAKE_R.getId("bottom_buttons_linearlayout"));


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
        button_fab_audio_onoff = findViewById(FAKE_R.getId("mute_action_fab"));

        //FLIP CAMERA
        button_fab_switchcamera = findViewById(FAKE_R.getId("switch_camera_action_fab"));

        //SWITCH AUDIO - SPEAKER/HEADPHONES
        button_fab_switchaudio = findViewById(FAKE_R.getId("switch_audio_action_fab"));

        //RED DISCONNECT BUTTON
        button_fab_disconnect = findViewById(FAKE_R.getId("connect_action_fab"));
        //------------------------------------------------------------------------------------------

        //filled below from url passed in from cordova via intent
        imageViewRemoteParticipantWhilstCalling = findViewById(FAKE_R.getId("imageViewRemoteParticipantWhilstCalling"));
        imageViewRemoteParticipantWhilstCallingToAnimate = findViewById(FAKE_R.getId("imageViewRemoteParticipantWhilstCallingToAnimate"));
        imageViewRemoteParticipantInCall        = findViewById(FAKE_R.getId("imageViewRemoteParticipantInCall"));
        imageViewLocalParticipant1               = findViewById(FAKE_R.getId("imageViewLocalParticipant1"));

        textViewRemoteParticipantName = findViewById(FAKE_R.getId("textViewRemoteParticipantName"));
        textViewRemoteParticipantConnectionState = findViewById(FAKE_R.getId("textViewRemoteParticipantConnectionState"));


        imageViewInCallRemoteMicMuteState = findViewById(FAKE_R.getId("imageViewInCallRemoteMicMuteState"));
        textViewInCallRemoteName = findViewById(FAKE_R.getId("textViewInCallRemoteName"));

        //hidden button no text over thrumbnail tap to flip camera
        buttonHiddenSwitchVideo = findViewById(FAKE_R.getId("buttonHiddenSwitchVideo"));
        buttonHiddenSwitchVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e(TAG, "onClick: buttonHiddenSwitchVideo TODO");

                if (cameraCapturer != null) {
                    CameraSource cameraSource = cameraCapturer.getCameraSource();
                    cameraCapturer.switchCamera();
                    if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                        thumbnailVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    } else {
                        fullScreenVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    }
                }else{
                	Log.e(TAG, "cameraCapturer is null");
                }
            }
        });
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

        hide_inCall_remoteUserNameAndMic();

//        //------------------------------------------------------------------------------------------
//        //PARSE PARAMS FROM Intent
//        //------------------------------------------------------------------------------------------
//        //    P1 CALLER
//        //        - openRoom
//        //            - startActivity
//        //                - onCreate
//        //                    - instance 699
//        //                    - get Intents
//        //                        - action > openRoom
//        //                - onResume
//        //        - startCall
//        //            - startActivity
//        //                - doesnt call onCreate due to FLAG_ACTIVITY_REORDER_TO_FRONT
//        //                - onResume
//        //                    - same instance
//        //                    - ISSUE - Intents parsed in onCreate
//        //    P2 - PERSON BEING CALLED
//        //            - answerCall()
//        //                - startActivity
//        //                    - onCreate
//        //                        - instance 699
//        //                        - get Intents
//        //                        - action > openRoom
//        //                      - onResume
//        //------------------------------------------------------------------------------------------
//        parse_Intents();
//        //------------------------------------------------------------------------------------------

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

// TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
                String local_user_name = intent.getStringExtra("local_user_name");
                String local_user_photo_url = intent.getStringExtra("local_user_photo_url");

                String remote_user_name = intent.getStringExtra("remote_user_name");
                String remote_user_photo_url = intent.getStringExtra("remote_user_photo_url");

                Log.e(TAG, "onCreate: INTENT > action:'"  + action + "' >> CALL this.openRoom()");
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

                Log.e(TAG, "onCreate: INTENT > action:'"  + action + "' >> CALL this.startCall()");
                this.action_startCall(roomId, token);

            }
            else if(action.equals(TwilioVideoActivityNextAction.action_answerCall))
            {
                //----------------------------------------------------------------------------------
                //CORDOVA > answerCall()
                //----------------------------------------------------------------------------------
                Log.e(TAG, "onCreate: INTENT > action:'"  + action + "' >> CALL this.answerCall()");
                String token = intent.getStringExtra("token");
                String roomId = intent.getStringExtra("roomId");

                // TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
                String local_user_name = intent.getStringExtra("local_user_name");
                String local_user_photo_url = intent.getStringExtra("local_user_photo_url");

                String remote_user_name = intent.getStringExtra("remote_user_name");
                String remote_user_photo_url = intent.getStringExtra("remote_user_photo_url");



                //needed for flow after onRequestPermissionsResult
                this.action_current = TwilioVideoActivityNextAction.action_answerCall;

                this.action_answerCall(roomId, token, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url);

            }
            else{
                Log.e(TAG, "UNHANDLED action:" + action);

                //needed for onRequestPermissionsResult
                this.action_current = null;
            }
        }else{
            Log.e(TAG, "action is null");
        }
    }

    private void onCreate_configureAudio(){

        //Enable changing the volume using the up/down keys during a conversation
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        //------------------------------------------------------------------------------------------
        //Needed for setting/abandoning audio focus during call
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//TODO - speakerphone
        audioManager.setSpeakerphoneOn(true);

        //------------------------------------------------------------------------------------------
        //PLAY RING TONE - ringing.mp3
        //------------------------------------------------------------------------------------------
        int id_ringing =  FAKE_R.getResourceId( TwilioVideoActivity.this, "raw", "ringing");

        mediaPlayer = MediaPlayer.create(TwilioVideoActivity.this, id_ringing);

        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            //use .play + pause
            //.play() + .stop() seems to kill it next .play() fails
        }else{
            Log.e(TAG, "methodName: mediaPlayer is null");
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
                        //PHONE is NEAR to  ear - turn off local camera
                        //--------------------------------------------------------------------------
                        Log.e(TAG, "onSensorChanged: NEAR >> localVideoTrack.enable(false)");

                        //--------------------------------------------------------------------------
                        //during openRoom we havent
                        if(null != localVideoTrack){
                            localVideoTrack.enable(false);
                        }else{
                        	Log.e(TAG, "localVideoTrack is null");
                        }


                        //--------------------------------------------------------------------------
                        //WRAPPER - ALWAYS VISIBLE
                        ///thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);
                        //thumbnailVideoViewFrameLayout.bringToFront();

                        thumbnailVideoView.setVisibility(View.INVISIBLE);
                        imageViewLocalParticipant1.setVisibility(View.VISIBLE);
                        //imageViewLocalParticipant1.bringToFront();

                        //BACKGROUND is the solid fill in border.xml
                        //BORDER is stroke in border.xml
                        //--------------------------------------------------------------------------

                    } else {
                        //--------------------------------------------------------------------------
                        //PHONE is AWAY from  ear
                        //--------------------------------------------------------------------------
                        Log.e(TAG, "onSensorChanged: AWAY >> localVideoTrack.enable(true)");
                        //--------------------------------------------------------------------------

                        if(null != localVideoTrack){
                            localVideoTrack.enable(true);
                        }else{
                            Log.e(TAG, "localVideoTrack is null");
                        }
                        //--------------------------------------------------------------------------
                        //WRAPPER - ALWAYS VISIBLE
                        //thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

                        thumbnailVideoView.setVisibility(View.VISIBLE);
                        imageViewLocalParticipant1.setVisibility(View.INVISIBLE);
                        //imageViewLocalParticipant.bringToFront();
                        //--------------------------------------------------------------------------
                    }
                }
            }
        };

        //------------------------------------------------------------------------------------------
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        //------------------------------------------------------------------------------------------
        if (mSensor == null) {
            Log.e(TAG, "onCreate_configureProximitySensor: mSensor is null - cant access Phone Proximity Sensor");
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

        Log.e(TAG, "action_openRoom: STARTED");


        this.roomId = room;
        this.accessToken = token;

// TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
        this.local_user_name = localUserName;
        this.local_user_photo_url = localUserPhotoURL;

        this.remote_user_name = remoteUserName;
        this.remote_user_photo_url = remoteUserPhotoURL;


        showRoomUI(true);

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------
        Log.d(TwilioVideo.TAG, "BEFORE REQUEST PERMISSIONS");
        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TwilioVideo.TAG, "REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TwilioVideo.TAG, "PERMISSIONS OK");
            permissionOk();
        }
        //------------------------------------------------------------------------------------------
    }


    private void action_startCall(  String room,
                            String token)
    {
        Log.e(TAG, "action_startCall: STARTED");

        this.roomId = room;
        this.accessToken = token;

        //------------------------------------------------------------------------------------------
        //enable mic button
        this.showRoomUI(true);

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------

        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TwilioVideo.TAG, "REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TwilioVideo.TAG, "PERMISSIONS OK");
            permissionOk();
        }
        //------------------------------------------------------------------------------------------

    }

    private void action_answerCall( String room,
                                    String token,
                                    String localUserName,
                                    String localUserPhotoURL,
                                    String remoteUserName,
                                    String remoteUserPhotoURL)
    {
        Log.e(TAG, "action_answerCall: STARTED");

        this.roomId = room;
        this.accessToken = token;


        // TODO: 15/12/2020 CHECK FOR JS NULL > NSNull
        this.local_user_name = localUserName;
        this.local_user_photo_url = localUserPhotoURL;

        this.remote_user_name = remoteUserName;
        this.remote_user_photo_url = remoteUserPhotoURL;

        //------------------------------------------------------------------------------------------
        //enable mic button
        this.showRoomUI(true);

        //------------------------------------------------------------------------------------------
        //CAMERA PERMISSIONS
        //------------------------------------------------------------------------------------------
        //moved from onCreate
        //called by openRoom and answerRoom
        //------------------------------------------------------------------------------------------

        if (!hasPermissionForCameraAndMicrophone()) {

            Log.d(TwilioVideo.TAG, "REQUEST PERMISSIONS");
            requestPermissions();
            //if ok will call permissionOk()

        } else {
            Log.d(TwilioVideo.TAG, "PERMISSIONS OK");
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
                TwilioVideoActivity.this.handleConnectionError(config.getI18nConnectionError());
                //----------------------------------------------------------------------------------
            }
        }
    }

    private void permissionOk(){
        if(this.action_current.equals(TwilioVideoActivityNextAction.action_openRoom)){

            //--------------------------------------------------------------------------------------
            //SETUP LOCAL CAMERA AND AUDIO
            //--------------------------------------------------------------------------------------
            createAudioAndVideoTracks();

            //--------------------------------------------------------------------------------------
            //p1 - wait for P2 to call room.connect() then backend will send startCall() to P1
            //--------------------------------------------------------------------------------------
            displayCallWaiting();

        }
        else if(this.action_current.equals(TwilioVideoActivityNextAction.action_startCall))
        {
            setupLocalCamera_ifnull();
            connectToRoom();

        }
        else if(this.action_current.equals(TwilioVideoActivityNextAction.action_answerCall))
        {
            createAudioAndVideoTracks();
            setupLocalCamera_ifnull();

            showHideBlurView(true);

            connectToRoom();

        }
        else{
            Log.e(TAG, "UNHANDLED this.action_current:" + this.action_current);
        }
    }




    //----------------------------------------------------------------------------------------------
    //UI
    //----------------------------------------------------------------------------------------------
    // Reset the client ui status
    private void showRoomUI(boolean inRoom) {
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




//    //border is animated so I cheated and added it to a UIView wraping the remote image view then I animate the alpha whilst Calling..
//    //viewWrapperAnimatedBorder is exact ame frame as imageViewRemoteParticipant
//    //coregraphic border will be outside this frame
//    private void addFakeBordersToRemoteImageView(){
//        Log.e(TAG, "addFakeBordersToRemoteImageView: TODO" );
//        //        this.viewWrapperAnimatedBorder0.layer.cornerRadius = this.viewWrapperAnimatedBorder0.frame.size.height / 2.0;
//    }
//
//    private void animateAlphaBorderForViews_ShowBorder(){
//        Log.e(TAG, "animateAlphaBorderForViews_ShowBorder: TODO" );
//        //TODO
//        // [this.viewWrapperAnimatedBorder0 setHidden:FALSE];
//    }

    //Disconnected... just hide the animation is not stopped
    private void animateAlphaBorderForViews_HideBorder(){
        Log.e(TAG, "animateAlphaBorderForViews_HideBorder" );

        hide_imageViewRemoteParticipantWhilstCallingToAnimate();
    }

    private void animateAlphaBorderForViews(){

        if(null != this.imageViewRemoteParticipantWhilstCallingToAnimate){
            animateView_start(this.imageViewRemoteParticipantWhilstCallingToAnimate);
        }else{
            Log.e(TAG, "this.imageViewRemoteParticipantWhilstCallingToAnimate is null - cant animate");
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
        runAnimation = !runAnimation;
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
                            //Log.e(TAG, "animateView_fadeOut - runAnimation is true - CALL animateView_fadeIn");
                            animateView_fadeIn(view, duration);
                        }else{
                            Log.e(TAG, "animateView_fadeOut - runAnimation is false - STOP ANIMATION");
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
                            //Log.e(TAG, "animateView_fadeIn - runAnimation is true - CALL animateView_fadeOut");
                            animateView_fadeOut(view,duration);
                        }else{
                            Log.e(TAG, "animateView_fadeIn - runAnimation is false - STOP ANIMATION");
                        }
                    }
                });
    }








    private void fillIn_viewRemoteParticipantInfo(){

        if (this.remote_user_name != null) {
            this.textViewRemoteParticipantName.setText(this.remote_user_name);
            this.textViewInCallRemoteName.setText(this.remote_user_name);
        }else{
            Log.e(TAG, "instance initializer: this.remoteUserName is NULL");
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
    private void fill_imageView_LocalParticipant(){
        //DEBUG DO NOT RELEASE - self.localUserPhotoURL = NULL;

        this.loadUserImageInBackground_async(this.local_user_photo_url, this.imageViewLocalParticipant1);

    }
    
    private void loadUserImageInBackground_async(String userPhotoURL, ImageView imageView){
        //TODO add border around image

        //------------------------------------------------------------------------------------------
        //Fill imageView async
        //------------------------------------------------------------------------------------------
        //"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"
        //------------------------------------------------------------------------------------------

        if (userPhotoURL != null) {
            if (imageView != null) {
                Picasso.get().load(userPhotoURL).into(imageView);

            }else{
                Log.e(TAG, "loadUserImageInBackground_async: imageView is null");
            }
        }else{
            Log.e(TAG, "loadUserImageInBackground_async: userPhotoURL is null");
        }
    }


    //Rounded Image - with border
    //https://android--examples.blogspot.com/2015/11/android-how-to-create-circular.html
    private RoundedBitmapDrawable createRoundedBitmapDrawableWithBorder(Bitmap bitmap){
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        int borderWidthHalf = 10; // In pixels
        //Toast.makeText(mContext,""+bitmapWidth+"|"+bitmapHeight,Toast.LENGTH_SHORT).show();

        // Calculate the bitmap radius
        int bitmapRadius = Math.min(bitmapWidth,bitmapHeight)/2;

        int bitmapSquareWidth = Math.min(bitmapWidth,bitmapHeight);
        //Toast.makeText(mContext,""+bitmapMin,Toast.LENGTH_SHORT).show();

        int newBitmapSquareWidth = bitmapSquareWidth+borderWidthHalf;
        //Toast.makeText(mContext,""+newBitmapMin,Toast.LENGTH_SHORT).show();

        Bitmap roundedBitmap = Bitmap.createBitmap(newBitmapSquareWidth,newBitmapSquareWidth,Bitmap.Config.ARGB_8888);

        // Initialize a new Canvas to draw empty bitmap
        Canvas canvas = new Canvas(roundedBitmap);

        //fill canvas
        canvas.drawColor(Color.RED);

        // Calculation to draw bitmap at the circular bitmap center position
        int x = borderWidthHalf + bitmapSquareWidth - bitmapWidth;
        int y = borderWidthHalf + bitmapSquareWidth - bitmapHeight;

        canvas.drawBitmap(bitmap, x, y, null);

        // Initializing a new Paint instance to draw circular border
        Paint borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        //borderPaint.setStrokeWidth(borderWidthHalf*2);//too thick
        borderPaint.setStrokeWidth(5.0f);
        borderPaint.setColor(Color.WHITE);

        canvas.drawCircle(canvas.getWidth()/2, canvas.getWidth()/2, newBitmapSquareWidth/2, borderPaint);

        RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(),roundedBitmap);

        roundedBitmapDrawable.setCornerRadius(bitmapRadius);
        roundedBitmapDrawable.setAntiAlias(true);

        return roundedBitmapDrawable;
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
            Log.e(TAG, "show_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCalling is null");
        }

        if (this.imageViewRemoteParticipantWhilstCallingToAnimate != null) {
            animateAlphaBorderForViews();
            this.imageViewRemoteParticipantWhilstCallingToAnimate.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "show_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCallingToAnimate is null");
        }
    }
    private void hide_imageViewRemoteParticipantWhilstCalling(){
        if (this.imageViewRemoteParticipantWhilstCalling != null) {
            this.imageViewRemoteParticipantWhilstCalling.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCalling is null");
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
            Log.e(TAG, "hide_imageViewRemoteParticipantWhilstCalling: imageViewRemoteParticipantWhilstCallingToAnimate is null");
        }
    }


    //----------------------------------------------------------------------------------------------
    //SHOW/HIDE each control
    private void show_imageViewRemoteParticipantInCall(){
        if (this.imageViewRemoteParticipantInCall != null) {
            this.imageViewRemoteParticipantInCall.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "show_imageViewRemoteParticipantInCall: imageViewRemoteParticipant is null");
        }

    }
    private void hide_imageViewRemoteParticipantInCall(){
        if (this.imageViewRemoteParticipantInCall != null) {
            this.imageViewRemoteParticipantInCall.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_imageViewRemoteParticipantInCall: imageViewRemoteParticipant is null");
        }
    }

    //--------------------------------------------------------------------------------------
    //REMOTE use has toggle camera ON so show them in the full screen view
    private void show_fullScreenVideoView(){
        if (this.fullScreenVideoView != null) {
            this.fullScreenVideoView.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "fullScreenVideoView: remoteVideoView is null");
        }
    }
    //REMOTE use has toggle camera OFF so fullscreenview is frozen so hide it
    private void hide_fullScreenVideoView(){
        if (this.fullScreenVideoView != null) {
            this.fullScreenVideoView.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_remoteVideoView: remoteVideoView is null");
        }
    }


    //----------------------------------------------------------------------------------------------
    //show_textViewRemoteParticipantName
    private void show_textViewRemoteParticipantName(){
        if (this.textViewRemoteParticipantName != null) {
            this.textViewRemoteParticipantName.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "show_textViewRemoteParticipantName: textViewRemoteParticipantName is null");
        }
    }
    private void hide_textViewRemoteParticipantName(){
        if (this.textViewRemoteParticipantName != null) {
            this.textViewRemoteParticipantName.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_textViewRemoteParticipantName: textViewRemoteParticipantName is null");
        }
    }

    private void show_textViewRemoteParticipantConnectionState(){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "show_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
    }
    private void hide_textViewRemoteParticipantConnectionState(){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
    }
    private void textViewRemoteParticipantConnectionState_setText(String state){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setText(state);
        }else{
            Log.e(TAG, "hide_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
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
    //MINI VIEW BORDER
    //----------------------------------------------------------------------------------------------
    private void addBorderToPreview(){
        Log.e(TAG, "addBorderToPreview: TODO" );
    }
    private void removeBorderFromPreview(){
        Log.e(TAG, "removeBorderFromPreview: TODO" );
    }


    //In Call - NAME and MIC state

    private void hide_inCall_remoteUserNameAndMic(){

        //DONT use GONE - it moves the buttons down then jumps when it reappears
        this.imageViewInCallRemoteMicMuteState.setVisibility(View.INVISIBLE);
        this.textViewInCallRemoteName.setVisibility(View.INVISIBLE);


    }

    private void show_inCall_remoteUserNameAndMic_isMuted(boolean micIsMuted){

        this.imageViewInCallRemoteMicMuteState.setVisibility(View.VISIBLE);
        this.textViewInCallRemoteName.setVisibility(View.VISIBLE);

    
        this.update_imageViewInCallRemoteMicMuteState_isMuted(micIsMuted);
    }

    private void update_imageViewInCallRemoteMicMuteState_isMuted(boolean micIsMuted){
        if(micIsMuted){
            //--------------------------------------------------------------------------------------
            //DONT USE R its //import capacitor.android.plugins.R;
            //will break in sea/chat as theres no capacitor
            //imageViewInCallRemoteMicMuteState.setImageResource(R.drawable.ic_mic_off_red_24px);
            //--------------------------------------------------------------------------------------
            //NOTE - main buttons use ic_mic_off_red_24px - this uses white
            imageViewInCallRemoteMicMuteState.setImageResource(FAKE_R.getDrawable("ic_mic_off_white_24px"));

        }else{
            //--------------------------------------------------------------------------------------
            //DONT USE R its //import capacitor.android.plugins.R;
            //will break in sea/chat as theres no capacitor
            //imageViewInCallRemoteMicMuteState.setImageResource(R.drawable.ic_mic_green_24px);
            //--------------------------------------------------------------------------------------
            imageViewInCallRemoteMicMuteState.setImageResource(FAKE_R.getDrawable("ic_mic_green_24px"));
            //--------------------------------------------------------------------------------------

        }
    }












    //------------------------------------------------------------------------------------------
    //DELEGATE > UPDATE UI
    //------------------------------------------------------------------------------------------

    //On the ANSWERING PHONE it will trigger
    //didConnectToRoom_AnswerACall only
    private void didConnectToRoom_AnswerACall(){
        Log.e(TAG, "didConnectToRoom_AnswerACall: START");
        
        if(this.previewIsFullScreen){
            this.show_viewRemoteParticipantInfoWithState("Connecting...");

            this.animateAlphaBorderForViews();

        }else{
            Log.e(TAG, "didConnectToRoom_AnswerACall: new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }




//    private void shrinkPreview(){
//        //------------------------------------------------------------------------------------------
//        //android.widget.FrameLayout video_container = findViewById(FAKE_R.getId("video_container"));
//        //ViewGroup.LayoutParams layoutParams = ((ViewGroup) video_container).getLayoutParams();
//
//        android.widget.FrameLayout thumbnail_video_view = findViewById(FAKE_R.getId("thumbnail_video_view"));
//        ViewGroup.LayoutParams layoutParams = ((ViewGroup) thumbnail_video_view).getLayoutParams();
//
//
//        //setMargins not found - you need to cast it
//        Log.e(TAG, "onClick: layoutParams:" + layoutParams);
//
//        int screen_width_pixels = Resources.getSystem().getDisplayMetrics().widthPixels;
//        int screen_height_pixels = Resources.getSystem().getDisplayMetrics().heightPixels;
//
//        Log.e(TAG, "onClick: screen_width_pixels:" + screen_width_pixels + ",screen_height_pixels:"+ screen_height_pixels );
//        //1080, 2076
//
//        int preview_mini_width = 350;
//        int preview_mini_height = 650;
//        int margin = 64;
//
//
//        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
//            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
//
//
//            int leftMargin = screen_width_pixels - preview_mini_width - margin;
//            int topMargin  = screen_height_pixels - preview_mini_height - margin;
//
//            //OK but sets all
//            marginLayoutParams.setMargins(leftMargin, topMargin, margin, margin * 6); // left, top, right, bottom
//
////// TODO: 27/11/2020 MAY NEED TO CALC margins
////                    call this first
////                        the mic button sets height/width and thsi code does unset it
////                            i pasted this into the plugin
//            //https://developer.android.com/reference/android/view/ViewGroup.MarginLayoutParams
//            //((ViewGroup.MarginLayoutParams) layoutParams).topMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).leftMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).rightMargin = 0;
//
//
//            //video_container.requestLayout();
//
//            thumbnail_video_view.requestLayout();
//        } else{
//            Log.e("MyApp", "Attempted to set the margins on a class that doesn't support margins: video_container");
//        }
//        //------------------------------------------------------------------------------------------
//
//        //java.lang.ClassCastException: androidx.coordinatorlayout.widget.CoordinatorLayout$LayoutParams cannot be cast to android.widget.FrameLayout$LayoutParams
//
////                if (thumbnailVideoView != null) {
////                    ViewGroup.LayoutParams thumbnailVideoView_layoutParams = thumbnailVideoView.getLayoutParams();
////
////                    //thumbnailVideoView_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
////                    //thumbnailVideoView_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
////                    thumbnailVideoView_layoutParams.width = 100;
////                    thumbnailVideoView_layoutParams.height = 200;
////
////                    //layoutParams.setMargins(10, 20, 30, 40);
////
////                    thumbnailVideoView.setLayoutParams(thumbnailVideoView_layoutParams);
////
////                    thumbnailVideoView.requestLayout();
////                } else {
////                    Log.e(TAG, "onClick: thumbnailVideoView is null");
////                }
//        //------------------------------------------------------------------------------------------
//
//        //------------------------------------------------------------------------------------------
//        //                ViewGroup.LayoutParams layoutParams = video_container.getLayoutParams();
//
//        //                layoutParams.setMargins(10, 20, 30, 40);
//
//
////        if(thumbnailVideoView != null){
////            ViewGroup.LayoutParams layoutParams = thumbnailVideoView.getLayoutParams();
////            layoutParams.width = 96;
////            layoutParams.height = 96;
////            thumbnailVideoView.setLayoutParams(layoutParams);
////        }else{
////            Log.e(TAG, "onClick: thumbnailVideoView is null");
////        }
//
//
//    }

//WITH ANIMATIONS - commented out till I add zoomanimation
//    private void update_PreviewView_showInFullScreen(boolean changeToFullScreen,
//                                                     boolean isAnimated,
//                                                     boolean showBlurView)
//    {
//        Log.e(TAG, "update_PreviewView_showInFullScreen: TODO");
////TODO BLURRED VIEW
////        //animation in and out should be same number of secs
////        NSTimeInterval duration = 1.0;
////
////        //When you show "Disconnected.." you dont show the blur - other use has hung up
////        if(showBlurView){
////        [this.uiVisualEffectViewBlur setHidden:FALSE];
////            //if alpha is 0.0 will still be hidden - a;pha is animated below
////        }else{
////        [this.uiVisualEffectViewBlur setHidden(true);
////        }
//
//
//        if(changeToFullScreen){
//
//            if(isAnimated){
//                //------------------------------------------------------------------
//                //FULL SCREEN + ANIMATED
//                //------------------------------------------------------------------
////TODO ANIMATE ZOOM
////                [UIView animateWithDuration:duration
////                    delay:0
////                    options:UIViewAnimationOptionCurveEaseInOut
////                    animations:^{
//                //--------------------------------------------------
//                this.update_PreviewView_toFullScreen(true);
//
//                //--------------------------------------------------
////TODO BLUR
////                        //may still be hidden if uiVisualEffectViewBlur setHidden: not changed above
////                        this.uiVisualEffectViewBlur.alpha = BLURRED_VIEW_ALPHA_ON;
//
//                //--------------------------------------------------
////                        //will resize but animate without this
////                        [this.view layoutIfNeeded];
//                //--------------------------------------------------
////                    }
////                    completion:^(BOOL finished) {
////                        //ANIMATION DONE
//                this.removeBorderFromPreview();
////                    }
////                ];
//
//            }else{
//                //------------------------------------------------------------------
//                //FULL SCREEN + UNANIMATED (when app starts)
//                //------------------------------------------------------------------
//                this.update_PreviewView_toFullScreen(true);
////TODO BLUR ON
////                this.uiVisualEffectViewBlur.alpha = BLURRED_VIEW_ALPHA_ON;
//
//                this.removeBorderFromPreview();
//            }
//        }else{
//            //------------------------------------------------------------------------------------------
//            //MINI VIEW
//            //------------------------------------------------------------------------------------------
//
//            //------------------------------------------------------------------------------------------
//            if(isAnimated){
//                //------------------------------------------------------------------
//                //NOT FULL SCREEN + ANIMATED - (dialing ends shrink preview to bottom right)
//                //------------------------------------------------------------------
////                [UIView animateWithDuration:duration
////                    delay:0
////                    options:UIViewAnimationOptionCurveEaseInOut
////                    animations:^{
//                //-------------------------------------------------
//                this.update_PreviewView_toFullScreen(false);
//                //--------------------------------------------------
////TODO BLUR OFF/alpha 0
////                        this.uiVisualEffectViewBlur.alpha = 0.0;
//                //--------------------------------------------------
//                //will resize but animate without this
////                        [this.view layoutIfNeeded];
////                        //--------------------------------------------------
////                    }
////                    completion:^(BOOL finished) {
//                //DONE
//                this.addBorderToPreview();
////                    }
////                ];
//
//            }else{
//                //------------------------------------------------------------------
//                //NOT FULL SCREEN + UNANIMATED (preview size jumps to bottom right - unused)
//                //------------------------------------------------------------------
//                this.update_PreviewView_toFullScreen(false);
//                //--------------------------------------------------
//                //FADE OUT
////TODO - alpha out
////                this.uiVisualEffectViewBlur.alpha = 0.0;
//                //--------------------------------------------------
//
//                this.addBorderToPreview();
//
//            }
//        }
//
//        //[this.view setNeedsUpdateConstraints];
//    }

    private void showHideBlurView(boolean showBlurView){

        if(showBlurView){
            if(null != this.blurredviewgroup){
                //if alpha is 0.0 will still be hidden - a;pha is animated below
                this.blurredviewgroup.setVisibility(View.VISIBLE);
            }else{
            	Log.e(TAG, "this.blurredviewgroup is null");
            }

            //--------------------------------------------------------------------------------------
            //TODO - works but needs tweaking
            //--------------------------------------------------------------------------------------
            //                        if(null != blurredviewgroup){
            //                            Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(this.blurredviewgroup);
            //
            ////                            Blurry.with(TwilioVideoActivity.this).radius(10)
            ////                                    .sampling(8)
            ////                                    .color(Color.argb(66, 255, 255, 0))
            ////                                    .async()
            ////                                    .animate(500)
            ////                                    .onto(blurredviewgroup);
            //                        }else{
            //                            Log.e(TAG, "blurredviewgroup is null");
            //                        }
            //--------------------------------------------------------------------------------------
        }else{
            if(null != this.blurredviewgroup){
                //if alpha is 0.0 will still be hidden - a;pha is animated below
                this.blurredviewgroup.setVisibility(View.GONE);
            }else{
                Log.e(TAG, "this.blurredviewgroup is null");
            }
        }
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


//    private void update_PreviewView_toFullScreen(boolean fullScreen){
//        if(fullScreen){
////TODO - ZOOM
////            //THESE are linked to SuperView not Layoutguide - may go behind nav bar
////            this.nsLayoutConstraint_previewView_top.constant = 0.0;
////            this.nsLayoutConstraint_previewView_bottom.constant = 0.0;
////
////            this.nsLayoutConstraint_previewView_leading.constant = 0.0;
////            this.nsLayoutConstraint_previewView_trailing.constant = 0.0;
//
//
//
//            this.previewIsFullScreen = true;
//
//
//        }else{
//            //------------------------------------------------------------------------------------------
////TODO ZOOM TO MINI VIEW
//            //------------------------------------------------------------------------------------------
////            CGFloat screen_width = this.view.frame.size.width;
////            CGFloat screen_height = this.view.frame.size.height;
////
////            CGFloat border = 8.0;
////            CGFloat previewView_height_small = 160.0;
////            CGFloat previewView_width_small = 120.0;
////
////            //----------------------------------------------------------------------
////            //BOTTOM
////            //----------------------------------------------------------------------
////            //ISSUE - previewView in full screen ignores Safe Area
////            //BUT viewButtonOuter bottom is calculated from view.safeAreaInsets.bottom
////            //
////            //UIEdgeInsets screen_safeAreaInsets = this.view.safeAreaInsets;
////
////            CGFloat bottom = (this.viewButtonOuter.frame.size.height + this.view.safeAreaInsets.bottom + 8.0);
////
////            this.nsLayoutConstraint_previewView_bottom.constant = bottom;
////
////            //----------------------------------------------------------------------
////            //TOP = BOTTOM + HEIGHT of preview
////            //----------------------------------------------------------------------
////            CGFloat top = screen_height - (bottom + previewView_height_small);
////            this.nsLayoutConstraint_previewView_top.constant = top;
////
////            //----------------------------------------------------------------------
////            //TRAILING
////            //----------------------------------------------------------------------
////            CGFloat trailing = this.view.safeAreaInsets.right + border;
////            this.nsLayoutConstraint_previewView_trailing.constant = trailing;
////
////            //----------------------------------------------------------------------
////            //LEADING
////            //----------------------------------------------------------------------
////            CGFloat leading = screen_width - (trailing + previewView_width_small);
////            this.nsLayoutConstraint_previewView_leading.constant = leading;
//
//            //this.previewView.contentMode = UIViewContentModeScaleAspectFit;
//
//            this.previewIsFullScreen = false;
//
//            //Dont do here the layer is being updated by the animation so this may not work at first - moved up to update_PreviewView_showInFullScreen
//            //
//            //        //didnt work on p1 startCall() but did on p2 answerCall() - animation still happening on layer?
//            //        dispatch_async(dispatch_get_main_queue(), ^{
//            //            this.addBorderToView:this.previewView withColor:[UIColor redColor] borderWidth: 2.0f];
//            //        });
//
//        }
//    }



    private void update_PreviewView_toFullScreen(boolean fullScreen) {
        Log.e(TAG, "update_PreviewView_toFullScreen: START");
        if(fullScreen){

            //------------------------------------------------------------------------------------------
            //FULLSCREEN VIEW
            //------------------------------------------------------------------------------------------

//            if (thumbnailVideoView != null) {
//                ViewGroup.LayoutParams thumbnailVideoView_layoutParams = thumbnailVideoView.getLayoutParams();
//
//                thumbnailVideoView_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
//                thumbnailVideoView_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
//
//                thumbnailVideoView.setLayoutParams(thumbnailVideoView_layoutParams);
//
//                thumbnail_video_view_setmargins(0, 0, 0, 0);
//
//                thumbnailVideoView.requestLayout();
//            } else {
//                Log.e(TAG, "onClick: thumbnailVideoView is null");
//            }

            if (thumbnailVideoViewFrameLayout != null) {
                ViewGroup.LayoutParams thumbnailVideoViewFrameLayout_layoutParams = thumbnailVideoViewFrameLayout.getLayoutParams();

                thumbnailVideoViewFrameLayout_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                thumbnailVideoViewFrameLayout_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

                thumbnailVideoViewFrameLayout.setLayoutParams(thumbnailVideoViewFrameLayout_layoutParams);

                thumbnail_video_view_framelayout_setmargins(0, 0, 0, 0);

                thumbnailVideoViewFrameLayout.requestLayout();
                //imageViewLocalParticipant.requestLayout();
            } else {
                Log.e(TAG, "onClick: thumbnailVideoViewFrameLayout is null");
            }

            this.previewIsFullScreen = true;
        }else{
            //------------------------------------------------------------------------------------------
            //MINI VIEW
            //------------------------------------------------------------------------------------------

            int screen_width_pixels = Resources.getSystem().getDisplayMetrics().widthPixels;
            int screen_height_pixels = Resources.getSystem().getDisplayMetrics().heightPixels;

            Log.e(TAG, "onClick: screen_width_pixels:" + screen_width_pixels + ",screen_height_pixels:"+ screen_height_pixels );
            //1080, 2076

            int preview_mini_width = 350;
            int preview_mini_height = 580;
            int margin = 64;
            int margin_bottom = 364;

            if(null != this.bottom_buttons_linearlayout){
                int[] location = new int[2];
                this.bottom_buttons_linearlayout.getLocationOnScreen(location);


                //    int bottom_buttons_linearlayout_x = location[0];
                //    int bottom_buttons_linearlayout_y = location[1];
                //
                //    int bottom_buttons_linearlayout_top = bottom_buttons_linearlayout_y;

                int leftMargin = screen_width_pixels - preview_mini_width - margin;
                int topMargin  = screen_height_pixels - preview_mini_height - margin_bottom;

                //thumbnail_video_view_setmargins(leftMargin, topMargin, margin, margin * 4);

                //thumbnail_video_view_setmargins(leftMargin, topMargin, margin, margin_bottom);
                thumbnail_video_view_framelayout_setmargins(leftMargin, topMargin, margin, margin_bottom);

                //layout Inspector is in 88dp


            }else{
            	Log.e(TAG, "bottom_buttons_linearlayout is null");
            }





            this.previewIsFullScreen = false;
        }
    }

    //----------------------------------------------------------------------------------------------
//CLEANUP
//    private void thumbnail_video_view_setmargins(int leftmargin, int topMargin, int rightMargin, int bottomMargin){
//        VideoView thumbnail_video_view = findViewById(FAKE_R.getId("thumbnail_video_view"));
//        ViewGroup.LayoutParams layoutParams = thumbnail_video_view.getLayoutParams();
//
//
//        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
//            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
//
//            //OK but sets all
//            marginLayoutParams.setMargins(leftmargin, topMargin, rightMargin, bottomMargin); // left, top, right, bottom
//
//            //https://developer.android.com/reference/android/view/ViewGroup.MarginLayoutParams
//            //((ViewGroup.MarginLayoutParams) layoutParams).topMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).leftMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin = 0;
//            //((ViewGroup.MarginLayoutParams) layoutParams).rightMargin = 0;
//
//            //video_container.requestLayout();
//            thumbnail_video_view.requestLayout();
//        } else{
//            Log.e("MyApp", "Attempted to set the margins on a class that doesn't support margins: video_container");
//        }
//    }


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
            Log.e("MyApp", "Attempted to set the margins on a class that doesn't support margins: video_container");
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
            mediaPlayer.start();
            //--------------------------------------------------------------------------------------
            //use .start() + pause() 
            //not .start() + .stop() seems to kill it, next .play() fails
            //--------------------------------------------------------------------------------------
        }else{
            Log.e(TAG, "methodName: mediaPlayer is null");
        }
    }

    private void dialing_sound_pause(){
        if (mediaPlayer != null) {
            mediaPlayer.pause();
            //use .start() + pause() 
            //not .start() + .stop() seems to kill it, next .play() fails

        }else{
            Log.e(TAG, "methodName: mediaPlayer is null");
        }
    }

    private void dialing_sound_stop(){
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            //use .start() + pause() 
            //not .start() + .stop() seems to kill it, next .play() fails

        }else{
            Log.e(TAG, "methodName: mediaPlayer is null");
        }
    }
    



    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume: STARTED" );


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
        setupLocalCamera_ifnull();
        publishTrack_video(); //needed room.connect not set yet so cant publish??
        //------------------------------------------------------------------------------------------

    }


    //1 - openRooom - setup local camera to show full screen
    //2 - startCall > room.connect > room.localParticipant > publishtrack
    private void setupLocalCamera_ifnull(){
        //If the local video track was released when the app was put in the background, recreate.
        if (localVideoTrack == null) {

            Log.e(TAG, "onResume: localVideoTrack == null >> recreate" );

            if (hasPermissionForCameraAndMicrophone()) {

                Log.e(TAG, "onResume: hasPermissionForCameraAndMicrophone(): TRUE - recreate" );

                if(null != cameraCapturer){
                    //------------------------------------------------------------------------------
                    localVideoTrack = LocalVideoTrack.create(this,
                            true,
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
                    Log.e(TAG, "cameraCapturer is null");
                }

            }else{
                //can happen when openRoom and StartCall both did startActivity
                Log.e(TAG, "hasPermissionForCameraAndMicrophone() FAILED");
            }
        }else{
            //can happen when openRoom and StartCall both did startActivity
            Log.e(TAG, "setupLocalCamera_ifnull - localVideoTrack is OK ");

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

                Log.e(TAG, "onResume: hasPermissionForCameraAndMicrophone(): TRUE - recreate" );

                if (localParticipant != null) {
                    localParticipant.publishTrack(localVideoTrack);
                }else{
                    Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");

                    if(null != room){
                        localParticipant = room.getLocalParticipant();

                        if (localParticipant != null) {
                            localParticipant.publishTrack(localVideoTrack);
                        }else{
                            Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");

                        }
                    }else{
                        Log.e(TAG, "room is null");
                    }
                }

            }else{
                //can happen when openRoom and StartCall both did startActivity
                Log.e(TAG, "hasPermissionForCameraAndMicrophone() FAILED");
            }
        }else{
            //can happen when openRoom and StartCall both did startActivity


            if(null != room){
                localParticipant = room.getLocalParticipant();

                if (localParticipant != null) {
                    localParticipant.publishTrack(localVideoTrack);
                }else{
                    Log.e(TAG, "localVideoTrack is OK - ocalParticipant is null >> wait for room.connect");

                }
            }else{
                Log.e(TAG, "room is null >> wait for room.connect");
            }

        }
    }

    @Override
    protected void onPause() {
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
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {

        /*
         * Always disconnect from the room before leaving the Activity to
         * ensure any memory allocated to the Room resource is freed.
         */
        if (room != null && room.getState() != Room.State.DISCONNECTED) {
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

        publishEvent(CallEvent.CLOSED);

        TwilioVideoManager.getInstance().setActionListenerObserver(null);

        super.onDestroy();
    }

    private boolean hasPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                TwilioVideo.PERMISSIONS_REQUIRED,
                PERMISSIONS_REQUEST_CODE);

    }

    private void createAudioAndVideoTracks() {
        //------------------------------------------------------------------------------------------
        //AUDIO -  Share your microphone
        //------------------------------------------------------------------------------------------

        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);

        if(null != localAudioTrack){
            Log.e(TAG, "localAudioTrack is created ok from  callers audio source");

        }else{
            Log.e(TAG, "localAudioTrack is null - failed to create from  callers audio source");
        }

        //------------------------------------------------------------------------------------------
        //LOCAL CAMERA -  Share your camera
        cameraCapturer = new CameraCapturerCompat(this, getAvailableCameraSource());
        //------------------------------------------------------------------------------------------
        localVideoTrack = LocalVideoTrack.create(this,
                                                 true,
                                                  cameraCapturer.getVideoCapturer(),
                                                  LOCAL_VIDEO_TRACK_NAME);

        if(null != localVideoTrack){
            Log.e(TAG, "localVideoTrack is created ok from camera capture");

        }else{
        	Log.e(TAG, "localVideoTrack is null - failed to create from camera capture");
        }

        this.moveLocalVideoToThumbnailView();
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
        Log.d(TwilioVideo.TAG, "TODO displayCallWaiting");


        if(this.previewIsFullScreen){
            //----------------------------------------------------------------------
            //Show the dialing panel

            this.show_viewRemoteParticipantInfoWithState("Calling...");

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
            Log.e(TAG, "[displayCallWaiting] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }





    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 0 remote - LOCAL USER is person dialing REMOTE participant.
    //Remote hasnt joined the room yet so hasnt answered so show 'Dialing..'
    //On the CALLING PHONE it will trigger
    //participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking
    private void participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom(){
        Log.w(TAG, "[participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] START");
    
        this.dialing_sound_stop();

        if(this.previewIsFullScreen){
            
            //hide Waiting...
            this.hide_viewRemoteParticipantInfo();

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
            Log.w(TAG, "[participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

    private void participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking(){
        Log.w(TAG, "[participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] START");

        this.dialing_sound_stop();

        if(this.previewIsFullScreen){
        
        this.hide_viewRemoteParticipantInfo();

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
        Log.w(TAG, "[participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 1 remote - REMOTE user in room is the other person who started the call
    //LOCAL USER is answering a call so dont show 'Dialing..'
    private void participantDidConnect(){
        Log.w(TAG, "[participantDidConnect] Unused in 1..1 - use for GROUP");
    }

    private void participantDidDisconnect(String remoteParticipant_identity){

        if(this.previewIsFullScreen){
        Log.w(TAG, "[participantDidDisconnect] new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL");

        }else{
            //REMOTE USER DISCONNECTED
            //show the remote user panel with state 'Disconnected'

            //if app running on REMOTE photo will just show white circle no photo
            //this is so Disconnected isnt off center
        
            this.show_viewRemoteParticipantInfoWithState("Disconnected");

            //pulse animation is on repeat forever - just hide the fake border view - I think when in full sea/chat disconnect will close this whole VC
            this.animateAlphaBorderForViews_HideBorder();

            //Zoom the preview from MINI to FULL SCREEN
            //ONLY show BLUR when dialing
            //Here remote has disconnected so dont show blur
        
            this.update_PreviewView_showInFullScreen(true,true, false);
        }
    }
    
    

    private void connectToRoom() {

        configureAudio(true);

        ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                                                                .roomName(this.roomId)
                                                                .enableIceGatheringOnAnyAddressPorts(true);

        /*
         * Add local audio track to connect options to share with participants.
         */
        if (localAudioTrack != null) {
            connectOptionsBuilder.audioTracks(Collections.singletonList(localAudioTrack));
        }else{
            Log.e(TAG, "connectToRoom: localAudioTrack is null");
        }

        //------------------------------------------------------------------------------------------
        //Add local video track to connect options to share with participants.
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }else{
            Log.e(TAG, "connectToRoom: localVideoTrack is null");
        }
        //------------------------------------------------------------------------------------------
        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
        //------------------------------------------------------------------------------------------
    }

    /*
     * The initial state when there is no active conversation.
     */
    private void initializeUI() {

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
        //                	Log.e(TAG, "switchCameraActionFab is null");
        //                }
        //
        //                button_fab_localvideo_onoff.setBackgroundTintList(color);
        //                button_fab_audio_onoff.setBackgroundTintList(color);
        //                button_fab_switchaudio.setBackgroundTintList(color);
        //            }
        //        }else{
        //            Log.e(TAG, "this.config is null");
        //        }
        //------------------------------------------------------------------------------------------



        //------------------------------------------------------------------------------------------
        //DISCONNECT - always RED
        //------------------------------------------------------------------------------------------
//        int colorButtonDisconnect = ContextCompat.getColor(this, R.color.colorButtonDisconnect);
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




        //Doesnt work properly with FABs
        //        button_fab_localvideo_onoff.setBackgroundColor(colorButtonOn);

        //------------------------------------------------------------------------------------------
        ColorStateList colorStateList = createColorStateList(colorButtonSelected, colorButtonUnselected);

        //------------------------------------------------------------------------------------------
        button_fab_localvideo_onoff.setBackgroundTintList(colorStateList);
        button_fab_localvideo_onoff.show();
        button_fab_localvideo_onoff.setOnClickListener(button_localVideo_OnClickListener());

        //------------------------------------------------------------------------------------------
        button_fab_audio_onoff.setBackgroundTintList(colorStateList);
        button_fab_audio_onoff.show();
        button_fab_audio_onoff.setOnClickListener(button_mute_OnClickListener());

        //------------------------------------------------------------------------------------------
        //dont use ColorStateList - same color for all states
        //button_fab_switchaudio.setBackgroundTintList(colorStateList);

        //tint is always blue - if tapped switches to speaker or headphones

        //DONT USE R its capacitor...R
        //button_fab_switchaudio.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.colorButtonUnselected));
        button_fab_switchaudio.setBackgroundTintList(ContextCompat.getColorStateList(this, FAKE_R.getColor("colorButtonUnselected")));



        button_fab_switchaudio.show();
        button_fab_switchaudio.setOnClickListener(button_switchAudio_OnClickListener());


        //------------------------------------------------------------------------------------------
        //DISCONNECT
        //------------------------------------------------------------------------------------------
        button_fab_disconnect.setImageDrawable(ContextCompat.getDrawable(this, FAKE_R.getDrawable("ic_call_end_white_24px")));
        button_fab_disconnect.show();
        button_fab_disconnect.setOnClickListener(button_disconnect_OnClickListener());


        //------------------------------------------------------------------------------------------
        if(null != button_fab_switchcamera){
            button_fab_switchcamera.show();
            button_fab_switchcamera.setOnClickListener(button_switchCamera_OnClickListener());
        }else{
            Log.e(TAG, "switchCameraActionFab is null");
        }
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
    //BUTTON - CAMERA ON/OFF
    //----------------------------------------------------------------------------------------------
    private View.OnClickListener button_localVideo_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, "button_localVideo_OnClickListener.onClick: ");
                //Enable/disable the local video track
                if (localVideoTrack != null) {
                    boolean enabled = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enabled);

                    //------------------------------------------------------------------------------
                    //CHANGE ICON and COLOR
                    //------------------------------------------------------------------------------
                    int icon;

                    if (enabled) {
                        //LOCAL CAMERA IS ON
                        icon = FAKE_R.getDrawable("ic_videocam_green_24px");

                        //WRAPPER - ALWAYS VISIBLE
                        thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);

                        thumbnailVideoView.setVisibility(View.VISIBLE);
                        imageViewLocalParticipant1.setVisibility(View.INVISIBLE);
                        //imageViewLocalParticipant.bringToFront();


                    } else {
                        //LOCAL CAMERA IS OFF
                        icon = FAKE_R.getDrawable("ic_videocam_off_red_24px");

                        //WRAPPER - ALWAYS VISIBLE
                        thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);
                        thumbnailVideoViewFrameLayout.bringToFront();

                        thumbnailVideoView.setVisibility(View.INVISIBLE);
                        imageViewLocalParticipant1.setVisibility(View.VISIBLE);
                        imageViewLocalParticipant1.bringToFront();

                    }

                    button_fab_localvideo_onoff.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));

                    //------------------------------------------------------------------------------
                    //BUTTON BACKGROUND TINT
                    //------------------------------------------------------------------------------
                    //doesnt work properly for FABs
                    //button_fab_localvideo_onoff.setBackgroundColor(colorButton);

                    //colors setup in ColorStateList above
                    //we just need to set state
                    if (button_fab_localvideo_onoff.isSelected()) {
                        button_fab_localvideo_onoff.setSelected(false);
                    }
                    else {
                        button_fab_localvideo_onoff.setSelected(true);
                    }
                    //------------------------------------------------------------------------------
                }else{
                    Log.e(TAG, "onClick: localVideoTrack is null - TODO" );
                }

            }//onClick
        };
    }

    //----------------------------------------------------------------------------------------------
    //BUTTON - AUDIO ON/OFF
    //----------------------------------------------------------------------------------------------
    private View.OnClickListener button_mute_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, "muteClickListener.onClick: ");

                /*
                 * Enable/disable the local audio track. The results of this operation are
                 * signaled to other Participants in the same Room. When an audio track is
                 * disabled, the audio is muted.
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);

                    //------------------------------------------------------------------------------
                    //BUTTON ICON - badly named OFF is grey not red - see svg
                    //------------------------------------------------------------------------------
                    int icon = enable ? FAKE_R.getDrawable("ic_mic_green_24px") : FAKE_R.getDrawable("ic_mic_off_red_24px");

                    button_fab_audio_onoff.setImageDrawable(ContextCompat.getDrawable(TwilioVideoActivity.this, icon));



                    //------------------------------------------------------------------------------
                    //BUTTON BACKGROUND TINT
                    //------------------------------------------------------------------------------
                    //doesnt work properly for FABs
                    //button_fab_localvideo_onoff.setBackgroundColor(colorButton);

                    //colors setup in ColorStateList above
                    //we just need to set state
                    if (button_fab_audio_onoff.isSelected()) {
                        button_fab_audio_onoff.setSelected(false);
                    }
                    else {
                        button_fab_audio_onoff.setSelected(true);
                    }
                    //------------------------------------------------------------------------------

                }else{
                    Log.e(TAG, "onClick: localAudioTrack is null");
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
                if (config.isHangUpInApp()) {
                    // Propagating the event to the web side in order to allow developers to do something else before disconnecting the room
                    publishEvent(CallEvent.HANG_UP);
                } else {
                    onDisconnect();
                }
            }
        };
    }

    //----------------------------------------------------------------------------------------------
    //BUTTON - FLIP FRONT and BACk camera - tap on mini view
    //----------------------------------------------------------------------------------------------
    private View.OnClickListener button_switchCamera_OnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.e(TAG, "onClick: switchCameraClickListener" );

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
                Log.e(TAG, "switchAudioClickListener.onClick: ");
                if (audioManager.isSpeakerphoneOn()) {
                    audioManager.setSpeakerphoneOn(false);
                } else {
                    audioManager.setSpeakerphoneOn(true);

                }
                int icon = audioManager.isSpeakerphoneOn() ?
                        FAKE_R.getDrawable("ic_phonelink_ring_white_24dp") : FAKE_R.getDrawable("ic_volume_headhphones_white_24dp");
                button_fab_switchaudio.setImageDrawable(ContextCompat.getDrawable(
                        TwilioVideoActivity.this, icon));
                //----------------------------------------------------------------------------------
//DO NOT RELEASE - DEBUG triggers startCall
//                publishEvent(CallEvent.DEBUGSTARTACALL);
                //----------------------------------------------------------------------------------
                //applyBlur();
                //----------------------------------------------------------------------------------

            }
        };
    }

    private void applyBlur(){

        //----------------------------------------------------------------------------------
        //v1 - apply Blurry to Twilio VideoView
        //WRONG - Can only apply Blurry to a Viewgroup
        //VideoView is a twilio class
        //----------------------------------------------------------------------------------
        //                if(null != primaryVideoView){
        //                    //Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(blurredviewgroup);
        //
        //                    Blurry.with(TwilioVideoActivity.this).radius(10)
        //                            .sampling(8)
        //                            .color(Color.argb(66, 255, 255, 0))
        //                            .async()
        //                            .animate(500)
        //                            .onto(primaryVideoView);
        //                }else{
        //                    Log.e(TAG, "primaryVideoView is null");
        //                }
        //----------------------------------------------------------------------------------
        //v2 - apply blur to Viewgroup above videoview
        //----------------------------------------------------------------------------------
//                if(null != blurredviewgroup){
//                    //Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(blurredviewgroup);
//
//                            Blurry.with(TwilioVideoActivity.this).radius(10)
//                                    .sampling(8)
//                                    .color(Color.argb(66, 255, 255, 0))
//                                    .async()
//                                    .animate(500)
//                                    .onto(blurredviewgroup);
//                }else{
//                    Log.e(TAG, "blurredviewgroup is null");
//                }
        //----------------------------------------------------------------------------------
//                if(null != video_container){
//                    Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(video_container);
//
////                    Blurry.with(TwilioVideoActivity.this).radius(10)
////                            .sampling(8)
////                            .color(Color.argb(66, 255, 255, 0))
////                            .async()
////                            .animate(500)
////                            .onto(video_container);
//                }else{
//                    Log.e(TAG, "video_container is null");
//                }
        //----------------------------------------------------------------------------------
//                if(null != activity_video_coordinatorlayout){
//                    Blurry.with(TwilioVideoActivity.this).radius(25).sampling(2).onto(activity_video_coordinatorlayout);
//
////                    Blurry.with(TwilioVideoActivity.this).radius(10)
////                            .sampling(8)
////                            .color(Color.argb(66, 255, 255, 0))
////                            .async()
////                            .animate(500)
////                            .onto(video_container);
//                }else{
//                    Log.e(TAG, "activity_video_coordinatorlayout is null");
//                }
        //----------------------------------------------------------------------------------

    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch
            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                                        }
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
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

        Log.e(TAG, "moveLocalVideoToThumbnailView: STARTED" );

        if (thumbnailVideoView.getVisibility() == View.GONE) {
            Log.e(TAG, "moveLocalVideoToThumbnailView: thumbnailVideoView.setVisibility(View.VISIBLE)" );

            thumbnailVideoViewFrameLayout.setVisibility(View.VISIBLE);
////  PUTBACKTUES IF YOU COMMENT THIS OUT YOU GET THAT CYAN BACKGROUND I THINKG thumbnailVideoView is fullscreen on startup
            thumbnailVideoView.setVisibility(View.VISIBLE);

//  PUTBACKTUES
            imageViewLocalParticipant1.setVisibility(View.INVISIBLE);

            //--------------------------------------------------------------------------------------
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(fullScreenVideoView);
                localVideoTrack.addRenderer(thumbnailVideoView);
            }else{
                Log.e(TAG, "moveLocalVideoToThumbnailView: localVideoTrack is null");
            }
            //--------------------------------------------------------------------------------------
            if (localVideoView != null && thumbnailVideoView != null) {
                localVideoView = thumbnailVideoView;
            }else{
                Log.e(TAG, "moveLocalVideoToThumbnailView: localVideoView != null && thumbnailVideoView != null FAILED");
            }
            //--------------------------------------------------------------------------------------
            //flip camera on horizontal axis?
            thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() == CameraSource.FRONT_CAMERA);
            //--------------------------------------------------------------------------------------
            Log.e(TAG, "moveLocalVideoToThumbnailView: DEBUG");
        }else{
            Log.e(TAG, "moveLocalVideoToThumbnailView: thumbnailVideoView.getVisibility() not View.GONE - skip");
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
                Log.e(TAG, "Room.Listener onConnected: ");

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
                        Log.e(TAG, "Room.Listener onConnected: room.remoteParticipants count is 0 >> LOCAL USER is STARTING A 1..1 CALL");

                        //--------------------------------------------------------------------------
                        didConnectToRoom_AnswerACall();

                        //--------------------------------------------------------------------------

                    }else if (remoteParticipants.size() == 1) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
                        //----------------------------------------------------------------------
                        Log.e(TAG, "Room.Listener onConnected: room.remoteParticipants count:%lu >> LOCAL USER is CONNECTING TO ROOM AFTER REMOTE for A 1..1 CALL");
                        //----------------------------------------------------------------------
                        participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking();
                    }
                    else {
                        Log.e(TAG, "remoteParticipants.size() > 1 - GROUP CALL NOT HANDLED IN v1");
                    }
                }else{
                    Log.e(TAG, "methodName: remoteParticipants is null");
                }
            }

            @Override
            public void onConnectFailure(Room room, TwilioException e) {
                Log.e(TAG, "Room.Listener onConnectFailure: ");

                publishEvent(CallEvent.CONNECT_FAILURE, TwilioVideoUtils.convertToJSON(e));
                TwilioVideoActivity.this.handleConnectionError(config.getI18nConnectionError());
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException e) {
                Log.e(TAG, "Room.Listener onReconnecting: ");

                publishEvent(CallEvent.RECONNECTING, TwilioVideoUtils.convertToJSON(e));
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                Log.e(TAG, "Room.Listener onReconnected: ");

                publishEvent(CallEvent.RECONNECTED);
            }

            @Override
            public void onDisconnected(Room room, TwilioException e) {
                //----------------------------------------------------------------------------------
                //ROOM - onDisconnected
                //----------------------------------------------------------------------------------
                Log.e(TAG, "Room.Listener onDisconnected: ");


                localParticipant = null;
                TwilioVideoActivity.this.room = null;

                // Only reinitialize the UI if disconnect was not called from onDestroy()
                if (!disconnectedFromOnDestroy && e != null) {
                    publishEvent(CallEvent.DISCONNECTED_WITH_ERROR, TwilioVideoUtils.convertToJSON(e));
                    TwilioVideoActivity.this.handleConnectionError(config.getI18nDisconnectedWithError());

                } else {
                    publishEvent(CallEvent.DISCONNECTED);
                }
            }

            @Override
            public void onParticipantConnected(Room room, RemoteParticipant participant) {
                //----------------------------------------------------------------------------------
                //PARTICIPANT - onParticipantConnected
                //----------------------------------------------------------------------------------

                Log.e(TAG, "Room.Listener onParticipantConnected: ");

                publishEvent(CallEvent.PARTICIPANT_CONNECTED);
                addRemoteParticipant(participant);

                //----------------------------------------------------------------------------------
                final List<RemoteParticipant> remoteParticipants = room.getRemoteParticipants();

                if (remoteParticipants != null) {

                    if (remoteParticipants.isEmpty()) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - no remote users so I an STARTING A CALL
                        //----------------------------------------------------------------------
                        Log.e(TAG, "onParticipantConnected: oom.remoteParticipants count:0 >> LOCAL USER is STARTING A 1..1 CALL");
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
                        Log.e(TAG, "onParticipantConnected: room.remoteParticipants count:1 >>  room.remoteParticipants count:%lu >> REMOTE USER is ANSWERING A 1..1 CALL >> participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom");
                        //----------------------------------------------------------------------
                        participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom();
                    }
                    else {
                        Log.e(TAG, "remoteParticipants.size() > 1 - GROUP CALL NOT HANDLED IN v1");
                    }
                }else{
                    Log.e(TAG, "methodName: remoteParticipants is null");
                }
            }

            @Override
            public void onParticipantDisconnected(Room room, RemoteParticipant participant) {
                Log.e(TAG, "Room.Listener onParticipantDisconnected: ");

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
                Log.e(TAG, "Room.Listener onRecordingStarted: ");
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TwilioVideo.TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(Room room) {
                Log.e(TAG, "Room.Listener onRecordingStopped: ");
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TwilioVideo.TAG, "onRecordingStopped");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {

            @Override
            public void onAudioTrackPublished(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "RemoteParticipant.Listener onAudioTrackPublished: ");

                Log.e(TwilioVideo.TAG, String.format("onAudioTrackPublished: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onAudioTrackUnpublished: ");

                Log.i(TwilioVideo.TAG, String.format("onAudioTrackUnpublished: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onAudioTrackSubscribed: ");

                Log.i(TwilioVideo.TAG, String.format("onAudioTrackSubscribed: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onAudioTrackSubscriptionFailed: ");


                Log.i(TwilioVideo.TAG, String.format("onAudioTrackSubscriptionFailed: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onAudioTrackUnsubscribed: ");


                Log.i(TwilioVideo.TAG, String.format("onAudioTrackUnsubscribed: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onVideoTrackPublished: ");


                Log.i(TwilioVideo.TAG, String.format("onVideoTrackPublished: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onVideoTrackUnpublished: ");


                Log.i(TwilioVideo.TAG, String.format("onVideoTrackUnpublished: " +
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
                Log.e(TAG, "RemoteParticipant.Listener onVideoTrackSubscribed: ");

                Log.i(TwilioVideo.TAG, String.format("onVideoTrackSubscribed: " +
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
                	Log.e(TAG, "remoteVideoTrack.isEnabled():TRUE");
                    hide_fullScreenVideoView();
                    show_imageViewRemoteParticipantInCall();
                }

            }

            @Override
            public void onVideoTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication, TwilioException twilioException) {
                Log.e(TAG, "RemoteParticipant.Listener onVideoTrackSubscriptionFailed: ");

                Log.e(TwilioVideo.TAG, String.format("onVideoTrackSubscriptionFailed: " +
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
                Log.e(TwilioVideo.TAG, String.format("onVideoTrackUnsubscribed: " +
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
                Log.e(TwilioVideo.TAG, String.format("onDataTrackPublished: " +
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
                Log.e(TwilioVideo.TAG, String.format("onDataTrackUnpublished: " +
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
                Log.e(TwilioVideo.TAG, String.format("onDataTrackSubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onDataTrackSubscriptionFailed(RemoteParticipant remoteParticipant, RemoteDataTrackPublication remoteDataTrackPublication, TwilioException twilioException) {
                Log.i(TwilioVideo.TAG, String.format("onDataTrackSubscriptionFailed: " +
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
                Log.e(TwilioVideo.TAG, String.format("onDataTrackUnsubscribed: " +
                                "[RemoteParticipant: identity=%s], " +
                                "[RemoteDataTrack: enabled=%b, name=%s]",
                        remoteParticipant.getIdentity(),
                        remoteDataTrack.isEnabled(),
                        remoteDataTrack.getName()));
            }

            @Override
            public void onAudioTrackEnabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "onAudioTrackEnabled: CALLED" );

                show_inCall_remoteUserNameAndMic_isMuted(false);
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "onAudioTrackDisabled: CALLED" );

                show_inCall_remoteUserNameAndMic_isMuted(true);
            }
            //--------------------------------------------------------------------------------------
            //OTHER USER TURNS VIDEO / ON OFF - hide the video feed and show remote users photo
            //--------------------------------------------------------------------------------------
            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.e(TAG, "onVideoTrackEnabled: CALLED" );

                show_fullScreenVideoView();

                hide_imageViewRemoteParticipantInCall();
                //see also Room.Listener onParticipantDisconnected:
                //see also onVideoTrackSubscribed
            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.e(TAG, "onVideoTrackDisabled: CALLED" );

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
        if (config.isHandleErrorInApp()) {
            Log.i(TwilioVideo.TAG, "Error handling disabled for the plugin. This error should be handled in the hybrid app");
            this.finish();
            return;
        }
        Log.i(TwilioVideo.TAG, "Connection error handled by the plugin");
//no alert in plugin let main app handle it
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setMessage(message)
//                .setCancelable(false)
//                .setPositiveButton(config.getI18nAccept(), new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        TwilioVideoActivity.this.finish();
//                    }
//                });
//        AlertDialog alert = builder.create();
//        alert.show();
    }


    @Override
    public void onDisconnect() {
        /*
         * Disconnect from room
         */
        if (room != null) {
            room.disconnect();
        }
        //if user pressed RED disconnect button while mp3 is playing it will keep playing till app killed
        dialing_sound_stop();

        finish();
    }

    @Override
    public void finish() {
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

}
