# NEW PERMISSIONS API

```bash
RELEASE IN PLUGIN RELEASE
cordova-plugin-twilio-video 7.2.7
4 mar 2020

https://github.com/SeaChatGit/cordova-plugin-twilio-video/

Example code
https://github.com/SeaChatGit/twillio-hybrid-app-poc/blob/master/www/js/index.html
https://github.com/SeaChatGit/twillio-hybrid-app-poc/blob/master/www/js/index.js
calls plugin methos in 
https://github.com/SeaChatGit/cordova-plugin-twilio-video/twiliovideo.js
```

## Old api

```jsx
//has user given permission for app to use Camera AND Mic
hasRequiredPermissions()
//ask iOS or Android to request permission to Camera AND Mic
requestPermission()
```

### FLOW

```bash
if(hasRequiredPermissions()}{
 	// safe to use Camera and Mic
}else{
	// request permisson to Camera and Mic
	// iOS or Android shows two alerts
	requestPermission()
}
```

### Issues with old api

ISSUES:

- Checks Camera and Mic together
- For Audio Calls - only need mic - confusing to ask for camera so I created
- For iOS - api doesn't handle some permissions properly
    - iOS has 4 permissions
    - **AUTHORIZED**
        - user was asked already and said yes - ok to access camera and mic and start call.
    - **UNDETERMINED**
        - user not asked for permissions yet
        - call requestPermission() to ask ios or Android to show permission alerts
    - **Current api doesn't handle the next 2 permissions properly**
    - **DENIED**
        - users asked for permission before but said no.
        - we should show alert 'please check your settings'
    - **RESTRICTED** - Camera or mic access off due to security policy.
    - 
    - Note there is no DENIED or RESTRICTED in Android
        - On android if user previously said no
        - then just calling  requestPermission again and it will always show alert.
        - they do have to option to Deny and dont ask me again but restarting app resets this.
- Android
    - There is currently a bug where audio may get stuck off because
        - user presses YES to CAMERA alert,
        - switches back to video Screen
        - TwilioVideoActivity calls the old **hasRequiredPermissions**()
            - CAMERA permission is OK
            - MIC is still not OK
        - so **hasRequiredPermissions**() fails
            - because Audio is off.
        - And annoyingly stays off
            - even if you accept MIC permission
            - and close and the video chat
    - FIX: call these new apis in the cordova side rather than just in the video plugin in iOS / Android

## OLD FLOW

```jsx
if(hasRequiredPermissions()}{
	// safe to use Camera and Mic
}else{
	// request permisson to Camera and Mic
	requestPermission()
}
```

## NEW APIS

I created separate apis for Camera and Mic.

I created new api to check for DENIED and RESTRICTED

- CAMERA
    - hasRequiredPermissionCamera()
    - **isPermissionRestrictedOrDeniedForCamera**()
    - requestPermissionCamera()
- MIC - Audio only calls
    - hasRequiredPermissionMicrophone()
    - isPermissionRestrictedOrDeniedForMicrophone()
    - requestPermissionMicrophone()

### Android has no DENIED or RESTRICTED

```jsx

**isPermissionRestrictedOrDeniedForCamera()**
isPermissionRestrictedOrDeniedForMicrophone()
```

- iOS
    - if permission is DENIED OR RESTRICTED apis return TRUE
- Android
    - apis ALWAYS return FALSE
    - needed so you dont have to write Android or iOS versions of the permissions checks
    - 

### FLOW - AUDIO ONLY CALL

pseudocode

```jsx
if(hasRequiredPermissionMicrophone()}{
	// safe to use Microphone

}else{
	// no permission - check why?
	if(isPermissionRestrictedOrDeniedForMicrophone()){
			//DENIED / RESTRICTED
			alert("Please enable microphone in Settings");
	}else{
			//ask iOS or Android to request permssion from the user
			requestPermissionMicrophone()

			// after just call hasRequiredPermissionMicrophone() again to double check
	}
}
```

### VIDEO CALL

```jsx

if(hasRequiredPermissionCamera()}{
	

		if(hasRequiredPermissionMicrophone()}{
			// safe to use Camera and Microphone
		
		}else{
			//.... see flow above
		}

}else{
	// no permission - check why?
	if(isPermissionRestrictedOrDeniedForCamera()){
			//DENIED / RESTRICTED
			alert("Please enable Camera in Settings");
	}else{
			//ask iOS or Android to request permssion from the user
			requestPermissionCamera()

			// after just call hasRequiredPermissionCamera() again to double check
	}
}
```

### Example code

there is a separate project where we test the cordova-plugin-twilio-video

https://github.com/SeaChatGit/twillio-hybrid-app-poc

```jsx

https://github.com/SeaChatGit/twillio-hybrid-app-poc/blob/master/www/index.html
BUTTONS to call index.js

https://github.com/SeaChatGit/twillio-hybrid-app-poc/blob/master/www/js/index.js
calls twilio.js in cordova-plugin-twilio-video

https://github.com/SeaChatGit/cordova-plugin-twilio-video/blob/master/www/twiliovideo.js
```