
#import "RCTSpotify.h"
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate>
{
	SPTAuth* _auth;
	
	
}
@end



@implementation RCTSpotify

-(id)init
{
	if(self = [super init])
	{
		//
	}
	return self;
}

RCT_EXPORT_MODULE()

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(test)
{
	NSLog(@"ayy lmao");
	return nil;
}

RCT_EXPORT_METHOD(start:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	SPTAuth* auth = [SPTAuth defaultInstance];
	auth.requestedScopes = @[SPTAuthStreamingScope];
	
	auth.clientID = options[@"clientID"];
	auth.redirectURL = [NSURL URLWithString:options[@"redirectURL"]];
	auth.sessionUserDefaultsKey = options[@"sessionUserDefaultsKey"];
	
	NSNumber* cacheSize = options[@"cacheSize"];
	if(cacheSize==nil)
	{
		cacheSize = @(1024 * 1024 * 64);
	}
	BOOL allowCaching = (cacheSize.unsignedIntegerValue > 0);
	
	SPTAudioStreamingController* player = [SPTAudioStreamingController sharedInstance];
	
	NSError* error = nil;
	if([player startWithClientId:auth.clientID audioController:nil allowCaching:allowCaching error:&error])
	{
		player.delegate = self;
		player.playbackDelegate = self;
		if(allowCaching)
		{
			player.diskCache = [[SPTDiskCache alloc] initWithCapacity:cacheSize.unsignedIntegerValue];
		}
		completion(@[
					 [NSNull null]
					 ]);
	}
	else
	{
		completion(@[@{
						 @"domain":error.domain,
						 @"code":@(error.code),
						 @"description":error.description
						 }]);
	}
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(stop)
{
	SPTAudioStreamingController* player = [SPTAudioStreamingController sharedInstance];
	NSError* error = nil;
	if(![player stopWithError:&error])
	{
		NSLog(@"error stopping Spotify player: %@", error.description);
	}
	
	SPTAuth* auth = [SPTAuth defaultInstance];
	auth.session = nil;
	
	return nil;
}

@end
  
