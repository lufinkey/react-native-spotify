
#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

extern NSString* const RCTSpotifyErrorDomain;

typedef enum
{
	//! Multiple calls of an asynchronous function are conflicting
	RCTSpotifyErrorCodeConflictingCallbacks = 100,
	//! Missing parameters or options
	RCTSpotifyErrorCodeMissingParameters = 101,
	//! RCTSpotify is not initialized
	RCTSpotifyErrorCodeNotInitialized = 102,
	//! RCTSpotify must be logged in to use this function
	RCTSpotifyErrorCodeNotLoggedIn = 103
} RCTSpotifyErrorCode;



@interface RCTSpotify : NSObject <RCTBridgeModule>

+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description;

//test()
-(id)test;

//initialize(options, (loggedIn, error?))
-(void)initialize:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

//login((loggedIn, error?))
-(void)login:(RCTResponseSenderBlock)completion;
//isLoggedIn()
-(id)isLoggedIn;
//handleAuthURL(url)
-(id)handleAuthURL:(NSString*)url;

//search(query, (results?, error?))
-(void)search:(NSString*)query completion:(RCTResponseSenderBlock)completion;

@end
