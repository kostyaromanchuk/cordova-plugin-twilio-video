#import "TwilioVideoPlugin.h"
#import <AVFoundation/AVFoundation.h>

@implementation TwilioVideoPlugin

#pragma mark - Plugin Initialization
- (void)pluginInitialize
{
    [[TwilioVideoManager getInstance] setEventDelegate:self];
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
    //args[2] - Added by BC
    NSString* remote_user_name = args[2];
    
    //--------------------------------------------------------------------------
    //args[3] - Added by BC
    NSString* remote_user_photo_url = args[3];
    
    //--------------------------------------------------------------------------
    //args[4] - was args[2]
    TwilioVideoConfig *config = [[TwilioVideoConfig alloc] init];
    if ([args count] > 2) {
        [config parse: command.arguments[4]];
    }
    //--------------------------------------------------------------------------
    //ARGUMENTS - END
    //--------------------------------------------------------------------------

    dispatch_async(dispatch_get_main_queue(), ^{
        UIStoryboard *sb = [UIStoryboard storyboardWithName:@"TwilioVideo" bundle:nil];
        TwilioVideoViewController *vc = [sb instantiateViewControllerWithIdentifier:@"TwilioVideoViewController"];
        
        vc.config = config;

        //----------------------------------------------------------------------
        //not required - needed when the Leave Room was in the cordova WebView
        //behind this TwilioVideoViewController.view
        //vc.view.backgroundColor = [UIColor clearColor];
        //vc.view.backgroundColor = [UIColor redColor];
        //----------------------------------------------------------------------
        //Sea/ grey - if you comment this out it will use the color set in TwilioVideo.storyboard
        vc.view.backgroundColor = [UIColor colorWithRed: 17.0/255.0
                                                  green: 37.0/255.0
                                                   blue: 57.0/255.0
                                                  alpha:1.0];
        //----------------------------------------------------------------------
        vc.modalPresentationStyle = UIModalPresentationOverFullScreen;
        
        [self.viewController presentViewController:vc animated:NO completion:^{
            //------------------------------------------------------------------
            //remoteUserName and remoteUserPhotoURL ADDED BY BC
            [vc connectToRoom:room
                        token:token
               remoteUserName:remote_user_name
           remoteUserPhotoURL:remote_user_photo_url];
            //------------------------------------------------------------------
        }];
    });
}

- (void)closeRoom:(CDVInvokedUrlCommand*)command {
    if ([[TwilioVideoManager getInstance] publishDisconnection]) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_OK] callbackId:command.callbackId];
    } else {
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Twilio video is not running"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }
}

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

#pragma mark - TwilioVideoEventProducerDelegate

- (void)onCallEvent:(NSString *)event with:(NSDictionary*)data {
    if (!self.listenerCallbackID) {
        NSLog(@"Listener callback unavailable.  event %@", event);
        return;
    }
    
    if (data != NULL) {
        NSLog(@"Event received %@ with data %@", event, data);
    } else {
        NSLog(@"Event received %@", event);
    }
    
    NSMutableDictionary *message = [NSMutableDictionary dictionary];
    [message setValue:event forKey:@"event"];
    [message setValue:data != NULL ? data : [NSNull null] forKey:@"data"];
    
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:message];
    [result setKeepCallbackAsBool:YES];
    
    [self.commandDelegate sendPluginResult:result callbackId:self.listenerCallbackID];
}

@end
