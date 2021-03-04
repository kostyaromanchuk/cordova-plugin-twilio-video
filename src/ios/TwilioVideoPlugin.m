#import "TwilioVideoPlugin.h"
#import <AVFoundation/AVFoundation.h>
#import "AppDelegate.h"

#pragma mark -
#pragma mark private ivars
#pragma mark -
@interface TwilioVideoPlugin(){
}
@property (nonatomic, strong) TwilioVideoViewController *tvc;
@end

#pragma mark -
#pragma mark implementation
#pragma mark -

@implementation TwilioVideoPlugin

#pragma mark - Plugin Initialization
- (void)pluginInitialize
{
    [[TwilioVideoManager getInstance] setEventDelegate:self];
}

-(void) instantiate_TwilioVideoViewController_withConfig:(TwilioVideoConfig *) config{
    
    if (self.tvc) {
        NSLog(@"[VIDEOPLUGIN][TwilioVideoPlugin.m] self.tvc TwilioVideoViewController is not null - bring to front");
        
        [self.tvc show_twiliovideo];
        
    } else {
        NSLog(@"[VIDEOPLUGIN][TwilioVideoPlugin.m] self.tvc TwilioVideoViewController is null - INSTANTIATE and PRESENT IT");
        
        UIStoryboard *sb = [UIStoryboard storyboardWithName:@"TwilioVideo" bundle:nil];
        self.tvc = [sb instantiateViewControllerWithIdentifier:@"TwilioVideoViewController"];
        
        self.tvc.config = config;
        
        //----------------------------------------------------------------------
        //not required - needed when the Leave Room was in the cordova WebView
        //behind this TwilioVideoViewController.view
        //vc.view.backgroundColor = [UIColor clearColor];
        //vc.view.backgroundColor = [UIColor redColor];
        //----------------------------------------------------------------------
        //Sea/ grey - if you comment this out it will use the color set in TwilioVideo.storyboard
        
        //accessing self.tvc.view here triggers TwilioVideoViewController.viewDidLoad
        
        //    self.tvc.view.backgroundColor = [UIColor colorWithRed: 17.0/255.0
        //                                                    green: 37.0/255.0
        //                                                     blue: 57.0/255.0
        //                                                    alpha:1.0];
        //----------------------------------------------------------------------
        self.tvc.modalPresentationStyle = UIModalPresentationOverFullScreen;
        
        
        //cant user appd.window its in siwft here bu in main sea/chat app we dont know
        //    AppDelegate *appDelegate = (AppDelegate *)[[UIApplication sharedApplication] delegate];
        
        if(self.tvc.view){
            if(self.tvc.view.window){
                
                if(self.tvc.view.window.rootViewController){
                    
                    NSString *className = NSStringFromClass([self.tvc.view.window.rootViewController class]);
                    
                    
                    NSLog(@"[VIDEOPPLUGIN][TwilioVideoPlugin.m][instantiate_TwilioVideoViewController_withConfig] self.view.window.rootViewController:%@", className);
                    //note viewDidLoad also triggered above
                    [self.tvc.view.window.rootViewController presentViewController:self.tvc animated:YES completion:nil];
                    
                    
                }else{
                    NSLog(@"[VIDEOPPLUGIN][TwilioVideoPlugin.m][instantiate_TwilioVideoViewController_withConfig] self.view.window.rootViewController is NULL");
                }
            }else{
                NSLog(@"[VIDEOPPLUGIN][TwilioVideoPlugin.m][instantiate_TwilioVideoViewController_withConfig] self.view.window is NULL");
            }
        }else{
            NSLog(@"[VIDEOPPLUGIN][TwilioVideoPlugin.m][instantiate_TwilioVideoViewController_withConfig] self.view is NULL");
        }
    }
    
    
}

