var exec = require('cordova/exec');

var TwilioVideo = function() {};

TwilioVideo.openRoom = function(token, room,
                                    remote_user_name,
                                    remote_user_photo_url,
                                    eventCallback, config)
    {
    config = config != null ? config : null;
    exec(function(e) {
        console.log("Twilio openRoom event fired: " + e);
        //just logs [log] - Twilio video event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.openRoom(token,room)
        if (eventCallback) {
            eventCallback(e.event, e.data);
        }
    }, null, 'TwilioVideoPlugin', 'openRoom', [token, room, remote_user_name, remote_user_photo_url, config]);
};

TwilioVideo.startCall = function(token, room,
                                eventCallback, config)
{
    config = config != null ? config : null;
    exec(function(e) {
        console.log("Twilio startCall event fired: " + e);
        //just logs [log] - Twilio startCall event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.startRoom(token,room)
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
        console.log("Twilio answerCall event fired: " + e);
        //just logs [log] - Twilio answerCall event fired: [object Object]
        //then passes to index.js
        //[log] - [index.js] window.twiliovideo.startRoom(token,room)
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
