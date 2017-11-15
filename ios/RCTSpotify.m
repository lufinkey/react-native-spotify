
#import "RCTSpotify.h"
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>
#import "SpotifyWebViewController.h"
#import "RCTSpotifyConvert.h"
#import "HelperMacros.h"


NSString* const RCTSpotifyErrorDomain = @"RCTSpotifyErrorDomain";
NSString* const RCTSpotifyWebAPIDomain = @"com.spotify.web-api";

#define SPOTIFY_API_BASE_URL @"https://api.spotify.com/v1/"
#define SPOTIFY_API_URL(endpoint) [NSURL URLWithString:NSString_concat(SPOTIFY_API_BASE_URL, endpoint)]

@interface RCTSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate, SpotifyWebViewDelegate>
{
	SPTAuth* _auth;
	SPTAudioStreamingController* _player;
	
	NSDictionary* _options;
	NSNumber* _cacheSize;
	
	void(^_authControllerResponse)(BOOL loggedIn, NSError* error);
	NSMutableArray<void(^)(BOOL, NSError*)>* _loginPlayerResponses;
	NSMutableArray<void(^)(NSError*)>* _logoutResponses;
}
+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description;
+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description fields:(NSDictionary*)fields;
+(NSMutableDictionary*)mutableDictFromDict:(NSDictionary*)dict;
-(BOOL)hasPlayerScope;

-(void)logBackInIfNeeded:(void(^)(BOOL loggedIn, NSError* error))completion;
-(void)initializePlayerIfNeeded:(void(^)(BOOL loggedIn, NSError* error))completion;
-(void)loginPlayer:(NSString*)accessToken completion:(void(^)(BOOL, NSError*))completion;
-(void)prepareForPlayer:(void(^)(NSError*))completion;
-(void)prepareForRequest:(void(^)(NSError* error))completion;
-(void)performRequest:(NSURLRequest*)request completion:(void(^)(id resultObj, NSError* error))completion;
-(void)doAPIRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params jsonBody:(BOOL)jsonBody completion:(void(^)(id resultObj, NSError* error))completion;
@end

@implementation RCTSpotify

+(id)reactSafeArg:(id)arg
{
	if(arg==nil)
	{
		return [NSNull null];
	}
	return arg;
}

+(id)objFromError:(NSError*)error
{
	if(error==nil)
	{
		return [NSNull null];
	}
	NSDictionary* fields = error.userInfo[@"jsFields"];
	NSMutableDictionary* obj = nil;
	if(fields!=nil)
	{
		obj = fields.mutableCopy;
	}
	else
	{
		obj = [NSMutableDictionary dictionary];
	}
	obj[@"domain"] = error.domain;
	obj[@"code"] = @(error.code);
	obj[@"description"] = error.localizedDescription;
	return obj;
}

+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description
{
	return [RCTSpotify errorWithCode:code description:description fields:nil];
}

+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description fields:(NSDictionary*)fields
{
	NSMutableDictionary* userInfo = [NSMutableDictionary dictionary];
	userInfo[NSLocalizedDescriptionKey] = description;
	if(fields!=nil)
	{
		userInfo[@"jsFields"] = fields;
	}
	return [NSError errorWithDomain:RCTSpotifyErrorDomain code:code userInfo:userInfo];
}

+(NSMutableDictionary*)mutableDictFromDict:(NSDictionary*)dict
{
	if(dict==nil)
	{
		return [NSMutableDictionary dictionary];
	}
	return dict.mutableCopy;
}

-(BOOL)hasPlayerScope
{
	if(_options==nil)
	{
		return NO;
	}
	id scopes = _options[@"scopes"];
	if(scopes==nil || ![scopes isKindOfClass:[NSArray class]])
	{
		return NO;
	}
	return [scopes containsObject:@"streaming"];
}



#pragma mark - React Native functions

RCT_EXPORT_MODULE()

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(test)
{
	NSLog(@"ayy lmao");
	return [NSNull null];
}