- (void)openRoom:(CDVInvokedUrlCommand*)command {
    self.listenerCallbackID = command.callbackId;
    NSArray *args = command.arguments;
    
    //--------------------------------------------------------------------------
    //EXTRACT ARGUMENTS - START
    //--------------------------------------------------------------------------
    //args[0]
    NSString* token = args[0];
    
    //--------------------------------------------------------------------------
    //args[1]
    NSString* room = args[1];
    
    //--------------------------------------------------------------------------
    NSString* local_user_name = args[2];
    NSString* local_user_photo_url = args[3];
    NSString* remote_user_name = args[4];
    NSString* remote_user_photo_url = args[5];

    //--------------------------------------------------------------------------
    //args[4] - was args[2]
    TwilioVideoConfig *config = [[TwilioVideoConfig alloc] init];
    if ([args count] > 6) {
        [config parse: command.arguments[6]];
    }
    //--------------------------------------------------------------------------
    //ARGUMENTS - END
    //--------------------------------------------------------------------------

    dispatch_async(dispatch_get_main_queue(), ^{
        //----------------------------------------------------------------------
        //ISSUE - the 2nd time openRoom called UI(TVC) doesnt appear
        //so always PRESENTVC then openRoom:
        //----------------------------------------------------------------------
    
        //creates it of brings it to the front if hidden
        [self instantiate_TwilioVideoViewController_withConfig:config];
        
        if (NULL != self.tvc) {
            //------------------------------------------------------------------
            //ALWAYS PRSENTVC > THEN OPEN
            //------------------------------------------------------------------
            //if you only openRoom: then 2nd time it may not appear
            //------------------------------------------------------------------
            //Sergey asked for this can happen on double tap
            if(self.viewController.presentedViewController == self.tvc){
                NSLog(@"[VIDEOPLUGIN] [TwilioVideoPlugin.m] ERROR TwilioVideoViewController already visible - just call [tvc openRoom:...] directly");
                //----------------------------------------------------------
                [self.tvc openRoom:room
                             token:token
                     localUserName:local_user_name
                 localUserPhotoURL:local_user_photo_url
                    remoteUserName:remote_user_name
                remoteUserPhotoURL:remote_user_photo_url];
                //----------------------------------------------------------
            }else{
                [self.viewController presentViewController:self.tvc animated:NO completion:^{
                    //----------------------------------------------------------
                    [self.tvc openRoom:room
                                 token:token
                         localUserName:local_user_name
                     localUserPhotoURL:local_user_photo_url
                        remoteUserName:remote_user_name
                    remoteUserPhotoURL:remote_user_photo_url];
                    //----------------------------------------------------------
                }];
            }
        }else{
            NSLog(@"[VIDEOPLUGIN] [TwilioVideoPlugin.m] ERROR instantiate_TwilioVideoViewController FAILED - self.tvc is null");
        }
    });
}


