
#import "RCTSpotify.h"
#import <AVFoundation/AVFoundation.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>
#import "RCTSpotifyAuthController.h"
#import "RCTSpotifyProgressView.h"
#import "RCTSpotifyConvert.h"
#import "HelperMacros.h"


NSString* const RCTSpotifyErrorDomain = @"RCTSpotifyErrorDomain";
NSString* const RCTSpotifyWebAPIDomain = @"com.spotify.web-api";

#define SPOTIFY_API_BASE_URL @"https://api.spotify.com/"
#define SPOTIFY_API_URL(endpoint) [NSURL URLWithString:NSString_concat(SPOTIFY_API_BASE_URL, endpoint)]

@interface RCTSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate>
{
	BOOL _initialized;
	BOOL _loggingIn;
	BOOL _loggingOut;
	
	SPTAuth* _auth;
	SPTAudioStreamingController* _player;
	
	NSDictionary* _options;
	NSNumber* _cacheSize;
	
	NSMutableArray<void(^)(BOOL, NSError*)>* _loginPlayerResponses;
	NSMutableArray<void(^)(NSError*)>* _logoutResponses;
	
	NSString* _audioSessionCategory;
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

@synthesize bridge = _bridge;

-(id)init
{
	if(self = [super init])
	{
		_initialized = NO;
		_loggingIn = NO;
		_loggingOut = NO;
		
		_auth = nil;
		_player = nil;
		
		_options = nil;
		_cacheSize = nil;
		
		_loginPlayerResponses = [NSMutableArray array];
		_logoutResponses = [NSMutableArray array];
		
		_audioSessionCategory = nil;
	}
	return self;
}

+(BOOL)requiresMainQueueSetup
{
	return YES;
}

RCT_EXPORT_METHOD(__registerAsJSEventEmitter:(int)moduleId)
{
	[RNEventEmitter registerEventEmitterModule:self withID:moduleId bridge:_bridge];
}

-(void)sendEvent:(NSString*)event args:(NSArray*)args
{
	[RNEventEmitter emitEvent:event withParams:args module:self bridge:_bridge];
}

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

-(void)activateAudioSession
{
	AVAudioSession* audioSession = [AVAudioSession sharedInstance];
	NSError* error = nil;
	if(![_audioSessionCategory isEqualToString:audioSession.category])
	{
		[audioSession setCategory:_audioSessionCategory error:&error];
		if(error != nil)
		{
			NSLog(@"Error setting spotify audio session category: %@", error);
		}
	}
	error = nil;
	[audioSession setActive:YES error:&error];
	if(error != nil)
	{
		NSLog(@"Error setting spotify audio session active: %@", error);
	}
}

-(void)deactivateAudioSession
{
	AVAudioSession* audioSession = [AVAudioSession sharedInstance];
	NSError* error = nil;
	[audioSession setActive:NO error:&error];
	if(error != nil)
	{
		NSLog(@"Error setting spotify audio session inactive: %@", error);
	}
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
			completion(@[ [self isLoggedIn], [RCTSpotifyConvert NSError:[RCTSpotify errorWithCode:RCTSpotifyErrorCodeAlreadyInitialized description:@"Spotify has already been initialized"]] ]);
		}
		return;
	}
	_initialized = NO;
	
	//set default values
	_options = options;
	_auth = [SPTAuth defaultInstance];
	_player = [SPTAudioStreamingController sharedInstance];
	_cacheSize = @(1024 * 1024 * 64);
	
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
	
	NSDictionary* iosOptions = options[@"ios"];
	if(iosOptions == nil)
	{
		iosOptions = @{};
	}
	_audioSessionCategory = iosOptions[@"audioSessionCategory"];
	if(_audioSessionCategory == nil)
	{
		_audioSessionCategory = AVAudioSessionCategoryPlayback;
	}
	
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error) {
		_initialized = YES;
		if(loggedIn)
		{
			[self sendEvent:@"login" args:@[]];
		}
		if(completion)
		{
			completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isInitialized)
{
	if(_auth==nil)
	{
		return @NO;
	}
	return [NSNumber numberWithBool:_initialized];
}

RCT_EXPORT_METHOD(isInitializedAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self isInitialized]] ]);
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
	else
	{
		[self renewSessionIfNeeded:^(BOOL renewed, NSError* error) {
			if(error != nil)
			{
				completion(NO, error);
			}
			else
			{
				[self initializePlayerIfNeeded:^(BOOL loggedIn, NSError* error) {
					completion(loggedIn, error);
				}];
			}
		}];
	}
}

