#import "TwilioVideoViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>

// CALL EVENTS
NSString *const OPENED = @"OPENED";
NSString *const CONNECTED = @"CONNECTED";
NSString *const CONNECT_FAILURE = @"CONNECT_FAILURE";
NSString *const DISCONNECTED = @"DISCONNECTED";
NSString *const DISCONNECTED_WITH_ERROR = @"DISCONNECTED_WITH_ERROR";
NSString *const RECONNECTING = @"RECONNECTING";
NSString *const RECONNECTED = @"RECONNECTED";
NSString *const PARTICIPANT_CONNECTED = @"PARTICIPANT_CONNECTED";
NSString *const PARTICIPANT_DISCONNECTED = @"PARTICIPANT_DISCONNECTED";
NSString *const AUDIO_TRACK_ADDED = @"AUDIO_TRACK_ADDED";
NSString *const AUDIO_TRACK_REMOVED = @"AUDIO_TRACK_REMOVED";
NSString *const VIDEO_TRACK_ADDED = @"VIDEO_TRACK_ADDED";
NSString *const VIDEO_TRACK_REMOVED = @"VIDEO_TRACK_REMOVED";
NSString *const PERMISSIONS_REQUIRED = @"PERMISSIONS_REQUIRED";
NSString *const HANG_UP = @"HANG_UP";
NSString *const CLOSED = @"CLOSED";

#define BLURRED_VIEW_ALPHA_ON 0.5
#define VC_VIEW_TAG 6655


#pragma mark - private  

@interface TwilioVideoViewController()<AVAudioPlayerDelegate>{
    BOOL _log_info_on;
    BOOL _log_debug_on;
    BOOL _log_error_on;
    
    //Alexay said turn them off Sea/chat will display them not plugin
    BOOL showNativeUIAlerts;

    //Dialing... tone
    AVAudioPlayer *audioPlayer;

}

//Proximity Monitoring
@property (nonatomic, assign) BOOL localVideoTrack_wasOnBeforeMovedPhoneToEar;

//Constraints animating the ZOOM of Preview from FULLSCREEN to mini in bottom right corner after remote user connects
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_top;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_bottom;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_leading;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_trailing;

//we animate preview to be above this
@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewButtonOuter;
//------------------------------------------------------------------------------------------
//CALLING PANEL - photo and name and Calling.../Disconnected....
@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewRemoteParticipantWhilstCalling;
@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewRemoteParticipantInCall;
@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewLocalParticipant;

@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewWrapperAnimatedBorder0;

@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewRemoteParticipantName;
//Calling.../Disconnected
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewRemoteParticipantConnectionState;

@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewRemoteParticipantInfo;
//------------------------------------------------------------------------------------------
//DURING CALl REMOTE USERS name and muted icon is above buttons
@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewInCallRemoteMicMuteState;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewInCallRemoteName;

//------------------------------------------------------------------------------------------
@property (nonatomic, assign) BOOL previewIsFullScreen;

@property (nonatomic, strong) NSString * localUserName;
@property (nonatomic, strong) NSString * localUserPhotoURL;
@property (nonatomic, strong) NSString * remoteUserName;
@property (nonatomic, strong) NSString * remoteUserPhotoURL;

@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewAlert;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textFieldAlertTitle;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textFieldAlertSubtitle;

@property (unsafe_unretained, nonatomic) IBOutlet UIButton *buttonDebug_showOffline;
@property (unsafe_unretained, nonatomic) IBOutlet UIButton *buttonDebug_showOnline;

@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewAudioWrapper;
@property (unsafe_unretained, nonatomic) IBOutlet UIVisualEffectView *uiVisualEffectViewBlur1;

@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewSwitchVideo;
@property (unsafe_unretained, nonatomic) IBOutlet UIButton *buttonBackToCall;


@end

@implementation TwilioVideoViewController

#pragma mark - UIViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.view.tag = VC_VIEW_TAG;
    
    [self.viewAlert setHidden:TRUE];
    
    //hide UIAlerts in plgugin - will be displayed by Sea/chat main app
    showNativeUIAlerts = FALSE;
    
    //---------------------------------------------------------
    //REMOTE USER PANEL
    //---------------------------------------------------------
    self.textViewRemoteParticipantName.text = @"";
    
    [self textViewRemoteParticipantConnectionState_setText:@""];
    
    [self hide_inCall_remoteUserNameAndMic];
    
    //---------------------------------------------------------
    //when camera off show caller photo - hide this on startup
    [self viewLocalCameraDisabled_hide];
    
    //---------------------------------------------------------
    //this view has border whos alpha will be animated when Calling...
    [self addFakeBordersToRemoteImageView];
    
    //---------------------------------------------------------
    [self placeVolumeIconOverButton];
    
    //---------------------------------------------------------
    [self configureLogging];
    
    
    //---------------------------------------
    //DIALING.. MP3
    //---------------------------------------
    [self dialing_sound_setup];
    //moved from didConnectToRoom_StartACall - run it before room connects
    //[self dialing_sound_start];
    
    //---------------------------------------
    //PREVIEW
    //---------------------------------------
    [self setupPreviewView];
    
    //---------------------------------------
    //DEBUG - draws borders aroud view - handy to track animations
    //---------------------------------------
    //    [self addBorderToView:self.previewView withColor:[UIColor redColor] borderWidth: 1.0f];
    //    [self addBorderToView:self.viewButtonOuter withColor:[UIColor blueColor] borderWidth: 1.0f];
    //    [self addBorderToView:self.imageViewOtherUser withColor:[UIColor whiteColor] borderWidth: 1.0f];
    
     
    //---------------------------------------
    [[TwilioVideoManager getInstance] setActionDelegate:self];
    
    [[TwilioVideoManager getInstance] publishEvent: OPENED];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
    
    [self log_info:[NSString stringWithFormat:@"TwilioVideo v%@", [TwilioVideoSDK sdkVersion]]];
    
    // Configure access token for testing. Create one manually in the console
    // at https://www.twilio.com/console/video/runtime/testing-tools
    self.accessToken = @"TWILIO_ACCESS_TOKEN";
    
    // Preview our local camera track in the local video preview view.
    [self startPreview];
    
    
    //----------------------------------------------------------------------------------------------
    //INIT BUTTONS
    //----------------------------------------------------------------------------------------------
    // Disconnect and mic button will be displayed when client is connected to a room.
    self.micButton.hidden = YES;

    [self setup_button_states];
    
    // Customize button colors
    NSString *primaryColor = [self.config primaryColorHex];
    if (primaryColor != NULL) {
        self.disconnectButton.backgroundColor = [TwilioVideoConfig colorFromHexString:primaryColor];
    }
    
    NSString *secondaryColor = [self.config secondaryColorHex];
    if (secondaryColor != NULL) {
        self.micButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
        self.videoButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
        //self.cameraSwitchButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
    }
    
    //----------------------------------------------------------------------------------------------
    //INIT BUTTONS
    //----------------------------------------------------------------------------------------------
    [self startProximitySensor];
    
    //----------------------------------------------------------------------------------------------
    //REMOTE PHOTO - In call
    [self hide_imageViewRemoteParticipantInCall];
    
    //----------------------------------------------------------------------------------------------
    [self.imageViewSwitchVideo setHidden:TRUE];
    
    //----------------------------------------------------------------------------------------------
    //odd issue we set ENABLED COLOR in code for VIDEO/MIC
    //but Audio source is  view behind - its color is set in IB
    //they look different not sure if something in the button is changing it slightly
    
    self.viewAudioWrapper.backgroundColor = [self button_backGroundColor_enabled];

    //------------------------------------------------------------------------------------------
    //lee said only show remote user is muted - dont show icon if unmoted
    [self.imageViewInCallRemoteMicMuteState setHidden:TRUE];
}


#pragma mark -
#pragma mark Borders
#pragma mark -

//DEBUG - draws line aroudn a UIView
-(void)addBorderToView:(UIView *)view withColor:(UIColor *) color borderWidth:(CGFloat) borderWidth{
    //DEBUG view.layer.borderColor = [[UIColor redColor] CGColor];
    view.layer.borderColor = [color CGColor];
    view.layer.borderWidth = borderWidth; // 1.0f / 0.0f;

    //[view layoutIfNeeded];
}

-(void)removeBorderFromView:(UIView *)view{
    view.layer.borderWidth = 0.0f;
    
}

#pragma mark -
#pragma mark User Image - Default
#pragma mark -

-(void)loadDefaultUserImageIntoImageView:(UIImageView *) imageViewToFill{
    
    if(imageViewToFill){
        
        NSString *imageName = @"baseline_account_circle_black_18dp.png";
        UIImage * defaultImage = [UIImage imageNamed:imageName];
        
        if(defaultImage){
            //imageViewToFill.backgroundColor = [UIColor clearColor];
            imageViewToFill.layer.cornerRadius = self.imageViewRemoteParticipantWhilstCalling.frame.size.height / 2.0;
            
            imageViewToFill.image = defaultImage;
            imageViewToFill.image = defaultImage;
        }else{
            imageViewToFill.image = nil;
            imageViewToFill.image = nil;
        }
    }else{
        [self log_error:@"[loadDefaultUserImageIntoImageView:] imageViewToFill is NULL"];
    }
   
}

//for P2 show whos calling P1
//for P1 show whos is being called P2

//When user turnes local camera off we show their photo
-(void)loadUserImageInBackground_async:(NSString *) userPhotoURL toImageView:(UIImageView *)imageView{
    
    //check NULL inside loadUserImageInBackground as then it should load default image
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        [self loadUserImageInBackground: userPhotoURL toImageView:(UIImageView *)imageView];
    });
}

