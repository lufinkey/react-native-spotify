
#import "RCTSpotifyManager.h"
#import "RCTSpotifyData.h"

@interface RCTSpotifyManager()
{
	NSMutableArray<RCTSpotifyData*>* _spotifyInstances;
}
-(RCTSpotifyData*)spotifyDataWithInstanceID:(NSString*)instanceID;
@end



@implementation RCTSpotifyManager

-(id)init
{
	if(self = [super init])
	{
		_spotifyInstances = [NSMutableArray array];
	}
	return self;
}

-(RCTSpotifyData*)spotifyDataWithInstanceID:(NSString*)instanceID
{
	for(RCTSpotifyData* spotifyData in _spotifyInstances)
	{
		if([instanceID isEqualToString:spotifyData.instanceID])
		{
			return spotifyData;
		}
	}
	return nil;
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(test)
{
	NSLog(@"ayy lmao");
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(createSpotifyInstance:(NSDictionary*)options)
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
	
	NSError* error = nil;
	RCTSpotifyData* instance = [[RCTSpotifyData alloc] initWithAuth:auth error:&error];
	if(instance == nil)
	{
		return @{
				 @"success":@NO,
				 @"error":error.localizedDescription
				 };
	}
	
	[_spotifyInstances addObject:instance];
	return @{
			 @"success":@YES,
			 @"instanceID":instance.instanceID
			 };
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(destroySpotifyInstance:(NSString*)instanceID)
{
	RCTSpotifyData* spotifyData = [self spotifyDataWithInstanceID:instanceID];
	if(spotifyData != nil)
	{
		[_spotifyInstances removeObject:spotifyData];
	}
	return nil;
}

@end
  
