#import "TwilioVideoViewController.h"
#import <AVFoundation/AVFoundation.h>

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

@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewRemoteParticipant;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewRemoteParticipantName;
//Calling.../Disconnected
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewRemoteParticipantConnectionState;

@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewRemoteParticipantInfo;

@property (nonatomic, assign) BOOL previewIsFullScreen;

@property (nonatomic, strong) NSString * remoteUserName;
@property (nonatomic, strong) NSString * remoteUserPhotoURL;
@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewAlert;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textFieldAlertTitle;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textFieldAlertSubtitle;

@property (unsafe_unretained, nonatomic) IBOutlet UIButton *buttonDebug_showOffline;
@property (unsafe_unretained, nonatomic) IBOutlet UIButton *buttonDebug_showOnline;

@end

@implementation TwilioVideoViewController

#pragma mark - UIViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    [self.viewAlert setHidden:TRUE];
    
    //hide UIAlerts in plgugin - will be displayed by Sea/chat main app
    showNativeUIAlerts = FALSE;
    
    //---------------------------------------------------------
    //REMOTE USER PANEL
    //---------------------------------------------------------
    self.textViewRemoteParticipantName.text = @"";
    self.textViewRemoteParticipantConnectionState.text = @"";
    
    
    //---------------------------------------------------------
    [self configureLogging];
    
    
    //---------------------------------------
    //DIALING.. MP3
    //---------------------------------------
    [self dialingSound_setup];
    //moved from didConnectToRoom_StartACall - run it before room connects
    //[self dialingSound_start];
    
    //---------------------------------------
    //PREVIEW
    //---------------------------------------
    [self setupPreviewView];
    
    //---------------------------------------
    //DEBUG - draws borders aroud view - handy to track animations
    //---------------------------------------
    //    [self addBorderToView:self.previewView withColor:[UIColor redColor]];
    //    [self addBorderToView:self.viewButtonOuter withColor:[UIColor blueColor]];
    //    [self addBorderToView:self.imageViewOtherUser withColor:[UIColor whiteColor]];
    
     
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
    
    // Disconnect and mic button will be displayed when client is connected to a room.
    self.micButton.hidden = YES;
    [self.micButton setImage:[UIImage imageNamed:@"mic"] forState: UIControlStateNormal];
    [self.micButton setImage:[UIImage imageNamed:@"no_mic"] forState: UIControlStateSelected];
    [self.videoButton setImage:[UIImage imageNamed:@"video"] forState: UIControlStateNormal];
    [self.videoButton setImage:[UIImage imageNamed:@"no_video"] forState: UIControlStateSelected];
    
    // Customize button colors
    NSString *primaryColor = [self.config primaryColorHex];
    if (primaryColor != NULL) {
        self.disconnectButton.backgroundColor = [TwilioVideoConfig colorFromHexString:primaryColor];
    }
    
    NSString *secondaryColor = [self.config secondaryColorHex];
    if (secondaryColor != NULL) {
        self.micButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
        self.videoButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
        self.cameraSwitchButton.backgroundColor = [TwilioVideoConfig colorFromHexString:secondaryColor];
    }
    
    [self startProximitySensor];
}



//DEBUG - draws line aroudn a UIView
-(void)addBorderToView:(UIView *)view withColor:(UIColor *) color{
    //view.layer.borderColor = [[UIColor colorWithRed:70.53/255.0 green:94.54/255.0 blue:107.50/255.0 alpha:1.00] CGColor];
    //view.layer.borderColor = [[UIColor redColor] CGColor];
    view.layer.borderColor = [color CGColor];
    view.layer.borderWidth = 1.0f;
}
-(void)loadUserImage_default{
    NSString *imageName = @"baseline_account_circle_black_18dp.png";
    UIImage * defaultImage = [UIImage imageNamed:imageName];
    if(defaultImage){
        self.imageViewRemoteParticipant.image = defaultImage;
    }else{
        self.imageViewRemoteParticipant.image = nil;
    }
}

-(void)loadUserImageInBackground_async{
   
    [self loadUserImage_default];
    
    self.imageViewRemoteParticipant.backgroundColor = [UIColor whiteColor];
    self.imageViewRemoteParticipant.layer.cornerRadius = self.imageViewRemoteParticipant.frame.size.height / 2.0;
    self.imageViewRemoteParticipant.layer.borderWidth = 4.f;
    self.imageViewRemoteParticipant.layer.borderColor = [[UIColor whiteColor] CGColor];
    
    [self performSelectorInBackground:@selector(loadUserImageInBackground) withObject:nil];

}

