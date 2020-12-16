package org.apache.cordova.twiliovideo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
     */
    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;

    /*
     * Android application UI elements
     */
    private CameraCapturerCompat cameraCapturer;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;

    private FloatingActionButton connectActionFab;
    private FloatingActionButton switchCameraActionFab;
    private FloatingActionButton localVideoActionFab;
    private FloatingActionButton muteActionFab;
    private FloatingActionButton switchAudioActionFab;
    private AudioManager audioManager;
    private String participantIdentity;

    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private boolean disconnectedFromOnDestroy;
    private VideoRenderer localVideoView;

    //play ringing.mp3
    private MediaPlayer mediaPlayer;


    //remote participant image
    private ImageView imageViewRemoteParticipant;
    // TODO: 16/12/20   imageViewLocalParticipant
    private ImageView imageViewLocalParticipant;

    private TextView textViewRemoteParticipantName;
    private TextView textViewRemoteParticipantConnectionState;

    private boolean previewIsFullScreen;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TwilioVideoManager.getInstance().setActionListenerObserver(this);

        FAKE_R = new FakeR(this);

        publishEvent(CallEvent.OPENED);

        //------------------------------------------------------------------------------------------
        //ContentView
        //------------------------------------------------------------------------------------------
        setContentView(FAKE_R.getLayout("activity_video"));

        primaryVideoView = findViewById(FAKE_R.getId("primary_video_view"));
        thumbnailVideoView = findViewById(FAKE_R.getId("thumbnail_video_view"));

        connectActionFab = findViewById(FAKE_R.getId("connect_action_fab"));
        switchCameraActionFab = findViewById(FAKE_R.getId("switch_camera_action_fab"));
        localVideoActionFab = findViewById(FAKE_R.getId("local_video_action_fab"));
        muteActionFab = findViewById(FAKE_R.getId("mute_action_fab"));
        switchAudioActionFab = findViewById(FAKE_R.getId("switch_audio_action_fab"));

        //filled below from url passed in from cordova via intent
        imageViewRemoteParticipant = findViewById(FAKE_R.getId("imageViewRemoteParticipant"));
        textViewRemoteParticipantName = findViewById(FAKE_R.getId("textViewRemoteParticipantName"));
        textViewRemoteParticipantConnectionState = findViewById(FAKE_R.getId("textViewRemoteParticipantConnectionState"));


        //------------------------------------------------------------------------------------------
        //AUDIO
        //------------------------------------------------------------------------------------------
        onCreate_configureAudio();

        //------------------------------------------------------------------------------------------
        //LOCAL CAMERA VIEW
        //------------------------------------------------------------------------------------------
        //must call before handling Intents
        setupPreviewView();

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
        this.hide_imageViewRemoteParticipant();
        this.hide_textViewRemoteParticipantName();
        this.hide_textViewRemoteParticipantConnectionState();


        if (textViewRemoteParticipantName != null) {
            textViewRemoteParticipantName.setText(this.remote_user_name);
        }else{
            Log.e(TAG, "methodName: textViewRemoteParticipantName is null");
        }
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
        Log.e(TAG, "showRoomUI: hide mic button");

