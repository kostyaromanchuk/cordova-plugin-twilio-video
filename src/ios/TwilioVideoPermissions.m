#import "TwilioVideoPermissions.h"

@implementation TwilioVideoPermissions

+ (BOOL)hasRequiredPermissions {
    AVAuthorizationStatus videoPermissionStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    AVAudioSessionRecordPermission audioPermissionStatus = [AVAudioSession sharedInstance].recordPermission;
    return videoPermissionStatus == AVAuthorizationStatusAuthorized && audioPermissionStatus == AVAudioSessionRecordPermissionGranted;
}

+ (void)requestRequiredPermissions:(void (^)(BOOL))response {
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL grantedCamera)
    {
        [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL grantedAudio) {
            if (response) { response(grantedAudio && grantedCamera); }
        }];
    }];
}



//--------------------------------------------------------------------------------------------------
//REQUEST CAMERA PERMISSIONS ONLY
//--------------------------------------------------------------------------------------------------
//call always before using camera

//if true then safe to use Camera
//if false then theres 3 reasons why
//Camera is UNDETERMINED - user NEVER asked - call requestPermissionCamera: to ask
//Camera is DENIED       - user was asked and said NO - you need to show alert to check their settings
//Camera is RESTRICTED   - parental Control or corporate security restriction - you need to show alert to check their settings or contact their IT support.
//to check call isPermissionRestrictedOrDeniedForCamera:

//Recommendation : call BEFORE you start the TwilioVideo screen
//.... possible issue with TwilioSdk starting with permission off.
//.... Turning permission back on may not update twilio till room disconnects and reconnects
//.... So workaround alwways call permissions early
//.... once permission accepted by user hasRequiredPermissionCamera always returns true

+ (BOOL)hasRequiredPermissionCamera {
    AVAuthorizationStatus videoPermissionStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    return videoPermissionStatus == AVAuthorizationStatusAuthorized;
}


//call  hasRequiredPermissionCamera: first
//if it returns false
// AVAuthorizationStatusRestricted
//users camera is Restricted by Corporate or Parental Controls

// AVAuthorizationStatusDenied
//user was ahown permission alert and said NO
//we need to show alert to tell them to change it in Settings > Privacy > Camera

+ (BOOL)isPermissionRestrictedOrDeniedForCamera {
    BOOL permissionRestrictedOrDenied = FALSE;
    
    AVAuthorizationStatus videoPermissionStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];

    if(AVAuthorizationStatusNotDetermined == videoPermissionStatus){
        //user never asked for permission - call requestPermissionCamera: next to show permission request alerts
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCameraRestrictedOrDenied: AVAuthorizationStatusNotDetermined > return FALSE");
        permissionRestrictedOrDenied = FALSE;
        
    }else if(AVAuthorizationStatusRestricted == videoPermissionStatus){
        //users camera is Restricted by Corporate or Parental Controls
        //we need to show alert to tell them to change it in Settings > Privacy > Camera, Parental Support or contact their IT support
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCameraRestrictedOrDenied: AVAuthorizationStatusRestricted > return TRUE");
        permissionRestrictedOrDenied = TRUE;
        
    }else if(AVAuthorizationStatusDenied == videoPermissionStatus){
        //user was ahown permission alert and said NO
        //we need to show alert to tell them to change it in Settings > Privacy > Camera
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCameraRestrictedOrDenied: AVAuthorizationStatusDenied > return TRUE");
        permissionRestrictedOrDenied = TRUE;
        
    }else if(AVAuthorizationStatusAuthorized == videoPermissionStatus){
        //ok to open camera - no need to show extra alerts
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCameraRestrictedOrDenied: AVAuthorizationStatusAuthorized > return FALSE");
        permissionRestrictedOrDenied = FALSE;
        
    }else {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isCameraRestrictedOrDenied: UNHANDLED AVAuthorizationStatus");
    }

    return permissionRestrictedOrDenied;
}