-(void)renewSessionIfNeeded:(void(^)(BOOL, NSError*))completion
{
	if(_auth.session == nil)
	{
		completion(NO, nil);
	}
	else if(_auth.session.isValid)
	{
		completion(YES, nil);
	}
	else if(_auth.session.encryptedRefreshToken == nil)
	{
		completion(NO, nil);
	}
	else
	{
		[self renewSession:^(BOOL renewed, NSError* error) {
			completion(renewed, error);
		} wait:NO];
	}
}

-(void)renewSession:(void(^)(BOOL, NSError*))completion wait:(BOOL)waitForResponse
{
	if(_auth.session == nil)
	{
		completion(NO, nil);
	}
	else if(!_auth.hasTokenRefreshService)
	{
		completion(NO, nil);
	}
	else if(_auth.session.encryptedRefreshToken == nil)
	{
		completion(NO, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeAuthorizationFailed description:@"Can't refresh session without a refresh token"]);
	}
	else
	{
		[_auth renewSession:_auth.session callback:^(NSError* error, SPTSession* session){
			if(_auth.session != nil)
			{
				_auth.session = session;
			}
			if(error != nil)
			{
				completion(NO, error);
			}
			else
			{
				completion(YES, nil);
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
	// ensure we're not already logging in
	if(_loggingIn)
	{
		if(completion != nil)
		{
			completion(@[ @NO, [RCTSpotifyConvert NSError:[RCTSpotify errorWithCode:RCTSpotifyErrorCodeConflictingCallbacks description:@"Cannot call login multiple times before completing"]] ]);
		}
		return;
	}
	_loggingIn = YES;
	// do UI logic on main thread
	dispatch_async(dispatch_get_main_queue(), ^{
		RCTSpotifyAuthController* authController = [[RCTSpotifyAuthController alloc] initWithAuth:_auth];
		
		__weak RCTSpotifyAuthController* weakAuthController = authController;
		authController.completion = ^(BOOL authenticated, NSError* error) {
			RCTSpotifyAuthController* authController = weakAuthController;
			
			if(!authenticated)
			{
				[authController.presentingViewController dismissViewControllerAnimated:YES completion:^{
					_loggingIn = NO;
					if(completion != nil)
					{
						completion(@[ @NO, [NSNull null] ]);
					}
				}];
				return;
			}
			
			[self initializePlayerIfNeeded:^(BOOL loggedIn, NSError* error) {
				dispatch_async(dispatch_get_main_queue(), ^{
					[authController.presentingViewController dismissViewControllerAnimated:YES completion:^{
						_loggingIn = NO;
						if(loggedIn)
						{
							[self sendEvent:@"login" args:@[]];
						}
						if(completion)
						{
							completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotifyConvert NSError:error] ]);
						}
					}];
				});
			}];
		};
		
		UIViewController* topViewController = [RCTSpotifyAuthController topViewController];
		[topViewController presentViewController:authController animated:YES completion:nil];
	});
}

RCT_EXPORT_METHOD(logout:(RCTResponseSenderBlock)completion)
{
	if(_player == nil)
	{
		_auth.session = nil;
		if(completion)
		{
			completion(@[ [NSNull null] ]);
		}
		return;
	}
	dispatch_async(dispatch_get_main_queue(), ^{
		if(!_player.loggedIn)
		{
			// if player is already logged out, clear session and finish
			_auth.session = nil;
			if(completion)
			{
				completion(@[ [NSNull null] ]);
			}
			return;
		}
		// log out player
		if(completion!=nil)
		{
			[_logoutResponses addObject:^void(NSError* error) {
				completion(@[ [RCTSpotifyConvert NSError:error] ]);
			}];
		}
		if(!_loggingOut)
		{
			_loggingOut = YES;
			[_player logout];
		}
	});
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isLoggedIn)
{
	if(_auth.session == nil)
	{
		return @NO;
	}
	return @YES;
}

RCT_EXPORT_METHOD(isLoggedInAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self isLoggedIn]] ]);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getAuth)
{
	return [RCTSpotifyConvert SPTAuth:_auth];
}

RCT_EXPORT_METHOD(getAuthAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self getAuth]] ]);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(handleAuthURL:(NSString*)urlString)
{
	// unused function
	return @NO;
}

RCT_EXPORT_METHOD(handleAuthURLAsync:(NSString*)url completion:(RCTResponseSenderBlock)completion)
{
	// unused function
	return completion(@[ [self handleAuthURL:url] ]);
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

RCT_EXPORT_METHOD(getVolumeAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self getVolume]] ]);
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
			repeatMode = SPTRepeatContext;
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

