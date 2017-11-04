
#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

@interface RCTSpotify : NSObject <RCTBridgeModule>

-(id)test;

-(void)start:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
-(id)stop;

@end