- (void)loadUserImageInBackground:(NSString *) userPhotoURL toImageView:(UIImageView *)imageViewToUpdate
{
    //NOT THE SAME AS self.remoteUserPhotoURL = NULL;
    //TO TRIGGER - let global_remote_user_photo_url = null;
    // self.remoteUserPhotoURL is not null it's [NSNull null] description :'<null>'
    BOOL userImageLoadedOk = FALSE;
    
    if(NULL != userPhotoURL){
        if([userPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[loadImage] [self.userPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere"];
        }else{
            //--------------------------------------------------------------------------------------
            //not obj-C null or JS nil ()
            //--------------------------------------------------------------------------------------
            NSURL * url = [NSURL URLWithString:userPhotoURL];
            //--------------------------------------------------------------------------------------
            //remoteUserPhotoURL is passed in from cordova - check is valid url can be NSNull no NULL
            if(url){
                NSData * data = [NSData dataWithContentsOfURL:url];
                if(data){
                    UIImage * image = [UIImage imageWithData:data];
                    if (image)
                    {
                        userImageLoadedOk = TRUE;
                        
                        // set image on main thread
                        dispatch_async(dispatch_get_main_queue(), ^{
                            if(imageViewToUpdate){
                                
                                imageViewToUpdate.backgroundColor = [UIColor clearColor];
                                imageViewToUpdate.layer.cornerRadius = imageViewToUpdate.frame.size.height / 2.0;
                                //border is animated so I cheated added it to a UIView wraping the remote image view then I animate the alpha whilst calling

                                imageViewToUpdate.image = image;
                            }else{
                                [self log_error:@"[loadUserImageInBackground:toImageView:] imageViewToUpdate is NULL"];
                            }
                        });
                    }
                    else
                    {
                        // Failed (load an error image?)
                        [self log_error:[NSString stringWithFormat:@"[loadImage] imageWithData failed to load from self.remoteUserPhotoURL:'%@'", userPhotoURL]];
                        //default image loaded below - need to handle all the else clauses in this hierarchy
                    }
                }else{
                    [self log_error:[NSString stringWithFormat:@"[loadImage] dataWithContentsOfURL failed to load from userPhotoURL:'%@'", userPhotoURL]];
                }
            }else{
                [self log_error:[NSString stringWithFormat:@"[loadImage] URLWithString failed to load from userPhotoURL:'%@'", userPhotoURL]];
            }
        }
    }else{
        [self log_error:@"[loadUserImageInBackground] userPhotoURL is NULL"];
    }
    
    //something failed above load default image - need to handle all the else clauses in this hierarchy
    if(userImageLoadedOk){
        //
    }else{
        [self log_error:@"[loadUserImageInBackground:..] userImageLoadedOk is FALSE >> loadDefaultUserImageIntoImageView"];
        dispatch_async(dispatch_get_main_queue(), ^{
            
            [self loadDefaultUserImageIntoImageView: imageViewToUpdate];
            
        });
    }
}

-(void)fillIn_viewRemoteParticipantInfo{

    if (self.remoteUserName) {
        //---------------------------------------------------------
        //shown with photo when calling
        self.textViewRemoteParticipantName.text = self.remoteUserName;
        //---------------------------------------------------------
        //shown above buttons when call active
        self.textViewInCallRemoteName.text = self.remoteUserName;
        //---------------------------------------------------------
    }else{
        [self log_error:@"[fillIn_viewRemoteParticipantInfo] self.remoteUserName is NULL"];
        self.textViewRemoteParticipantName.text = @"";
    }

    [self fill_imageView_RemoteParticipant];
    [self fill_imageView_RemoteParticipantInCall];
    [self fill_imageView_LocalParticipant];
}

//border is animated so I cheated and added it to a UIView wraping the remote image view then I animate the alpha whilst Calling..
//viewWrapperAnimatedBorder is exact ame frame as imageViewRemoteParticipant
//coregraphic border will be outside this frame
-(void)addFakeBordersToRemoteImageView{
    self.viewWrapperAnimatedBorder0.layer.cornerRadius = self.viewWrapperAnimatedBorder0.frame.size.height / 2.0;
}

-(void)animateAlphaBorderForViews_ShowBorder{
    [self.viewWrapperAnimatedBorder0 setHidden:FALSE];
}

//Disconnected... just hide the animation is not stopped
-(void)animateAlphaBorderForViews_HideBorder{
    [self.viewWrapperAnimatedBorder0 setHidden:TRUE];
}

-(void)animateAlphaBorderForViews{
    
    [self animateAlphaBorderForViews_ShowBorder];
    
    self.viewWrapperAnimatedBorder0.alpha = 0.0; //fade in so start at 0, then animate to alpha 1 below
    
    //----------------------------------------------------------------------------------------------
    //same duration but 0 has delayed start
    NSTimeInterval duration = 2.0;
    //----------------------------------------------------------------------------------------------

    UIViewAnimationOptions options = UIViewAnimationOptionCurveEaseOut | UIViewAnimationOptionRepeat | UIViewAnimationOptionAutoreverse;

    //----------------------------------------------------------------------------------------------
    [UIView animateWithDuration:duration
                          delay:0         //START immediately
                        options:options
                     animations:^{
                                self.viewWrapperAnimatedBorder0.alpha = 1.0;  //FADE IN - Autoreverse then means fade out - pulse
                    }
                    completion:^(BOOL finished) {

                    }
    ];
    //----------------------------------------------------------------------------------------------
}

//in answerCall - local and remote urls are reversed
-(void)fill_imageView_RemoteParticipant{
    //DEBUG self.remoteUserPhotoURL = NULL;
    
    [self loadUserImageInBackground_async: self.remoteUserPhotoURL toImageView:self.imageViewRemoteParticipantWhilstCalling];
}
-(void)fill_imageView_RemoteParticipantInCall{
    //DEBUG self.remoteUserPhotoURL = NULL;
    
    [self loadUserImageInBackground_async: self.remoteUserPhotoURL toImageView:self.imageViewRemoteParticipantInCall];
}

//when LOCAL user is offline we show their image over the disable camera view
//in answerCall - local and remote urls are reversed
-(void)fill_imageView_LocalParticipant{
    //DEBUG DO NOT RELEASE - self.localUserPhotoURL = NULL;
    
    [self loadUserImageInBackground_async: self.localUserPhotoURL toImageView:self.imageViewLocalParticipant];
    
}

-(void)show_viewRemoteParticipantInfoWithState:(NSString *) state{
    
    [self.viewRemoteParticipantInfo setHidden:FALSE];
    [self.textViewRemoteParticipantName setHidden:FALSE];
    [self.textViewRemoteParticipantConnectionState setHidden:FALSE];
    [self.textViewRemoteParticipantName setHidden:FALSE];
    
    //----------------------------------------------------------------------------------------------
    //UPDATE CONNECTION STATE
    [self textViewRemoteParticipantConnectionState_setText:state];
    //----------------------------------------------------------------------------------------------
    
    [self.buttonBackToCall setHidden:TRUE];
}

-(void)textViewRemoteParticipantConnectionState_setText:(NSString *) state{
    if(self.textViewRemoteParticipantName != NULL){
        self.textViewRemoteParticipantConnectionState.text = state;
    }else{
        [self log_error:@"self.textViewRemoteParticipantName is NULL"];
    }
}

-(void)hide_viewRemoteParticipantInfo{
    [self.viewRemoteParticipantInfo setHidden:TRUE];

    [self textViewRemoteParticipantConnectionState_setText: @""];
}

#pragma mark -
#pragma mark In Call - NAME and MIC state
#pragma mark -
-(void)hide_inCall_remoteUserNameAndMic{
    
    [self.imageViewInCallRemoteMicMuteState setHidden:TRUE];
    [self.textViewInCallRemoteName setHidden:TRUE];
    
}

-(void)show_inCall_remoteUserNameAndMic_isMuted: (BOOL) micIsMuted{
    
    
    [self.textViewInCallRemoteName setHidden:FALSE];
    
    [self update_imageViewInCallRemoteMicMuteState_isMuted:micIsMuted];
}

-(void)update_imageViewInCallRemoteMicMuteState_isMuted: (BOOL) micIsMuted{
    if(micIsMuted){
        [self update_imageViewInCallRemoteMicMuteState:@"no_mic.png"];
        [self.imageViewInCallRemoteMicMuteState setHidden:FALSE];
    }else{
        //no effect icon will be hidden
        [self update_imageViewInCallRemoteMicMuteState:@"mic.png"];
        //Lee said only show MUTED ICON
        [self.imageViewInCallRemoteMicMuteState setHidden:TRUE];
    }
}

//@"mic.png"
//@"no_mic.png
-(void)update_imageViewInCallRemoteMicMuteState:(NSString *) imageName{
    
    [self update_imageView:self.imageViewInCallRemoteMicMuteState imageName:imageName];
    
}

-(void)update_imageView:(UIImageView *) imageView imageName:(NSString *) imageName{
    if(imageView){
        if(imageName){
            //NSString * imageName = @"mic.png";
            UIImage * image = [UIImage imageNamed:imageName];
            if(image){
                imageView.image = image;
            }else{
                [self log_error:[NSString stringWithFormat:@"imageNamed:'%@' is null",imageName]];
            }
        }else{
            [self log_error:@"[update_imageView:imageName:] imageName is null"];
        }
    }else{
        [self log_error:@"[update_imageView:imageName:] imageView is null"];
    }
}

-(void)update_button:(UIButton *) button imageName:(NSString *) imageName{
    if(button){
        if(imageName){
            //NSString * imageName = @"mic.png";
            UIImage * image = [UIImage imageNamed:imageName];
            if(image){
                [button setImage:image forState:UIControlStateNormal];
                [button setImage:image forState:UIControlStateHighlighted];
                [button setImage:image forState:UIControlStateNormal];
                [button setImage:image forState:UIControlStateDisabled];
            }else{
                [self log_error:[NSString stringWithFormat:@"imageNamed:'%@' is null",imageName]];
            }
        }else{
            [self log_error:@"[update_imageView:imageName:] imageName is null"];
        }
    }else{
        [self log_error:@"[update_imageView:imageName:] button is null"];
    }
}




#pragma mark -
#pragma mark REMOTE PHOTOS
#pragma mark -

//SHOW/HIDE each control
-(void)show_imageViewRemoteParticipantWhilstCalling{
    if(self.imageViewRemoteParticipantWhilstCalling){
        [self.imageViewRemoteParticipantWhilstCalling setHidden:FALSE];
    }else{
        [self log_error:@"imageViewRemoteParticipant is null is NULL"];
    }
}
-(void)hide_imageViewRemoteParticipantWhilstCalling{
    if(self.imageViewRemoteParticipantWhilstCalling){
        [self.imageViewRemoteParticipantWhilstCalling setHidden:TRUE];
    }else{
        [self log_error:@"imageViewRemoteParticipant is null is NULL"];
    }
}
//SHOW/HIDE each control
-(void)show_imageViewRemoteParticipantInCall{
    if(self.imageViewRemoteParticipantInCall){
        [self.imageViewRemoteParticipantInCall setHidden:FALSE];
    }else{
        [self log_error:@"imageViewRemoteParticipant is null is NULL"];
    }
}

-(void)hide_imageViewRemoteParticipantInCall{
    if(self.imageViewRemoteParticipantInCall){
        [self.imageViewRemoteParticipantInCall setHidden:TRUE];
    }else{
        [self log_error:@"imageViewRemoteParticipant is null is NULL"];
    }
}

-(void)sendtoback_imageViewRemoteParticipantInCall{
    if(self.imageViewRemoteParticipantInCall){
        [self.view sendSubviewToBack:self.imageViewRemoteParticipantInCall];
    }else{
        [self log_error:@"imageViewRemoteParticipant is null is NULL"];
    }
}
//------------------------------------------------------------------------------------------



#pragma mark -
#pragma mark setupPreview
#pragma mark -
-(void)setupPreviewView{

    //HIDE till my camera connected
    [self hide_viewRemoteParticipantInfo];
    [self.buttonBackToCall setHidden:TRUE];
    
    //when video is full screen is may have wrong AspectFit or AspectFil
    //I set it always to Fill so it looks ok in fullscreen
    //I tried changing it to Fit/Fill but jumps at the end when it zooms in
    self.previewView.contentMode = UIViewContentModeScaleAspectFill;
    
    [self update_PreviewView_showInFullScreen: TRUE animated:FALSE showBlurView:TRUE];
}

-(void)update_PreviewView_toFullScreen:(BOOL)fullScreen{
    if(fullScreen){

        //THESE are linked to SuperView not Layoutguide - may go behind nav bar
        self.nsLayoutConstraint_previewView_top.constant = 0.0;
        self.nsLayoutConstraint_previewView_bottom.constant = 0.0;
        
        self.nsLayoutConstraint_previewView_leading.constant = 0.0;
        self.nsLayoutConstraint_previewView_trailing.constant = 0.0;
        
        //self.previewView.contentMode = UIViewContentModeScaleAspectFill;
        
        self.previewIsFullScreen = TRUE;
        
        //Dont do here the layer is being updated by the animation so this may not work at first - moved up to update_PreviewView_showInFullScreen
        //[self removeBorderFromView:self.previewView];
        
        //------------------------------------------------------------------------------------------
        [self.imageViewSwitchVideo setHidden:TRUE];
        //------------------------------------------------------------------------------------------
    }else{
        //------------------------------------------------------------------------------------------
        //MINI VIEW
        //------------------------------------------------------------------------------------------
        CGFloat screen_width = self.view.frame.size.width;
        CGFloat screen_height = self.view.frame.size.height;
        
        CGFloat border = 8.0;
        CGFloat previewView_height_small = 160.0;
        CGFloat previewView_width_small = 120.0;
    
        //----------------------------------------------------------------------
        //BOTTOM
        //----------------------------------------------------------------------
        //ISSUE - previewView in full screen ignores Safe Area
        //BUT viewButtonOuter bottom is calculated from view.safeAreaInsets.bottom
        //
        //UIEdgeInsets screen_safeAreaInsets = self.view.safeAreaInsets;
    
        CGFloat bottom = (self.viewButtonOuter.frame.size.height + self.imageViewInCallRemoteMicMuteState.frame.size.height  + 8.0 + self.view.safeAreaInsets.bottom + 8.0);
        
        self.nsLayoutConstraint_previewView_bottom.constant = bottom;
        
        //----------------------------------------------------------------------
        //TOP = BOTTOM + HEIGHT of preview
        //----------------------------------------------------------------------
        CGFloat top = screen_height - (bottom + previewView_height_small);
        self.nsLayoutConstraint_previewView_top.constant = top;
        
        //----------------------------------------------------------------------
        //TRAILING
        //----------------------------------------------------------------------
        CGFloat trailing = self.view.safeAreaInsets.right + border;
        self.nsLayoutConstraint_previewView_trailing.constant = trailing;
        
        //----------------------------------------------------------------------
        //LEADING
        //----------------------------------------------------------------------
        CGFloat leading = screen_width - (trailing + previewView_width_small);
        self.nsLayoutConstraint_previewView_leading.constant = leading;
        
        //self.previewView.contentMode = UIViewContentModeScaleAspectFit;
        
        self.previewIsFullScreen = FALSE;
        
        //Dont do here the layer is being updated by the animation so this may not work at first - moved up to update_PreviewView_showInFullScreen
        //
        //        //didnt work on p1 startCall() but did on p2 answerCall() - animation still happening on layer?
        //        dispatch_async(dispatch_get_main_queue(), ^{
        //            [self addBorderToView:self.previewView withColor:[UIColor redColor] borderWidth: 2.0f];
        //        });
        
        //------------------------------------------------------------------------------------------
        [self.imageViewSwitchVideo setHidden:FALSE];
        //------------------------------------------------------------------------------------------
        
    }
}


-(void)update_PreviewView_showInFullScreen:(BOOL)changeToFullScreen animated:(BOOL)isAnimated showBlurView:(BOOL) showBlurView{

    //animation in and out should be same number of secs
    NSTimeInterval duration = 0.50;

    
    if(changeToFullScreen){
        
        if(isAnimated){
            //------------------------------------------------------------------
            //FULL SCREEN + ANIMATED
            //------------------------------------------------------------------
            [UIView animateWithDuration:duration
                                  delay:0
                                options:UIViewAnimationOptionCurveEaseInOut
                             animations:^{
                                //--------------------------------------------------
                                [self update_PreviewView_toFullScreen: TRUE];
                
                                //--------------------------------------------------
                                //will resize but animate without this
                                [self.view layoutIfNeeded];
                                //--------------------------------------------------
                             }
                             completion:^(BOOL finished) {
                                //ANIMATION DONE
                                [self removeBorderFromPreview];
                             }
            ];
           
        }else{
            //------------------------------------------------------------------
            //FULL SCREEN + UNANIMATED (when app starts)
            //------------------------------------------------------------------
            [self update_PreviewView_toFullScreen: TRUE];

            [self removeBorderFromPreview];
        }
    }else{
        //------------------------------------------------------------------------------------------
        //MINI VIEW
        //------------------------------------------------------------------------------------------
        
        //------------------------------------------------------------------------------------------
        if(isAnimated){
            //------------------------------------------------------------------
            //NOT FULL SCREEN + ANIMATED - (dialing ends shrink preview to bottom right)
            //------------------------------------------------------------------
            [UIView animateWithDuration:duration
                                  delay:0
                                options:UIViewAnimationOptionCurveEaseInOut
                             animations:^{
                                        //--------------------------------------------------
                                        [self update_PreviewView_toFullScreen: FALSE];
                                        //--------------------------------------------------
                                        //will resize but animate without this
                                        [self.view layoutIfNeeded];
                                        //--------------------------------------------------
                                    }
                                    completion:^(BOOL finished) {
                                        //DONE
                                        [self addBorderToPreview];
                                    }
            ];
            
        }else{
            //------------------------------------------------------------------
            //NOT FULL SCREEN + UNANIMATED (preview size jumps to bottom right - unused)
            //------------------------------------------------------------------
            [self update_PreviewView_toFullScreen: FALSE];
            //------------------------------------------------------------------
            [self addBorderToPreview];
            
        }
    }
    
    //[self.view setNeedsUpdateConstraints];
}

//Note - ading border to prev
-(void)removeBorderFromPreview{
    [self removeBorderFromView:self.previewView];
}


-(void)addBorderToPreview{
    
    //----------------------------------------------------------------------------------------------
    //TRIED TO ADD BORDER TO self.previewView
    //but previewView is not a simple UIView - TVIVideoView *previewView
    //I think TVIVideoView removes any border on preView
    //so in Storyboard I just wrapped it in a UIView and added border to that
    //but when p1 calls StartRoom() i think the whole view is cleared
    //Note if you dont wrap this in mainq then outer border wont appear
    //in the storybaord I a 1pixel gap so if this fails then at least theres a border
    //on p2 the border is set correctly in answerCall()
    //on p1 it shows the layer..border
    //put app in background and out again and it gets bigger - constraints?
    //gave up set this to 1 and have 1 in Storyboard less noticable
    //----------------------------------------------------------------------------------------------
    dispatch_async(dispatch_get_main_queue(), ^{
        //------------------------------------------------------------------------------------------
        UIColor * borderColor = [UIColor colorWithRed:14.0/255.0
                                                green:27.0/255.0
                                                 blue:42.0/255.0
                                                alpha:1.0];  //1 + 1,1,1,1 set in SB means at least border of 1
        //------------------------------------------------------------------------------------------
        //DEBUG  borderColor = [UIColor redColor];
        //------------------------------------------------------------------------------------------
      
        
        if(self.viewBorderFor_previewView){
            //--------------------------------------------------------------------------------------
            [self addBorderToView:self.viewBorderFor_previewView
                        withColor: borderColor
                      borderWidth: 1.0f];
            //--------------------------------------------------------------------------------------
        }else{
            [self log_debug:@"[TwilioVideoViewController] [ addBorderToPreview] viewBorderFor_previewView is null"];
        }
        //------------------------------------------------------------------------------------------
    });
    //----------------------------------------------------------------------------------------------
}


#pragma mark -
#pragma mark LOCAL part 1 - openRoom
#pragma mark -
- (void)openRoom:(NSString *)room token:(NSString *)token
                          localUserName:(NSString *)localUserName
                      localUserPhotoURL:(NSString *)localUserPhotoURL
                         remoteUserName:(NSString *)remoteUserName
                      remoteUserPhotoURL:(NSString *)remoteUserPhotoURL
{
    
    [self log_debug:@"[TwilioVideoViewController] [openRoom]"];
    
    [self showhide_buttonDebugStartACall];
    
    //----------------------------------------------------------------------------------------------
    //STORE PARAMS
    self.roomName = room;
    self.accessToken = token;
    
    //----------------------------------------------------------------------------------------------
    //JS nil > [NS null] not NULL
    //----------------------------------------------------------------------------------------------
    if(NULL != localUserName){
        if([localUserName isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] localUserName isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.localUserName = NULL;
        }else{
            self.localUserName = localUserName;
        }
    }else{
        [self log_error:@"[openRoom:] localUserName is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != localUserPhotoURL){
        if([localUserPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] localUserPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.localUserPhotoURL = NULL;
        }else{
            self.localUserPhotoURL = localUserPhotoURL;
        }
    }else{
        [self log_error:@"[openRoom:] localUserPhotoURL is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != remoteUserName){
        if([remoteUserName isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] remoteUserName isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.remoteUserName = NULL;
        }else{
            self.remoteUserName = remoteUserName;
        }
    }else{
        [self log_error:@"[openRoom:] remoteUserName is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != remoteUserPhotoURL){
        if([remoteUserPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] remoteUserPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.remoteUserPhotoURL = NULL;
        }else{
            self.remoteUserPhotoURL = remoteUserPhotoURL;
        }
    }else{
        [self log_error:@"[openRoom:] remoteUserPhotoURL is NULL"];
    }

    //----------------------------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];

    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
         if (grantedPermissions) {
             [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
             //[self doConnect];
             //we connect later with startCall
             [self startCamera];
             dispatch_async(dispatch_get_main_queue(), ^{
                 [self displayCallWaiting];
             });
             
             
         } else {
             [self log_error:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
             [[TwilioVideoManager getInstance] publishEvent: PERMISSIONS_REQUIRED];
             [self handleConnectionError: [self.config i18nConnectionError]];
         }
    }];
}
#pragma mark -
#pragma mark LOCAL part 2 - startCall
#pragma mark -

- (void)startCall:(NSString*)room
            token:(NSString *)token
{
    
    [self log_debug:@"[TwilioVideoViewController] [ startCall:]"];
    
    [self.buttonDebugStartACall setHidden:TRUE];
    
//cleanup VIDEO_TRACK_ADDED always called bu enabled is false if remote is off
//    //if remote camera is off then we should show the remote users photo
//    //if remote camera is on then this will be hidden later when P2 remote user turns their camera back on and triggers remoteParticipant:didEnableVideoTrack
//    [self show_imageViewRemoteParticipantInCall];
//    //if remote camera is ok can be infront of it
//    [self sendtoback_imageViewRemoteParticipantInCall];
    
    self.roomName = room;
    self.accessToken = token;
    
    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> [self showRoomUI:YES]"];
    
    //shows mic button
    [self showRoomUI:YES];
    
    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
        if (grantedPermissions) {
            [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
            [self connectToRoom];
            //we connect later with startCall
            //[self startCamera];
            
        } else {
            [self log_error:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
            [[TwilioVideoManager getInstance] publishEvent: PERMISSIONS_REQUIRED];
            [self handleConnectionError: [self.config i18nConnectionError]];
        }
    }];
}

#pragma mark -
#pragma mark LOCAL part 2 - answerCall
#pragma mark -

//p2 answers call + connects to the room and waits for p1 to connect to room
- (void)answerCall:(NSString*)room token:(NSString *)token
                           localUserName:(NSString *)localUserName
                       localUserPhotoURL:(NSString *)localUserPhotoURL
                          remoteUserName:(NSString *)remoteUserName
                      remoteUserPhotoURL:(NSString *)remoteUserPhotoURL
{
    
    [self log_debug:@"[TwilioVideoViewController] [ answerCall:]"];
    
    [self.buttonDebugStartACall setHidden:TRUE];
    
    //----------------------------------------------------------------------------------------------
    //STORE PARAMS
    self.roomName = room;
    self.accessToken = token;
    
    //----------------------------------------------------------------------------------------------
    //JS nil > [NS null] not NULL
    //----------------------------------------------------------------------------------------------
    if(NULL != localUserName){
        if([localUserName isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] localUserName isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.localUserName = NULL;
        }else{
            self.localUserName = localUserName;
        }
    }else{
        [self log_error:@"[openRoom:] localUserName is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != localUserPhotoURL){
        if([localUserPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] localUserPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.localUserPhotoURL = NULL;
        }else{
            self.localUserPhotoURL = localUserPhotoURL;
        }
    }else{
        [self log_error:@"[openRoom:] localUserPhotoURL is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != remoteUserName){
        if([remoteUserName isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] remoteUserName isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.remoteUserName = NULL;
        }else{
            self.remoteUserName = remoteUserName;
        }
    }else{
        [self log_error:@"[openRoom:] remoteUserName is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    if(NULL != remoteUserPhotoURL){
        if([remoteUserPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[openRoom:] remoteUserPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere - set to NULL"];
            self.remoteUserPhotoURL = NULL;
        }else{
            self.remoteUserPhotoURL = remoteUserPhotoURL;
        }
    }else{
        [self log_error:@"[openRoom:] remoteUserPhotoURL is NULL"];
    }
    //----------------------------------------------------------------------------------------------
    
    //----------------------------------------------------------------------------------------------
    
    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];
    
    [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
        if (grantedPermissions) {
            [self log_debug:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
            [self startCamera];
            
            [self connectToRoom];
            //we connect later with answerCall
            //[self startCamera];
            
        } else {
            [self log_error:@"[TwilioVideoViewController] [ connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
            [[TwilioVideoManager getInstance] publishEvent: PERMISSIONS_REQUIRED];
            [self handleConnectionError: [self.config i18nConnectionError]];
        }
    }];
}
#pragma mark -
#pragma mark BUTTONS
#pragma mark -


-(void) setup_button_states{
    
    //----------------------------------------------------------------------------------------------
    //MIC
    //----------------------------------------------------------------------------------------------
    [self.micButton setImage:[UIImage imageNamed:@"mic"] forState: UIControlStateNormal];

    //----------------------------------------------------------------------------------------------
    //VIDEO
    //----------------------------------------------------------------------------------------------
    [self.videoButton setImage:[UIImage imageNamed:@"video"] forState: UIControlStateNormal];

}

-(UIColor *)button_backGroundColor_enabled{
    //blue
    //https://design.sea.live/design-system
    //maritech-action 
    return [UIColor colorWithRed:33.0/255.0
                           green:150.0/255.0
                            blue:243.0/255.0
                           alpha:1.0];
}
//icon is greyed
-(UIColor *)button_backGroundColor_disabled{
    return [UIColor whiteColor];
}



#pragma mark -
#pragma mark MAIN BUTTON - VIDEO ON/OFF
#pragma mark -
- (IBAction)videoButtonPressed:(id)sender {
    if(self.localVideoTrack){
        self.localVideoTrack.enabled = !self.localVideoTrack.isEnabled;
        //changes icon
        //[self.videoButton setSelected: !self.localVideoTrack.isEnabled];
        if(self.localVideoTrack.isEnabled){
            [self videoButton_changeTo_videoEnabled];
            [self viewLocalCameraDisabled_hide];
            
        }else{
            [self videoButton_changeTo_videoDisabled];
            [self viewLocalCameraDisabled_show];

        }
    }
}

//MUTE VIDEO BUTTON
-(void)videoButton_changeTo_videoEnabled{
    //icons for SELECTED/UNSELECTED are set in setup_button_states
    //[self.videoButton setSelected: FALSE];
    [self update_button:self.videoButton imageName:@"video"];
    
    [self.videoButton setBackgroundColor:[self button_backGroundColor_enabled]];
}

-(void)videoButton_changeTo_videoDisabled{
    //icons for SELECTED/UNSELECTED are set in setup_button_states
    //[self.videoButton setSelected: FALSE];
    [self update_button:self.videoButton imageName:@"no_video_grey"];
    
    [self.videoButton setBackgroundColor:[self button_backGroundColor_disabled]];
}

#pragma mark -
#pragma mark MAIN BUTTON - VIDEO ON/OFF
#pragma mark -
- (IBAction)micButtonPressed:(id)sender {
    // We will toggle the mic to mute/unmute and change the title according to the user action.
    
    if (self.localAudioTrack) {
        self.localAudioTrack.enabled = !self.localAudioTrack.isEnabled;
        
        // If audio not enabled, mic is muted and button crossed out
        //[self.micButton setSelected: !self.localAudioTrack.isEnabled];
        
        if(self.localAudioTrack.isEnabled){
            [self micButton_changeIconTo_on];
        }else{
            [self micButton_changeIconTo_off];
        }
        
        
    }
    //[self updateConstraints_PreviewView_toFullScreen: FALSE animated:TRUE];
    //DEBUG [self dialing_sound_stop];
}

//MUTE VIDEO BUTTON
-(void)micButton_changeIconTo_on{
    //[self.micButton setSelected: TRUE];
    
    [self update_button:self.micButton imageName:@"mic"];
    [self.micButton setBackgroundColor:[self button_backGroundColor_enabled]];
}
-(void)micButton_changeIconTo_off{
    //[self.micButton setSelected: FALSE];
    [self update_button:self.micButton imageName:@"no_mic_grey"];
    [self.micButton setBackgroundColor:[self button_backGroundColor_disabled]];
}
-(void)placeVolumeIconOverButton{
    
    if(self.viewAudioWrapper){
        MPVolumeView *mpVolumeView = [[MPVolumeView alloc] init];
        mpVolumeView.hidden = NO;
        [mpVolumeView setShowsRouteButton:YES];
        
        //this is a volume slide and a button to choose SPEAKER/airpods etc
        //but we hide the slider
        [mpVolumeView setShowsVolumeSlider:NO];
        
        //[mpVolumeView setFrame:CGRectMake(100, 200, 200 ,200)];
        CGRect rect = self.viewAudioWrapper.bounds;
        
        
        [mpVolumeView setFrame:rect];
        [self.viewAudioWrapper addSubview:mpVolumeView];
        
        [self.viewAudioWrapper bringSubviewToFront:mpVolumeView];
        //------------------------------------------------------------------------------------------
        //        AVRoutePickerView
        //        //see also AVRoutePickerView
        //
        //    https://developer.apple.com/documentation/mediaplayer/mpvolumeview
        //------------------------------------------------------------------------------------------
    }else{
        [self log_error:@" is null"];
    }
    
   
}
#pragma mark -
#pragma mark BUTTON DISCONECT
#pragma mark -

- (IBAction)disconnectButtonPressed:(id)sender {
    if ([self.config hangUpInApp]) {
        [[TwilioVideoManager getInstance] publishEvent: HANG_UP];
    } else {
        [self onDisconnect];
    }
}

#pragma mark -
#pragma mark BUTTON: Back to Call (Hide VC.view)
#pragma mark -
//proper way is to have a childVC and show/hide it but the view stack in managed by cordova and native
//so I just hide this VC.view and disable touches
//then added JS methos show_twiliovideo() to show it again
- (IBAction)buttonBackTocCall_HideVCView:(id)sender {
    
    if(self.view.window.rootViewController){
        
        NSString *className = NSStringFromClass([self.view.window.rootViewController class]);
        
        NSLog(@"self.view.window.rootViewController:%@", className);
        
        NSString *logMessage = [NSString stringWithFormat:@"self.view.window.rootViewController:%@", className];
        [self log_info:logMessage];
    }else{
        [self log_error:@"self.view.window.rootViewController is NULL"];
    }
    
    [self hide_twiliovideo];
   
}

//call by BACK TO CALL button

-(void) hide_twiliovideo{
    [self log_info:@"hide_twiliovideo CALLED"];
    
    //    [self.view setHidden:TRUE];
    //    [self.view setUserInteractionEnabled:FALSE];
    //    [self resignFirstResponder];
    
    if(self.view.window){
        
        if(self.view.window.rootViewController){
            
            NSString *logMessage = [NSString stringWithFormat:@"self.view.window.rootViewController:%@", NSStringFromClass([self.view.window.rootViewController class])];
            [self log_info:logMessage];
  
        }else{
            [self log_error:@"self.view.window.rootViewController is NULL"];
        }
        
        //------------------------------------------------------------------------------------------
        //window
        //------------------------------------------------------------------------------------------
        NSLog(@"[self.view.window.subviews count]:%lu", (unsigned long)[self.view.window.subviews count]);

        //------------------------------------------------------------------------------------------
        //WORKS moves front most view to the back but unsafe to do the other way
        //[self.view.window sendSubviewToBack:self.view.window.subviews[count - 1]];
        //------------------------------------------------------------------------------------------
        //v2 - moves the window.subview that has this tvc.view(tag:VC_VIEW_TAG) as a child
        UIView * window_subView_to_move = [self findWindowSubViewThatThisVCIsChildOf];
        if (window_subView_to_move) {
            [self.view.window sendSubviewToBack:window_subView_to_move];
        } else {
            [self log_error:@"window_subView_to_move is NULL - view.tag == VC_VIEW_TAG FAILED - cant move to back"];
        }
        //------------------------------------------------------------------------------------------
        
    }else{
        [self log_error:@"self.view.window.rootViewController is NULL"];
    }
}

//show_twiliovideo() called from index.js > twilio.js > TwilioPlugin.m > show_twiliovideo
-(void) show_twiliovideo{
    [self log_info:@"show_twiliovideo CALLED"];
    
    //    [self.view setHidden:FALSE];
    //    [self.view setUserInteractionEnabled:TRUE];
    //    [self canBecomeFirstResponder];
    
    //UIWindow is a UIView so can use bringSubviewToFront
    
    
    if(self.view.window){
        
        //------------------------------------------------------------------------------------------
        //window.rootViewController
        //------------------------------------------------------------------------------------------
        if(self.view.window.rootViewController){
            NSString *className = NSStringFromClass([self.view.window.rootViewController class]);
            
            NSLog(@"self.view.window.rootViewController:%@", className);
            
        }else{
            [self log_error:@"self.view.window.rootViewController is NULL"];
        }
        
        //------------------------------------------------------------------------------------------
        //window
        //------------------------------------------------------------------------------------------
        NSLog(@"[self.view.window.subviews count]:%lu", (unsigned long)[self.view.window.subviews count]);
        
        //------------------------------------------------------------------------------------------
        //[self.view.window sendSubviewToBack:self.view];
        
        //------------------------------------------------------------------------------------------
        //window may have view between window.subviews and TVC.view
        //subview[0]- NSTransitionView.....CAPBridgeViewController.view
        //subview[1]- NSTransitionView.....TwilioVideoViewController.m.view
        //[self.view.window bringSubviewToFront:self.view];
        
        //v1 - WORKED IN POC as theres only 2 VCs but in full Sea/chat app we dont know how many
        //[self.view.window bringSubviewToFront:self.view.window.subviews[0]];
        
        //------------------------------------------------------------------------------------------
        //v2 - moves the window.subview that has this tvc.view(tag:VC_VIEW_TAG) as a child
        UIView * window_subView_to_move = [self findWindowSubViewThatThisVCIsChildOf];
        
        if (window_subView_to_move) {
            [self.view.window bringSubviewToFront:window_subView_to_move];
        } else {
            [self log_error:@"window_subView_to_move is NULL - view.tag == VC_VIEW_TAG FAILED - cant bringSubviewToFront"];
        }
        //------------------------------------------------------------------------------------------
        
    }else{
        [self log_error:@"self.view.window.rootViewController is NULL"];
    }
    
}

//We want to move this VC.view behind the Cordova webview
//issue 1 we can get the window.subviews and
//for HIDE - move the top to the back
//for SHOW - move bottom to the top
//but a bit insafe there could be alerts etc and I dont know how many views are in the full sea/chat app
//so I added a tag to this VC.view.tag = VC_VIEW_TAG  //6655
//and this method iterates over all window.subview and search all views for tag 6655
//if found it returns the window.subview that is its parent
//e.g.
//------------------------------------------------------------------------------------------
//window.subviews

//  NSTransitionView[0]>otherVC.view
//  NSTransitionView[1]>TwilioVideoViewController.view(tag 6655)  << FOUND so return the root NSTransitionView[1]
//  NSTransitionView[2]>someotherVC.view
//------------------------------------------------------------------------------------------
//    window
//    subView0 class:UITransitionView [tag  :0]     <<< window.subview may not be our VC.view
//      subView1 class:UIDropShadowView [tag  :0]
//        subView2 class:WKWebView [tag  :0]
//    subView0 class:UITransitionView [tag  :0]     <<< THIS IS THE SUBVIEW WE WANT TO MOVE TO BACK....
//        subView1 class:UIView [tag  :6655]        <<< because TVC.view(this vc) is a child of the window.subview
//            subView2 class:UIButton [tag  :0]
//            subView2 class:UIView [tag  :0]
//            ...controls on the view
//------------------------------------------------------------------------------------------
//return NSTransitionView[1]
//UIWindow is a UIView so can use bringSubviewToFront/sednSubViewToBack
//then we can call window.sendToBack[NSTransitionView[1]] and it will move the whole TwiliViewController to the back
-(UIView *) findWindowSubViewThatThisVCIsChildOf{
    //------------------------------------------------------------------------------------------
    UIView * window_subView_to_move = NULL;
    //------------------------------------------------------------------------------------------
    for (UIView *subView0 in self.view.window.subviews) {
        NSInteger subView0_tag = subView0.tag;
        NSLog(@"subView0 class:%@ [tag  :%ld]", NSStringFromClass([subView0 class]), (long)subView0_tag);
        //------------------------------------------------------------------------------------------
        for (UIView *subView1 in subView0.subviews) {
            NSInteger subView1_tag = subView1.tag;
            NSLog(@"    subView1 class:%@ [tag  :%ld]", NSStringFromClass([subView1 class]), (long)subView1_tag);
            //------------------------------------------------------------------------------------------
            //our TVC.view is usually at this level
            if (VC_VIEW_TAG == subView1_tag) {
                window_subView_to_move = subView0;
                break;
            } else {
                //no match skip
            }
            //------------------------------------------------------------------------------------------
            
            for (UIView *subView2 in subView1.subviews) {
                NSInteger subView2_tag = subView2.tag;
                NSLog(@"        subView2 class:%@ [tag  :%ld]", NSStringFromClass([subView2 class]), (long)subView2_tag);
                
                //our TVC.view is usually at parent level but added in case - may never be called the break; in the parent may cancel first
                if (VC_VIEW_TAG == subView2_tag) {
                    window_subView_to_move = subView0;
                    break;
                } else {
                    //no match skip
                }
            }
        }
    }
    return window_subView_to_move;
}

#pragma mark - Private

- (BOOL)isSimulator {
#if TARGET_IPHONE_SIMULATOR
    return YES;
#endif
    return NO;
}


- (void)startPreview {
    [self log_debug:@"[startPreview] START"];

    // TVICameraCapturer is not supported with the Simulator.
    if ([self isSimulator]) {
        [self log_error:@"[startPreview] preview doesnt work in Simulator. Must run on real device 'TVICameraCapturer is not supported with the Simulator.'"];
        
        [self.previewView removeFromSuperview];
        return;
    }
    
    AVCaptureDevice *frontCamera = [TVICameraSource captureDeviceForPosition:AVCaptureDevicePositionFront];
    AVCaptureDevice *backCamera = [TVICameraSource captureDeviceForPosition:AVCaptureDevicePositionBack];
    
    if (frontCamera != nil || backCamera != nil) {
        [self log_debug:@"[startPreview] localVideoTrack set to self.camera which is frontCamera or backCamera"];
        
        self.camera = [[TVICameraSource alloc] initWithDelegate:self];
        
        //BC - startWithVideoOff - alexey asked for this - passed in via config
        //SEE also startWithAudioOff
        BOOL localVideoTrack_enabled = NO;
        
        if ([self.config startWithVideoOff]) {
            localVideoTrack_enabled = NO;
        } else {
            localVideoTrack_enabled = YES;
        }
        
        self.localVideoTrack = [TVILocalVideoTrack trackWithSource:self.camera
                                                             enabled:localVideoTrack_enabled
                                                                name:@"Camera"];
        if (!self.localVideoTrack) {
            [self log_error:@"[startPreview] Failed to add video track - self.localVideoTrack is NULL"];
            
        } else {
            [self log_debug:@"[startPreview] localVideoTrack ok >> addRenderer:self.previewView"];
            
            // Add renderer to video track for local preview
            [self.localVideoTrack addRenderer:self.previewView];
            
            
            [self log_debug:@"self.localVideoTrack created and connected to previewView"];
            
            //------------------------------------------------------------------
            //TAP on preview swaps to front or back
            //------------------------------------------------------------------
            if (frontCamera != nil && backCamera != nil) {
                UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self
                                                                                      action:@selector(flipCamera)];
                [self.previewView addGestureRecognizer:tap];
  
                self.viewAudioWrapper.hidden = NO;

                
            }
            
            //UNHIDE video button(so user can turn it off if needed)
            self.videoButton.hidden = NO;
            
            //change icon
            //audio was on background thread - adding this for safety
            dispatch_async(dispatch_get_main_queue(), ^{
                if(self.localVideoTrack.isEnabled){
                    [self videoButton_changeTo_videoEnabled];
                    [self viewLocalCameraDisabled_hide];
                }else{
                    [self videoButton_changeTo_videoDisabled];
                    [self viewLocalCameraDisabled_show];
                }
            });

            [self log_debug:@"[startPreview][self.camera startCaptureWithDevice: FROM current camera frontCamera OR backCamera"];
            
            [self.camera startCaptureWithDevice:frontCamera != nil ? frontCamera : backCamera
                 completion:^(AVCaptureDevice *device, TVIVideoFormat *format, NSError *error) {
                     if (error != nil) {
                        [self log_error:[NSString stringWithFormat:@"[startPreview][self.camera startCaptureWithDevice: failed with error.\ncode = %lu error = %@", error.code, error.localizedDescription]];
                        
                     } else {
                         [self log_info:@"[startPreview]self.camera startCaptureWithDevice: STARTED OK"];
                         
                         //FLIP the video on horizontal so it looks right (e.g. text on tshort not flipped)
                         self.previewView.mirror = (device.position == AVCaptureDevicePositionFront);
                         
                         //---------------------------------------------------------
                         //preview is inserted o
                         //---------------------------------------------------------
                         //preview can be inserted in front - if full screen can hide the viewRemoteParticipantInfo
                         [self.view bringSubviewToFront:self.viewRemoteParticipantInfo];
                         
                         //WRONG startPreview calls always  [self displayCallWaiting];
                     }
            }];
        }
    } else {
       [self log_info:@"No front or back capture device found!"];
   }
}

//called after startCamera > startCaptureWithDevice
-(void)displayCallWaiting{
    [self log_info:@"[displayCallWaiting] START"];
    
    if(self.previewIsFullScreen){
        //----------------------------------------------------------------------
        //Show the dialing panel
        
        [self show_viewRemoteParticipantInfoWithState:@"Calling..."];
        
        [self animateAlphaBorderForViews];
        
        //----------------------------------------------------------------------
        //show LOCAL USER full screen while waiting for othe ruser to answer
        [self update_PreviewView_showInFullScreen:TRUE animated:FALSE showBlurView:TRUE];
        
        //----------------------------------------------------------------------
        //FIRST TIME VERY LOUD - cant set volume to 0
        //NEXT TIMES too quiet
        //will start it before room connect in viewDidLoad
//RELEASE COMMENT IN
//        [self dialing_sound_start];
        //----------------------------------------------------------------------
        
    }else{
        [self log_error:@"[displayCallWaiting] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

//On the ANSWERING PHONE it will trigger
//didConnectToRoom_AnswerACall only
-(void)didConnectToRoom_AnswerACall{
    [self log_info:@"[didConnectToRoom_AnswerACall] START REMOTE USER in Room - show Waiting... till caller enters room"];
    
    if(self.previewIsFullScreen){
        //------------------------------------------------------------------------------
        //REMOTE USER CONNECTED.. waiting for CALLER to enter room
       
        [self show_viewRemoteParticipantInfoWithState:@"Connecting..."];
        
        [self animateAlphaBorderForViews];
        
    }else{
        [self log_error:@"[didConnectToRoom_AnswerACall] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}


//called by TVIRoomDelegate.participantDidConnect
//Same app installed on both phones but UI changes depending on who starts or answers a call
//1 local + 0 remote - LOCAL USER is person dialing REMOTE participant.
//Remote hasnt joined the room yet so hasnt answered so show 'Dialing..'
//On the CALLING PHONE it will trigger
//didConnectToRoom_StartACall >> participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking
-(void)participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom{
    [self log_info:@"[participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] START"];

    [self dialing_sound_stop];

    if(self.previewIsFullScreen){
        //hide Waiting...
        [self hide_viewRemoteParticipantInfo];
        [self.buttonBackToCall setHidden:FALSE];
        //------------------------------------------------------------------------------------------
        //INCALL remote users name and muted icon
        //------------------------------------------------------------------------------------------
        //default ot muted - AUDIO_TRACK_ADDED will set it to unmuted
        [self show_inCall_remoteUserNameAndMic_isMuted:TRUE];
       
        //------------------------------------------------------------------------------------------
        //REMOTE user is visible in full screen
        //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
        [self update_PreviewView_showInFullScreen: FALSE animated:TRUE showBlurView:TRUE];

    }else{
        [self log_error:@"[participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

-(void)participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking{
    [self log_info:@"[participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] START"];
    
    [self dialing_sound_stop];
    
    if(self.previewIsFullScreen){
        
        [self hide_viewRemoteParticipantInfo];
        [self.buttonBackToCall setHidden:FALSE];
        
        //------------------------------------------------------------------------------------------
        //INCALL remote users name and muted icon
        //------------------------------------------------------------------------------------------
        //default ot muted - AUDIO_TRACK_ADDED will set it to unmuted
        [self show_inCall_remoteUserNameAndMic_isMuted:TRUE];
        
        //------------------------------------------------------------------------------------------
        //REMOTE user is visible in full screen
        //------------------------------------------------------------------------------------------
        //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
        [self update_PreviewView_showInFullScreen: FALSE animated:TRUE showBlurView:TRUE];
        
        
    }else{
        [self log_error:@"[participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

//called by TVIRoomDelegate.participantDidConnect
//Same app installed on both phones but UI changes depending on who starts or answers a call
//1 local + 1 remote - REMOTE user in room is the other person who started the call
//LOCAL USER is answering a call so dont show 'Dialing..'
-(void)participantDidConnect{
    [self log_info:@"[participantDidConnect] Unused in 1..1 - use for GROUP"];
}

-(void)participantDidDisconnect:(NSString *)remoteParticipant_identity;{
    
    if(self.previewIsFullScreen){
        [self log_error:@"[participantDidDisconnect] new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL"];
        
    }else{
        //REMOTE USER DISCONNECTED
        //show the remote user panel with state 'Disconnected'
        
        //if app running on REMOTE photo will just show white circle no photo
        //this is so Disconnected isnt off center
        
        [self show_viewRemoteParticipantInfoWithState:@"Disconnected"];
        
        //pulse animation is on repeat forever - just hide the fake border view - I think when in full sea/chat disconnect will close this whole VC
        [self animateAlphaBorderForViews_HideBorder];
        
        //Zoom the preview from MINI to FULL SCREEN
        //ONLY show BLUR when dialing
        //Here remote has disconnected so dont show blur
        
        [self update_PreviewView_showInFullScreen:TRUE animated:TRUE showBlurView:FALSE];
        
        [self hide_inCall_remoteUserNameAndMic];
        
        //if remote user has turned off their camera before disconnecting then remote phot in center of screen
        [self hide_imageViewRemoteParticipantInCall];
    }
}
- (void)flipCamera {
    
    [self log_debug:@"[flipCamera] START"];
    
    AVCaptureDevice *newDevice = nil;
    
    if (self.camera.device.position == AVCaptureDevicePositionFront) {
        newDevice = [TVICameraSource captureDeviceForPosition:AVCaptureDevicePositionBack];
    } else {
        newDevice = [TVICameraSource captureDeviceForPosition:AVCaptureDevicePositionFront];
    }
    
    if (newDevice != nil) {
        [self.camera selectCaptureDevice:newDevice completion:^(AVCaptureDevice *device, TVIVideoFormat *format, NSError *error) {
            if (error != nil) {
                [self log_info:[NSString stringWithFormat:@"Error selecting capture device.\ncode = %lu error = %@", error.code, error.localizedDescription]];
            } else {
                self.previewView.mirror = (device.position == AVCaptureDevicePositionFront);
            }
        }];
    }
}

- (void)prepareLocalMedia {
    
    // We will share local audio and video when we connect to room.
    
    // Create an audio track.
    if (!self.localAudioTrack) {
        //
        //https://twilio.github.io/twilio-video-ios/docs/2.8.1/index.html
        
        //BC - startWithVideoOff - alexey asked for this - passed in via config
        //SEE also startWithAudioOff
        BOOL localAudioTrack_enabled = NO;
        
        if ([self.config startWithAudioOff]) {
            localAudioTrack_enabled = NO;
        } else {
            localAudioTrack_enabled = YES;
        }
        
        //----------------------------------------------------------------------
        self.localAudioTrack = [TVILocalAudioTrack trackWithOptions:nil
                                                            enabled:localAudioTrack_enabled
                                                               name:@"Microphone"];
        //----------------------------------------------------------------------
        if (!self.localAudioTrack) {
            [self log_info:@"Failed to add audio track"];
        }else{
            dispatch_async(dispatch_get_main_queue(), ^{
                if(self.localAudioTrack.isEnabled){
                    [self micButton_changeIconTo_on];
                }else{
                    [self micButton_changeIconTo_off];
                }
            });
        }
        //----------------------------------------------------------------------
    }
    
    // Create a video track which captures from the camera.
    if (!self.localVideoTrack) {
        [self startPreview];
    }
}

- (void)startCamera {
    [self log_debug:@"[TwilioVideoViewController] [ startCamera] START"];
    
    if ([self.accessToken isEqualToString:@"TWILIO_ACCESS_TOKEN"]) {
        [self log_info:@"Please provide a valid token to connect to a room"];
        return;
    }
    
    //--------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController] [ doConnect] >> prepareLocalMedia"];
    
    // Prepare local media which we will share with Room Participants.
    [self prepareLocalMedia];
    //--------------------------------------------------------------------------
    
}

- (void)connectToRoom {
    [self log_debug:@"[TwilioVideoViewController] [ doConnect] START"];
    
    if ([self.accessToken isEqualToString:@"TWILIO_ACCESS_TOKEN"]) {
        [self log_info:@"Please provide a valid token to connect to a room"];
        return;
    }
    
    //--------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController] [ doConnect] >> prepareLocalMedia"];
    
    // Prepare local media which we will share with Room Participants.
//    [self prepareLocalMedia];
    
    //--------------------------------------------------------------------------
    
    //--------------------------------------------------------------------------
    TVIConnectOptions *connectOptions = [TVIConnectOptions optionsWithToken:self.accessToken
                                                                      block:^(TVIConnectOptionsBuilder * _Nonnull builder) {
        builder.roomName = self.roomName;
        // Use the local media that we prepared earlier.
        builder.audioTracks = self.localAudioTrack ? @[ self.localAudioTrack ] : @[ ];
        builder.videoTracks = self.localVideoTrack ? @[ self.localVideoTrack ] : @[ ];
    }];
    //--------------------------------------------------------------------------
    // Connect to the Room using the options we provided.
    
    [self log_debug:@"[TwilioVideoViewController] [ doConnect] >> connectWithOptions:connectOptions (LOCAL VIDEO AUDIO) >> ROOM"];
    self.room = [TwilioVideoSDK connectWithOptions:connectOptions delegate:self];
    
    [self log_info:@"Attempting to connect to room"];
}

#pragma mark -
#pragma mark remoteView
#pragma mark -

- (void)setupRemoteView {
    [self log_debug:@"[TwilioVideoViewController] [ setupRemoteView] MAKE TVIVideoView *remoteView"];

    // Creating `TVIVideoView` programmatically
    TVIVideoView *remoteView = [[TVIVideoView alloc] init];
        
    // `TVIVideoView` supports UIViewContentModeScaleToFill, UIViewContentModeScaleAspectFill and UIViewContentModeScaleAspectFit
    // UIViewContentModeScaleAspectFit is the default mode when you create `TVIVideoView` programmatically.
    remoteView.contentMode = UIViewContentModeScaleAspectFill;

    [self.view insertSubview:remoteView atIndex:0];
    self.remoteVideoView = remoteView;
    
    NSLayoutConstraint *centerX = [NSLayoutConstraint constraintWithItem:self.remoteVideoView
                                                               attribute:NSLayoutAttributeCenterX
                                                               relatedBy:NSLayoutRelationEqual
                                                                  toItem:self.view
                                                               attribute:NSLayoutAttributeCenterX
                                                              multiplier:1
                                                                constant:0];
    [self.view addConstraint:centerX];
    NSLayoutConstraint *centerY = [NSLayoutConstraint constraintWithItem:self.remoteVideoView
                                                               attribute:NSLayoutAttributeCenterY
                                                               relatedBy:NSLayoutRelationEqual
                                                                  toItem:self.view
                                                               attribute:NSLayoutAttributeCenterY
                                                              multiplier:1
                                                                constant:0];
    [self.view addConstraint:centerY];
    NSLayoutConstraint *width = [NSLayoutConstraint constraintWithItem:self.remoteVideoView
                                                             attribute:NSLayoutAttributeWidth
                                                             relatedBy:NSLayoutRelationEqual
                                                                toItem:self.view
                                                             attribute:NSLayoutAttributeWidth
                                                            multiplier:1
                                                              constant:0];
    [self.view addConstraint:width];
    
    NSLayoutConstraint *height = [NSLayoutConstraint constraintWithItem:self.remoteVideoView
                                                              attribute:NSLayoutAttributeHeight
                                                              relatedBy:NSLayoutRelationEqual
                                                                 toItem:self.view
                                                              attribute:NSLayoutAttributeHeight
                                                             multiplier:1
                                                               constant:0];
    [self.view addConstraint:height];
}

#pragma mark -
#pragma mark showRoomUI
#pragma mark -


// Reset the client ui status
- (void)showRoomUI:(BOOL)inRoom {
    [self log_debug:@"[TwilioVideoViewController] [ showRoomUI] hide mic/ show spinner"];

    self.micButton.hidden = !inRoom;
    [UIApplication sharedApplication].idleTimerDisabled = inRoom;
    
    [self fillIn_viewRemoteParticipantInfo];
}

#pragma mark -
#pragma mark - CAORDOVA > OFFLINE ALERT
#pragma mark -

//internet gone - seachat tells plugin to show alert
- (void)showOffline{
    
    [self.viewAlert setHidden:FALSE];
    [self.view bringSubviewToFront:self.viewAlert];
    
    
    //DO NOT RELEASE - cordova should send showOnline()
    [self.buttonDebug_showOnline setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebug_showOnline];

}

//internet returned - seachat tells plugin to hide alert
- (void)showOnline{
    [self.viewAlert setHidden:TRUE];
 
}

-(void)showhide_buttonDebugStartACall{
    //------------------------------------------------------------------------------------------
    //FOR RELEASE - COMMENT THIS OUT
    //------------------------------------------------------------------------------------------
    [self.buttonDebugStartACall setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebugStartACall];
    
    //------------------------------------------------------------------------------------------
    //FOR RELEASE - COMMENT THIS IN
    //------------------------------------------------------------------------------------------
//    [self.buttonDebugStartACall setHidden:TRUE];
    //------------------------------------------------------------------------------------------
}

- (IBAction)buttonDebug_showOffline_Action:(id)sender {
    [self showOffline];
    
    [self.buttonDebug_showOnline setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebug_showOnline];
    
    //its hidden in answercall  for REMOTE user just bring to front may stil be hidden
    //[self.buttonDebugStartACall setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebugStartACall];
}

- (IBAction)buttonDebug_showOnline_Action:(id)sender {
    [self showOnline];
    
    [self.buttonDebug_showOnline setHidden:TRUE];
    
    //its hidden in answercall  for REMOTE user just bring to front may stil be hidden
    //[self.buttonDebugStartACall setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebugStartACall];
}


#pragma mark -
#pragma mark - cleanupRemoteParticipant
#pragma mark -

- (void)cleanupRemoteParticipant {
    [self log_debug:@"[TwilioVideoViewController] [ cleanupRemoteParticipant]"];
    if (self.remoteParticipant) {
        if ([self.remoteParticipant.videoTracks count] > 0) {
            TVIRemoteVideoTrack *videoTrack = self.remoteParticipant.remoteVideoTracks[0].remoteTrack;
            [videoTrack removeRenderer:self.remoteVideoView];
            [self.remoteVideoView removeFromSuperview];
        }
        self.remoteParticipant = nil;
    }
}


- (void)handleConnectionError: (NSString*)message {
    if ([self.config handleErrorInApp]) {
        [self log_info: @"Error handling disabled for the plugin. This error should be handled in the hybrid app"];
        [self dismiss];
        return;
    }
    
    [self log_info: @"Connection error handled by the plugin"];
    if(showNativeUIAlerts){
        UIAlertController * alert = [UIAlertController
                                     alertControllerWithTitle:NULL
                                     message: message
                                     preferredStyle:UIAlertControllerStyleAlert];
        
        //Add Buttons
        
        UIAlertAction* yesButton = [UIAlertAction
                                    actionWithTitle:[self.config i18nAccept]
                                    style:UIAlertActionStyleDefault
                                    handler: ^(UIAlertAction * action) {
            //------------------------------------------------------------------
            [self dismiss];
            //------------------------------------------------------------------
        }];
        
        [alert addAction:yesButton];
        [self presentViewController:alert animated:YES completion:nil];
    }else{
        NSString * messageToLog = [NSString stringWithFormat:@"showNativeUIAlerts is FALSE: ALERT('%@') HIDDEN - to be handled by Sea/chat not plugin", message];
        [self log_info: messageToLog];
        
        //must have same code here as above in OK/ Accept button
        //------------------------------------------------------------------
        [self dismiss];
        //------------------------------------------------------------------
        
    }
}

- (void) dismiss {
    [[TwilioVideoManager getInstance] publishEvent: CLOSED];
    [self dismissViewControllerAnimated:NO completion:nil];
}

#pragma mark - TwilioVideoActionProducerDelegate

- (void)onDisconnect {
    [self log_debug:@"[TwilioVideoViewController] [ TwilioVideoActionProducerDelegate.onDisconnect] >> [self.room disconnect]"];
    if (self.room != NULL) {
        [self.room disconnect];
    }else{
        NSLog(@"self.room is NULL - OK if LOCAL/STAGE1/");
        
        //NOTE - if LOCAL USER hasnt connected to Room this isnt called
        //onDiconnect needs to manually
        [self disconnectFromUIAndSend_DISCONNECTED:nil];
    }
}

#pragma mark - TVIRoomDelegate

- (void)didConnectToRoom:(nonnull TVIRoom *)room {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self log_debug:@"[TwilioVideoViewController] [ TVIRoomDelegate.didConnectToRoom] >> GET FIRST room.remoteParticipants[0]"];
        
        // At the moment, this example only supports rendering one Participant at a time.
        [self log_info:[NSString stringWithFormat:@"[didConnectToRoom] Connected to room %@ as %@", room.name, room.localParticipant.identity]];
        [[TwilioVideoManager getInstance] publishEvent: CONNECTED];
        
        
        
        //NO MATTER HOW MANY ROOM PARTICIPANTS - just pick the first
        if (room.remoteParticipants.count > 0) {
            self.remoteParticipant = room.remoteParticipants[0];
            self.remoteParticipant.delegate = self;
        }
        
        //didConnectToRoom
        
        if([room.remoteParticipants count] == 0){
            //----------------------------------------------------------------------
            //1..1 CALL - no remote users so I am DIALING the REMOTE USER
            //----------------------------------------------------------------------
            [self log_info:[NSString stringWithFormat:@"[didConnectToRoom] room.remoteParticipants count:%lu >> LOCAL USER is STARTING A 1..1 CALL",
                            (unsigned long)[room.remoteParticipants count]]];
            //----------------------------------------------------------------------
            [self didConnectToRoom_AnswerACall];
            //does nothing Dialing.. moved to displayCallWaiting
            //----------------------------------------------------------------------
        }
        else if([room.remoteParticipants count] == 1){
            //----------------------------------------------------------------------
            //1..1 CALL - 1 remote user in room so LOCAL USER(Caller))
            //----------------------------------------------------------------------
            [self log_info:[NSString stringWithFormat:@"[didConnectToRoom] room.remoteParticipants count:%lu >> LOCAL USER is CONNECTING TO ROOM AFTER REMOTE for A 1..1 CALL",
                            (unsigned long)[room.remoteParticipants count]]];
            //----------------------------------------------------------------------
            [self participantDidConnect_LocalUserAndCallerHaveConnectedToRoom_StartTalking];
            //----------------------------------------------------------------------
        }
        else{
            [self log_error:[NSString stringWithFormat:@"[didConnectToRoom] room.remoteParticipants count:%lu >> UNHANDLED MORE THAN 2 USERS in room 1 LOCAL + %lu REMOTE - TODO IN FUTURE GROUP CALLS",
                             (unsigned long)[room.remoteParticipants count],
                             (unsigned long)[room.remoteParticipants count]]];
        }
    });
}

- (void)room:(nonnull TVIRoom *)room didFailToConnectWithError:(nonnull NSError *)error {
    [self log_info:[NSString stringWithFormat:@"[didFailToConnectWithError:] Failed to connect to room, error = %@", error]];
    [[TwilioVideoManager getInstance] publishEvent: CONNECT_FAILURE with:[TwilioVideoUtils convertErrorToDictionary:error]];
    
    self.room = nil;
    
    [self showRoomUI:NO];
    [self handleConnectionError: [self.config i18nConnectionError]];
}

- (void)room:(nonnull TVIRoom *)room didDisconnectWithError:(nullable NSError *)error {
    [self log_info:[NSString stringWithFormat:@"Disconnected from room %@, error = %@", room.name, error]];
    
    [self cleanupRemoteParticipant];
    self.room = nil;
    
    //NOTE - if LOCAL USER hasnt connected to Room this isnt called
    //onDiconnect needs to manually
    [self disconnectFromUIAndSend_DISCONNECTED:error];
}

//called by disconnectButtonpressed (if room is nil) or by  didDisconnectWithError:
-(void)disconnectFromUIAndSend_DISCONNECTED:(nullable NSError *)error{
    [self showRoomUI:NO];
    if (error != NULL) {
        [[TwilioVideoManager getInstance] publishEvent:DISCONNECTED_WITH_ERROR with:[TwilioVideoUtils convertErrorToDictionary:error]];
        [self handleConnectionError: [self.config i18nDisconnectedWithError]];
    } else {
        [[TwilioVideoManager getInstance] publishEvent: DISCONNECTED];
        [self dismiss];
    }
}

- (void)room:(nonnull TVIRoom *)room isReconnectingWithError:(nonnull NSError *)error {
    
    [self log_debug:[NSString stringWithFormat:@"[TwilioVideoViewController] [ TVIRoomDelegate.room:isReconnectingWithError:%@ >> publishEvent: RECONNECTING with Error:", error]];
    
    [[TwilioVideoManager getInstance] publishEvent: RECONNECTING with:[TwilioVideoUtils convertErrorToDictionary:error]];
}

- (void)didReconnectToRoom:(nonnull TVIRoom *)room {
    [self log_debug:[NSString stringWithFormat:@"[TwilioVideoViewController] [ TVIRoomDelegate.room:didReconnectToRoom:%@ >> publishEvent: RECONNECTED", room.name]];

    [[TwilioVideoManager getInstance] publishEvent: RECONNECTED];
}

- (void)room:(nonnull TVIRoom *)room participantDidConnect:(nonnull TVIRemoteParticipant *)participant {
    
    
    #pragma mark FUTURE GROUP CALLS - MORE THAN 1 REMOTE USER
    if (!self.remoteParticipant) {
        self.remoteParticipant = participant;
        self.remoteParticipant.delegate = self;
    }
    [self log_info:[NSString stringWithFormat:@"[participantDidConnect][TwilioVideoViewController] [ TVIRoomDelegate.participantDidConnect][room:participantDidConnect:] Participant '%@' connected with %lu audio and %lu video tracks >> publishEvent: PARTICIPANT_CONNECTED",
                      participant.identity,
                      (unsigned long)[participant.audioTracks count],
                      (unsigned long)[participant.videoTracks count]]];
    
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_CONNECTED];
    
    
    
    if([room.remoteParticipants count] == 0){
        //----------------------------------------------------------------------
        //1..1 CALL - no remote users so I an STARTING A CALL
        //----------------------------------------------------------------------
        [self log_info:[NSString stringWithFormat:@"[participantDidConnect][TwilioVideoViewController] [ TVIRoomDelegate.participantDidConnect][room:participantDidConnect:] room.remoteParticipants count:%lu >> LOCAL USER is STARTING A 1..1 CALL >> do nothing",
                        (unsigned long)[room.remoteParticipants count]]];
        //----------------------------------------------------------------------
        //for GROUP participantDidConnect will do things like inc particpant count
        //show list of users etc
        //----------------------------------------------------------------------
    }
    else if([room.remoteParticipants count] == 1){
        //----------------------------------------------------------------------
        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
        //----------------------------------------------------------------------
        [self log_info:[NSString stringWithFormat:@"[participantDidConnect][TwilioVideoViewController] [ TVIRoomDelegate.participantDidConnect][room:participantDidConnect:] room.remoteParticipants count:%lu >> REMOTE USER is ANSWERING A 1..1 CALL >> participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom",
                        (unsigned long)[room.remoteParticipants count]]];
        //----------------------------------------------------------------------
        //1 LOCAL  - PERSON BEING CALLED
        //1 REMOTE - CALLER
        [self participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom];
        //----------------------------------------------------------------------
    }
    else{
        [self log_error:[NSString stringWithFormat:@"[participantDidConnect][TwilioVideoViewController] room.remoteParticipants count:%lu >> UNHANDLED MORE THAN 2 USERS in room 1 LOCAL + %lu REMOTE - TODO IN FUTURE GROUP CALLS",
                        (unsigned long)[room.remoteParticipants count],
                         (unsigned long)[room.remoteParticipants count]]];
    }
    
}

- (void)room:(nonnull TVIRoom *)room participantDidDisconnect:(nonnull TVIRemoteParticipant *)participant {
    
    if (self.remoteParticipant == participant) {
        [self cleanupRemoteParticipant];
    }
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [ TVIRoomDelegate.participantDidDisconnect] Room %@ participant %@ disconnected >> publishEvent: PARTICIPANT_DISCONNECTED", room.name, participant.identity]];
    
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_DISCONNECTED];
    // if LOCAL caller hangs up first the REMOTE phone will show 'Disconnected'
    // but self.remoteUserName is NULL on REMOTE side so no IMAGE or NAME
    // so pass in the identity of the participant that disconnected to we can say
    //'John Smith Disconnected'
    
    [self participantDidDisconnect:participant.identity];
}


#pragma mark - TVIRemoteParticipantDelegate

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didPublishVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    // Remote Participant has offered to share the video Track.
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didPublishVideoTrack] Participant '%@' published video track:'%@' .",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didUnpublishVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    // Remote Participant has stopped sharing the video Track.
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didUnpublishVideoTrack]Participant '%@' unpublished video track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didPublishAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    // Remote Participant has offered to share the audio Track.
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didPublishAudioTrack]Participant '%@' published audio track:'%@' >> DO NOTHING JUST LOG",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didUnpublishAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {

    // Remote Participant has stopped sharing the audio Track.
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didUnpublishAudioTrack]Participant '%@' unpublished audio track:'%@' >> DO NOTHING JUST LOG",
                      participant.identity, publication.trackName]];
}

- (void)didSubscribeToVideoTrack:(nonnull TVIRemoteVideoTrack *)videoTrack
                     publication:(nonnull TVIRemoteVideoTrackPublication *)publication
                  forParticipant:(nonnull TVIRemoteParticipant *)participant {
    
    // We are subscribed to the remote Participant's audio Track. We will start receiving the
    // remote Participant's video frames now.
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [TVIRemoteParticipantDelegate.didSubscribeToVideoTrack:publication:forParticipant:]Subscribed to video track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: VIDEO_TRACK_ADDED];

    if (self.remoteParticipant == participant) {
        [self setupRemoteView];
        [videoTrack addRenderer:self.remoteVideoView];
        
        //if remote camera is off when P1 calls startACall() then photo can be missing
        //so in startACall: we show it by default
        //here we hide it once a remote video feed connects
        
        
        if(videoTrack.enabled){
            [self log_error:@"VIDEO_TRACK_ADDED - videoTrack.enabled:TRUE"];
            [self hide_imageViewRemoteParticipantInCall];
        }else{
            [self log_error:@"VIDEO_TRACK_ADDED - videoTrack.enabled:FALSE"];
            [self show_imageViewRemoteParticipantInCall];
            //of p1 camera is on then turned off the remote photo can appear above the localcamer till its shrunk to mini view
            [self sendtoback_imageViewRemoteParticipantInCall];
        }
        
        if(videoTrack.state == TVITrackStateLive){
            [self log_error:@"VIDEO_TRACK_ADDED - videoTrack.state:TVITrackStateLive"];
        }
        else if(videoTrack.state == TVITrackStateEnded){
            [self log_error:@"VIDEO_TRACK_ADDED - videoTrack.state:TVITrackStateEnded"];
        }
        else
        {
            [self log_error:@"VIDEO_TRACK_ADDED - videoTrack.state:UNHANDLED"];
        }
        
    }
}

- (void)didUnsubscribeFromVideoTrack:(nonnull TVIRemoteVideoTrack *)videoTrack
                         publication:(nonnull TVIRemoteVideoTrackPublication *)publication
                      forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[TwilioVideoViewController] [TVIRemoteParticipantDelegate.didUNSUBSCRIBEFromVideoTrack:publication:forParticipant:] send VIDEO_TRACK_REMOVED remove REMOTE VIDEO"];

    // We are unsubscribed from the remote Participant's video Track. We will no longer receive the
    // remote Participant's video.
    
    [self log_info:[NSString stringWithFormat:@"Unsubscribed from video track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: VIDEO_TRACK_REMOVED];
    
    if (self.remoteParticipant == participant) {
        [videoTrack removeRenderer:self.remoteVideoView];
        [self.remoteVideoView removeFromSuperview];
    }
}

- (void)didSubscribeToAudioTrack:(nonnull TVIRemoteAudioTrack *)audioTrack
                     publication:(nonnull TVIRemoteAudioTrackPublication *)publication
                  forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[didSubscribeToAudioTrack][TwilioVideoViewController] [TVIRemoteParticipantDelegate.didSubscribeToAudioTrack:publication:forParticipant:] send AUDIO_TRACK_ADDED swap REMOTE AUDIO to new TRACK"];

 
    // We are subscribed to the remote Participant's audio Track. We will start receiving the
    // remote Participant's audio now.
    
    [self log_info:[NSString stringWithFormat:@"Subscribed to audio track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: AUDIO_TRACK_ADDED];
    
    [self update_imageViewInCallRemoteMicMuteState_isMuted:FALSE];
}

- (void)didUnsubscribeFromAudioTrack:(nonnull TVIRemoteAudioTrack *)audioTrack
                         publication:(nonnull TVIRemoteAudioTrackPublication *)publication
                      forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[TwilioVideoViewController] [TVIRemoteParticipantDelegate.didUnsubscribeFromAudioTrack:publication:forParticipant:] send AUDIO_TRACK_REMOVED remove REMOTE AUDIO"];

    // We are unsubscribed from the remote Participant's audio Track. We will no longer receive the
    // remote Participant's audio.
    
    [self log_info:[NSString stringWithFormat:@"Unsubscribed from audio track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: AUDIO_TRACK_REMOVED];
    
    [self update_imageViewInCallRemoteMicMuteState_isMuted:TRUE];
}

#pragma mark -
#pragma mark VIDEO TRACK on/off
#pragma mark -

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didEnableVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didEnableVideoTrack] Participant '%@' enabled video track:'%@' >> remoteView.isHidden = FALSE",
                      participant.identity, publication.trackName]];
    
    //remoteView is the video feed - so unhide this
    [self.remoteVideoView setHidden: FALSE];
    
    //and hide the remote photo in cetner of the screen
    [self hide_imageViewRemoteParticipantInCall];
    //see also unPublishVideo - CALLER app enters back ground - Other phone should show remote photo
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didDisableVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
   
    [self log_debug:[NSString stringWithFormat:@"[didDisableVideoTrack][TwilioVideoViewController] [remoteParticipant:didDisableVideoTrack]Participant '%@' disabled video track:'%@' >> remoteView.isHidden = TRUE",
                      participant.identity, publication.trackName]];
    
    //main view is now frozen need to turn it off
    [self.remoteVideoView setHidden: TRUE];

    //and hide the remote photo in center of the screen
    [self show_imageViewRemoteParticipantInCall];

}
#pragma mark -
#pragma mark AUDIO TRACK on/off
#pragma mark -

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didEnableAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_debug:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didEnableAudioTrack] Participant '%@' enabled %@ audio track.",
                      participant.identity, publication.trackName]];
    
    [self show_inCall_remoteUserNameAndMic_isMuted:FALSE];
    
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didDisableAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [remoteParticipant:didDisableAudioTrack] Participant '%@' disabled %@ audio track.",
                      participant.identity, publication.trackName]];
    
    [self show_inCall_remoteUserNameAndMic_isMuted:TRUE];
}

