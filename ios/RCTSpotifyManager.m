
#import "RCTSpotifyManager.h"
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotifyManager()
@end

@implementation RCTSpotifyManager

-(dispatch_queue_t)methodQueue
{
	return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(testSpotify)
{
	NSLog(@"ayy lmao");
}

@end
  
