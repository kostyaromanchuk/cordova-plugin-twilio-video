declare module TwilioVideo {
  interface TwilioVideoPlugin {
    /**
     * It opens Twilio Video controller and tries to start the videocall.
     * All videocall UI controls will be positioned on the current view, so we can put
     * our own controls from the application that uses the plugin.
     * @param token
     * @param roomName
     * @param remote_user_name
     * @param remote_user_photo_url
     * @param onEvent - (Optional) It will be fired any time that a call event is received
     * @param {Object} config - (Optional) Call configuration
     * @param config.primaryColor - Hex primary color that the app will use
     * @param config.secondaryColor - Hex secondary color that the app will use
     * @param config.i18nConnectionError - Message shown when it is not possible to join the room
     * @param config.i18nDisconnectedWithError - Message show when the client is disconnected due to an error
     * @param config.i18nAccept - Accept translation
     * @param config.handleErrorInApp - (Default = false) Flag to indicate the application will manage any error in the app by events emitted by the plugin
     * @param config.hangUpInApp - (Default = false) Flag to indicate the application should hang up the call by calling 'closeRoom'
     */
    openRoom(
      token: string,
      roomName: string,
      remote_user_name: string,
      remote_user_photo_url: string,
      onEvent?: Function,
      config?: any
    ): void;

    /**
     * It's complete join to videocall.
     * All videocall UI controls will be positioned on the current view, so we can put
     * our own controls from the application that uses the plugin.
     * @param token
     * @param roomName
     * @param {Object} config - (Optional) Call configuration
     * @param config.primaryColor - Hex primary color that the app will use
     * @param config.secondaryColor - Hex secondary color that the app will use
     * @param config.i18nConnectionError - Message shown when it is not possible to join the room
     * @param config.i18nDisconnectedWithError - Message show when the client is disconnected due to an error
     * @param config.i18nAccept - Accept translation
     * @param config.handleErrorInApp - (Default = false) Flag to indicate the application will manage any error in the app by events emitted by the plugin
     * @param config.hangUpInApp - (Default = false) Flag to indicate the application should hang up the call by calling 'closeRoom'
     */
    startCall(
      token: string,
      roomName: string,
      onEvent?: Function,
      config?: any
    ): void;
    
    /**
     * REMOTE user - answers call started by other caller
     * All videocall UI controls will be positioned on the current view, so we can put
     * our own controls from the application that uses the plugin.
     * @param token
     * @param roomName
     * @param local_user_name
     * @param local_user_photo_url
     * @param remote_user_name
     * @param remote_user_photo_url
     * @param {Object} config - (Optional) Call configuration
     * @param config.primaryColor - Hex primary color that the app will use
     * @param config.secondaryColor - Hex secondary color that the app will use
     * @param config.i18nConnectionError - Message shown when it is not possible to join the room
     * @param config.i18nDisconnectedWithError - Message show when the client is disconnected due to an error
     * @param config.i18nAccept - Accept translation
     * @param config.handleErrorInApp - (Default = false) Flag to indicate the application will manage any error in the app by events emitted by the plugin
     * @param config.hangUpInApp - (Default = false) Flag to indicate the application should hang up the call by calling 'closeRoom'
     */
    answerCall(
      token: string,
      roomName: string,
      local_user_name: string,
      local_user_photo_url: string,
      remote_user_name: string,
      remote_user_photo_url: string,
      onEvent?: Function,
      config?: any
    ): void;
    
    
    /**
     * shows full screen alert that internet is offline.
     * onEvent  - BEWARE DISCONNECTED and CLOSED will come out in event handler of LAST METHOD called not always in event handler of startCall or answerCall
     */
    showOffline(
      onEvent?: Function
    ): void;
    
    /**
     * hides full screen alert that internet is offline.
     * onEvent  - BEWARE DISCONNECTED and CLOSED will come out in event handler of LAST METHOD called not always in event handler of startCall or answerCall
     */
    showOnline(
      onEvent?: Function
    ): void;
    
    
    /**
     * show_twiliovideo - moves the TwilioVideoController in FRONT of all other VCs e.g. Cordova (webview).
     * onEvent  - BEWARE DISCONNECTED and CLOSED will come out in event handler of LAST METHOD called not always in event handler of startCall or answerCall
     */
    show_twiliovideo(
      onEvent?: Function
    ): void;
    
   /**
     * hide_twiliovideo - moves the TwilioVideoController BEHIND of all other VCs e.g. Cordova (webview).
     * used for Back to Chat button
     * onEvent  - BEWARE DISCONNECTED and CLOSED will come out in event handler of LAST METHOD called not always in event handler of startCall or answerCall
     */
    hide_twiliovideo(
      onEvent?: Function
    ): void;
    
    
    /**
     * Promise based calls
     */

    /**
     * It closes the videocall room if it is running
     */
    closeRoom(): Promise<void>;

    /**
     * Check if the user granted all required permissions (Camera and Microphone)
     * @return If user has granted all permissions or not
     */
    hasRequiredPermissions(): Promise<boolean>;

    /**
     * Request required permissions (Camera and Microphone)
     * @return If user has granted all permissions or not
     */
    requestPermissions(): Promise<boolean>;
  }
}

declare var twiliovideo: TwilioVideo.TwilioVideoPlugin;
