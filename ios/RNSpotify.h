
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

@interface RNSpotify : NSObject <RCTBridgeModule, RNEventConformer>

//test()
-(id)test;

//initialize(options)
-(void)initialize:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//isInitialized()
-(id)isInitialized;
//isInitializedAsync()
-(void)isInitializedAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

//authenticate(options)
-(void)authenticate:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//loginWithSession(session)
-(void)loginWithSession:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//login(options)
-(void)login:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//logout()
-(void)logout:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//isLoggedIn()
-(id)isLoggedIn;
//isLoggedInAsync()
-(void)isLoggedInAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//getSession()
-(id)getSession;
//getSessionAsync()
-(void)getSessionAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//renewSession()
-(void)renewSession:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

//playURI(spotifyURI, startIndex, startPosition)
-(void)playURI:(NSString*)uri startIndex:(NSUInteger)startIndex startPosition:(NSTimeInterval)startPosition resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//queueURI(spotifyURI)
-(void)queueURI:(NSString*)uri resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//setVolume(volume)
-(void)setVolume:(double)volume resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//getVolume()
-(id)getVolume;
//getVolumeAsync()
-(void)getVolumeAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//setPlaying(playing)
-(void)setPlaying:(BOOL)playing resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//getPlaybackState()
-(id)getPlaybackState;
//getPlaybackStateAsync()
-(void)getPlaybackStateAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//getPlaybackMetadata()
-(id)getPlaybackMetadata;
//getPlaybackMetadataAsync:()
-(void)getPlaybackMetadataAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//skipToNext()
-(void)skipToNext:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//skipToPrevious()
-(void)skipToPrevious:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//seek(position)
-(void)seek:(double)position resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//setShuffling(shuffling)
-(void)setShuffling:(BOOL)shuffling resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
//setRepeating(repeating)
-(void)setRepeating:(BOOL)repeating resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

//sendRequest(endpoint, method, params, isJSONBody)
-(void)sendRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params isJSONBody:(BOOL)jsonBody resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

@end