//TODO        this.micButton.hidden = !inRoom;

        this.fillIn_viewRemoteParticipantInfo();
    }
    private void setupPreviewView(){
        //HIDE till my camera connected
        this.hide_viewRemoteParticipantInfo();

        //set it always to Fill so it looks ok in fullscreen
        //I tried changing it to Fit/Fill but jumps at the end when it zooms in
        //this.previewView.contentMode = UIViewContentModeScaleAspectFill;

        //DEBUG - when video is full screen is may have wrong AspectFit or AspectFil

        this.update_PreviewView_showInFullScreen(true, false, true);
    }




    //border is animated so I cheated and added it to a UIView wraping the remote image view then I animate the alpha whilst Calling..
    //viewWrapperAnimatedBorder is exact ame frame as imageViewRemoteParticipant
    //coregraphic border will be outside this frame
    private void addFakeBordersToRemoteImageView(){
        Log.e(TAG, "addFakeBordersToRemoteImageView: TODO" );
        //        this.viewWrapperAnimatedBorder0.layer.cornerRadius = this.viewWrapperAnimatedBorder0.frame.size.height / 2.0;
    }

    private void animateAlphaBorderForViews_ShowBorder(){
        Log.e(TAG, "animateAlphaBorderForViews_ShowBorder: TODO" );
        //TODO
        // [this.viewWrapperAnimatedBorder0 setHidden:FALSE];
    }

    //Disconnected... just hide the animation is not stopped
    private void animateAlphaBorderForViews_HideBorder(){
        Log.e(TAG, "animateAlphaBorderForViews_HideBorder: TODO" );
        //[this.viewWrapperAnimatedBorder0 setHidden:TRUE];
    }

    private void animateAlphaBorderForViews(){
        Log.e(TAG, "animateAlphaBorderForViews: TODO" );
//        this.animateAlphaBorderForViews_ShowBorder();
//
//        this.viewWrapperAnimatedBorder0.alpha = 0.0; //fade in so start at 0, then animate to alpha 1 below
//
//        //----------------------------------------------------------------------------------------------
//        //same duration but 0 has delayed start
//        NSTimeInterval duration = 2.0;
//        //----------------------------------------------------------------------------------------------
//
//        UIViewAnimationOptions options = UIViewAnimationOptionCurveEaseOut | UIViewAnimationOptionRepeat | UIViewAnimationOptionAutoreverse;
//
//        //----------------------------------------------------------------------------------------------
//        [UIView animateWithDuration:duration
//            delay:0         //START immediately
//            options:options
//            animations:^{
//                this.viewWrapperAnimatedBorder0.alpha = 1.0;  //FADE IN - Autoreverse then means fade out - pulse
//            }
//            completion:^(BOOL finished) {
//
//            }
//        ];
//        //----------------------------------------------------------------------------------------------

    }
    



    private void fillIn_viewRemoteParticipantInfo(){

        if (this.remote_user_name != null) {
            this.textViewRemoteParticipantName.setText(this.remote_user_name);
        }else{
            Log.e(TAG, "instance initializer: this.remoteUserName is NULL");
            this.textViewRemoteParticipantName.setText("");
        }

        fill_imageView_RemoteParticipant();

        //text set in didConnectToRoom_StartACall*
        this.textViewRemoteParticipantConnectionState.setText("");
    }


    private void fill_imageView_RemoteParticipant(){
        //DEBUG self.remoteUserPhotoURL = NULL;

        this.loadUserImageInBackground_async(this.remote_user_photo_url, this.imageViewRemoteParticipant);

    }

    //when LOCAL user is offline we show their image over the disable camera view
    private void fill_imageView_LocalParticipant(){
        //DEBUG DO NOT RELEASE - self.localUserPhotoURL = NULL;

        this.loadUserImageInBackground_async(this.local_user_photo_url, this.imageViewLocalParticipant);

    }