- (void)loadUserImageInBackground
{
    //NOT THE SAME AS self.remoteUserPhotoURL = NULL;
    //TO TRIGGER - let global_remote_user_photo_url = null;
    // self.remoteUserPhotoURL is not null it's [NSNull null] description :'<null>'
    
    if(NULL != self.remoteUserPhotoURL){
        
        if([self.remoteUserPhotoURL isEqual:[NSNull null]]){
            [self log_error:@"[loadImage] [self.remoteUserPhotoURL isEqual:[NSNull null]] - JS param is nil somewhere"];
        }else{
            //not obj-C null or JS nil ()
            NSURL * url = [NSURL URLWithString:self.remoteUserPhotoURL];
            //remoteUserPhotoURL is passed in from cordova - check is valid url
            if(url){
                NSData * data = [NSData dataWithContentsOfURL:url];
                if(data){
                    UIImage * image = [UIImage imageWithData:data];
                    if (image)
                    {
                        // set image on main thread
                        dispatch_async(dispatch_get_main_queue(), ^{
                            self.imageViewRemoteParticipant.image = image;
                        });
                    }
                    else
                    {
                        // Failed (load an error image?)
                        [self log_error:[NSString stringWithFormat:@"[loadImage] imageWithData failed to load from self.remoteUserPhotoURL:'%@'", self.remoteUserPhotoURL]];
                        
                        //if no image show blank circle else name an Disconnected look off center
                        //self.imageViewRemoteParticipant.layer.borderWidth = 0.0f;
                        
                        [self loadUserImage_default];
                        
                    }
                }else{
                    [self log_error:[NSString stringWithFormat:@"[loadImage] dataWithContentsOfURL failed to load from self.remoteUserPhotoURL:'%@'", self.remoteUserPhotoURL]];
                }
                
            }else{
                [self log_error:[NSString stringWithFormat:@"[loadImage] URLWithString failed to load from self.remoteUserPhotoURL:'%@'", self.remoteUserPhotoURL]];
            }
        }
    }else{
        [self log_error:@"[loadUserImageInBackground] self.remoteUserPhotoURL is NULL"];
    }
}

-(void)fillIn_viewRemoteParticipantInfo{
    if (self.remoteUserName) {
        self.textViewRemoteParticipantName.text = self.remoteUserName;
    }else{
        [self log_error:@"[fillIn_viewRemoteParticipantInfo] self.remoteUserName is NULL"];
        self.textViewRemoteParticipantName.text = @"";
    }
   
    
    //TODO - url is hard codes should be passed in from cordova
    [self loadUserImageInBackground_async];
    
    //text set in didConnectToRoom_StartACall*
    self.textViewRemoteParticipantConnectionState.text = @"";
}

-(void)show_viewRemoteParticipantInfoWithState:(NSString *) state{
    [self.viewRemoteParticipantInfo setHidden:FALSE];
    
    if(self.textViewRemoteParticipantName != NULL){
        self.textViewRemoteParticipantConnectionState.text = state;
    }else{
        
    }
}

-(void)hide_viewRemoteParticipantInfo{
    [self.viewRemoteParticipantInfo setHidden:TRUE];
    self.textViewRemoteParticipantConnectionState.text = @"";
}

-(void)setupPreviewView{

    //HIDE till my camera connected
    [self hide_viewRemoteParticipantInfo];
    
    //set it always to Fill so it looks ok in fullscreen
    //I tried changing it to Fit/Fill but jumps at the end when it zooms in
    self.previewView.contentMode = UIViewContentModeScaleAspectFill;
    
    //DEBUG - when video is full screen is may have wrong AspectFit or AspectFil
   [self updateConstraints_PreviewView_toFullScreen: TRUE animated:FALSE];
}

-(void)updateConstraints_PreviewView_toFullScreen:(BOOL)fullScreen{
    if(fullScreen){

        //THESE are linked to SuperView not Layoutguide - may go behind nav bar
        self.nsLayoutConstraint_previewView_top.constant = 0.0;
        self.nsLayoutConstraint_previewView_bottom.constant = 0.0;
        
        self.nsLayoutConstraint_previewView_leading.constant = 0.0;
        self.nsLayoutConstraint_previewView_trailing.constant = 0.0;
        
        //self.previewView.contentMode = UIViewContentModeScaleAspectFill;
        
        self.previewIsFullScreen = TRUE;
    }else{
        
        //----------------------------------------------------------------------
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
    
        CGFloat bottom = (self.viewButtonOuter.frame.size.height + self.view.safeAreaInsets.bottom + 8.0);
        
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
    }
}