RCT_EXPORT_METHOD(initialize:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	if(_auth!=nil)
	{
		if(completion)
		{
			completion(@[ [self isLoggedIn], [RCTSpotify errorWithCode:RCTSpotifyAlreadyInitialized description:@"Spotify has already been initialized"] ]);
		}
		return;
	}
	
	//set default values
	_options = options;
	_auth = [SPTAuth defaultInstance];
	_player = [SPTAudioStreamingController sharedInstance];
	_cacheSize = @(1024 * 1024 * 64);
	_authControllerResponse = nil;
	_loginPlayerResponses = [NSMutableArray array];
	_logoutResponses = [NSMutableArray array];
	
	//get options
	_auth.clientID = options[@"clientID"];
	_auth.redirectURL = [NSURL URLWithString:options[@"redirectURL"]];
	_auth.sessionUserDefaultsKey = options[@"sessionUserDefaultsKey"];
	_auth.requestedScopes = options[@"scopes"];
	_auth.tokenSwapURL = [NSURL URLWithString:options[@"tokenSwapURL"]];
	_auth.tokenRefreshURL = [NSURL URLWithString:options[@"tokenRefreshURL"]];
	NSNumber* cacheSize = options[@"cacheSize"];
	if(cacheSize!=nil)
	{
		_cacheSize = cacheSize;
	}
	
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error) {
		if(completion)
		{
			completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}



#pragma mark - React Native functions - Session Handling

-(void)logBackInIfNeeded:(void(^)(BOOL, NSError*))completion
{
	if(_auth==nil)
	{
		completion(NO, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotInitialized description:@"Spotify has not been initialized"]);
	}
	else if(_auth.session == nil)
	{
		completion(NO, nil);
	}
	else if([_auth.session isValid])
	{
		[self initializePlayerIfNeeded:^(BOOL loggedIn, NSError* error) {
			completion(loggedIn, error);
		}];
	}
	else if(!_auth.hasTokenRefreshService)
	{
		completion(NO, nil);
	}
	else
	{
		[_auth renewSession:_auth.session callback:^(NSError* error, SPTSession* session){
			if(error!=nil)
			{
				completion(NO, error);
			}
			else
			{
				_auth.session = session;
				[self initializePlayerIfNeeded:^(BOOL loggedIn, NSError* error) {
					completion(loggedIn, error);
				}];
			}
		}];
	}
}

-(void)initializePlayerIfNeeded:(void(^)(BOOL,NSError*))completion
{
	if(![self hasPlayerScope])
	{
		completion(YES, nil);
		return;
	}
	BOOL allowCaching = (_cacheSize.unsignedIntegerValue > 0);
	NSError* error = nil;
	if(_player.initialized)
	{
		if(!_player.loggedIn)
		{
			[self loginPlayer:_auth.session.accessToken completion:^(BOOL loggedIn, NSError* error) {
				completion(loggedIn, error);
			}];
		}
		else
		{
			completion(YES, nil);
		}
	}
	else if([_player startWithClientId:_auth.clientID audioController:nil allowCaching:allowCaching error:&error])
	{
		_player.delegate = self;
		_player.playbackDelegate = self;
		if(allowCaching)
		{
			_player.diskCache = [[SPTDiskCache alloc] initWithCapacity:_cacheSize.unsignedIntegerValue];
		}
		
		[self loginPlayer:_auth.session.accessToken completion:^(BOOL loggedIn, NSError* error) {
			completion(loggedIn, error);
		}];
	}
	else
	{
		completion(NO, error);
	}
}

-(void)loginPlayer:(NSString*)accessToken completion:(void(^)(BOOL, NSError*))completion
{
	if(accessToken==nil)
	{
		completion(NO, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"No access token has been received"]);
		return;
	}
	else if(_player.loggedIn)
	{
		completion(YES, nil);
		return;
	}
	dispatch_async(dispatch_get_main_queue(), ^{
		//wait for audioStreamingDidLogin:
		// or audioStreaming:didRecieveError:
		[_loginPlayerResponses addObject:^(BOOL loggedIn, NSError* error){
			completion(loggedIn, error);
		}];
		[_player loginWithAccessToken:accessToken];
		if(_player.loggedIn)
		{
			NSArray<void(^)(BOOL,NSError*)>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
			[_loginPlayerResponses removeAllObjects];
			for(void(^response)(BOOL,NSError*) in loginPlayerResponses)
			{
				response(YES, nil);
			}
		}
	});
}