//    private void loadUserImageInBackground_async(){
//        //TODO add border around image
//        //            this.imageViewRemoteParticipant.backgroundColor = [UIColor whiteColor];
//        //            this.imageViewRemoteParticipant.layer.cornerRadius = this.imageViewRemoteParticipant.frame.size.height / 2.0;
//        //            this.imageViewRemoteParticipant.layer.borderWidth = 4.f;
//        //            this.imageViewRemoteParticipant.layer.borderColor = [[UIColor whiteColor] CGColor];
//
//        //            this.performSelectorInBackground:@selector(loadUserImageInBackground) withObject:nil];
//
//        //------------------------------------------------------------------------------------------
//        //Fill imageView async
//        //------------------------------------------------------------------------------------------
//        //"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"
//        //------------------------------------------------------------------------------------------
//        if (this.remote_user_photo_url != null) {
//            Picasso.get().load(this.remote_user_photo_url).into(imageViewRemoteParticipant);
////CLEANUP after more testing of CircleView library
////            Picasso.get().load(this.remote_user_photo_url)
////                    .resize(96, 96)
////                    .into(imageViewRemoteParticipant, new Callback() {
////                        @Override
////                        public void onSuccess() {
////                            Bitmap imageBitmap = ((BitmapDrawable) imageViewRemoteParticipant.getDrawable()).getBitmap();
////
////                            //v1 circular image
////                            RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
////                            imageDrawable.setCircular(true);
////                            imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
////
////                            Canvas canvas = new Canvas(imageBitmap);
////                            canvas.drawBitmap(imageBitmap, 0, 0, null);
////                            int borderWidth = 5;
////                            Paint borderPaint = new Paint();
////                            borderPaint.setStyle(Paint.Style.STROKE);
////                            borderPaint.setStrokeWidth(borderWidth);
////                            borderPaint.setAntiAlias(true);
////                            borderPaint.setColor(Color.WHITE);
////                            //https://stackoverflow.com/questions/24878740/how-to-use-roundedbitmapdrawable
////                            //int circleDelta = (borderWidth / 2) - DisplayUtility.dp2px(context, 1);
////                            int radius = (canvas.getWidth() / 2);  // - circleDelta;
////                            canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, radius, borderPaint);
////
////
////
////                            imageViewRemoteParticipant.setImageDrawable(imageDrawable);
////
////                            //v2 with border
////                            //RoundedBitmapDrawable rbd = createRoundedBitmapDrawableWithBorder(imageBitmap);
////                            //imageViewRemoteParticipant.setImageDrawable(rbd);
////
////
////                        }
////                        @Override
////                        public void onError(Exception e) {
////                            //imageViewRemoteParticipant.setImageResource(R.drawable.default_image);
////                        }
////                    });
//
//        }else{
//            Log.e(TAG, "onCreate: imageViewRemoteParticipant is null");
//        }
//    }


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
        show_imageViewRemoteParticipant();
        show_textViewRemoteParticipantName();
        show_textViewRemoteParticipantConnectionState();

        textViewRemoteParticipantConnectionState_setText(state);
    }

    private void hide_viewRemoteParticipantInfo(){
        hide_imageViewRemoteParticipant();
        hide_textViewRemoteParticipantName();
        hide_textViewRemoteParticipantConnectionState();
        //clear it
        textViewRemoteParticipantConnectionState_setText("");
    }

    //------------------------------------------------------------------------------------------
    //SHOW/HIDE each control
    private void show_imageViewRemoteParticipant(){
        if (this.imageViewRemoteParticipant != null) {
            this.imageViewRemoteParticipant.setVisibility(View.VISIBLE);
        }else{
            Log.e(TAG, "show_imageViewRemoteParticipant: imageViewRemoteParticipant is null");
        }
    }
    private void hide_imageViewRemoteParticipant(){
        if (this.imageViewRemoteParticipant != null) {
            this.imageViewRemoteParticipant.setVisibility(View.GONE);
        }else{
            Log.e(TAG, "hide_imageViewRemoteParticipant: imageViewRemoteParticipant is null");
        }
    }

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


    private void addBorderToPreview(){
        Log.e(TAG, "addBorderToPreview: TODO" );
    }
    private void removeBorderFromPreview(){
        Log.e(TAG, "removeBorderFromPreview: TODO" );
    }














    //------------------------------------------------------------------------------------------
    //DELEGATE > UPDATE UI
    //------------------------------------------------------------------------------------------
    
    private void didConnectToRoom_StartACall(){
        Log.e(TAG, "didConnectToRoom_StartACall: START");

        if(this.previewIsFullScreen){
            //----------------------------------------------------------------------
            //Show the dialing panel
    
            this.fillIn_viewRemoteParticipantInfo();
        
            this.show_viewRemoteParticipantInfoWithState("Calling...");

            //----------------------------------------------------------------------
            //show LOCAL USER full screen while waiting for other user to answer
            this.update_PreviewView_showInFullScreen(false, true, true);
            //----------------------------------------------------------------------
            //FIRST TIME VERY LOUD - cant set volume to 0
            //NEXT TIMES too quiet
            //will start it before room connect in viewDidLoad
            this.dialing_sound_start();
            //----------------------------------------------------------------------

        }else{
            Log.e(TAG, "didConnectToRoom_StartACall: new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

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


    private void show_inCall_remoteUserNameAndMic_isMuted(boolean micIsMuted){
        Log.e(TAG, "show_inCall_remoteUserNameAndMic_isMuted: TODO");

//            [this.imageViewInCallRemoteMicMuteState setHidden:FALSE];
//    [this.textViewInCallRemoteName setHidden:FALSE];
//
//    this.update_imageViewInCallRemoteMicMuteState_isMuted:micIsMuted];
    }




    private void shrinkPreview(){
        //------------------------------------------------------------------------------------------
        //android.widget.FrameLayout video_container = findViewById(FAKE_R.getId("video_container"));
        //ViewGroup.LayoutParams layoutParams = ((ViewGroup) video_container).getLayoutParams();

        android.widget.FrameLayout thumbnail_video_view = findViewById(FAKE_R.getId("thumbnail_video_view"));
        ViewGroup.LayoutParams layoutParams = ((ViewGroup) thumbnail_video_view).getLayoutParams();


        //setMargins not found - you need to cast it
        Log.e(TAG, "onClick: layoutParams:" + layoutParams);

        int screen_width_pixels = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screen_height_pixels = Resources.getSystem().getDisplayMetrics().heightPixels;

        Log.e(TAG, "onClick: screen_width_pixels:" + screen_width_pixels + ",screen_height_pixels:"+ screen_height_pixels );
        //1080, 2076

        int preview_mini_width = 350;
        int preview_mini_height = 650;
        int margin = 64;


        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;


            int leftMargin = screen_width_pixels - preview_mini_width - margin;
            int topMargin  = screen_height_pixels - preview_mini_height - margin;

            //OK but sets all
            marginLayoutParams.setMargins(leftMargin, topMargin, margin, margin * 6); // left, top, right, bottom

//// TODO: 27/11/2020 MAY NEED TO CALC margins
//                    call this first
//                        the mic button sets height/width and thsi code does unset it
//                            i pasted this into the plugin
            //https://developer.android.com/reference/android/view/ViewGroup.MarginLayoutParams
            //((ViewGroup.MarginLayoutParams) layoutParams).topMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).leftMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin = 0;
            //((ViewGroup.MarginLayoutParams) layoutParams).rightMargin = 0;


            //video_container.requestLayout();

            thumbnail_video_view.requestLayout();
        } else{
            Log.e("MyApp", "Attempted to set the margins on a class that doesn't support margins: video_container");
        }
        //------------------------------------------------------------------------------------------

        //java.lang.ClassCastException: androidx.coordinatorlayout.widget.CoordinatorLayout$LayoutParams cannot be cast to android.widget.FrameLayout$LayoutParams

//                if (thumbnailVideoView != null) {
//                    ViewGroup.LayoutParams thumbnailVideoView_layoutParams = thumbnailVideoView.getLayoutParams();
//
//                    //thumbnailVideoView_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
//                    //thumbnailVideoView_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
//                    thumbnailVideoView_layoutParams.width = 100;
//                    thumbnailVideoView_layoutParams.height = 200;
//
//                    //layoutParams.setMargins(10, 20, 30, 40);
//
//                    thumbnailVideoView.setLayoutParams(thumbnailVideoView_layoutParams);
//
//                    thumbnailVideoView.requestLayout();
//                } else {
//                    Log.e(TAG, "onClick: thumbnailVideoView is null");
//                }
        //------------------------------------------------------------------------------------------

        //------------------------------------------------------------------------------------------
        //                ViewGroup.LayoutParams layoutParams = video_container.getLayoutParams();

        //                layoutParams.setMargins(10, 20, 30, 40);


//        if(thumbnailVideoView != null){
//            ViewGroup.LayoutParams layoutParams = thumbnailVideoView.getLayoutParams();
//            layoutParams.width = 96;
//            layoutParams.height = 96;
//            thumbnailVideoView.setLayoutParams(layoutParams);
//        }else{
//            Log.e(TAG, "onClick: thumbnailVideoView is null");
//        }


    }

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
////        [this.uiVisualEffectViewBlur setHidden:TRUE];
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

    private void update_PreviewView_showInFullScreen(boolean changeToFullScreen, boolean isAnimated, boolean showBlurView) {

        //animation in and out should be same number of secs
//        NSTimeInterval duration = 1.0;

        //When you show "Disconnected.." you dont show the blur - other use has hung up
        if(showBlurView){
//TODO BLUR VIEW ON
//            [this.uiVisualEffectViewBlur setHidden:FALSE];
            //if alpha is 0.0 will still be hidden - a;pha is animated below
        }else{
//TODO BLUR VIEW OFF
//            [this.uiVisualEffectViewBlur setHidden:TRUE];
        }

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

             this.update_PreviewView_toFullScreen(true);

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

                this.update_PreviewView_toFullScreen(false);

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

            if (thumbnailVideoView != null) {
                ViewGroup.LayoutParams thumbnailVideoView_layoutParams = thumbnailVideoView.getLayoutParams();

                thumbnailVideoView_layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                thumbnailVideoView_layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;

                thumbnailVideoView.setLayoutParams(thumbnailVideoView_layoutParams);

                thumbnail_video_view_setmargins(0, 0, 0, 0);

                thumbnailVideoView.requestLayout();
            } else {
                Log.e(TAG, "onClick: thumbnailVideoView is null");
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
            int preview_mini_height = 650;
            int margin = 64;
            int leftMargin = screen_width_pixels - preview_mini_width - margin;
            int topMargin  = screen_height_pixels - preview_mini_height - margin;

            thumbnail_video_view_setmargins(leftMargin, topMargin, margin, margin * 3);

            this.previewIsFullScreen = false;
        }
    }

    private void thumbnail_video_view_setmargins(int leftmargin, int topMargin, int rightMargin, int bottomMargin){
        VideoView thumbnail_video_view = findViewById(FAKE_R.getId("thumbnail_video_view"));
        ViewGroup.LayoutParams layoutParams = thumbnail_video_view.getLayoutParams();


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
            thumbnail_video_view.requestLayout();
        } else{
            Log.e("MyApp", "Attempted to set the margins on a class that doesn't support margins: video_container");
        }
    }









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
// TODO: 16/12/20 CLEANUP
//    private void setupLocalCamera(){
//        /*
//         * If the local video track was released when the app was put in the background, recreate.
//         */
//        if (localVideoTrack == null) {
//
//            Log.e(TAG, "onResume: localVideoTrack == null >> recreate" );
//
//            if (hasPermissionForCameraAndMicrophone()) {
//
//                Log.e(TAG, "onResume: hasPermissionForCameraAndMicrophone(): TRUE - recreate" );
//
//                if(null != cameraCapturer){
//                    localVideoTrack = LocalVideoTrack.create(this,
//                            true,
//                            cameraCapturer.getVideoCapturer(),
//                            LOCAL_VIDEO_TRACK_NAME);
//                    localVideoTrack.addRenderer(thumbnailVideoView);
//
//                    /*
//                     * If connected to a Room then share the local video track.
//                     */
//                    if (localParticipant != null) {
//                        localParticipant.publishTrack(localVideoTrack);
//                    }else{
//                        Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");
//                    }
//                }else{
//                    //happened when openRoom and StartCall both did startActivity
//                    Log.e(TAG, "cameraCapturer is null");
//                }
//
//            }else{
//                //can happen when openRoom and StartCall both did startActivity
//                Log.e(TAG, "hasPermissionForCameraAndMicrophone() FAILED");
//            }
//        }else{
//            //can happen when openRoom and StartCall both did startActivity
//            Log.e(TAG, "localVideoTrack is OK ");
//
//
//            //MUST CALL publishTrack else other user doesnt see the camera
//            /*
//             * If connected to a Room then share the local video track.
//             */
//            if (localParticipant != null) {
//                localParticipant.publishTrack(localVideoTrack);
//            }else{
//                Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");
//
//                if(null != room){
//                    localParticipant = room.getLocalParticipant();
//
//                    if (localParticipant != null) {
//                        localParticipant.publishTrack(localVideoTrack);
//                    }else{
//                        Log.e(TAG, "localParticipant is null >> publishTrack(localVideoTrack) FAILED");
//
//                    }
//                }else{
//                    Log.e(TAG, "room is null");
//                }
//            }
//        }
//    }

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
    //didConnectToRoom_StartACall >> participantDidConnect_RemoteUserHasAnswered
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
            this.update_PreviewView_showInFullScreen(false, true, true);

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
        this.update_PreviewView_showInFullScreen(false, true, true);


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
        setDisconnectAction();

        if(null != this.config){
            if (config.getPrimaryColorHex() != null) {
                int primaryColor = Color.parseColor(config.getPrimaryColorHex());
                ColorStateList color = ColorStateList.valueOf(primaryColor);
                connectActionFab.setBackgroundTintList(color);
            }

            if (config.getSecondaryColorHex() != null) {
                int secondaryColor = Color.parseColor(config.getSecondaryColorHex());
                ColorStateList color = ColorStateList.valueOf(secondaryColor);
                switchCameraActionFab.setBackgroundTintList(color);
                localVideoActionFab.setBackgroundTintList(color);
                muteActionFab.setBackgroundTintList(color);
                switchAudioActionFab.setBackgroundTintList(color);
            }
        }else{
            Log.e(TAG, "this.config is null");
        }


        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(button_switchCamera_OnClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(button_localVideo_OnClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(button_mute_OnClickListener());
        switchAudioActionFab.show();
        switchAudioActionFab.setOnClickListener(button_switchAudio_OnClickListener());
    }

    /*
     * The actions performed during disconnect.
     */
    private void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this, FAKE_R.getDrawable("ic_call_end_white_24px")));
        connectActionFab.show();
        connectActionFab.setOnClickListener(button_disconnect_OnClickListener());
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
        primaryVideoView.setVisibility(View.VISIBLE);
        primaryVideoView.setMirror(false);
        videoTrack.addRenderer(primaryVideoView);
    }

    private void moveLocalVideoToThumbnailView() {
        Log.e(TAG, "moveLocalVideoToThumbnailView: STARTED" );
        if (thumbnailVideoView.getVisibility() == View.GONE) {

            thumbnailVideoView.setVisibility(View.VISIBLE);
            //--------------------------------------------------------------------------------------
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(primaryVideoView);
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
        primaryVideoView.setVisibility(View.GONE);
        videoTrack.removeRenderer(primaryVideoView);
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
            }

            @Override
            public void onAudioTrackDisabled(RemoteParticipant remoteParticipant, RemoteAudioTrackPublication remoteAudioTrackPublication) {
                Log.e(TAG, "onAudioTrackDisabled: CALLED" );
            }

            @Override
            public void onVideoTrackEnabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.e(TAG, "onVideoTrackEnabled: CALLED" );
            }

            @Override
            public void onVideoTrackDisabled(RemoteParticipant remoteParticipant, RemoteVideoTrackPublication remoteVideoTrackPublication) {
                Log.e(TAG, "onVideoTrackDisabled: CALLED" );
            }
        };
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
                /*
                 * Enable/disable the local video track
                 */
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = FAKE_R.getDrawable("ic_videocam_green_24px");
                        switchCameraActionFab.show();
                    } else {
                        icon = FAKE_R.getDrawable("ic_videocam_off_red_24px");
                        switchCameraActionFab.hide();
                    }

                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(TwilioVideoActivity.this, icon));
                }

            }
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
                    int icon = enable ?
                            FAKE_R.getDrawable("ic_mic_green_24px") : FAKE_R.getDrawable("ic_mic_off_red_24px");
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            TwilioVideoActivity.this, icon));
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
                        primaryVideoView.setMirror(cameraSource == CameraSource.BACK_CAMERA);
                    }
                }
//DEBUG triggers startCall
//                publishEvent(CallEvent.DEBUGSTARTACALL);

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
                switchAudioActionFab.setImageDrawable(ContextCompat.getDrawable(
                        TwilioVideoActivity.this, icon));

            }
        };
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