-(void)updateConstraints_PreviewView_toFullScreen:(BOOL)fullScreen animated:(BOOL)isAnimated{

    //animation in and out should be same number of secs
    NSTimeInterval duration = 0.3;
    
    if(fullScreen){
        if(isAnimated){
            //------------------------------------------------------------------
            //FULL SCREEN + ANIMATED
            //------------------------------------------------------------------
            [UIView animateWithDuration:duration
                                  delay:0
                                options:UIViewAnimationOptionCurveEaseInOut
                             animations:^{
                                //--------------------------------------------------
                                [self updateConstraints_PreviewView_toFullScreen: TRUE];
                                //--------------------------------------------------
                                //will resize but animate without this
                                [self.view layoutIfNeeded];
                                //--------------------------------------------------
                             }
                             completion:^(BOOL finished) {
                                //ANIMATION DONE

                             }
            ];
           
        }else{
            //------------------------------------------------------------------
            //FULL SCREEN + UNANIMATED (when app starts)
            //------------------------------------------------------------------
            [self updateConstraints_PreviewView_toFullScreen: TRUE];
            
        }
    }else{
        if(isAnimated){
            //------------------------------------------------------------------
            //NOT FULL SCREEN + ANIMATED - (dialing ends shrink preview to bottom right)
            //------------------------------------------------------------------
            [UIView animateWithDuration:duration
                                  delay:0
                                options:UIViewAnimationOptionCurveEaseInOut
                             animations:^{
                //--------------------------------------------------
                [self updateConstraints_PreviewView_toFullScreen: FALSE];
                //--------------------------------------------------
                //will resize but animate without this
                [self.view layoutIfNeeded];
                //--------------------------------------------------
            }
            completion:^(BOOL finished) {
                //DONE
            }
             ];
            
        }else{
            //------------------------------------------------------------------
            //NOT FULL SCREEN + UNANIMATED (preview size jumps to bottom right - unused)
            //------------------------------------------------------------------
            [self updateConstraints_PreviewView_toFullScreen: FALSE];
            
        }
    }
    
    //[self.view setNeedsUpdateConstraints];
}



//#pragma mark -
//#pragma mark PUBLIC - connectToRoom
//#pragma mark -

//- (void)connectToRoom:(NSString*)room token:(NSString *)token {
//    #pragma mark TODO - NOW - HARDCODED NEEDS TO BE PASSED IN FROM CORDOVA
//    [self connectToRoom:room
//                  token:token
//         remoteUserName:@"Lorin Kalemi"
//     remoteUserPhotoURL:@"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"];
//}

