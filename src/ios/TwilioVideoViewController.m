#import "TwilioVideoViewController.h"

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

@interface TwilioVideoViewController(){
    BOOL _log_info_on;
    BOOL _log_debug_on;
    BOOL _log_error_on;

}

//Proximity Monitoring
@property (nonatomic, assign) BOOL localVideoTrack_wasOnBeforeMovedPhoneToEar;

@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_top;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_bottom;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_leading;
@property (unsafe_unretained, nonatomic) IBOutlet NSLayoutConstraint *nsLayoutConstraint_previewView_trailing;

//the default values for the 4 constraints for previewView BEFORE we go full screen
//We will reset to these values when we leave full screen
//values are set out in Storyboard
//@property (nonatomic, assign) CGFloat nsLayoutConstraint_previewView_top_constant;
//@property (nonatomic, assign) CGFloat nsLayoutConstraint_previewView_bottom_constant;
//@property (nonatomic, assign) CGFloat nsLayoutConstraint_previewView_leading_constant;
//@property (nonatomic, assign) CGFloat nsLayoutConstraint_previewView_trailing_constant;


//we animate preview to be above this
@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewButtonOuter;

@property (unsafe_unretained, nonatomic) IBOutlet UIImageView *imageViewOtherUser;
@property (unsafe_unretained, nonatomic) IBOutlet UILabel *textViewOtherUserName;
@property (unsafe_unretained, nonatomic) IBOutlet UIView *viewCallingInfo;

@end

@implementation TwilioVideoViewController

#pragma mark - UIViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    [self configureLogging];
    
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
    
    self.imageViewOtherUser.layer.cornerRadius = self.imageViewOtherUser.frame.size.height / 2.0;
    self.imageViewOtherUser.layer.borderWidth = 5.f;
    
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

-(void)loadUserImageInBackground_async{
    [self performSelectorInBackground:@selector(loadUserImageInBackground) withObject:nil];

}

- (void)loadUserImageInBackground
{
    NSURL * url = [NSURL URLWithString:@"https://sealogin-trfm-prd-cdn.azureedge.net/API/1_3/User/picture?imageUrl=673623fdc8b39b5b05b3167765019398.jpg"];
    NSData * data = [NSData dataWithContentsOfURL:url];
    UIImage * image = [UIImage imageWithData:data];
    if (image)
    {
        // Success use the image
        dispatch_async(dispatch_get_main_queue(), ^{
            self.imageViewOtherUser.image = image;
        });
    }
    else
    {
        // Failed (load an error image?)
        [self log_error:@"[loadImage] failed to load from url ] "];
    }
}

-(void)otherUserCallingPanel_configure{
    self.textViewOtherUserName.text = @"Lorin Kaliemi";
    
    //TODO - url is hard codes should be passed in from cordova
    [self loadUserImageInBackground_async];
}

-(void)otherUserCallingPanel_isVisible:(BOOL)isVisible{
    if(isVisible){
        [self.viewCallingInfo setHidden:FALSE];
        
    }else{
        [self.viewCallingInfo setHidden:TRUE];
       
    }
}

-(void)setupPreviewView{
    
    [self otherUserCallingPanel_configure];
    //HIDE till my camera connected
    [self otherUserCallingPanel_isVisible:FALSE];
    
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
                                //DONE
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



#pragma mark -
#pragma mark PUBLIC - connectToRoom
#pragma mark -

- (void)connectToRoom:(NSString*)room token:(NSString *)token {
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom]"];
    
    self.roomName = room;
    self.accessToken = token;
    
    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> [self showRoomUI:YES]"];
    
    [self showRoomUI:YES];

    [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions"];
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
         if (grantedPermissions) {
             [self log_debug:@"[TwilioVideoViewController.m - connectToRoom] >> requestRequiredPermissions:OK > doConnect"];
             [self doConnect];
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

- (IBAction)videoButtonPressed:(id)sender {
//    if(self.localVideoTrack){
//        self.localVideoTrack.enabled = !self.localVideoTrack.isEnabled;
//        [self.videoButton setSelected: !self.localVideoTrack.isEnabled];
//    }
    [self updateConstraints_PreviewView_toFullScreen: TRUE animated:TRUE];
}


- (IBAction)micButtonPressed:(id)sender {
    // We will toggle the mic to mute/unmute and change the title according to the user action.
    
//    if (self.localAudioTrack) {
//        self.localAudioTrack.enabled = !self.localAudioTrack.isEnabled;
//        // If audio not enabled, mic is muted and button crossed out
//        [self.micButton setSelected: !self.localAudioTrack.isEnabled];
//    }
    [self updateConstraints_PreviewView_toFullScreen: FALSE animated:TRUE];
}

- (IBAction)cameraSwitchButtonPressed:(id)sender {
    [self flipCamera];
}

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
        self.localVideoTrack = [TVILocalVideoTrack trackWithSource:self.camera
                                                             enabled:YES
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
                         [self otherUserCallingPanel_isVisible:TRUE];
                         [self.view bringSubviewToFront:self.viewCallingInfo];
                     }
            }];
        }
    } else {
       [self log_info:@"No front or back capture device found!"];
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
        //----------------------------------------------------------------------
        self.localAudioTrack = [TVILocalAudioTrack trackWithOptions:nil
                                                            enabled:YES
                                                               name:@"Microphone"];
        //----------------------------------------------------------------------
        if (!self.localAudioTrack) {
            [self log_info:@"Failed to add audio track"];
        }
        //----------------------------------------------------------------------
    }
    
    // Create a video track which captures from the camera.
    if (!self.localVideoTrack) {
        [self startPreview];
    }
}

