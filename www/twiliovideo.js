
var exec = require("cordova/exec");

var TwilioVideo = function () {};

TwilioVideo.openRoom = function (token, room,
								 local_user_name, local_user_photo_url,
								 remote_user_name, remote_user_photo_url,
								 eventCallback, config) {
	config = config != null ? config : null;
	exec(
		 function (e) {
		//----------------------------------------------------------------------
		//console.log("Twilio openRoom event fired: " + e);
		//just logs [log] - Twilio video event fired: [object Object]
		//then passes to index.js
		//[log] - [index.js] window.twiliovideo.openRoom(token,room)
		
		//----------------------------------------------------------------------
		console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom] openRoom(..) call returned with EVENT NAME:'" + e.event + "'");
		//----------------------------------------------------------------------
		//eventCallback(e.event, e.data)
		//    data {
		//        code = 20101;
		//        description = "Invalid Access Token";
		//    }
		//----------------------------------------------------------------------
		//YOU NEED TO name each sub property else JS just says [object Object] for description
		if (e.data) {
			if (e.data.code) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR CODE - e.data.code:'" +
							e.data.code +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil"
							);
			}
			if (e.data.description) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description:'" +
							e.data.description +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description is nil"
							);
			}
		} else {
			//console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "']");
		}
		//----------------------------------------------------------------------
		if (config) {
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG] config:'" + config + "'");
			if (config.startWithVideoOff) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG][config.startWithVideoOff:" + config.startWithVideoOff + "]"
							);
			} else {
				console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG][config.startWithVideoOff is undefined or false?");
			}
			if (config.startWithAudioOff) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG][config.startWithAudioOff:" + config.startWithAudioOff + "]"
							);
			} else {
				console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG][config.startWithAudioOff is undefined or false?");
			}
		} else {
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom][CONFIG] config is nil");
		}
		console.log("[VIDEOPLUGIN][twilio.js][Twilio.openRoom] pass resp to index.js");
		if (eventCallback) {
			eventCallback(e.event, e.data);
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "openRoom",
		 [token, room, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url, config]
		 );
};

TwilioVideo.startCall = function (token, room, eventCallback, config) {
	config = config != null ? config : null;
	exec(
		 function (e) {
		//----------------------------------------------------------------------
		//just logs [log] - Twilio startCall event fired: [object Object]
		//then passes to index.js
		//[log] - [index.js] window.twiliovideo.startRoom(token,room)
		console.log("[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] Twilio.startCall() has returned]");
		//----------------------------------------------------------------------
		//eventCallback(e.event, e.data)
		//    data {
		//        code = 20101;
		//        description = "Invalid Access Token";
		//    }
		//----------------------------------------------------------------------
		//YOU NEED TO name each sub property else JS just says [object Object] for description
		if (e.data) {
			if (e.data.code) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR CODE - e.data.code:'" +
							e.data.code +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil"
							);
			}
			if (e.data.description) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description:'" +
							e.data.description +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description is nil"
							);
			}
		} else {
			//console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "']");
		}
		if (eventCallback) {
			eventCallback(e.event, e.data);
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "startCall",
		 [token, room, config]
		 );
};

TwilioVideo.answerCall = function (token, room,
								   local_user_name, local_user_photo_url,
								   remote_user_name, remote_user_photo_url,
								   eventCallback, config) {
	config = config != null ? config : null;
	exec(
		 function (e) {
		//console.log("[VIDEOPLUGIN]Twilio answerCall event fired: " + e);
		//just logs [log] - Twilio answerCall event fired: [object Object]
		//then passes to index.js
		//[log] - [index.js] window.twiliovideo.startRoom(token,room)
		
		//----------------------------------------------------------------------
		//just logs [log] - Twilio startCall event fired: [object Object]
		//then passes to index.js
		//[log] - [index.js] window.twiliovideo.startRoom(token,room)
		console.log("[VIDEOPLUGIN][twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] Twilio.answerCall() returned]");
		//----------------------------------------------------------------------
		//eventCallback(e.event, e.data)
		//    data {
		//        code = 20101;
		//        description = "Invalid Access Token";
		//    }
		//----------------------------------------------------------------------
		//YOU NEED TO name each sub property else JS just says [object Object] for description
		if (e.data) {
			if (e.data.code) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR CODE - e.data.code:'" +
							e.data.code +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil"
							);
			}
			if (e.data.description) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description:'" +
							e.data.description +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description is nil"
							);
			}
			
		} else {
			//console.log("[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "']");
		}
		if (eventCallback) {
			eventCallback(e.event, e.data);
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "answerCall",
		 [token, room, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url, config]
		 );
};

