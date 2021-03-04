#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

@interface TwilioVideoPermissions : NSObject

//--------------------------------------------------------------------------------------------------
//PERMISSIONS v1 - OLD API - requests CAMERA and MIC together
//--------------------------------------------------------------------------------------------------
+ (BOOL)hasRequiredPermissions;
+ (void)requestRequiredPermissions:(void (^)(BOOL))response;


//--------------------------------------------------------------------------------------------------
//PERMISSIONS v2 - REQUEST CAMERA PERMISSIONS ONLY
//--------------------------------------------------------------------------------------------------
+ (BOOL)hasRequiredPermissionCamera;

//if permission Restricted or Denied calling requestPermissionCamera: always returns false
//if this is true we need to show alert to tell them to change it in Settings > Privacy > Camera or contact their IT support
+ (BOOL)isPermissionRestrictedOrDeniedForCamera;

//note on iOS calling requestPermissionCamera:
//when permission is Denied or Restricted will always return false, wont show alert
//to check call isPermissionRestrictedOrDeniedForCamera on iOS (android will show alert even if camera/mic off in settings)
+ (void)requestPermissionCamera:(void (^)(BOOL))response;


//--------------------------------------------------------------------------------------------------
//PERMISSIONS v2 - REQUEST MICROPHONE PERMISSIONS ONLY (used for Audio only calls)
//--------------------------------------------------------------------------------------------------
+ (BOOL)hasRequiredPermissionMicrophone;

//if permission Restricted or Denied calling requestPermissionMicrophone: always returns false
//if this is true we need to show alert to tell them to change it in Settings > Privacy > Microphone or contact their IT support
+ (BOOL)isPermissionRestrictedOrDeniedForMicrophone;

//note on iOS calling requestPermissionMicrophone
//when permission is Denied or Restricted will always return false, wont show alert(android will show alert even if camera/mic off in settings)
+ (void)requestPermissionMicrophone:(void (^)(BOOL))response;

@end