- (void)doConnect {
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] START"];
    
    if ([self.accessToken isEqualToString:@"TWILIO_ACCESS_TOKEN"]) {
        [self log_info:@"Please provide a valid token to connect to a room"];
        return;
    }
    
    //--------------------------------------------------------------------------
    [self log_debug:@"[TwilioVideoViewController.m - doConnect] >> prepareLocalMedia"];
    
    // Prepare local media which we will share with Room Participants.
    [self prepareLocalMedia];
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
    UIAlertController * alert = [UIAlertController
                                 alertControllerWithTitle:NULL
                                 message: message
                                 preferredStyle:UIAlertControllerStyleAlert];
    
    //Add Buttons
    
    UIAlertAction* yesButton = [UIAlertAction
                                actionWithTitle:[self.config i18nAccept]
                                style:UIAlertActionStyleDefault
                                handler: ^(UIAlertAction * action) {
                                    [self dismiss];
                                }];
    
    [alert addAction:yesButton];
    [self presentViewController:alert animated:YES completion:nil];
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
    }
}

#pragma mark - TVIRoomDelegate

- (void)didConnectToRoom:(nonnull TVIRoom *)room {
    
    [self log_debug:@"[TwilioVideoViewController.m - TVIRoomDelegate.didConnectToRoom] >> GET FIRST room.remoteParticipants[0]"];
    
    // At the moment, this example only supports rendering one Participant at a time.
    [self log_info:[NSString stringWithFormat:@"Connected to room %@ as %@", room.name, room.localParticipant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: CONNECTED];
    
    //NO MATTER HOW MANY ROOM PARTICIPANTS - just pick the first
    if (room.remoteParticipants.count > 0) {
        self.remoteParticipant = room.remoteParticipants[0];
        self.remoteParticipant.delegate = self;
    }
}

- (void)room:(nonnull TVIRoom *)room didFailToConnectWithError:(nonnull NSError *)error {
    [self log_info:[NSString stringWithFormat:@"Failed to connect to room, error = %@", error]];
    [[TwilioVideoManager getInstance] publishEvent: CONNECT_FAILURE with:[TwilioVideoUtils convertErrorToDictionary:error]];
    
    self.room = nil;
    
    [self showRoomUI:NO];
    [self handleConnectionError: [self.config i18nConnectionError]];
}

- (void)room:(nonnull TVIRoom *)room didDisconnectWithError:(nullable NSError *)error {
    [self log_info:[NSString stringWithFormat:@"Disconnected from room %@, error = %@", room.name, error]];
    
    [self cleanupRemoteParticipant];
    self.room = nil;
    
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
    [self log_info:[NSString stringWithFormat:@"Participant '%@' connected with %lu audio and %lu video tracks",
                      participant.identity,
                      (unsigned long)[participant.audioTracks count],
                      (unsigned long)[participant.videoTracks count]]];
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_CONNECTED];
}

- (void)room:(nonnull TVIRoom *)room participantDidDisconnect:(nonnull TVIRemoteParticipant *)participant {
    [self log_debug:@"[TwilioVideoViewController.m - TVIRoomDelegate.participantDidDisconnect] "];
    
    
    if (self.remoteParticipant == participant) {
        [self cleanupRemoteParticipant];
    }
    [self log_info:[NSString stringWithFormat:@"Room %@ participant %@ disconnected", room.name, participant.identity]];
    [[TwilioVideoManager getInstance] publishEvent: PARTICIPANT_DISCONNECTED];
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
    [self log_info:@"[TwilioVideoViewController.m] viewWillDisappear >> stopProximitySensor"];
    
    [self stopProximitySensor];
    
}

-(void)stopProximitySensor
{
    [UIDevice currentDevice].proximityMonitoringEnabled = FALSE;
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIDeviceProximityStateDidChangeNotification object:nil];
    
}

#pragma mark -
#pragma mark LOGGING
#pragma mark -

-(void) configureLogging{
    _log_info_on = TRUE;
    _log_debug_on = TRUE;
    _log_error_on = TRUE; //leave on
}
- (void)log_info:(NSString *)msg {
    if (_log_info_on) {
        NSLog(@"[INFO] %@", msg);
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
@end
