package org.apache.cordova.twiliovideo;

//To pass action to running TwilioVideoActivity
//https://stackoverflow.com/questions/4878159/whats-the-best-way-to-share-data-between-activities

//Intent only works with onCreate
//startCall jumps to onResume
//putExtra only works for onCreate
//intent_TwilioVideoActivity.putExtra("action", "openRoom");

//startCall doesnt recreate the activity it only brings it to the front
//so only onResume called
//but getIntent() only returns the intnet set by onCreate so shows action: "openRoom" not "startCall"

class TwilioVideoActivityNextAction {

    static String action_openRoom = "openRoom";
    static String action_startCall = "startCall";
    static String action_answerCall = "answerCall";

    //"openRoom"
    //"startCall"
    //"answerCall"

    private static String nextAction;

    public static String getNextAction() {
        return nextAction;
    }

    public static void setNextAction(String nextAction) {
        TwilioVideoActivityNextAction.nextAction = nextAction;
    }

}
