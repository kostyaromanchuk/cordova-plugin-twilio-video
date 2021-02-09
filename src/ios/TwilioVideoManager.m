#import "TwilioVideoManager.h"

@implementation TwilioVideoManager
+ (id)getInstance {
    static TwilioVideoManager *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}
- (void)publishEvent:(NSString*)event {
    if (self.eventDelegate != NULL) {
        [self.eventDelegate onCallEvent:event with:NULL];
    }
}
- (void)publishEvent:(NSString*)event with:(NSDictionary*)data {
    if (self.eventDelegate != NULL) {
        [self.eventDelegate onCallEvent:event with:data];
    }
}
//TwilioVideoPlugin closeRoom: used to call this
//but it doesnt do callbacks
//so instead in wilioVideoPlugin closeRoom: I call TVA.onDisconnect directly
- (BOOL)publishDisconnection {
    if (self.actionDelegate != NULL) {
        [self.actionDelegate onDisconnect];
        return true;
    }
    return false;
}
@end
