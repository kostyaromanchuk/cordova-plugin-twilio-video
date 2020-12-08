cordova.define("cordova-plugin-twilio-video.twiliovideo", function(require, exports, module) { 
var exec = require('cordova/exec');

var TwilioVideo = function() {};

TwilioVideo.openRoom = function(token, room,
                                    remote_user_name,
                                    remote_user_photo_url,
                                    eventCallback, config)
    {
    config = config != null ? config : null;
    exec(function(e) {
        //----------------------------------------------------------------------
        //console.log("Twilio openRoom event fired: " + e);
        //just logs [log] - Twilio video event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.openRoom(token,room)

        //----------------------------------------------------------------------
        console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] Twilio.openRoom() has returned]");
        //----------------------------------------------------------------------
        //eventCallback(e.event, e.data)
        //    data {
        //        code = 20101;
        //        description = "Invalid Access Token";
        //    }
        //----------------------------------------------------------------------
        //YOU NEED TO name each sub property else JS just says [object Object] for description
        if(e.data){
            if(e.data.code){
                console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code:'" + e.data.code + "']");
            }else{
                console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil");
            }
            if(e.data.description){
                console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description:'" + e.data.description + "']");
            }else{
                console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description is nil");
            }
        }else{
            //console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
            console.log("[twilio.js][Twilio.openRoom][EVENT NAME:'" + e.event + "']");
        }
        //----------------------------------------------------------------------
        if(config){
            console.log("[twilio.js][Twilio.openRoom]config:'" + config + "'");
            if(config.startWithVideoOff){
                console.log("[twilio.js][Twilio.openRoom][config.startWithVideoOff:" + config.startWithVideoOff + "]");
            }else{
                console.log("[twilio.js][Twilio.openRoom][config.startWithVideoOff is undefined or false?");
            }
            if(config.startWithAudioOff){
                console.log("[twilio.js][Twilio.openRoom][config.startWithAudioOff:" + config.startWithAudioOff + "]");
            }else{
                console.log("[twilio.js][Twilio.openRoom][config.startWithAudioOff is undefined or false?");
            }
        }else{
            console.log("[twilio.js][Twilio.openRoom] config is nil");
        }
        //----------------------------------------------------------------------
        if (eventCallback) {
            eventCallback(e.event, e.data);
        }
        //----------------------------------------------------------------------
        
    }, null, 'TwilioVideoPlugin', 'openRoom', [token, room, remote_user_name, remote_user_photo_url, config]);
};

TwilioVideo.startCall = function(token, room,
                                eventCallback, config)
{
    config = config != null ? config : null;
    exec(function(e) {
        //----------------------------------------------------------------------
        //just logs [log] - Twilio startCall event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.startRoom(token,room)
        console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] Twilio.startCall() has returned]");
        //----------------------------------------------------------------------
        //eventCallback(e.event, e.data)
        //    data {
        //        code = 20101;
        //        description = "Invalid Access Token";
        //    }
        //----------------------------------------------------------------------
        //YOU NEED TO name each sub property else JS just says [object Object] for description  
        if(e.data){
            if(e.data.code){
                console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code:'" + e.data.code + "']");
            }else{
                console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil");
            }
            if(e.data.description){
                console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description:'" + e.data.description + "']");
          }else{
                console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description is nil");
            }
        }else{
            //console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
            console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "']");
        }
        //----------------------------------------------------------------------
        if (eventCallback) {
            eventCallback(e.event, e.data);
        }
    }, null, 'TwilioVideoPlugin', 'startCall', [token, room, config]);
};

TwilioVideo.answerCall = function(token, room,
                                  eventCallback, config)
{
    config = config != null ? config : null;
    exec(function(e) {
        //console.log("Twilio answerCall event fired: " + e);
        //just logs [log] - Twilio answerCall event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.startRoom(token,room)
        
        //----------------------------------------------------------------------
        //just logs [log] - Twilio startCall event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.startRoom(token,room)
        console.log("[twilio.js][Twilio.startCall][EVENT NAME:'" + e.event + "'] Twilio.answerCall() returned]");
        //----------------------------------------------------------------------
        //eventCallback(e.event, e.data)
        //    data {
        //        code = 20101;
        //        description = "Invalid Access Token";
        //    }
        //----------------------------------------------------------------------
        //YOU NEED TO name each sub property else JS just says [object Object] for description
        if(e.data){
            if(e.data.code){
                console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code:'" + e.data.code + "']");
            }else{
                console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "'] [ERROR CODE - e.data.code is nil");
            }
            if(e.data.description){
                console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description:'" + e.data.description + "']");
            }else{
                console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "'] [ERROR DESC - e.data.description is nil");
            }
        }else{
            //console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "']  e.data is nil - ok if not error");
            console.log("[twilio.js][Twilio.answerCall][EVENT NAME:'" + e.event + "']");
        }
        //----------------------------------------------------------------------
        
        if (eventCallback) {
            eventCallback(e.event, e.data);
        }
    }, null, 'TwilioVideoPlugin', 'answerCall', [token, room, config]);
};
    
TwilioVideo.closeRoom = function() {
    return new Promise(function(resolve, reject) {
        exec(function() {
            resolve();
        }, function(error) {
            reject(error);
        }, "TwilioVideoPlugin", "closeRoom", []);
    });
};

TwilioVideo.hasRequiredPermissions = function() {
    return new Promise(function(resolve, reject) {
        exec(function(result) {
            resolve(result);
        }, function(error) {
            reject(error);
        }, "TwilioVideoPlugin", "hasRequiredPermissions", []);
    });
};

TwilioVideo.requestPermissions = function() {
    return new Promise(function(resolve, reject) {
        exec(function(result) {
            resolve(result);
        }, function(error) {
            reject(error);
        }, "TwilioVideoPlugin", "requestPermissions", []);
    });
};

module.exports = TwilioVideo;
});
