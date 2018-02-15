
#if __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#else
#import <React/RCTBridgeModule.h>
#endif

#if __has_include("RNEventEmitter.h")
#import "RNEventEmitter.h"
#else
#import <RNEventEmitter/RNEventEmitter.h>
#endif

extern NSString* const RCTSpotifyErrorDomain;
extern NSString* const RCTSpotifyWebAPIDomain;

typedef enum
{
	//! RCTSpotify has already been initialized
	RCTSpotifyErrorAlreadyInitialized = 90,
	//! The initialize method failed
	RCTSpotifyErrorInitializationFailed = 91,
	//! Authorization (renewal or login) has failed
	RCTSpotifyErrorAuthorizationFailed = 92,
	//! Multiple calls of an asynchronous function are conflicting
	RCTSpotifyErrorConflictingCallbacks = 100,
	//! Missing parameters or options
	RCTSpotifyErrorMissingParameter = 101,
	//! Bad parameters or options
	RCTSpotifyErrorBadParameter = 102,
	//! RCTSpotify is not initialized
	RCTSpotifyErrorNotInitialized = 103,
	//! RCTSpotify must be logged in to use this function
	RCTSpotifyErrorNotLoggedIn = 104,
	//! A sent request returned an error
	RCTSpotifyErrorRequestFailed = 105
} RCTSpotifyErrorCode;



@interface RCTSpotify : NSObject <RCTBridgeModule, RNEventConformer>

+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description;

//test()
-(id)test;

//initialize(options, (loggedIn, error?))
-(void)initialize:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//isInitialized()
-(id)isInitialized;
//isInitializedAsync((initialized))
-(void)isInitializedAsync:(RCTResponseSenderBlock)completion;

//login((loggedIn, error?))
-(void)login:(RCTResponseSenderBlock)completion;
//logout((error?))
-(void)logout:(RCTResponseSenderBlock)completion;
//isLoggedIn()
-(id)isLoggedIn;
//isLoggedInAsync((loggedIn))
-(void)isLoggedInAsync:(RCTResponseSenderBlock)completion;
//getAuth()
-(id)getAuth;
//getAuthAsync((auth))
-(void)getAuthAsync:(RCTResponseSenderBlock)completion;

//playURI(spotifyURI, startIndex, startPosition, (error?))
-(void)playURI:(NSString*)uri startIndex:(NSUInteger)startIndex startPosition:(NSTimeInterval)startPosition completion:(RCTResponseSenderBlock)completion;
//queueURI(spotifyURI, (error?))
-(void)queueURI:(NSString*)uri completion:(RCTResponseSenderBlock)completion;
//setVolume(volume, (error?))
-(void)setVolume:(double)volume completion:(RCTResponseSenderBlock)completion;
//getVolume()
-(id)getVolume;
//getVolumeAsync((volume))
-(void)getVolumeAsync:(RCTResponseSenderBlock)completion;
//setPlaying(playing, (error?))
-(void)setPlaying:(BOOL)playing completion:(RCTResponseSenderBlock)completion;
//getPlaybackState()
-(id)getPlaybackState;
//getPlaybackStateAsync((playbackState))
-(void)getPlaybackStateAsync:(RCTResponseSenderBlock)completion;
//getPlaybackMetadata()
-(id)getPlaybackMetadata;
//getPlaybackMetadataAsync:((playbackMetadata))
-(void)getPlaybackMetadataAsync:(RCTResponseSenderBlock)completion;
//skipToNext((error?))
-(void)skipToNext:(RCTResponseSenderBlock)completion;
//skipToPrevious((error?))
-(void)skipToPrevious:(RCTResponseSenderBlock)completion;
//setShuffling(shuffling, (error?))
-(void)setShuffling:(BOOL)shuffling completion:(RCTResponseSenderBlock)completion;
//setRepeating(repeating, (error?))
-(void)setRepeating:(BOOL)repeating completion:(RCTResponseSenderBlock)completion;

//sendRequest(endpoint, method, params, isJSONBody, (result?, error?))
-(void)sendRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params isJSONBody:(BOOL)jsonBody completion:(RCTResponseSenderBlock)completion;

@end
