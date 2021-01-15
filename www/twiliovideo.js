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
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL CALLS TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

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
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL CALLS TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

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
            
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL CALLS TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

            },
            null,
            "TwilioVideoPlugin",
            "answerCall",
             [token, room, local_user_name, local_user_photo_url, remote_user_name, remote_user_photo_url, config]
        );
    };
        
    //internet gone on device - tell plugin to show alert
    TwilioVideo.showOffline = function (eventCallback) {
        console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOffline] CALLED");
        exec(
            function (e) {
                console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOffline][EVENT NAME:'" + e.event + "'] Twilio.showOffline() returned]");
 
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL NEW TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

            },
            null,
            "TwilioVideoPlugin",
            "showOffline",
            []  /* no params */
            );
    };
    
    //internet returned on device - tell plugin to hide alert
    TwilioVideo.showOnline = function (eventCallback) {
        console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOnline] CALLED");
        exec(
             function (e) {
                console.log("[VIDEOPLUGIN][twilio.js][Twilio.showOnline][EVENT NAME:'" + e.event + "'] Twilio.showOnline() returned]");
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL NEW TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------
            },
             null,
             "TwilioVideoPlugin",
             "showOnline",
             []  /* no params */
             );
    };

    TwilioVideo.closeRoom = function (eventCallback) {
        return new Promise(function (resolve, reject) {
            exec(
                function () {
                    resolve();
                },
                function (error) {
                    reject(error);
                },
                "TwilioVideoPlugin",
                "closeRoom",
                []
            );
        });
    };
    
    TwilioVideo.show_twiliovideo = function (eventCallback) {
        console.log("[VIDEOPLUGIN][twilio.js][Twilio.show_twiliovideo] CALLED");
        exec(
             function (e) {
                console.log("[VIDEOPLUGIN][twilio.js][Twilio.show_twiliovideo][EVENT NAME:'" + e.event + "'] Twilio.show_twiliovideo() returned]");
                
                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL NEW TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

             },
             null,
             "TwilioVideoPlugin",
             "show_twiliovideo",
             []  /* no params */
             );
    };
    TwilioVideo.hide_twiliovideo = function (eventCallback) {
        console.log("[VIDEOPLUGIN][twilio.js][Twilio.hide_twiliovideo] CALLED");
        exec(
             function (e) {
                console.log("[VIDEOPLUGIN][twilio.js][Twilio.hide_twiliovideo][EVENT NAME:'" + e.event + "'] Twilio.hide_twiliovideo() returned]");

                //----------------------------------------------------------------------------------
                //MUST ADD TO ALL NEW TwilioVideo.* = function (){....)
                //----------------------------------------------------------------------------------
                //the last Twilio...method called will take over the event queue eventally when user disconnects it might not come out in answerCall or startCall event handlers
                if (eventCallback) {
                    eventCallback(e.event, e.data);
                }
                //----------------------------------------------------------------------------------

             },
             null,
             "TwilioVideoPlugin",
             "hide_twiliovideo",
             []  /* no params */
             );
    };

    TwilioVideo.hasRequiredPermissions = function () {
        return new Promise(function (resolve, reject) {
            exec(
                function (result) {
                    resolve(result);
                },
                function (error) {
                    reject(error);
                },
                "TwilioVideoPlugin",
                "hasRequiredPermissions",
                []
            );
        });
    };

    TwilioVideo.requestPermissions = function () {
        return new Promise(function (resolve, reject) {
            exec(
                function (result) {
                    resolve(result);
                },
                function (error) {
                    reject(error);
                },
                "TwilioVideoPlugin",
                "requestPermissions",
                []
            );
        });
    };

    module.exports = TwilioVideo;