- (void)startCall:(CDVInvokedUrlCommand*)command {
    self.listenerCallbackID = command.callbackId;
    NSArray *args = command.arguments;
    
    //--------------------------------------------------------------------------
    //EXTRACT ARGUMENTS - START
    //--------------------------------------------------------------------------
    NSString* token = args[0];
    
    //--------------------------------------------------------------------------
    NSString* room = args[1];
    
    //--------------------------------------------------------------------------
    //args[4] - config dictionary
    TwilioVideoConfig *config = [[TwilioVideoConfig alloc] init];

    if ([args count] > 2) {
        [config parse: command.arguments[2]];
    }
    //--------------------------------------------------------------------------
    //ARGUMENTS - END
    //--------------------------------------------------------------------------
    
    //Dont instantiate TwilioVideoViewController twice - done once in openRoom
    dispatch_async(dispatch_get_main_queue(), ^{
        //----------------------------------------------------------------------
        //ISSUE - the 2nd time openRoom called UI(TVC) doesnt appear
        //so always PRESENTVC then openRoom:
        //----------------------------------------------------------------------
        if (NULL == self.tvc) {
            //------------------------------------------------------------------
            //Create VC
            //------------------------------------------------------------------
            [self instantiate_TwilioVideoViewController_withConfig:config];
            //------------------------------------------------------------------
        }else{
            NSLog(@"INFO TwilioVideoViewController is not null ok to show");
            
        }
        //----------------------------------------------------------------------
        if (NULL != self.tvc) {
            //------------------------------------------------------------------
            //ALWAYS PRSENTVC > THEN OPEN
            //------------------------------------------------------------------
            //if you only startCall: then 2nd time VC wont appear
            
            //dont presentnt tvc twice - happens openRoom: prsent(tvc) startCall:
            //Application tried to present modally a view controller <TwilioVideoViewController: 0x10c822800> that is already being presented by <Capacitor.CAPBridgeViewController:
            if(self.viewController.presentedViewController == self.tvc){
                NSLog(@"ERROR TwilioVideoViewController already visible - just call [tvc startCall]");
                //----------------------------------------------------------
                [self.tvc startCall:room
                              token:token];
                //----------------------------------------------------------
            }else{
                [self.viewController presentViewController:self.tvc animated:NO completion:^{
                    //----------------------------------------------------------
                    [self.tvc startCall:room
                                  token:token];
                    //----------------------------------------------------------
                }];
            }
            
            //------------------------------------------------------------------
        }else{
            NSLog(@"ERROR instantiate_TwilioVideoViewController FAILED - self.tvc is null");
        }
        //----------------------------------------------------------------------
    });
}
- (void)answerCall:(CDVInvokedUrlCommand*)command {
    self.listenerCallbackID = command.callbackId;
    NSArray *args = command.arguments;
    
    //--------------------------------------------------------------------------
    //EXTRACT ARGUMENTS - START
    //--------------------------------------------------------------------------
    NSString* token = args[0];
    NSString* room = args[1];
    NSString* local_user_name = args[2];
    NSString* local_user_photo_url = args[3];
    NSString* remote_user_name = args[4];
    NSString* remote_user_photo_url = args[5];
    
    //--------------------------------------------------------------------------
    //args[4] - config dictionary
    TwilioVideoConfig *config = [[TwilioVideoConfig alloc] init];

    if ([args count] > 6) {
        [config parse: command.arguments[6]];
    }
    //--------------------------------------------------------------------------
    //ARGUMENTS - END
    //--------------------------------------------------------------------------
    
    dispatch_async(dispatch_get_main_queue(), ^{
        //----------------------------------------------------------------------
        //ISSUE - the 2nd time openRoom called UI(TVC) doesnt appear
        //so always PRESENTVC then openRoom:
        //----------------------------------------------------------------------
        if (NULL == self.tvc) {
            //------------------------------------------------------------------
            //Create VC
            //------------------------------------------------------------------
            [self instantiate_TwilioVideoViewController_withConfig:config];
            //------------------------------------------------------------------
        }else{
            //is not null ok to show
        }
        //----------------------------------------------------------------------
        if (NULL != self.tvc) {
            //alexey asked for this to prvent double tap
            //i had used it for startCall: as TVC already opened by joinRoom/openRoom
            if(self.viewController.presentedViewController == self.tvc){
                NSLog(@"ERROR TwilioVideoViewController already visible - just call [tvc answerCall:...] directly");
                //--------------------------------------------------------------
                [self.tvc answerCall:room
                               token:token
                       localUserName:local_user_name
                   localUserPhotoURL:local_user_photo_url
                      remoteUserName:remote_user_name
                  remoteUserPhotoURL:remote_user_photo_url];
                //--------------------------------------------------------------
            }else{
                [self.viewController presentViewController:self.tvc animated:NO completion:^{
                    //----------------------------------------------------------
                    [self.tvc answerCall:room
                                   token:token
                           localUserName:local_user_name
                       localUserPhotoURL:local_user_photo_url
                          remoteUserName:remote_user_name
                      remoteUserPhotoURL:remote_user_photo_url];
                    //----------------------------------------------------------
                }];
            }
        }else{
            NSLog(@"ERROR instantiate_TwilioVideoViewController FAILED - self.tvc is null");
        }
        //----------------------------------------------------------------------
        
        
    });
}


