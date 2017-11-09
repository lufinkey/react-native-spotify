
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
	//! Bad parameters or options
	RCTSpotifyErrorCodeBadParameters = 102,
	//! RCTSpotify is not initialized
	RCTSpotifyErrorCodeNotInitialized = 103,
	//! RCTSpotify must be logged in to use this function
	RCTSpotifyErrorCodeNotLoggedIn = 104,
	//! A sent request returned an error
	RCTSpotifyErrorCodeRequestError = 105
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
-(void)search:(NSDictionary*)query completion:(RCTResponseSenderBlock)completion;
//getAlbum(albumID, options, (result?, error?))
-(void)getAlbum:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

@end
