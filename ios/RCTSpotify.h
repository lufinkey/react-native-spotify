
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
//logout((error?))
-(void)logout:(RCTResponseSenderBlock)completion;
//isLoggedIn()
-(id)isLoggedIn;
//handleAuthURL(url)
-(id)handleAuthURL:(NSString*)url;

//sendRequest(endpoint, method, params, isJSONBody, (result?, error?))
-(void)sendRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params isJSONBody:(BOOL)jsonBody completion:(RCTResponseSenderBlock)completion;

//search(query, types, options?, (result?, error?))
-(void)search:(NSString*)query types:(NSArray<NSString*>*)types options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

//getAlbum(albumID, options?, (result?, error?))
-(void)getAlbum:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getAlbums(albumIDs, options?, (result?, error?))
-(void)getAlbums:(NSArray<NSString*>*)albumIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getAlbumTracks(albumID, options?, (result?, error?))
-(void)getAlbumTracks:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

//getArtist(artistID, options?, (result?, error?))
-(void)getArtist:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getArtists(artistIDs, options?, (result?, error?))
-(void)getArtists:(NSArray<NSString*>*)artistIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getArtistAlbums(artistID, options?, (result?, error?))
-(void)getArtistAlbums:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getArtistTopTracks(artistID, country, options?, (result?, error?))
-(void)getArtistTopTracks:(NSString*)artistID country:(NSString*)country options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getArtistRelatedArtists(artistID, options?, (result?, error?))
-(void)getArtistRelatedArtists:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

//getTrack(trackID, options?, (result?, error?))
-(void)getTrack:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getTracks(trackIDs, options?, (result?, error?))
-(void)getTracks:(NSArray<NSString*>*)trackIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getTrackAudioAnalysis(trackID, options?, (result?, error?))
-(void)getTrackAudioAnalysis:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getTrackAudioFeatures(trackID, options?, (result?, error?))
-(void)getTrackAudioFeatures:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;
//getTracksAudioFeatures(trackIDs, options?, (result?, error?))
-(void)getTracksAudioFeatures:(NSArray<NSString*>*)trackIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion;

@end