- (void)didFailToSubscribeToAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication
                                 error:(nonnull NSError *)error
                        forParticipant:(nonnull TVIRemoteParticipant *)participant {
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [didFailToSubscribeToAudioTrack:] Participant '%@' failed to subscribe to audio track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)didFailToSubscribeToVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication
                                 error:(nonnull NSError *)error
                        forParticipant:(nonnull TVIRemoteParticipant *)participant {
    
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [didFailToSubscribeToVideoTrack] Participant '%@' failed to subscribe to video track:'%@'.",
                      participant.identity, publication.trackName]];
}


#pragma mark - TVIVideoViewDelegate

- (void)videoView:(nonnull TVIVideoView *)view videoDimensionsDidChange:(CMVideoDimensions)dimensions {
    
    [self log_debug:[NSString stringWithFormat:@"[TwilioVideoViewController] [videoDimensionsDidChange] Dimensions changed to: %d x %d", dimensions.width, dimensions.height]];
    
    [self.view setNeedsLayout];
}

#pragma mark - TVICameraSourceDelegate

- (void)cameraSource:(nonnull TVICameraSource *)source didFailWithError:(nonnull NSError *)error {
    [self log_info:[NSString stringWithFormat:@"[TwilioVideoViewController] [cameraSource:didFailWithError:] Capture failed with error.\ncode = %lu error = %@", error.code, error.localizedDescription]];
}