- (void)showOffline:(CDVInvokedUrlCommand*)command {    
    dispatch_async(dispatch_get_main_queue(), ^{
        if (NULL != self.tvc) {
            [self.tvc showOffline];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
        }else{
            NSLog(@"ERROR [self.tvc showOffline] FAILED - self.tvc is null");
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Can't complete this operation"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    });
}

- (void)showOnline:(CDVInvokedUrlCommand*)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (NULL != self.tvc) {
            [self.tvc showOnline];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
        }else{
            NSLog(@"ERROR [self.tvc showOnline] FAILED - self.tvc is null");
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Can't complete this operation"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    });
}
- (void)show_twiliovideo:(CDVInvokedUrlCommand*)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (NULL != self.tvc) {
            [self.tvc show_twiliovideo];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
        }else{
            NSLog(@"ERROR [self.tvc show_twiliovideo] FAILED - self.tvc is null");
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Can't complete this operation"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    });
}
- (void)hide_twiliovideo:(CDVInvokedUrlCommand*)command {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (NULL != self.tvc) {
            [self.tvc hide_twiliovideo];
            [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
        }else{
            NSLog(@"ERROR [self.tvc hide_twiliovideo] FAILED - self.tvc is null");
            CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Can't complete this operation"];
            [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        }
    });
}

//- (void)closeRoom:(CDVInvokedUrlCommand*)command {
//    if ([[TwilioVideoManager getInstance] publishDisconnection]) {
//        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
//    } else {
//        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Twilio video is not running"];
//        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
//    }
//}

- (void)closeRoom:(CDVInvokedUrlCommand*)command {
    self.listenerCallbackID = command.callbackId;
  
    //NO ARGUMENTS - NSArray *args = command.arguments;
    
    
    //v1 - this came with the plugin but it has no call backs
    //[[TwilioVideoManager getInstance] publishDisconnection]
    
    //v2 - add support for call backs
    //TwilioVideoManager.publishDisconnection: just calls onDisconnect on TVA
    //so just call it directly here
   
    dispatch_async(dispatch_get_main_queue(), ^{
        //----------------------------------------------------------------------
        //ISSUE - the 2nd time openRoom called UI(TVC) doesnt appear
        //so always PRESENTVC then openRoom:
        //----------------------------------------------------------------------
        if (NULL == self.tvc) {
            NSLog(@"ERROR [self.tvc hide_twiliovideo] FAILED - self.tvc is null");
        }else{
          
            //------------------------------------------------------------------
            //Create VC
            //------------------------------------------------------------------
            [self.tvc onDisconnect];
            //delegates will return CLOSED(twilio room closed) and DISCONECTED(TVC dismissed)
            //------------------------------------------------------------------
        }
        
        //----------------------------------------------------------------------
    });
}



//--------------------------------------------------------------------------------------------------
//PERMISSIONS v1 - OLD API - requests CAMERA and MIC together
//--------------------------------------------------------------------------------------------------

- (void)hasRequiredPermissions:(CDVInvokedUrlCommand*)command {
    BOOL hasRequiredPermissions = [TwilioVideoPermissions hasRequiredPermissions];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:hasRequiredPermissions] callbackId:command.callbackId];
}

- (void)requestPermissions:(CDVInvokedUrlCommand*)command {
    [TwilioVideoPermissions requestRequiredPermissions:^(BOOL grantedPermissions) {
                     [self.commandDelegate sendPluginResult:
         [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:grantedPermissions]
                                    callbackId:command.callbackId];
    }];
}

//--------------------------------------------------------------------------------------------------
//PERMISSIONS v2 - REQUEST CAMERA PERMISSIONS ONLY
//--------------------------------------------------------------------------------------------------

- (void)hasRequiredPermissionCamera:(CDVInvokedUrlCommand*)command {
    BOOL hasRequiredPermissionCamera = [TwilioVideoPermissions hasRequiredPermissionCamera];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:hasRequiredPermissionCamera] callbackId:command.callbackId];
}