+ (void)requestPermissionCamera:(void (^)(BOOL))response {
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL grantedCamera)
     {
        //------------------------------------------------------------------------------------------
        if (grantedCamera) {
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionCamera: requestAccessForMediaType:AVMediaTypeVideo returns TRUE");
            
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionCamera: requestAccessForMediaType:AVMediaTypeVideo returns FALSE - did you check isPermissionRestrictedOrDeniedForCamera()");
        }
        
        //------------------------------------------------------------------------------------------
        if (response) {
            response(grantedCamera);
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionCamera: ERROR response block is NULL");
        }
        //------------------------------------------------------------------------------------------
    }];
}


//--------------------------------------------------------------------------------------------------
//REQUEST MICROPHONE PERMISSIONS ONLY
//--------------------------------------------------------------------------------------------------
//call always before using microphone

//if true then safe to use microphone
//if false then call isPermissionRestrictedOrDeniedForMicrophone:

//Recommendation : call BEFORE you start the TwilioVideo screen
//.... possible issue with TwilioSdk starting with permission off.
//.... Turning permission back on may not update twilio till room disconnects and reconnects
//.... So workaround alwways call permissions early
//.... once permission accepted by user hasRequiredPermissionMicrophone always returns true
+ (BOOL)hasRequiredPermissionMicrophone {
    AVAudioSessionRecordPermission audioPermissionStatus = [AVAudioSession sharedInstance].recordPermission;
    return audioPermissionStatus == AVAudioSessionRecordPermissionGranted;
}


//if permission Restricted or Denied calling requestPermissionMicrophone: always returns false
//if this is true we need to show alert to tell them to change it in Settings > Privacy > Microphone or contact their IT support

+ (BOOL)isPermissionRestrictedOrDeniedForMicrophone {
    BOOL permissionRestrictedOrDenied = FALSE;
    
    AVAudioSessionRecordPermission audioPermissionStatus = [AVAudioSession sharedInstance].recordPermission;
    
    //    typedef NS_ENUM(NSUInteger, AVAudioSessionRecordPermission) {
    //        AVAudioSessionRecordPermissionUndetermined = 'undt',
    //        AVAudioSessionRecordPermissionDenied = 'deny',
    //        AVAudioSessionRecordPermissionGranted = 'grnt'
    //    };
    
    if(AVAudioSessionRecordPermissionUndetermined == audioPermissionStatus){
        //user never asked for permission - safe to call requestPermissionMicrophone:
        permissionRestrictedOrDenied = FALSE;
        
    }else if(AVAudioSessionRecordPermissionDenied == audioPermissionStatus){
        //user's microphone is Restricted by Corporate or Parental Controls
        permissionRestrictedOrDenied = TRUE;
        
    }else if(AVAudioSessionRecordPermissionGranted == audioPermissionStatus){
        //user was ahown permission alert and said NO
        //we need to show alert to tell them to change it in Settings > Privacy > Microphone
        permissionRestrictedOrDenied = FALSE;
        
    }else {
        NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] isPermissionRestrictedOrDeniedForMicrophone: UNHANDLED AVAuthorizationStatus");
    }
    
    return permissionRestrictedOrDenied;
}

//note on iOS calling requestPermissionMicrophone
//when permission is Denied or Restricted will always return false, wont show alert
+ (void)requestPermissionMicrophone:(void (^)(BOOL))response {
    
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL grantedAudio) {
        
        //------------------------------------------------------------------------------------------
        if (grantedAudio) {
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionMicrophone: requestRecordPermission: returns TRUE");
            
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionMicrophone: requestRecordPermission: returns FALSE - did you check isPermissionRestrictedOrDeniedForMicrophone()");
        }
        
        //------------------------------------------------------------------------------------------
        if (response) {
            response(grantedAudio);
        }else{
            NSLog(@"[VOIPCALLKITPLUGIN][CordovaCall.m] requestPermissionMicrophone: ERROR response block is NULL");
        }
        //------------------------------------------------------------------------------------------

    }];
}


@end