#pragma mark - ProximityMonitoring

-(BOOL)startProximitySensor
{
    [self log_info:@"[TwilioVideoViewController] startProximitySensor"];
    
    if([UIDevice currentDevice].proximityMonitoringEnabled == FALSE)
        [UIDevice currentDevice].proximityMonitoringEnabled = TRUE;
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(proximityStateDidChange:) name:UIDeviceProximityStateDidChangeNotification object:nil];

    
    return [UIDevice currentDevice].proximityMonitoringEnabled;
}

-(NSString *)printableProximityInfo
{
    return (self.latestProximityReading == TRUE) ? @"CloseProximity" : @"DistantProximity";
}

-(BOOL) latestProximityReading
{
    return [UIDevice currentDevice].proximityState;
}

- (void)proximityStateDidChange:(NSNotification *)notification
{
    //--------------------------------------------------------------------------
    //DEBUG
    //    BOOL state = [UIDevice currentDevice].proximityState;
    //    NSString *msg = [NSString stringWithFormat:@"[TwilioVideoViewController.m] proximityStateDidChange:%d", state];
    //    [self logMessage:msg];
    //[TwilioVideoViewController.m] proximityStateDidChange:0
    //[TwilioVideoViewController.m] proximityStateDidChange:1
    //--------------------------------------------------------------------------
    //VIDEO is ON  > MOVE PHONE TO EAR        > TURN VIDEO OFF
    //               MOVE PHONE AWAY FROM EAR > turn it back on
    
    //VIDEO is OFF > MOVE PHONE TO EAR        > VIDEO STAYS OFF
    //               MOVE PHONE AWAY FROM EAR > dont turn it back on
    //--------------------------------------------------------------------------
    if([UIDevice currentDevice].proximityState){
        //PROXIMITY: is TRUE the phone is near users ear
        //iOS will turn off the screen
        //BUT twilio video will still be on and on other devices with see blurry ear
        
        if(self.localVideoTrack.enabled){
            //--------------------------------------------------------------------------------------
            //Camera was ON when user moved phone to their ear.
            //later when user moves phone away from ear
            //and proximityStateDidChange triggered again
            //we should turn camera back on
            self.localVideoTrack_wasOnBeforeMovedPhoneToEar = TRUE;
            //--------------------------------------------------------------------------------------
            //Phone is at EAR > TURN VIDEO OFF - youll only se this on other phone
            //on this phone iOS turns users screen off
            [self log_info:@"[PROXIMITY: TRUE] TURN OFF VIDEO: self.localVideoTrack.enabled = FALSE"];
            self.localVideoTrack.enabled = FALSE;
            //--------------------------------------------------------------------------------------
            [self viewLocalCameraDisabled_show];
            //--------------------------------------------------------------------------------------
            
        }else{
            //Camera was OFF when user moved phone to their ear.
            //later when user moves phone away from ear >  proximityStateDidChange called again
            //we should NOT turn camera back on
            self.localVideoTrack_wasOnBeforeMovedPhoneToEar = FALSE;
            
            //video already off
            [self log_info:@"[PROXIMITY: TRUE] Video already off - DO NOTHING"];
        }
    }else{
        //PROXIMITY: FALSE - phone is not near face
        //turn on video ONLY IF it had been previously ON
        if(self.localVideoTrack_wasOnBeforeMovedPhoneToEar){
            
            //turn it back on when you move phone away from ear
            self.localVideoTrack.enabled = TRUE;
            [self viewLocalCameraDisabled_hide];
            [self log_info:@"[PROXIMITY: FALSE] TURN VIDEO BACK ON"];
            
        }else{
            //user moved phone away from ear but they had VIDEO off before
            //so dont automatically turn it back on
            [self log_info:@"[PROXIMITY: FALSE] VIDEO was off BEFORE dont turn back on"];
        }
    }
}
//if proximity triggered
//if camera button set to off
-(void)viewLocalCameraDisabled_show{
    [self.viewLocalCameraDisabled setHidden:FALSE];
    
    //video feed gets inserted on top
    [self.viewBorderFor_previewView bringSubviewToFront:self.viewLocalCameraDisabled];
}
-(void)viewLocalCameraDisabled_hide{
    [self.viewLocalCameraDisabled setHidden:TRUE];
}