TwilioVideo.closeRoom = function (eventCallback) {
	
	exec(
		 function (e) {
		//console.log("[VIDEOPLUGIN]Twilio closeRoom event fired: " + e);
		//just logs [log] - Twilio closeRoom event fired: [object Object]
		//then passes to index.js
		//[log] - [index.js] window.twiliovideo.closeRoom(COME OUT IN THE CALLBACK)
		
		//----------------------------------------------------------------------
		//eventCallback(e.event, e.data)
		//    data {
		//        code = 20101;
		//        description = "Invalid Access Token";
		//    }
		//----------------------------------------------------------------------
		//YOU NEED TO name each sub property else JS just says [object Object] for description
		if (e.data) {
			if (e.data.code) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR CODE - e.data.code:'" +
							e.data.code +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil"
							);
			}
			if (e.data.description) {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description:'" +
							e.data.description +
							"']"
							);
			} else {
				console.log(
							"[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" +
							e.event +
							"'] [ERROR DESC - e.data.description is nil"
							);
			}
			
		} else {
			//console.log("[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
			console.log("[VIDEOPLUGIN][twilio.js][Twilio.closeRoom][EVENT NAME:'" + e.event + "']");
		}
		if (eventCallback) {
			eventCallback(e.event, e.data);
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "closeRoom",
		 []
		 );
};

//showOffline/showOnline - moved down

TwilioVideo.show_twiliovideo = function (success, error) {
	console.log("[VIDEOPLUGIN][twilio.js][Twilio.show_twiliovideo] CALLED");
	exec(
		 success,
		 error,
		 "TwilioVideoPlugin",
		 "show_twiliovideo",
		 []  /* no params */
		 );
};
TwilioVideo.hide_twiliovideo = function (success, error) {
	console.log("[VIDEOPLUGIN][twilio.js][Twilio.hide_twiliovideo] CALLED");
	exec(
		 success,
		 error,
		 "TwilioVideoPlugin",
		 "hide_twiliovideo",
		 []  /* no params */
		 );
};


//----------------------------------------------------------------------------------------------
//PERMISSIONS v1 CAMERA and MICROPHONE checked together
//----------------------------------------------------------------------------------------------


TwilioVideo.hasRequiredPermissions = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissions CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissions() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "hasRequiredPermissions",
		 []
		 );
};


TwilioVideo.requestPermissions = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] requestPermissions CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] requestPermissions() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "requestPermissions",
		 []
		 );
};


//----------------------------------------------------------------------------------------------
//PERMISSIONS v2 CAMERA and MICROPHONE seperate - audio call only needs audio permission
//----------------------------------------------------------------------------------------------

//----------------------------------------------------------------------------------------------
//REQUEST CAMERA PERMISSIONS ONLY
//----------------------------------------------------------------------------------------------
//call hasRequiredPermissionCamera() always before using camera

//if true then safe to use Camera
//if false then theres various reasons why:
//iOS has 3 states:
//Camera is UNDETERMINED - user NEVER asked - call requestPermissionCamera: to ask
//Camera is DENIED       - user was asked before and said NO - you need to show a JS alert to tell them check their Settings > Privacy
//Camera is RESTRICTED   - parental Control or corporate security restriction - you need to show alert to check their settings or contact their IT support.
//on iOS is hasRequiredPermissionCamera() returns false
//then always call isPermissionRestrictedOrDeniedForCamera() before requestPermissionCamera()
//isPermissionRestrictedOrDeniedForCamera():
//returns FALSE - safe to call requestPermissionCamera()
//returns TRUE  - calling requestPermissionCamera() will always retun FALSe and NEVER show alert again
//So you will have to show a JS alert to tell them to check their Settings > Privacy or contact IT SUPPORT for Restricted

//ANDROID:
//hasRequiredPermissionCamera() returns : TRUE / FALSE
//TRUE
//- user was asked and said yes
//FALSE
//- user never asked
//OR
//- user asked and said no
//on android if hasRequiredPermissionCamera returns FALSE just call requestPermissionCamera() again and it will ALWAYS show the alert even if they previously said NO

//Recommendation : call BEFORE you start the TwilioVideo screen
//.... possible issue with TwilioSdk starting with permission off.
//.... Turning permission back on may not update twilio till room disconnects and reconnects
//.... So workaround alwways call permissions early
//.... once permission accepted by user hasRequiredPermissionCamera always returns true

TwilioVideo.hasRequiredPermissionCamera = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissionCamera CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissionCamera() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "hasRequiredPermissionCamera",
		 []
		 );
};