#pragma mark -
#pragma mark LOCAL part 1 - openRoom
#pragma mark -
- (void)openRoom:(NSString*)room
           token:(NSString *)token
       remoteUserName:(NSString *)remoteUserName
   remoteUserPhotoURL:(NSString *)remoteUserPhotoURL
{
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom]"];
    
    //DEBUG - shows a button to trigger startCall() - NEVER RELEASE
    [self.buttonDebugStartACall setHidden:FALSE];
    //RELEASE
    //[self.buttonDebugStartACall setHidden:TRUE];
    
    self.roomName = room;
    self.accessToken = token;
    
    self.remoteUserName = remoteUserName;
    self.remoteUserPhotoURL = remoteUserPhotoURL;
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];

    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
         if (grantedPermissions) {
             [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
             //[self doConnect];
             //we connect later with startCall
             [self startCamera];
             dispatch_async(dispatch_get_main_queue(), ^{
                 [self displayCallWaiting];
             });
             
             
         } else {
             [self log_error:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
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
    
    [self log_debug:@"[TwilioVideoViewController.m - startCall:]"];
    
    [self.buttonDebugStartACall setHidden:TRUE];
    
    self.roomName = room;
    self.accessToken = token;
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
        if (grantedPermissions) {
            [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
            [self connectToRoom];
            //we connect later with startCall
            //[self startCamera];
            
        } else {
            [self log_error:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
            [[TwilioVideoManager getInstance] publishEvent: PERMISSIONS_REQUIRED];
            [self handleConnectionError: [self.config i18nConnectionError]];
        }
    }];
}

#pragma mark -
#pragma mark LOCAL part 2 - answerCall
#pragma mark -
//CONNECT TO THE
- (void)answerCall:(NSString*)room
             token:(NSString *)token
{
    
    [self log_debug:@"[TwilioVideoViewController.m - answerCall:]"];
    
    [self.buttonDebugStartACall setHidden:TRUE];
    
    self.roomName = room;
    self.accessToken = token;
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
        if (grantedPermissions) {
            [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
            [self startCamera];
            
            [self connectToRoom];
            //we connect later with answerCall
            //[self startCamera];
            
        } else {
            [self log_error:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions: grantedPermissions:FALSE > send PERMISSIONS_REQUIRED"];
            [[TwilioVideoManager getInstance] publishEvent: PERMISSIONS_REQUIRED];
            [self handleConnectionError: [self.config i18nConnectionError]];
        }
    }];
}
#pragma mark -
#pragma mark BUTTONS
#pragma mark -

//MUTE VIDEO BUTTON
-(void)videoButton_changeIconTo_on{
    [self.videoButton setSelected: FALSE];
}
-(void)videoButton_changeIconTo_off{
    [self.videoButton setSelected: TRUE];
}

- (IBAction)videoButtonPressed:(id)sender {
    if(self.localVideoTrack){
        self.localVideoTrack.enabled = !self.localVideoTrack.isEnabled;
        //changes icon
        //[self.videoButton setSelected: !self.localVideoTrack.isEnabled];
        if(self.localVideoTrack.isEnabled){
            [self videoButton_changeIconTo_on];
        }else{
            [self videoButton_changeIconTo_off];
        }
    }
    //DEBUG [self updateConstraints_PreviewView_toFullScreen: TRUE animated:TRUE];
    
    //DEBUG [self dialingSound_start];
}

//MUTE VIDEO BUTTON
-(void)micButton_changeIconTo_on{
    [self.micButton setSelected: FALSE];
}
-(void)micButton_changeIconTo_off{
    [self.micButton setSelected: TRUE];
}
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
    //DEBUG [self dialingSound_stop];
}

- (IBAction)cameraSwitchButtonPressed:(id)sender {
    [self flipCamera];
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
                self.cameraSwitchButton.hidden = NO;
            }
            
            //UNHIDE video button(so user can turn it off if needed)
            self.videoButton.hidden = NO;
            
            //change icon
            //audio was on background thread - adding this for safety
            dispatch_async(dispatch_get_main_queue(), ^{
                if(self.localVideoTrack.isEnabled){
                    [self videoButton_changeIconTo_on];
                }else{
                    [self videoButton_changeIconTo_off];
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
                         [self show_viewRemoteParticipantInfoWithState:@""];
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
        
        [self fillIn_viewRemoteParticipantInfo];
        
        [self show_viewRemoteParticipantInfoWithState:@"Calling..."];
        
        //----------------------------------------------------------------------
        //show LOCAL USER full screen while waiting for othe ruser to answer
        [self updateConstraints_PreviewView_toFullScreen:TRUE animated:FALSE];
        
        //----------------------------------------------------------------------
        //FIRST TIME VERY LOUD - cant set volume to 0
        //NEXT TIMES too quiet
        //will start it before room connect in viewDidLoad
        [self dialingSound_start];
        //----------------------------------------------------------------------
        
    }else{
        [self log_error:@"[participantDidConnect] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

//On the ANSWERING PHONE it will trigger
//didConnectToRoom_AnswerACall only
-(void)didConnectToRoom_AnswerACall{
    [self log_info:@"[didConnectToRoom_AnswerACall] START REMOTE USER in Room - show Waiting... till caller enters room"];
    
    if(self.previewIsFullScreen){
        //------------------------------------------------------------------------------
        //CLEANUP
        //        REMOTE USER CONNECTED
        //        //Hide the dialing screen
        //        [self hide_viewRemoteParticipantInfo];
        //
        //        //Zoom the preview from FULL SCREEN to MINI
        //        [self updateConstraints_PreviewView_toFullScreen: FALSE animated:TRUE];
        
        //------------------------------------------------------------------------------
        //REMOTE USER CONNECTED.. waiting for CALLER to enter room
        [self show_viewRemoteParticipantInfoWithState:@"Waiting..."];
        
    }else{
        [self log_error:@"[participantDidConnect] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}


//called by TVIRoomDelegate.participantDidConnect
//Same app installed on both phones but UI changes depending on who starts or answers a call
//1 local + 0 remote - LOCAL USER is person dialing REMOTE participant.
//Remote hasnt joined the room yet so hasnt answered so show 'Dialing..'
//On the CALLING PHONE it will trigger
//didConnectToRoom_StartACall >> participantDidConnect_RemoteUserHasAnswered
-(void)participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom{
    [self log_info:@"[participantDidConnect_StartACall] START"];

    [self dialingSound_stop];

    if(self.previewIsFullScreen){
        //hide Waiting...
        [self hide_viewRemoteParticipantInfo];

        //REMOTE user is visible in full screen
        //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
        [self updateConstraints_PreviewView_toFullScreen: FALSE animated:FALSE];

    }else{
        [self log_error:@"[participantDidConnect] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

-(void)participantDidConnect_LocalUserAndCallerHasConnectedToRoom{
    [self log_info:@"[participantDidConnect_StartACall] START"];
    
    [self dialingSound_stop];
    
    if(self.previewIsFullScreen){
        
        [self hide_viewRemoteParticipantInfo];
        
        //REMOTE user is visible in full screen
        //shrink PREVIEW from FULL SCREEN to MINI to show REMOTE user behind
        [self updateConstraints_PreviewView_toFullScreen: FALSE animated:FALSE];
        
    }else{
        [self log_error:@"[participantDidConnect] new participant joined room BUT previewIsFullScreen is false - shouldnt happen for 1..1 CALL"];
    }
}

//called by TVIRoomDelegate.participantDidConnect
//Same app installed on both phones but UI changes depending on who starts or answers a call
//1 local + 1 remote - REMOTE user in room is the other person who started the call
//LOCAL USER is answering a call so dont show 'Dialing..'
-(void)participantDidConnect{
    [self log_info:@"[participantDidConnect_AnswerACall] Unused in 1..1 - use for GROUP"];
}

-(void)participantDidDisconnect:(NSString *)remoteParticipant_identity;{
    
    if(self.previewIsFullScreen){
        [self log_error:@"[participantDidDisconnect] new participant joined room BUT previewIsFullScreen is true - shouldnt happen for 1..1 CALL"];
        
    }else{
        //REMOTE USER DISCONNECTED
        //show the remote user panel with state 'Disconnected'
        
        //if app running on REMOTE photo will just show white circle no photo
        //this is so Disconnected isnt off center
        [self loadUserImageInBackground_async];
        if(remoteParticipant_identity){
            self.textViewRemoteParticipantName.text = remoteParticipant_identity;
            
        }else{
            [self log_error:@"[participantDidDisconnect:] remoteParticipant_identity is NULL - if LOCAL hangs up before REMOTE then no photo or name just 'Disconnected may show'"];
        }
        [self show_viewRemoteParticipantInfoWithState:@"Disconnected"];
        
        //Zoom the preview from MINI to FULL SCREEN
        [self updateConstraints_PreviewView_toFullScreen:TRUE animated:TRUE];
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
    [self log_debug:@"[TwilioVideoViewController.m - startCamera] START"];
    
    if ([self.accessToken isEqualToString:@"TWILIO_ACCESS_TOKEN"]) {
        [self log_info:@"Please provide a valid token to connect to a room"];
        return;
    }
    
    //--------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] >> prepareLocalMedia"];
    
    // Prepare local media which we will share with Room Participants.
    [self prepareLocalMedia];
    //--------------------------------------------------------------------------
    
}

- (void)connectToRoom {
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] START"];
    
    if ([self.accessToken isEqualToString:@"TWILIO_ACCESS_TOKEN"]) {
        [self log_info:@"Please provide a valid token to connect to a room"];
        return;
    }
    
    //--------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] >> prepareLocalMedia"];
    
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
    
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] >> connectWithOptions:connectOptions (LOCAL VIDEO AUDIO) >> ROOM"];
    self.room = [TwilioVideoSDK connectWithOptions:connectOptions delegate:self];
    
    [self log_info:@"Attempting to connect to room"];
}

#pragma mark -
#pragma mark remoteView
#pragma mark -

- (void)setupRemoteView {
    [self log_debug:@"[TwilioVideoViewController.m - setupRemoteView] MAKE TVIVideoView *remoteView"];

    // Creating `TVIVideoView` programmatically
    TVIVideoView *remoteView = [[TVIVideoView alloc] init];
        
    // `TVIVideoView` supports UIViewContentModeScaleToFill, UIViewContentModeScaleAspectFill and UIViewContentModeScaleAspectFit
    // UIViewContentModeScaleAspectFit is the default mode when you create `TVIVideoView` programmatically.
    remoteView.contentMode = UIViewContentModeScaleAspectFill;

    [self.view insertSubview:remoteView atIndex:0];
    self.remoteView = remoteView;
    
    NSLayoutConstraint *centerX = [NSLayoutConstraint constraintWithItem:self.remoteView
                                                               attribute:NSLayoutAttributeCenterX
                                                               relatedBy:NSLayoutRelationEqual
                                                                  toItem:self.view
                                                               attribute:NSLayoutAttributeCenterX
                                                              multiplier:1
                                                                constant:0];
    [self.view addConstraint:centerX];
    NSLayoutConstraint *centerY = [NSLayoutConstraint constraintWithItem:self.remoteView
                                                               attribute:NSLayoutAttributeCenterY
                                                               relatedBy:NSLayoutRelationEqual
                                                                  toItem:self.view
                                                               attribute:NSLayoutAttributeCenterY
                                                              multiplier:1
                                                                constant:0];
    [self.view addConstraint:centerY];
    NSLayoutConstraint *width = [NSLayoutConstraint constraintWithItem:self.remoteView
                                                             attribute:NSLayoutAttributeWidth
                                                             relatedBy:NSLayoutRelationEqual
                                                                toItem:self.view
                                                             attribute:NSLayoutAttributeWidth
                                                            multiplier:1
                                                              constant:0];
    [self.view addConstraint:width];
    
    NSLayoutConstraint *height = [NSLayoutConstraint constraintWithItem:self.remoteView
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
    [self log_debug:@"[TwilioVideoViewController.m - showRoomUI] hide mic/ show spinner"];

    self.micButton.hidden = !inRoom;
    [UIApplication sharedApplication].idleTimerDisabled = inRoom;
}

#pragma mark -
#pragma mark - OFFLINE ALERT
#pragma mark -

//internet gone - seachat tells plugin to show alert
- (void)showOffline{
    [self.viewAlert setHidden:FALSE];
    [self.view bringSubviewToFront:self.viewAlert];
    
    [self.buttonDebug_showOnline setHidden:FALSE];
    [self.view bringSubviewToFront:self.buttonDebug_showOnline];

}

//internet returned - seachat tells plugin to hide alert
- (void)showOnline{
    [self.viewAlert setHidden:TRUE];
 
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
    [self log_debug:@"[TwilioVideoViewController.m - cleanupRemoteParticipant]"];
    if (self.remoteParticipant) {
        if ([self.remoteParticipant.videoTracks count] > 0) {
            TVIRemoteVideoTrack *videoTrack = self.remoteParticipant.remoteVideoTracks[0].remoteTrack;
            [videoTrack removeRenderer:self.remoteView];
            [self.remoteView removeFromSuperview];
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
    [self log_debug:@"[TwilioVideoViewController.m - TwilioVideoActionProducerDelegate.onDisconnect] >> [self.room disconnect]"];
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
    
    [self log_debug:@"[TwilioVideoViewController.m - TVIRoomDelegate.didConnectToRoom] >> GET FIRST room.remoteParticipants[0]"];
    
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
        [self participantDidConnect_LocalUserAndCallerHasConnectedToRoom];
        //----------------------------------------------------------------------
    }
    else{
        [self log_error:[NSString stringWithFormat:@"[didConnectToRoom] room.remoteParticipants count:%lu >> UNHANDLED MORE THAN 2 USERS in room 1 LOCAL + %lu REMOTE - TODO IN FUTURE GROUP CALLS",
                         (unsigned long)[room.remoteParticipants count],
                         (unsigned long)[room.remoteParticipants count]]];
    }
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
    [[TwilioVideoManager getInstance] publishEvent: RECONNECTING with:[TwilioVideoUtils convertErrorToDictionary:error]];
}

- (void)didReconnectToRoom:(nonnull TVIRoom *)room {
    [[TwilioVideoManager getInstance] publishEvent: RECONNECTED];
}

- (void)room:(nonnull TVIRoom *)room participantDidConnect:(nonnull TVIRemoteParticipant *)participant {
    
    [self log_debug:@"[TwilioVideoViewController.m - TVIRoomDelegate.participantDidConnect] "];
    
    if (!self.remoteParticipant) {
        self.remoteParticipant = participant;
        self.remoteParticipant.delegate = self;
    }
    [self log_info:[NSString stringWithFormat:@"[room:participantDidConnect:] Participant '%@' connected with %lu audio and %lu video tracks",
                      participant.identity,
                      (unsigned long)[participant.audioTracks count],
                      (unsigned long)[participant.videoTracks count]]];
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_CONNECTED];
    
    
    
    if([room.remoteParticipants count] == 0){
        //----------------------------------------------------------------------
        //1..1 CALL - no remote users so I an STARTING A CALL
        //----------------------------------------------------------------------
        [self log_info:[NSString stringWithFormat:@"[room:participantDidConnect:] room.remoteParticipants count:%lu >> LOCAL USER is STARTING A 1..1 CALL",
                        (unsigned long)[room.remoteParticipants count]]];
        //----------------------------------------------------------------------
        //[self participantDidConnect_AnswerACall];
        //used didConnectToRoom_AnswerACall instead
        //for GROUP participantDidConnect will do thinks like inc particpant count
        //show list of users etc
        //----------------------------------------------------------------------
    }
    else if([room.remoteParticipants count] == 1){
        //----------------------------------------------------------------------
        //1..1 CALL - 1 remote user in room so LOCAL USER is ANSWERING a CALL
        //----------------------------------------------------------------------
        [self log_info:[NSString stringWithFormat:@"[room:participantDidConnect:] room.remoteParticipants count:%lu >> REMOTE USER is ANSWERING A 1..1 CALL",
                        (unsigned long)[room.remoteParticipants count]]];
        //----------------------------------------------------------------------
        //1 LOCAL  - PERSON BEING CALLED
        //1 REMOTE - CALLER
        [self participantDidConnect_RemoteUserSide_CallerHasEnteredTheRoom];
        //----------------------------------------------------------------------
    }
    else{
        [self log_error:[NSString stringWithFormat:@"[room:participantDidConnect:] room.remoteParticipants count:%lu >> UNHANDLED MORE THAN 2 USERS in room 1 LOCAL + %lu REMOTE - TODO IN FUTURE GROUP CALLS",
                        (unsigned long)[room.remoteParticipants count],
                         (unsigned long)[room.remoteParticipants count]]];
    }
    
}

- (void)room:(nonnull TVIRoom *)room participantDidDisconnect:(nonnull TVIRemoteParticipant *)participant {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRoomDelegate.participantDidDisconnect] "];
    
    
    if (self.remoteParticipant == participant) {
        [self cleanupRemoteParticipant];
    }
    [self log_info:[NSString stringWithFormat:@"Room %@ participant %@ disconnected", room.name, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_DISCONNECTED];
    // if LOCAL caller hangs up first the REMOTE phone will show 'Disconnected'
    // but self.remoteUserName is NULL on REMOTE side so no IMAGE or NAME
    // so pass in the identity of the participant that disconnected to we can say
    //'John Smith Disconnected'
    
    [self participantDidDisconnect:participant.identity];
}


#pragma mark - TVIRemoteParticipantDelegate

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didPublishVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didPublishVideoTrack] DO NOTHING JUST LOG"];
    
    // Remote Participant has offered to share the video Track.
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' published video track:'%@' .",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didUnpublishVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didUnpublishVideoTrack] DO NOTHING JUST LOG"];
    // Remote Participant has stopped sharing the video Track.
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' unpublished video track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didPublishAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didPublishAudioTrack] DO NOTHING JUST LOG"];
    // Remote Participant has offered to share the audio Track.
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' published audio track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didUnpublishAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didUnpublishAudioTrack] DO NOTHING JUST LOG"];

    // Remote Participant has stopped sharing the audio Track.
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' unpublished audio track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)didSubscribeToVideoTrack:(nonnull TVIRemoteVideoTrack *)videoTrack
                     publication:(nonnull TVIRemoteVideoTrackPublication *)publication
                  forParticipant:(nonnull TVIRemoteParticipant *)participant {
    
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.didSubscribeToVideoTrack:publication: forParticipant] send VIDEO_TRACK_ADDED swap REMOTE VIDEO to new TRACK"];

    
    // We are subscribed to the remote Participant's audio Track. We will start receiving the
    // remote Participant's video frames now.
    
    [self log_info:[NSString stringWithFormat:@"Subscribed to video track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: VIDEO_TRACK_ADDED];

    if (self.remoteParticipant == participant) {
        [self setupRemoteView];
        [videoTrack addRenderer:self.remoteView];
    }
}

- (void)didUnsubscribeFromVideoTrack:(nonnull TVIRemoteVideoTrack *)videoTrack
                         publication:(nonnull TVIRemoteVideoTrackPublication *)publication
                      forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.didUNSUBSCRIBEFromVideoTrack:publication: forParticipant] send VIDEO_TRACK_REMOVED remove REMOTE VIDEO"];

    // We are unsubscribed from the remote Participant's video Track. We will no longer receive the
    // remote Participant's video.
    
    [self log_info:[NSString stringWithFormat:@"Unsubscribed from video track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: VIDEO_TRACK_REMOVED];
    
    if (self.remoteParticipant == participant) {
        [videoTrack removeRenderer:self.remoteView];
        [self.remoteView removeFromSuperview];
    }
}

- (void)didSubscribeToAudioTrack:(nonnull TVIRemoteAudioTrack *)audioTrack
                     publication:(nonnull TVIRemoteAudioTrackPublication *)publication
                  forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.didSubscribeToAudioTrack:publication: forParticipant] send AUDIO_TRACK_ADDED swap REMOTE AUDIO to new TRACK"];

 
    // We are subscribed to the remote Participant's audio Track. We will start receiving the
    // remote Participant's audio now.
    
    [self log_info:[NSString stringWithFormat:@"Subscribed to audio track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: AUDIO_TRACK_ADDED];
}

- (void)didUnsubscribeFromAudioTrack:(nonnull TVIRemoteAudioTrack *)audioTrack
                         publication:(nonnull TVIRemoteAudioTrackPublication *)publication
                      forParticipant:(nonnull TVIRemoteParticipant *)participant {

    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.didUnsubscribeFromAudioTrack:publication: forParticipant] send AUDIO_TRACK_REMOVED remove REMOTE AUDIO"];

    // We are unsubscribed from the remote Participant's audio Track. We will no longer receive the
    // remote Participant's audio.
    
    [self log_info:[NSString stringWithFormat:@"Unsubscribed from audio track:'%@' for Participant '%@'",
                      publication.trackName, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: AUDIO_TRACK_REMOVED];
}

#pragma mark -
#pragma mark VIDEO TRACK on/off
#pragma mark -

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didEnableVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didEnableVideoTrack] >> UNHIDE self.remoteView"];

    [self log_info:[NSString stringWithFormat:@"Participant '%@' enabled video track:'%@' >> remoteView.isHidden = FALSE",
                      participant.identity, publication.trackName]];
    
    [self.remoteView setHidden: FALSE];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didDisableVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication {
   
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didDisableVideoTrack] >> HIDE self.remoteView"];

    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' disabled video track:'%@' >> remoteView.isHidden = TRUE",
                      participant.identity, publication.trackName]];
    
    //main view is now frozen need to turn it off
    [self.remoteView setHidden: TRUE];
    
}
#pragma mark -
#pragma mark AUDIO TRACK on/off
#pragma mark -

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didEnableAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didEnableAudioTrack] >> do nothing"];
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' enabled %@ audio track.",
                      participant.identity, publication.trackName]];
}

- (void)remoteParticipant:(nonnull TVIRemoteParticipant *)participant didDisableAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRemoteParticipantDelegate.remoteParticipant:didDisableAudioTrack] >> do nothing"];
    
    [self log_info:[NSString stringWithFormat:@"Participant '%@' disabled %@ audio track.",
                      participant.identity, publication.trackName]];
}

- (void)didFailToSubscribeToAudioTrack:(nonnull TVIRemoteAudioTrackPublication *)publication
                                 error:(nonnull NSError *)error
                        forParticipant:(nonnull TVIRemoteParticipant *)participant {
    [self log_info:[NSString stringWithFormat:@"Participant '%@' failed to subscribe to audio track:'%@'.",
                      participant.identity, publication.trackName]];
}

- (void)didFailToSubscribeToVideoTrack:(nonnull TVIRemoteVideoTrackPublication *)publication
                                 error:(nonnull NSError *)error
                        forParticipant:(nonnull TVIRemoteParticipant *)participant {
    [self log_info:[NSString stringWithFormat:@"Participant '%@' failed to subscribe to video track:'%@'.",
                      participant.identity, publication.trackName]];
}


#pragma mark - TVIVideoViewDelegate

- (void)videoView:(nonnull TVIVideoView *)view videoDimensionsDidChange:(CMVideoDimensions)dimensions {
    
    [self log_debug:[NSString stringWithFormat:@"Dimensions changed to: %d x %d", dimensions.width, dimensions.height]];
    
    [self.view setNeedsLayout];
}

#pragma mark - TVICameraSourceDelegate

- (void)cameraSource:(nonnull TVICameraSource *)source didFailWithError:(nonnull NSError *)error {
    [self log_info:[NSString stringWithFormat:@"Capture failed with error.\ncode = %lu error = %@", error.code, error.localizedDescription]];
}


#pragma mark - ProximityMonitoring

-(BOOL)startProximitySensor
{
    [self log_info:@"[TwilioVideoViewController.m] startProximitySensor"];
    
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
            //Camera was ON when user moved phone to their ear.
            //later when user moves phone away from ear
            //and proximityStateDidChange triggered again
            //we should turn camera back on
            self.localVideoTrack_wasOnBeforeMovedPhoneToEar = TRUE;
            
            //Phone is at EAR > TURN VIDEO OFF - youll only se this on other phone
            //on this phone iOS turns users screen off
            [self log_info:@"[PROXIMITY: TRUE] TURN OFF VIDEO: self.localVideoTrack.enabled = FALSE"];
            self.localVideoTrack.enabled = FALSE;
            
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
            [self log_info:@"[PROXIMITY: FALSE] TURN VIDEO BACK ON"];
            
        }else{
            //user moved phone away from ear but they had VIDEO off before
            //so dont automatically turn it back on
            [self log_info:@"[PROXIMITY: FALSE] VIDEO was off BEFORE dont turn back on"];
        }
    }
}

