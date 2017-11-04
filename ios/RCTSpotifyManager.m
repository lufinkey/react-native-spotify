
#import "RCTSpotifyManager.h"
#import "RCTSpotifyData.h"

@interface RCTSpotifyManager()
{
	NSMutableDictionary<NSString*,RCTSpotifyData*>* _spotifyInstances;
}
@end

@implementation RCTSpotifyManager

-(id)init
{
	if(self = [super init])
	{
		_spotifyInstances = [NSMutableDictionary dictionary];
	}
	return self;
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(test)
{
	NSLog(@"ayy lmao");
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(createSpotify:(NSDictionary*)options)
{
	SPTAuth* auth = [[SPTAuth alloc] init];
	auth.requestedScopes = @[SPTAuthStreamingScope];
	
	NSString* clientID = options[@"clientID"];
	if(clientID != nil)
	{
		auth.clientID = clientID;
	}
	NSString* redirectURL = options[@"redirectURL"];
	if(redirectURL != nil)
	{
		auth.redirectURL = [NSURL URLWithString:redirectURL];
	}
	NSString* sessionUserDefaultsKey = options[@"sessionUserDefaultsKey"];
	if(sessionUserDefaultsKey != nil)
	{
		auth.sessionUserDefaultsKey = sessionUserDefaultsKey;
	}
	
	RCTSpotifyData* instance = [[RCTSpotifyData alloc] initWithAuth:auth];
	NSString* instanceID = [NSString stringWithFormat:@"%p", instance];
	_spotifyInstances[instanceID] = instance;
	return @{
		@"instanceID":instanceID
	};
}

@end
  
