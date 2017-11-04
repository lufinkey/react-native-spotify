
#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

@interface RCTSpotifyManager : NSObject <RCTBridgeModule>

-(void)test;
-(NSDictionary*)createSpotify:(NSDictionary*)options;

@end