-(void)viewWillDisappear:(BOOL)animated{
    [super viewWillDisappear: animated];
    
    //if user disconnects while waiting for remote user to answer
    [self dialing_sound_stop];
    //Strange issue where first time was quiet but 2nd, 3rd loud
    //i think maybe multiple players?
    audioPlayer = NULL;
    
    [self log_debug:@"[TwilioVideoViewController.m] viewWillDisappear >> stopCaptureWithCompletion"];
    [self.camera stopCaptureWithCompletion:^(NSError * _Nullable error) {
        if(error){
            [self log_debug:[NSString stringWithFormat:@"[TwilioVideoViewController.m] stopCaptureWithCompletion: >> error:%@", error]];
        }else{
            [self log_debug:@"[TwilioVideoViewController.m] stopCaptureWithCompletion: OK"];
        }
    }];
      
    [self log_debug:@"[TwilioVideoViewController.m] viewWillDisappear >> stopProximitySensor"];
    [self stopProximitySensor];
    
    [self log_info:@"[TwilioVideoViewController.m] viewWillDisappear - VIEW CONTROLLER closed"];
}

-(void)stopProximitySensor
{
    [UIDevice currentDevice].proximityMonitoringEnabled = FALSE;
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIDeviceProximityStateDidChangeNotification object:nil];
    
}