RCT_EXPORT_METHOD(getPlaybackStateAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self getPlaybackState]] ]);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPlaybackMetadata)
{
	return [RCTSpotifyConvert SPTPlaybackMetadata:_player.metadata];
}

RCT_EXPORT_METHOD(getPlaybackMetadataAsync:(RCTResponseSenderBlock)completion)
{
	completion(@[ [RCTSpotifyConvert ID:[self getPlaybackMetadata]] ]);
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
		
		BOOL isJSON = NO;
		NSError* httpError = nil;
		if([response isKindOfClass:[NSHTTPURLResponse class]])
		{
			NSHTTPURLResponse* httpResponse = (NSHTTPURLResponse*)response;
			NSString* contentType = httpResponse.allHeaderFields[@"Content-Type"];
			if(contentType!=nil)
			{
				contentType = [contentType componentsSeparatedByString:@";"][0];
			}
			if([contentType caseInsensitiveCompare:@"application/json"] == NSOrderedSame)
			{
				isJSON = YES;
			}
			if(httpResponse.statusCode < 200 || httpResponse.statusCode >= 300)
			{
				httpError = [NSError errorWithDomain:RCTSpotifyWebAPIDomain code:httpResponse.statusCode userInfo:@{NSLocalizedDescriptionKey:[NSHTTPURLResponse localizedStringForStatusCode:httpResponse.statusCode]}];
			}
		}
		
		id resultObj = nil;
		if(isJSON)
		{
			resultObj = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
			callbackAndReturnIfError(error, completion, resultObj, error);
		}
		else
		{
			resultObj = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
		}
		
		if(resultObj != nil && [resultObj isKindOfClass:[NSDictionary class]])
		{
			id errorObj = resultObj[@"error_description"];
			if(errorObj!=nil && [errorObj isKindOfClass:[NSString class]])
			{
				completion(resultObj, [NSError errorWithDomain:RCTSpotifyWebAPIDomain
														  code:httpError.code
													  userInfo:@{NSLocalizedDescriptionKey:errorObj}]);
				return;
			}
			errorObj = resultObj[@"error"];
			if(errorObj!=nil && [errorObj isKindOfClass:[NSDictionary class]])
			{
				completion(resultObj, [NSError errorWithDomain:RCTSpotifyWebAPIDomain
														code:[errorObj[@"status"] integerValue]
													userInfo:@{NSLocalizedDescriptionKey:errorObj[@"message"]}]);
				return;
			}
			completion(resultObj, httpError);
		}
		else
		{
			completion(resultObj, httpError);
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

RCT_EXPORT_METHOD(getMe:(RCTResponseSenderBlock)completion)
{
	[self doAPIRequest:@"v1/me" method:@"GET" params:nil jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(search:(NSString*)query types:(NSArray<NSString*>*)types options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(query, completion, [NSNull null], );
	reactCallbackAndReturnIfNil(types, completion, [NSNull null], );
	
	NSMutableDictionary* body = [[self class] mutableDictFromDict:options];
	body[@"q"] = query;
	body[@"type"] = [types componentsJoinedByString:@","];
	
	[self doAPIRequest:@"v1/search" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getAlbum:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(albumID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"v1/albums/", albumID);
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
	
	[self doAPIRequest:@"v1/albums" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getAlbumTracks:(NSString*)albumID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(albumID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"v1/albums/", albumID, @"/tracks");
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
	
	NSString* endpoint = NSString_concat(@"v1/artists/", artistID);
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
	
	[self doAPIRequest:@"v1/artists" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtistAlbums:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"v1/artists/", artistID, @"/albums");
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
	
	NSString* endpoint = NSString_concat(@"v1/artists/", artistID, @"/top-tracks");
	[self doAPIRequest:endpoint method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getArtistRelatedArtists:(NSString*)artistID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(artistID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"v1/artists/", artistID, @"/related-artists");
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
	
	NSString* endpoint = NSString_concat(@"v1/tracks/", trackID);
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
	
	[self doAPIRequest:@"v1/tracks" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
}

RCT_EXPORT_METHOD(getTrackAudioAnalysis:(NSString*)trackID options:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(trackID, completion, [NSNull null], );
	
	NSString* endpoint = NSString_concat(@"v1/audio-analysis/", trackID);
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
	
	NSString* endpoint = NSString_concat(@"v1/audio-features/", trackID);
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
	
	[self doAPIRequest:@"v1/audio-features" method:@"GET" params:body jsonBody:NO completion:^(id resultObj, NSError* error) {
		if(completion)
		{
			completion(@[ [RCTSpotifyConvert ID:resultObj], [RCTSpotifyConvert NSError:error] ]);
		}
	}];
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
	BOOL wasLoggingOut = _loggingOut;
	_loggingOut = NO;
	
	// handle loginPlayer callbacks
	NSArray<void(^)(BOOL, NSError*)>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
	[_loginPlayerResponses removeAllObjects];
	for(void(^response)(BOOL,NSError*) in loginPlayerResponses)
	{
		response(NO, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"Spotify was logged out"]);
	}
	
	// if we didn't explicitly log out, try to renew the session
	if(!wasLoggingOut)
	{
		if(_auth.hasTokenRefreshService && _auth.session != nil && _auth.session.encryptedRefreshToken != nil)
		{
			[self renewSession:^(BOOL renewed, NSError* error) {
				if(renewed)
				{
					[self loginPlayer:_auth.session.accessToken completion:^(BOOL loggedIn, NSError* error) {
						// do nothing
					}];
				}
				else
				{
					// clear session
					_auth.session = nil;
					// send logout event
					[self sendEvent:@"logout" args:@[]];
				}
			} wait:YES];
			return;
		}
	}
	
	// clear session and stop player
	_auth.session = nil;
	[_player stopWithError:nil];
	// send logout event
	[self sendEvent:@"logout" args:@[]];
	
	// handle logout callbacks
	NSArray<void(^)(NSError*)>* logoutResponses = [NSArray arrayWithArray:_logoutResponses];
	[_logoutResponses removeAllObjects];
	for(void(^response)(NSError*) in logoutResponses)
	{
		response(nil);
	}
}

-(void)audioStreamingDidDisconnect:(SPTAudioStreamingController *)audioStreaming
{
	[self sendEvent:@"disconnect" args:@[]];
}

-(void)audioStreamingDidReconnect:(SPTAudioStreamingController*)audioStreaming
{
	[self sendEvent:@"reconnect" args:@[]];
}

-(void)audioStreamingDidEncounterTemporaryConnectionError:(SPTAudioStreamingController*)audioStreaming
{
	[self sendEvent:@"temporaryPlayerError" args:@[]];
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveMessage:(NSString*)message
{
	[self sendEvent:@"playerMessage" args:@[message]];
}



#pragma mark - SPTAudioStreamingPlaybackDelegate

-(NSMutableDictionary*)createPlaybackEvent
{
	NSMutableDictionary* event = [NSMutableDictionary dictionary];
	event[@"state"] = [self getPlaybackState];
	event[@"metadata"] = [self getPlaybackMetadata];
	event[@"error"] = [NSNull null];
	return event;
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceivePlaybackEvent:(SpPlaybackEvent)event
{
	switch(event)
	{
		case SPPlaybackNotifyPlay:
			[self sendEvent:@"play" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyPause:
			[self sendEvent:@"pause" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyTrackChanged:
			[self sendEvent:@"trackChange" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyMetadataChanged:
			[self sendEvent:@"metadataChange" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyContextChanged:
			[self sendEvent:@"contextChange" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyShuffleOn:
		case SPPlaybackNotifyShuffleOff:
			// ignore in favor of delegate event
			break;
			
		case SPPlaybackNotifyRepeatOn:
		case SPPlaybackNotifyRepeatOff:
			// ignore in favor of delegate event
			break;
			
		case SPPlaybackNotifyBecameActive:
			[self sendEvent:@"active" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyBecameInactive:
			[self sendEvent:@"inactive" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyLostPermission:
			[self sendEvent:@"permissionLost" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackEventAudioFlush:
			[self sendEvent:@"audioFlush" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyAudioDeliveryDone:
			[self sendEvent:@"audioDeliveryDone" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyTrackDelivered:
			[self sendEvent:@"trackDelivered" args:@[[self createPlaybackEvent]]];
			break;
			
		case SPPlaybackNotifyNext:
		case SPPlaybackNotifyPrev:
			// deprecated
			break;
	}
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didChangePlaybackStatus:(BOOL)isPlaying
{
	if(isPlaying)
	{
		[self activateAudioSession];
	}
	else
	{
		[self deactivateAudioSession];
	}
}

-(void)audioStreaming:(SPTAudioStreamingController *)audioStreaming didChangeShuffleStatus:(BOOL)enabled
{
	[self sendEvent:@"shuffleStatusChange" args:@[[self createPlaybackEvent]]];
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didChangeRepeatStatus:(SPTRepeatMode)repeateMode
{
	[self sendEvent:@"repeatStatusChange" args:@[[self createPlaybackEvent]]];
}

@end