- (void)isPermissionRestrictedOrDeniedForCamera:(CDVInvokedUrlCommand*)command {
    BOOL isPermissionRestrictedOrDeniedForCamera = [TwilioVideoPermissions isPermissionRestrictedOrDeniedForCamera];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:isPermissionRestrictedOrDeniedForCamera] callbackId:command.callbackId];
}

- (void)requestPermissionCamera:(CDVInvokedUrlCommand*)command {
    [TwilioVideoPermissions requestPermissionCamera:^(BOOL grantedPermissions) {
        [self.commandDelegate sendPluginResult:
         [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:grantedPermissions]
                                    callbackId:command.callbackId];
    }];
}

//--------------------------------------------------------------------------------------------------
//PERMISSIONS v2 - REQUEST MICROPHONE PERMISSIONS ONLY (used for Audio only calls)
//--------------------------------------------------------------------------------------------------
//call always before using camera

//if true then safe to use Camera
//if false then call isPermissionRestrictedOrDeniedForCamera:

//Recommendation : call BEFORE you start the TwilioVideo screen
//.... possible issue with TwilioSdk starting with permission off.
//.... Turning permission back on may not update twilio till room disconnects and reconnects
//.... So workaround alwways call permissions early
//.... once permission accepted by user hasRequiredPermissionCamera always returns true

- (void)hasRequiredPermissionMicrophone:(CDVInvokedUrlCommand*)command {
    BOOL hasRequiredPermissions = [TwilioVideoPermissions hasRequiredPermissionMicrophone];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:hasRequiredPermissions] callbackId:command.callbackId];
}

- (void)isPermissionRestrictedOrDeniedForMicrophone:(CDVInvokedUrlCommand*)command {
    BOOL isPermissionRestrictedOrDeniedForMicrophone = [TwilioVideoPermissions isPermissionRestrictedOrDeniedForMicrophone];
    [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:isPermissionRestrictedOrDeniedForMicrophone] callbackId:command.callbackId];
}

- (void)requestPermissionMicrophone:(CDVInvokedUrlCommand*)command {
    [TwilioVideoPermissions requestPermissionMicrophone:^(BOOL grantedPermissions) {
        [self.commandDelegate sendPluginResult:
         [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:grantedPermissions]
                                    callbackId:command.callbackId];
    }];
}

//--------------------------------------------------------------------------------------------------





#pragma mark - TwilioVideoEventProducerDelegate

- (void)onCallEvent:(NSString *)event with:(NSDictionary*)data {
    if (!self.listenerCallbackID) {
        NSLog(@"Listener callback unavailable.  event %@", event);
        return;
    }
    //--------------------------------------------------------------------------
    if (data != NULL) {
        NSLog(@"[TwilioVideoPlugin.m][onCallEvent] Event received %@ with data %@", event, data);
        
    } else {
        //NSLog(@"[TwilioVideoPlugin.m][onCallEvent] Event received %@ BUT data is nil - ok for events with no data e.g. CLOSED", event);
        NSLog(@"[TwilioVideoPlugin.m][onCallEvent] Event received %@", event);
    }
    
    //--------------------------------------------------------------------------
    //had issues with reopening TVC answerCall > disconnectAnswerCall - other video wont appear
    //note DISCONNECTED_WITH_ERROR is usually also followed by CLOSED
    if([event isEqual:@"CLOSED"]){
        NSLog(@"[TwilioVideoPlugin.m][onCallEvent] Event is closed set self.tvc = nil");
        self.tvc = nil;
        
    }else{
        //NOISY NSLog(@"[TwilioVideoPlugin.m][onCallEvent] Event is not 'CLOSED' dont set self.tvc to nil - reuse TwilioVideoViewController");
    }
    //--------------------------------------------------------------------------
    NSMutableDictionary *message = [NSMutableDictionary dictionary];
    [message setValue:event forKey:@"event"];
    [message setValue:data != NULL ? data : [NSNull null] forKey:@"data"];
    
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [result setKeepCallbackAsBool:YES];
    
    [self.commandDelegate sendPluginResult:result callbackId:self.listenerCallbackID];
}


@end