#pragma mark -
#pragma mark DIALING... TONE
#pragma mark -

//ISSUE - IF i turn up the volume and get 50% it get stuck at loud
// I had to start the mp3 BEFORE connecting to the room else it jumps to loud

-(void) dialing_sound_setup{
    
    if (NULL != audioPlayer) {
        NSLog(@"ERROR: audioPlayer is NOT NULL - dont call dialing_sound_setup TWICE youll get same sound played twice sounds VERY LOUD");
    }else{
        //NSError *setCategoryError = nil;
        
     //   [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryAmbient error:&setCategoryError];
        //[[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategorySoloAmbient error:&setCategoryError];
        //[[AVAudioSession sharedInstance] setCategory:AVAudioSessionModeDefault error:&setCategoryError];
        
        //UInt32 mix  = 1;
        //UInt32 duck = 1;
        //AudioSessionSetProperty(kAudioSessionProperty_OverrideCategoryMixWithOthers, sizeof(mix), &mix);
        //AudioSessionSetProperty(kAudioSessionProperty_OtherMixableAudioShouldDuck, sizeof(duck), &duck);
        
        NSString * fileName = @"ringing";
        NSString * fileExtension = @"mp3";
        
        NSURL *soundPath = [[NSURL alloc] initFileURLWithPath:[[NSBundle mainBundle] pathForResource:fileName ofType:fileExtension]];
        if (NULL != soundPath) {
            audioPlayer = [[AVAudioPlayer alloc]initWithContentsOfURL:soundPath error:NULL];
            if (NULL != audioPlayer) {
                [audioPlayer setDelegate:self];
                [audioPlayer prepareToPlay];
                /* APPLE DOCS The volume for the sound. The nominal range is from 0.0 to 1.0. */
                //[audioPlayer setVolume:0.3];
            }else{
                NSLog(@"ERROR: audioPlayer is NULL");
            }
        }else{
            NSLog(@"ERROR: soundPath is NULL cant find sound file in mainBundle called %@.%@", fileName, fileExtension);
        }
    }
}
-(void) dialing_sound_set_volume{
    //FIRST TIME ALWAYS LOUD THEN USES VOLUME
   // [audioPlayer setVolume:0.6];
}
-(void) dialing_sound_start{
    
    //viewDidLoad only called once - even if you press red disconnect and UI disappears
    //2nd time it will jump into openRoom: or answerCall
    if (audioPlayer) {
        [self log_info:@"[dialing_sound_start] audioPlayer is not NULL - ok to start/restart"];
    }else{
        [self log_error:@"[dialing_sound_start] audioPlayer is NULL calling setup"];
        [self dialing_sound_setup];
    }
    
    
    if (audioPlayer) {
        
        if([audioPlayer isPlaying]){
            [self log_debug:@"[dialing_sound_start] [audioPlayer isPlaying] is TRUE - DONT start another one you get a VERY loud audio"];
        }else{
            [self log_debug:@"[dialing_sound_start]  >> [audioPlayer play]"];
            
            [audioPlayer setNumberOfLoops:1];
            
            //[audioPlayer setVolume:0.4];
            [audioPlayer play];
            
            //https://stackoverflow.com/questions/25394627/how-to-lower-the-volume-of-music-in-swift
            //SO - make sure you set volume AFTER PLAY
            [self dialing_sound_set_volume];
        }
        
    }else{
        [self log_error:@"[dialing_sound_start] audioPlayer is NULL [audioPlayer play] FAILED"];
    }
}