RCT_EXPORT_METHOD(login:(RCTResponseSenderBlock)completion)
{
	//do UI logic on main thread
	dispatch_async(dispatch_get_main_queue(), ^{
		SpotifyWebViewController* authController = [[SpotifyWebViewController alloc] initWithURL:_auth.spotifyWebAuthenticationURL];
		authController.title = @"Log into Spotify";
		authController.delegate = self;
		UIViewController* rootController = [UIApplication sharedApplication].keyWindow.rootViewController;
		if(rootController == nil)
		{
			//no root view controller to present on
			if(completion)
			{
				completion(@[ @NO, @{@"description":@"can't login when not in foreground"} ]);
			}
		}
		else
		{
			__weak RCTSpotify* _self = self;
			
			if(_authControllerResponse != nil)
			{
				if(completion)
				{
					completion(@[ @NO, [RCTSpotifyConvert NSError:[RCTSpotify errorWithCode:RCTSpotifyErrorCodeConflictingCallbacks description:@"Cannot call login while login is already being called"]] ]);
				}
				return;
			}
			
			//wait for handleAuthURL:
			// or spotifyWebControllerDidCancelLogin
			_authControllerResponse = ^(BOOL loggedIn, NSError* error){
				authController.view.userInteractionEnabled = NO;
				if(!loggedIn)
				{
					if(authController.presentingViewController != nil)
					{
						[authController.presentingViewController dismissViewControllerAnimated:YES completion:nil];
					}
					if(completion)
					{
						completion(@[ @NO, [RCTSpotifyConvert NSError:error] ]);
					}
					return;
				}
				[_self initializePlayerIfNeeded:^(BOOL loggedIn, NSError* error) {
					dispatch_async(dispatch_get_main_queue(), ^{
						if(authController.presentingViewController != nil)
						{
							[authController.presentingViewController dismissViewControllerAnimated:YES completion:nil];
						}
						if(completion)
						{
							completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotifyConvert NSError:error] ]);
						}
					});
				}];
			};
			[rootController presentViewController:authController animated:YES completion:nil];
		}
	});
}

RCT_EXPORT_METHOD(logout:(RCTResponseSenderBlock)completion)
{
	if(![[self isLoggedIn] boolValue])
	{
		if(completion)
		{
			completion(@[ [NSNull null] ]);
		}
		return;
	}
	dispatch_async(dispatch_get_main_queue(), ^{
		if(![[self isLoggedIn] boolValue])
		{
			if(completion)
			{
				completion(@[ [NSNull null] ]);
			}
			return;
		}
		
		__weak RCTSpotify* _self = self;
		[_logoutResponses addObject:^void(NSError* error) {
			RCTSpotify* __self = _self;
			[__self->_player stopWithError:&error];
			__self->_auth.session = nil;
			
			if(completion!=nil)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
		[_player logout];
	});
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isLoggedIn)
{
	if(_auth.session == nil)
	{
		return @NO;
	}
	else if(![_auth.session isValid])
	{
		return @NO;
	}
	else if(!_player.loggedIn)
	{
		return @NO;
	}
	return @YES;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(handleAuthURL:(NSString*)urlString)
{
	NSURL* url = [NSURL URLWithString:urlString];
	if([_auth canHandleURL:url])
	{
		[_auth handleAuthCallbackWithTriggeredAuthURL:url callback:^(NSError* error, SPTSession* session){
			if(session!=nil)
			{
				_auth.session = session;
			}
			if(_authControllerResponse != nil)
			{
				void(^response)(BOOL, NSError*) = _authControllerResponse;
				_authControllerResponse = nil;
				if(error!=nil)
				{
					response(NO, error);
				}
				else
				{
					response(YES, nil);
				}
			}
		}];
		return @YES;
	}
	return @NO;
}





#pragma mark - React Native functions - Playback

-(void)prepareForPlayer:(void(^)(NSError*))completion
{
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error){
		error = nil;
		if(_auth==nil)
		{
			error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotInitialized description:@"Spotify has not been initialized"];
		}
		completion(error);
	}];
}