//iOS check if Camera is DENIED or RESTRICTED
//Android - does nothing always returns false
TwilioVideo.isPermissionRestrictedOrDeniedForCamera = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] isPermissionRestrictedOrDeniedForCamera CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] isPermissionRestrictedOrDeniedForCamera() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "isPermissionRestrictedOrDeniedForCamera",
		 []
		 );
};

//note on IOS
//if permission is RESTRICTED or DENIED then always returns false
//check isPermissionRestrictedOrDeniedForCamera() first
//on ANDROID if user turned off camera before then requestPermissionCamera() will always ask them again by showing alert
TwilioVideo.requestPermissionCamera = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] requestPermissionCamera CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] requestPermissionCamera() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "requestPermissionCamera",
		 []
		 );
};

//----------------------------------------------------------------------------------------------
//REQUEST CAMERA PERMISSIONS ONLY
//----------------------------------------------------------------------------------------------
//see notes above for camera - same rules/recommendations apply
TwilioVideo.hasRequiredPermissionMicrophone = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissionMicrophone CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] hasRequiredPermissionMicrophone() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "hasRequiredPermissionMicrophone",
		 []
		 );
};
//see notes above for camera - same rules/recommendations apply
TwilioVideo.isPermissionRestrictedOrDeniedForMicrophone = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] isPermissionRestrictedOrDeniedForMicrophone CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] isPermissionRestrictedOrDeniedForMicrophone() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "isPermissionRestrictedOrDeniedForMicrophone",
		 []
		 );
};
//see notes above for camera - same rules/recommendations apply
TwilioVideo.requestPermissionMicrophone = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] requestPermissionMicrophone CALLED");
	
	exec(
		 function (e) {
		console.log("[VIDEOPLUGIN][twilio.js] requestPermissionMicrophone() RESPONSE: " + e); //e is true/false
		
		if (eventCallback) {
			// not object
			//eventCallback(e.event, e.data);
			eventCallback(e.event, e);  //e is true/false
		}
	},
		 null,
		 "TwilioVideoPlugin",
		 "requestPermissionMicrophone",
		 []
		 );
};



//---------------------------------------------------------
//SHOW HIDE
//---------------------------------------------------------

//internet gone on device - tell plugin to show alert
TwilioVideo.showOffline = function (success, error) {
	console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOffline] CALLED");
	exec(
		 success,
		 error,
		 "TwilioVideoPlugin",
		 "showOffline",
		 []  /* no params */
		 );
};

//internet returned on device - tell plugin to hide alert
TwilioVideo.showOnline = function (success, error) {
	console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOnline] CALLED");
	exec(
		 success,
		 error,
		 "TwilioVideoPlugin",
		 "showOnline",
		 []  /* no params */
		 );
};



//    TwilioVideo.showOnline = function (eventCallback) {
//
//        console.log("[VIDEOPLUGIN][twilio.js] showOnline CALLED");
//
//        exec(
//             function (e) {
//                console.log("[VIDEOPLUGIN][twilio.js] showOnline() RESPONSE: " + e); //e is true/false
//
//                if (eventCallback) {
//                    // not object
//                    //eventCallback(e.event, e.data);
//                    eventCallback(e.event, e);  //e is true/false
//                }
//            },
//             null,
//             "TwilioVideoPlugin",
//             "showOnline",
//             []
//        );
//    };
//
//    TwilioVideo.showOffline = function (eventCallback) {
//
//        console.log("[VIDEOPLUGIN][twilio.js] showOffline CALLED");
//
//        exec(
//             function (e) {
//            console.log("[VIDEOPLUGIN][twilio.js] showOffline() RESPONSE: " + e); //e is true/false
//
//            if (eventCallback) {
//                // not object
//                //eventCallback(e.event, e.data);
//                eventCallback(e.event, e);  //e is true/false
//            }
//        },
//             null,
//             "TwilioVideoPlugin",
//             "showOffline",
//             []
//             );
//    };

TwilioVideo.showAlert = function (eventCallback) {
	
	console.log("[VIDEOPLUGIN][twilio.js] showAlert CALLED");
	
	exec(
		 function (e) {
			console.log("[VIDEOPLUGIN][twilio.js] showAlert() RESPONSE: " + e); //e is true/false
			
			if (eventCallback) {
				// not object
				//eventCallback(e.event, e.data);
				eventCallback(e.event, e);  //e is true/false
			}
		},
		 null,
		 "TwilioVideoPlugin",
		 "showAlert",
		 []
		 );
};

module.exports = TwilioVideo;