-(void) dialing_sound_stop{
    if (audioPlayer) {
        [self log_debug:@"[dialing_sound_stop] >> [audioPlayer stop]"];
        
        [audioPlayer stop];
    }else{
        [self log_error:@"[dialing_sound_stop] audioPlayer is NULL [audioPlayer stop] FAILED"];
    }
}

#pragma mark -
#pragma mark AVAudioPlayerDelegate
#pragma mark -
/* audioPlayerDidFinishPlaying:successfully: is called when a sound has finished playing. This method is NOT called if the player is stopped due to an interruption. */
- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag{
    if (flag) {
        NSLog(@"audioPlayerDidFinishPlaying: successfully: TRUE");
    }else{
        NSLog(@"audioPlayerDidFinishPlaying: successfully: FALSE");
    }
}

/* if an error occurs while decoding it will be reported to the delegate. */
- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError * __nullable)error{
    
    NSLog(@"audioPlayerDecodeErrorDidOccur: error:%@", error);
}





#pragma mark -
#pragma mark LOGGING
#pragma mark -

-(void) configureLogging{
    _log_debug_on = TRUE; //turn off on release
    _log_info_on = TRUE;
    _log_error_on = TRUE; //leave on
}
- (void)log_info:(NSString *)msg {
    if (_log_info_on) {
        NSLog(@"[VIDEOPLUGIN] [INFO ] %@", msg);
    }
}
- (void)log_debug:(NSString *)msg {
    if (_log_debug_on) {
        NSLog(@"[VIDEOPLUGIN] [DEBUG] %@", msg);
    }
}
- (void)log_error:(NSString *)msg {
    if (_log_error_on) {
        NSLog(@"[VIDEOPLUGIN] [ERROR] %@", msg);
    }
}


- (IBAction)buttonDebugStartACall_action:(id)sender {
    [[TwilioVideoManager getInstance] publishEvent: @"DEBUGSTARTACALL"];
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    
    NSLog(@"[ERROR] didReceiveMemoryWarning");
    
}

@end