RCT_EXPORT_METHOD(playURI:(NSString*)uri startIndex:(NSUInteger)startIndex startPosition:(NSTimeInterval)startPosition completion:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError* error) {
		if(error)
		{
			if(completion!=nil)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		
		[_player playSpotifyURI:uri startingWithIndex:startIndex startingWithPosition:startPosition callback:^(NSError* error) {
			if(completion!=nil)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(queueURI:(NSString*)uri completion:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError* error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		
		[_player queueSpotifyURI:uri callback:^(NSError* error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(setVolume:(double)volume completion:(RCTResponseSenderBlock)completion)
{
	[_player setVolume:(SPTVolume)volume callback:^(NSError *error){
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getVolume)
{
	if(_player==nil)
	{
		return nil;
	}
	return @(_player.volume);
}

RCT_EXPORT_METHOD(setPlaying:(BOOL)playing completion:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError* error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		[_player setIsPlaying:playing callback:^(NSError* error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(setShuffling:(BOOL)shuffling completion:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError* error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		[_player setShuffle:shuffling callback:^(NSError* error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(setRepeating:(BOOL)repeating completion:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError* error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		SPTRepeatMode repeatMode = SPTRepeatOff;
		if(repeating)
		{
			repeatMode = SPTRepeatOne;
		}
		[_player setRepeat:repeatMode callback:^(NSError* error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPlaybackState)
{
	return [RCTSpotifyConvert SPTPlaybackState:_player.playbackState];
}

RCT_EXPORT_METHOD(skipToNext:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError *error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		[_player skipNext:^(NSError *error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(skipToPrevious:(RCTResponseSenderBlock)completion)
{
	[self prepareForPlayer:^(NSError *error) {
		if(error)
		{
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
			return;
		}
		[_player skipPrevious:^(NSError *error) {
			if(completion)
			{
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}
		}];
	}];
}



#pragma mark - React Native functions - Request Sending

-(void)prepareForRequest:(void(^)(NSError*))completion
{
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error){
		error = nil;
		if(_auth==nil)
		{
			error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotInitialized description:@"Spotify has not been initialized"];
		}
		else if(_auth.session==nil || _auth.session.accessToken==nil)
		{
			error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"You are not logged in"];
		}
		completion(error);
	}];
}

-(void)performRequest:(NSURLRequest*)request completion:(void(^)(id, NSError*))completion
{
	[[SPTRequest sharedHandler] performRequest:request callback:^(NSError* error, NSURLResponse* response, NSData* data) {
		callbackAndReturnIfError(error, completion, nil, error);
		
		id jsonObj = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
		callbackAndReturnIfError(error, completion, jsonObj, error);
		
		if([jsonObj isKindOfClass:[NSDictionary class]])
		{
			NSDictionary* errorObj = jsonObj[@"error"];
			if(errorObj!=nil)
			{
				completion(jsonObj, [NSError errorWithDomain:RCTSpotifyWebAPIDomain
														code:[errorObj[@"status"] integerValue]
													userInfo:@{NSLocalizedDescriptionKey:errorObj[@"message"]}]);
			}
			else
			{
				completion(jsonObj, nil);
			}
		}
		else
		{
			completion(jsonObj, nil);
		}
	}];
}

-(void)doAPIRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params jsonBody:(BOOL)jsonBody completion:(void(^)(id,NSError*))completion
{
	[self prepareForRequest:^(NSError* error) {
		callbackAndReturnIfError(error, completion, nil, error);
		
		NSURLRequest* request = [SPTRequest createRequestForURL:SPOTIFY_API_URL(endpoint)
												withAccessToken:_auth.session.accessToken
													 httpMethod:method
														 values:params
												valueBodyIsJSON:jsonBody
										  sendDataAsQueryString:!jsonBody
														  error:&error];
		callbackAndReturnIfError(error, completion, nil, error);
		
		[self performRequest:request completion:^(id resultObj, NSError* error){
			if(completion)
			{
				completion(resultObj, error);
			}
		}];
	}];
}

RCT_EXPORT_METHOD(sendRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params isJSONBody:(BOOL)jsonBody completion:(RCTResponseSenderBlock)completion)
{
	[self doAPIRequest:endpoint method:method params:params jsonBody:jsonBody completion:^(id resultObj, NSError *error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}



#pragma mark - React Native methods - Web API

RCT_EXPORT_METHOD(search:(NSString*)query types:(NSArray<NSString*>*)types options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(query, completion, [NSNull null], );
	reactCallbackAndReturnIfNil(types, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"q"] = query;
	body[@"type"] = [types componentsJoinedByString:@","];
	
	[self doAPIRequest:@"search" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getAlbum:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(albumID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"albums/", albumID);
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError *error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getAlbums:(NSArray<NSString*>*)albumIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(albumIDs, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"ids"] = [albumIDs componentsJoinedByString:@","];
	
	[self doAPIRequest:@"albums" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getAlbumTracks:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(albumID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"albums/", albumID, @"/tracks");
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtist:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"artists/", artistID);
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError *error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtists:(NSArray<NSString*>*)artistIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistIDs, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"ids"] = [artistIDs componentsJoinedByString:@","];
	
	[self doAPIRequest:@"artists" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtistAlbums:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"artists/", artistID, @"/albums");
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtistTopTracks:(NSString*)artistID country:(NSString*)country options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	reactCallbackAndReturnIfNil(country, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"country"] = country;
	
	NSString* endpoint = NSString_concat(@"artists/", artistID, @"/top-tracks");
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtistRelatedArtists:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"artists/", artistID, @"/related-artists");
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTrack:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"tracks/", trackID);
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError *error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTracks:(NSArray<NSString*>*)trackIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackIDs, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"ids"] = [trackIDs componentsJoinedByString:@","];
	
	[self doAPIRequest:@"tracks" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTrackAudioAnalysis:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"audio-analysis/", trackID);
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError *error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTrackAudioFeatures:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"audio-features/", trackID);
	[self doAPIRequest:endpoint method:@"GET" params:options jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTracksAudioFeatures:(NSArray<NSString*>*)trackIDs options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackIDs, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"ids"] = [trackIDs componentsJoinedByString:@","];
	
	[self doAPIRequest:@"audio-features" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}



#pragma mark - SpotifyWebViewDelegate

-(void)spotifyWebControllerDidCancel:(SpotifyWebViewController*)webController
{
	if(_authControllerResponse != nil)
	{
		void(^response)(BOOL, NSError*) = _authControllerResponse;
		_authControllerResponse = nil;
		response(NO, nil);
	}
}



#pragma mark - SPTAudioStreamingDelegate

-(void)audioStreamingDidLogin:(SPTAudioStreamingController*)audioStreaming
{
	//do initializePlayerIfNeeded callbacks
	NSArray<void(^)(BOOL, NSError*)>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
	[_loginPlayerResponses removeAllObjects];
	for(void(^response)(BOOL,NSError*) in loginPlayerResponses)
	{
		response(YES, nil);
	}
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveError:(NSError*)error
{
	if(error.code==SPErrorGeneralLoginError || error.code==SPErrorLoginBadCredentials)
	{
		//do initializePlayerIfNeeded callbacks
		NSArray<void(^)(BOOL, NSError*)>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
		[_loginPlayerResponses removeAllObjects];
		for(void(^response)(BOOL,NSError*) in loginPlayerResponses)
		{
			response(NO, error);
		}
	}
}

-(void)audioStreamingDidLogout:(SPTAudioStreamingController*)audioStreaming
{
	NSError* error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"Spotify was logged out"];
	
	//do initializePlayerIfNeeded callbacks
	NSArray<void(^)(BOOL, NSError*)>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
	[_loginPlayerResponses removeAllObjects];
	for(void(^response)(BOOL,NSError*) in loginPlayerResponses)
	{
		response(NO, error);
	}
	
	//do logout callbacks
	NSArray<void(^)(NSError*)>* logoutResponses = [NSArray arrayWithArray:_logoutResponses];
	[_logoutResponses removeAllObjects];
	for(void(^response)(NSError*) in logoutResponses)
	{
		response(nil);
	}
}

@end

