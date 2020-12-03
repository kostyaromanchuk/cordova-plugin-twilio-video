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
import android.graphics.drawable.BitmapDrawable;
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

import com.squareup.picasso.Callback;
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
    private CallConfig config;

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

    private TextView textViewRemoteParticipantName;
    private TextView textViewRemoteParticipantConnectionState;

    private boolean previewIsFullScreen;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TwilioVideoManager.getInstance().setActionListenerObserver(this);

        FAKE_R = new FakeR(this);

        publishEvent(CallEvent.OPENED);
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



        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);

        //to play ringing.mp3
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
        Intent intent = getIntent();

        this.accessToken = intent.getStringExtra("token");
        this.roomId = intent.getStringExtra("roomId");
        this.config = (CallConfig) intent.getSerializableExtra("config");

        this.remote_user_name = intent.getStringExtra("remote_user_name");
        this.remote_user_photo_url = intent.getStringExtra("remote_user_photo_url");

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





        //------------------------------------------------------------------------------------------

        Log.d(TwilioVideo.TAG, "BEFORE REQUEST PERMISSIONS");
        if (!hasPermissionForCameraAndMicrophone()) {
            Log.d(TwilioVideo.TAG, "REQUEST PERMISSIONS");
            requestPermissions();
        } else {
            Log.d(TwilioVideo.TAG, "PERMISSIONS OK. CREATE LOCAL MEDIA");
            createAudioAndVideoTracks();
            connectToRoom();
        }

        /*
         * Set the initial state of the UI
         */
        initializeUI();
        setupPreviewView();
    }

    private void setupPreviewView(){
        //HIDE till my camera connected
        this.hide_viewRemoteParticipantInfo();

        //set it always to Fill so it looks ok in fullscreen
        //I tried changing it to Fit/Fill but jumps at the end when it zooms in
        //self.previewView.contentMode = UIViewContentModeScaleAspectFill;

        //DEBUG - when video is full screen is may have wrong AspectFit or AspectFil

        this.updateConstraints_PreviewView_toFullScreen(true, false);
    }



    private void fillIn_viewRemoteParticipantInfo(){

        if (this.remote_user_name != null) {
            this.textViewRemoteParticipantName.setText(this.remote_user_name);
        }else{
            Log.e(TAG, "instance initializer: this.remoteUserName is NULL");
            this.textViewRemoteParticipantName.setText("");
        }

        this.loadUserImageInBackground_async();

        //text set in didConnectToRoom_StartACall*
        this.textViewRemoteParticipantConnectionState.setText("");
    }
    

    private void loadUserImageInBackground_async(){
        //TODO add border around image
        //            self.imageViewRemoteParticipant.backgroundColor = [UIColor whiteColor];
        //            self.imageViewRemoteParticipant.layer.cornerRadius = self.imageViewRemoteParticipant.frame.size.height / 2.0;
        //            self.imageViewRemoteParticipant.layer.borderWidth = 4.f;
        //            self.imageViewRemoteParticipant.layer.borderColor = [[UIColor whiteColor] CGColor];

        //            [self performSelectorInBackground:@selector(loadUserImageInBackground) withObject:nil];

        //------------------------------------------------------------------------------------------
        //Fill imageView async
        //------------------------------------------------------------------------------------------
        //"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"
        //------------------------------------------------------------------------------------------
        if (this.remote_user_photo_url != null) {
            //Picasso.get().load(this.remote_user_photo_url).into(imageViewRemoteParticipant);

            Picasso.get().load(this.remote_user_photo_url)
                    .resize(96, 96)
                    .into(imageViewRemoteParticipant, new Callback() {
                        @Override
                        public void onSuccess() {
                            Bitmap imageBitmap = ((BitmapDrawable) imageViewRemoteParticipant.getDrawable()).getBitmap();

                            //v1 circular image
                            RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                            imageDrawable.setCircular(true);
                            imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);

                            Canvas canvas = new Canvas(imageBitmap);
                            canvas.drawBitmap(imageBitmap, 0, 0, null);
                            int borderWidth = 5;
                            Paint borderPaint = new Paint();
                            borderPaint.setStyle(Paint.Style.STROKE);
                            borderPaint.setStrokeWidth(borderWidth);
                            borderPaint.setAntiAlias(true);
                            borderPaint.setColor(Color.WHITE);
                            //https://stackoverflow.com/questions/24878740/how-to-use-roundedbitmapdrawable
                            //int circleDelta = (borderWidth / 2) - DisplayUtility.dp2px(context, 1);
                            int radius = (canvas.getWidth() / 2);  // - circleDelta;
                            canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, radius, borderPaint);



                            imageViewRemoteParticipant.setImageDrawable(imageDrawable);

                            //v2 with border
                            //RoundedBitmapDrawable rbd = createRoundedBitmapDrawableWithBorder(imageBitmap);
                            //imageViewRemoteParticipant.setImageDrawable(rbd);


                        }
                        @Override
                        public void onError(Exception e) {
                            //imageViewRemoteParticipant.setImageResource(R.drawable.default_image);
                        }
                    });

        }else{
            Log.e(TAG, "onCreate: imageViewRemoteParticipant is null");
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

        set_state_textViewRemoteParticipantConnectionState(state);
    }
    private void hide_viewRemoteParticipantInfo(){
        hide_imageViewRemoteParticipant();
        hide_textViewRemoteParticipantName();
        hide_textViewRemoteParticipantConnectionState();
        //clear it
        set_state_textViewRemoteParticipantConnectionState("");
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
    //show_textViewRemoteParticipantConnectionState
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
    private void set_state_textViewRemoteParticipantConnectionState(String state){
        if (this.textViewRemoteParticipantConnectionState != null) {
            this.textViewRemoteParticipantConnectionState.setText(state);
        }else{
            Log.e(TAG, "hide_textViewRemoteParticipantConnectionState: textViewRemoteParticipantConnectionState is null");
        }
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
            this.updateConstraints_PreviewView_toFullScreen(true, false);
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
            //REMOTE USER CONNECTED
            //Hide the dialing screen
            this.hide_viewRemoteParticipantInfo();

            //Zoom the preview from FULL SCREEN to MINI
            this.updateConstraints_PreviewView_toFullScreen(false, true);

        }else{
            Log.e(TAG, "didConnectToRoom_AnswerACall: new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }


    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 0 remote - LOCAL USER is person dialing REMOTE participant.
    //Remote hasnt joined the room yet so hasnt answered so show 'Dialing..'
    //On the CALLING PHONE it will trigger
    //didConnectToRoom_StartACall >> participantDidConnect_RemoteUserHasAnswered
    private void participantDidConnect_RemoteUserHasAnswered() {
        Log.e(TAG, "participantDidConnect_RemoteUserHasAnswered: START");

        //if you need to restart it use _pause
        this.dialing_sound_stop();

        if(this.previewIsFullScreen){

            this.hide_viewRemoteParticipantInfo();

            //REMOTE user is visible in full screen
            //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
            this.updateConstraints_PreviewView_toFullScreen(false, false);

        }else{
            Log.e(TAG, "participantDidConnect_RemoteUserHasAnswered: new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL");
        }
    }

    //called by TVIRoomDelegate.participantDidConnect
    //Same app installed on both phones but UI changes depending on who starts or answers a call
    //1 local + 1 remote - REMOTE user in room is the other person who started the call
    //LOCAL USER is answering a call so dont show 'Dialing..'

    private void participantDidConnect(String remoteParticipant_identity) {
        Log.e(TAG, "participantDidConnect: START -  Unused in 1..1 - use for GROUP");

        if(this.previewIsFullScreen){
            Log.e(TAG, "participantDidConnect:new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL");

        }else{
            //REMOTE USER DISCONNECTED
            //show the remote user panel with state 'Disconnected'

            //if app running on REMOTE photo will just show white circle no photo
            //this is so Disconnected isnt off center

            this.loadUserImageInBackground_async();

            if (remoteParticipant_identity != null) {
                this.textViewRemoteParticipantName.setText(remoteParticipant_identity);
            }else{
                Log.e(TAG, "remoteParticipant_identity is null");
            }

            this.show_viewRemoteParticipantInfoWithState("Disconnected");

            //Zoom the preview from MINI to FULL SCREEN
            this.updateConstraints_PreviewView_toFullScreen(true, true);

        }
    }

    private void participantDidDisconnect(String remoteParticipant_identity) {
        Log.e(TAG, "participantDidDisconnect: START");

        if(this.previewIsFullScreen){
            Log.e(TAG, "participantDidDisconnect: new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL");

        }else{
            //REMOTE USER DISCONNECTED
            //show the remote user panel with state 'Disconnected'

            //if app running on REMOTE photo will just show white circle no photo
            //this is so Disconnected isnt off center
            this.loadUserImageInBackground_async();
             if (remoteParticipant_identity != null) {
                   this.textViewRemoteParticipantName.setText(remoteParticipant_identity);
             }else{
                 Log.e(TAG, "emoteParticipant_identity is NULL - if LOCAL hangs up before REMOTE then no photo or name just 'Disconnected may show'");
             }

             this.show_viewRemoteParticipantInfoWithState("Disconnected");
             //TODO - should prob hange up

            //Zoom the preview from MINI to FULL SCREEN
            this.updateConstraints_PreviewView_toFullScreen(true, true);
        }
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



    private void updateConstraints_PreviewView_toFullScreen(boolean fullScreen) {
        Log.e(TAG, "updateConstraints_PreviewView_toFullScreen: START");
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

    private void updateConstraints_PreviewView_toFullScreen(boolean fullScreen, boolean isAnimated) {

        //animation in and out should be same number of secs
        //NSTimeInterval duration = 0.3;

        if(fullScreen){
            if(isAnimated){
                //------------------------------------------------------------------
                //FULL SCREEN + ANIMATED
                //------------------------------------------------------------------
//            [UIView animateWithDuration:duration
//                delay:0
//                options:UIViewAnimationOptionCurveEaseInOut
//                animations:^{
//                    //--------------------------------------------------
//                                [self updateConstraints_PreviewView_toFullScreen: TRUE];
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

             this.updateConstraints_PreviewView_toFullScreen(true);

            }else{
                //------------------------------------------------------------------
                //FULL SCREEN + UNANIMATED (when app starts)
                //------------------------------------------------------------------
                this.updateConstraints_PreviewView_toFullScreen(true);

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
//                [self updateConstraints_PreviewView_toFullScreen: FALSE];
//                    //--------------------------------------------------
//                    //will resize but animate without this
//                [this.view layoutIfNeeded];
//                    //--------------------------------------------------
//                }
//                completion:^(BOOL finished) {
//                    //DONE
//                }
//             ];

                this.updateConstraints_PreviewView_toFullScreen(false);

            }else{
                //------------------------------------------------------------------
                //NOT FULL SCREEN + UNANIMATED (preview size jumps to bottom right - unused)
                //------------------------------------------------------------------

                this.updateConstraints_PreviewView_toFullScreen(false);

            }
        }
    }




    //------------------------------------------------------------------------------------------
    //MEDIA PLAYER - ringing.mp3
    //------------------------------------------------------------------------------------------
    //use .play + pause 
    //.play() + .stop() seems to kill it next .play() fails
    
    private void dialing_sound_start(){
        //use .play + pause 
        //.play() + .stop() seems to kill it next .play() fails
        if (mediaPlayer != null) {
            mediaPlayer.start();
            //use .start() + pause() 
            //not .start() + .stop() seems to kill it, next .play() fails
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
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean permissionsGranted = true;

            for (int grantResult : grantResults) {
                permissionsGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (permissionsGranted) {
                createAudioAndVideoTracks();
                connectToRoom();
            } else {
                publishEvent(CallEvent.PERMISSIONS_REQUIRED);
                TwilioVideoActivity.this.handleConnectionError(config.getI18nConnectionError());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        if (localVideoTrack == null && hasPermissionForCameraAndMicrophone()) {
            localVideoTrack = LocalVideoTrack.create(this,
                    true,
                    cameraCapturer.getVideoCapturer(),
                    LOCAL_VIDEO_TRACK_NAME);
            localVideoTrack.addRenderer(thumbnailVideoView);

            /*
             * If connected to a Room then share the local video track.
             */
            if (localParticipant != null) {
                localParticipant.publishTrack(localVideoTrack);
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
        // Share your microphone
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);

        // Share your camera
        cameraCapturer = new CameraCapturerCompat(this, getAvailableCameraSource());
        localVideoTrack = LocalVideoTrack.create(this,
                true,
                cameraCapturer.getVideoCapturer(),
                LOCAL_VIDEO_TRACK_NAME);
        this.moveLocalVideoToThumbnailView();
    }

    private CameraSource getAvailableCameraSource() {
        return (CameraCapturer.isSourceAvailable(CameraSource.FRONT_CAMERA)) ?
                (CameraSource.FRONT_CAMERA) :
                (CameraSource.BACK_CAMERA);
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
            connectOptionsBuilder
                    .audioTracks(Collections.singletonList(localAudioTrack));
        }

        /*
         * Add local video track to connect options to share with participants.
         */
        if (localVideoTrack != null) {
            connectOptionsBuilder.videoTracks(Collections.singletonList(localVideoTrack));
        }

        room = Video.connect(this, connectOptionsBuilder.build(), roomListener());
    }

    /*
     * The initial state when there is no active conversation.
     */
    private void initializeUI() {
        setDisconnectAction();

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

        switchCameraActionFab.show();
        switchCameraActionFab.setOnClickListener(switchCameraClickListener());
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener());
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener());
        switchAudioActionFab.show();
        switchAudioActionFab.setOnClickListener(switchAudioClickListener());
    }

    /*
     * The actions performed during disconnect.
     */
    private void setDisconnectAction() {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this, FAKE_R.getDrawable("ic_call_end_white_24px")));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
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
        if (thumbnailVideoView.getVisibility() == View.GONE) {
            thumbnailVideoView.setVisibility(View.VISIBLE);
            if (localVideoTrack != null) {
                localVideoTrack.removeRenderer(primaryVideoView);
                localVideoTrack.addRenderer(thumbnailVideoView);
            }
            if (localVideoView != null && thumbnailVideoView != null) {
                localVideoView = thumbnailVideoView;
            }
            thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                    CameraSource.FRONT_CAMERA);
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
                Log.e(TAG, "Room.Listener onConnected: ");
                localParticipant = room.getLocalParticipant();
                publishEvent(CallEvent.CONNECTED);

                final List<RemoteParticipant> remoteParticipants = room.getRemoteParticipants();
                if (remoteParticipants != null && !remoteParticipants.isEmpty()) {
                    addRemoteParticipant(remoteParticipants.get(0));
                }

                if (remoteParticipants != null) {
                    if (remoteParticipants.isEmpty()) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - no remote users so I am DIALING the REMOTE USER
                        //----------------------------------------------------------------------
                        Log.e(TAG, "Room.Listener onConnected: room.remoteParticipants count is 0 >> LOCAL USER is STARTING A 1..1 CALL");

                        //----------------------------------------------------------------------
                        didConnectToRoom_StartACall();

                    }else if (remoteParticipants.size() == 1) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
                        //----------------------------------------------------------------------
                        Log.e(TAG, "Room.Listener onConnected: room.remoteParticipants count:%lu >> REMOTE USER is ANSWERING A 1..1 CALL");
                        //----------------------------------------------------------------------
                        didConnectToRoom_AnswerACall();
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
                Log.e(TAG, "Room.Listener onParticipantConnected: ");

                publishEvent(CallEvent.PARTICIPANT_CONNECTED);
                addRemoteParticipant(participant);

                //------------------------------------------------------------------------------------------
                final List<RemoteParticipant> remoteParticipants = room.getRemoteParticipants();

                if (remoteParticipants != null) {

                    if (remoteParticipants.isEmpty()) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - no remote users so I an STARTING A CALL
                        //----------------------------------------------------------------------
                        Log.e(TAG, "onParticipantConnected: oom.remoteParticipants count:0 >> LOCAL USER is STARTING A 1..1 CALL");
                        //----------------------------------------------------------------------
                        //[self participantDidConnect_AnswerACall];
                        //used didConnectToRoom_AnswerACall instead
                        //for GROUP participantDidConnect will do thinks like inc particpant count
                        //show list of users etc
                        //----------------------------------------------------------------------

                    }else if (remoteParticipants.size() == 1) {
                        //----------------------------------------------------------------------
                        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
                        //----------------------------------------------------------------------
                        Log.e(TAG, "onParticipantConnected: room.remoteParticipants count:1 >> REMOTE USER is ANSWERING A 1..1 CALL");
                        //----------------------------------------------------------------------
                        participantDidConnect_RemoteUserHasAnswered();
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

    private View.OnClickListener disconnectClickListener() {
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


    private View.OnClickListener switchCameraClickListener() {
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

            }//onclick

        };//return
    }

    private View.OnClickListener switchAudioClickListener() {
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

    private View.OnClickListener localVideoClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e(TAG, "switchAudioClickListener.onClick: ");
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

    private View.OnClickListener muteClickListener() {
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setCancelable(false)
                .setPositiveButton(config.getI18nAccept(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        TwilioVideoActivity.this.finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
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