-(void)viewWillDisappear:(BOOL)animated{
    [super viewWillDisappear: animated];
    
    //if user disconnects while waiting for remote user to answer
    [self dialingSound_stop];
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

-(void) dialingSound_setup{
    
    if (NULL != audioPlayer) {
        NSLog(@"ERROR: audioPlayer is NOT NULL - dont call dialingSound_setup TWICE youll get same sound played twice sounds VERY LOUD");
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
-(void) dialingSound_setVolume{
    //FIRST TIME ALWAYS LOUD THEN USES VOLUME
   // [audioPlayer setVolume:0.6];
}
-(void) dialingSound_start{
    
    //viewDidLoad only called once - even if you press red disconnect and UI disappears
    //2nd time it will jump into openRoom: or answerCall
    if (audioPlayer) {
        [self log_info:@"[dialingSound_start] audioPlayer is not NULL - ok to start/restart"];
    }else{
        [self log_error:@"[dialingSound_start] audioPlayer is NULL calling setup"];
        [self dialingSound_setup];
    }
    
    
    if (audioPlayer) {
        
        if([audioPlayer isPlaying]){
            [self log_debug:@"[dialingSound_start] [audioPlayer isPlaying] is TRUE - DONT start another one you get a VERY loud audio"];
        }else{
            [self log_debug:@"[dialingSound_start]  >> [audioPlayer play]"];
            
            [audioPlayer setNumberOfLoops:1];
            
            //[audioPlayer setVolume:0.4];
            [audioPlayer play];
            
            //https://stackoverflow.com/questions/25394627/how-to-lower-the-volume-of-music-in-swift
            //SO - make sure you set volume AFTER PLAY
            [self dialingSound_setVolume];
        }
        
    }else{
        [self log_error:@"[dialingSound_start] audioPlayer is NULL [audioPlayer play] FAILED"];
    }
}

-(void) dialingSound_stop{
    if (audioPlayer) {
        [self log_debug:@"[dialingSound_stop] >> [audioPlayer stop]"];
        
        [audioPlayer stop];
    }else{
        [self log_error:@"[dialingSound_stop] audioPlayer is NULL [audioPlayer stop] FAILED"];
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
    _log_debug_on = FALSE; //turn off on release
    _log_info_on = TRUE;
    _log_error_on = TRUE; //leave on
}
- (void)log_info:(NSString *)msg {
    if (_log_info_on) {
        NSLog(@"[INFO ] %@", msg);
    }
}
- (void)log_debug:(NSString *)msg {
    if (_log_debug_on) {
        NSLog(@"[DEBUG] %@", msg);
    }
}
- (void)log_error:(NSString *)msg {
    if (_log_error_on) {
        NSLog(@"[ERROR] %@", msg);
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
