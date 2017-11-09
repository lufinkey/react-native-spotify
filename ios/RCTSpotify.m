
#import "RCTSpotify.h"
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>
#import "SpotifyWebViewController.h"
#import "HelperMacros.h"


NSString* const RCTSpotifyErrorDomain = @"RCTSpotifyErrorDomain";

#define SPOTIFY_API_BASE_URL @"https://api.spotify.com/v1/"
#define SPOTIFY_API_URL(endpoint) [NSURL URLWithString:NSString_concat(SPOTIFY_API_BASE_URL, endpoint)]

@interface RCTSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate, SpotifyWebViewDelegate>
{
	SPTAuth* _auth;
	SPTAudioStreamingController* _player;
	
	NSNumber* _cacheSize;
	
	void(^_authControllerResponse)(BOOL loggedIn, NSError* error);
	void(^_startResponse)(BOOL loggedIn, NSError* error);
	
	NSMutableArray<void(^)(BOOL, NSError*)>* _logBackInResponses;
}
+(id)reactSafeArg:(id)arg;
+(id)objFromError:(NSError*)error;
+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description;
+(NSError*)errorWithCode:(RCTSpotifyErrorCode)code description:(NSString*)description fields:(NSDictionary*)fields;

-(void)logBackInIfNeeded:(void(^)(BOOL loggedIn, NSError* error))completion;
-(void)start:(void(^)(BOOL,NSError*))completion;
-(void)prepareForRequest:(void(^)(NSError*))completion;
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

#pragma mark - React Native functions

RCT_EXPORT_MODULE()

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(test)
{
	NSLog(@"ayy lmao");
	return nil;
}

RCT_EXPORT_METHOD(initialize:(NSDictionary*)options completion:(RCTResponseSenderBlock)completion)
{
	//set default values
	_auth = [SPTAuth defaultInstance];
	_player = [SPTAudioStreamingController sharedInstance];
	_cacheSize = @(1024 * 1024 * 64);
	_authControllerResponse = nil;
	_startResponse = nil;
	_logBackInResponses = [NSMutableArray array];
	
	//if a session exists, make sure it's using the same clientID. Otherwise, kill the session
	if(_auth.session != nil)
	{
		if(![_auth.clientID isEqualToString:options[@"clientID"]])
		{
			_auth.session = nil;
		}
	}
	
	//set default options
	_auth.requestedScopes = @[SPTAuthStreamingScope];
	
	//get options
	_auth.clientID = options[@"clientID"];
	_auth.redirectURL = [NSURL URLWithString:options[@"redirectURL"]];
	_auth.sessionUserDefaultsKey = options[@"sessionUserDefaultsKey"];
	_auth.tokenSwapURL = [NSURL URLWithString:options[@"tokenSwapURL"]];
	_auth.tokenRefreshURL = [NSURL URLWithString:options[@"tokenRefreshURL"]];
	NSNumber* cacheSize = options[@"cacheSize"];
	if(cacheSize!=nil)
	{
		_cacheSize = cacheSize;
	}
	
	if(_player.initialized)
	{
		NSLog(@"stopping player");
		NSError* error = nil;
		if(![_player stopWithError:&error])
		{
			NSLog(@"error stopping Spotify player: %@", error.localizedDescription);
		}
	}
	
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error) {
		if(loggedIn)
		{
			completion(@[ @YES, [NSNull null] ]);
		}
		else
		{
			completion(@[ @NO, [RCTSpotify objFromError:error] ]);
		}
	}];
}

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
		if(!_player.initialized)
		{
			[self start:^(BOOL loggedIn, NSError* error) {
				completion(loggedIn, error);
			}];
			return;
		}
		else if(_player.loggedIn)
		{
			completion(YES, nil);
			return;
		}
		dispatch_async(dispatch_get_main_queue(), ^{
			if(!_player.loggedIn)
			{
				//wait for audioStreamingDidLogin:
				// or audioStreamingDidReceiveError:
				// or audioStreamingDidLogout:
				[_logBackInResponses addObject:^(BOOL loggedIn, NSError* error) {
					completion(loggedIn, error);
				}];
				[_player loginWithAccessToken:_auth.session.accessToken];
			}
			else
			{
				completion(YES, nil);
			}
		});
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
				[self start:^(BOOL loggedIn, NSError* error) {
					completion(loggedIn, error);
				}];
			}
		}];
	}
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
			completion(@[ @NO, @{@"description":@"can't login when not in foreground"} ]);
		}
		else
		{
			__weak RCTSpotify* _self = self;
			
			if(_authControllerResponse != nil)
			{
				completion(@[ @NO, [RCTSpotify objFromError:[RCTSpotify errorWithCode:RCTSpotifyErrorCodeConflictingCallbacks description:@"Cannot call login while login is already being called"]] ]);
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
					completion(@[ @NO, [RCTSpotify objFromError:error] ]);
					return;
				}
				[_self start:^(BOOL loggedIn, NSError* error) {
					dispatch_async(dispatch_get_main_queue(), ^{
						if(authController.presentingViewController != nil)
						{
							[authController.presentingViewController dismissViewControllerAnimated:YES completion:nil];
						}
						completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotify objFromError:error] ]);
					});
				}];
			};
			[rootController presentViewController:authController animated:YES completion:nil];
		}
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
	return @YES;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(handleAuthURL:(NSString*)urlString)
{
	NSURL* url = [NSURL URLWithString:urlString];
	if([_auth canHandleURL:url])
	{
		[_auth handleAuthCallbackWithTriggeredAuthURL:url callback:^(NSError* error, SPTSession* session){
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

-(void)start:(void(^)(BOOL,NSError*))completion
{
	BOOL allowCaching = (_cacheSize.unsignedIntegerValue > 0);
	NSError* error = nil;
	if(_player.initialized && _player.loggedIn)
	{
		completion(YES, nil);
	}
	else if([_player startWithClientId:_auth.clientID audioController:nil allowCaching:allowCaching error:&error])
	{
		_player.delegate = self;
		_player.playbackDelegate = self;
		if(allowCaching)
		{
			_player.diskCache = [[SPTDiskCache alloc] initWithCapacity:_cacheSize.unsignedIntegerValue];
		}
		
		dispatch_async(dispatch_get_main_queue(), ^{
			if(_startResponse != nil)
			{
				completion(NO, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeConflictingCallbacks description:@"cannot call start method while start is already being called"]);
				return;
			}
			
			//wait for audioStreamingDidLogin:
			// or audioStreaming:didRecieveError:
			_startResponse = ^(BOOL loggedIn, NSError* error){
				completion(loggedIn, error);
			};
			[_player loginWithAccessToken:_auth.session.accessToken];
		});
	}
	else
	{
		completion(NO, error);
	}
}

-(void)prepareForRequest:(void(^)(NSError*))completion
{
	[self logBackInIfNeeded:^(BOOL loggedIn, NSError* error){
		if(!loggedIn)
		{
			if(error == nil)
			{
				error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"You are not logged in"];
			}
			completion(error);
		}
		else
		{
			completion(nil);
		}
	}];
}

-(void)doRequest:(NSURLRequest*)request completion:(void(^)(id, NSError*))completion
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
				completion(jsonObj, [RCTSpotify errorWithCode:RCTSpotifyErrorCodeRequestError
												  description:errorObj[@"message"]
													   fields:@{@"statusCode":errorObj[@"status"]}]);
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

RCT_EXPORT_METHOD(search:(NSDictionary*)query completion:(RCTResponseSenderBlock)completion)
{
	reactCallbackAndReturnIfNil(query, completion, [NSNull null], );
	
	[self prepareForRequest:^(NSError* error) {
		callbackAndReturnIfError(error, completion, @[ [NSNull null], [RCTSpotify objFromError:error] ]);
		
		NSMutableDictionary* body = query.mutableCopy;
		
		//change "type" field from array to comma separated string if necessary
		id typeArg = body[@"type"];
		if(typeArg==nil)
		{
			completion(@[ [NSNull null], NIL_OPTION_ERROR_OBJ(@"query", @"type") ]);
			return;
		}
		else if([typeArg isKindOfClass:[NSArray class]])
		{
			body[@"type"] = [typeArg componentsJoinedByString:@","];
		}
		
		NSURLRequest* request = [SPTRequest createRequestForURL:SPOTIFY_API_URL(@"search")
												withAccessToken:_auth.session.accessToken
													 httpMethod:@"GET"
														 values:body
												valueBodyIsJSON:NO sendDataAsQueryString:YES error:&error];
		callbackAndReturnIfError(error, completion, @[ [NSNull null], [RCTSpotify objFromError:error] ]);
		
		[self doRequest:request completion:^(id resultObj, NSError* error){
			completion(@[ [RCTSpotify reactSafeArg:resultObj], [RCTSpotify objFromError:error] ]);
		}];
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
	if(_startResponse != nil)
	{
		//do login callback
		void(^response)(BOOL,NSError*) = _startResponse;
		_startResponse = nil;
		response(YES, nil);
	}
	
	//do log back in callbacks
	NSArray<void(^)(BOOL, NSError*)>* logBackInResponses = _logBackInResponses;
	[_logBackInResponses removeAllObjects];
	for(void(^response)(BOOL,NSError*) in logBackInResponses)
	{
		response(YES, nil);
	}
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveError:(NSError*)error
{
	if(error.code==SPErrorGeneralLoginError || error.code==SPErrorLoginBadCredentials)
	{
		if(_startResponse != nil)
		{
			//do login callback
			void(^response)(BOOL,NSError*) = _startResponse;
			_startResponse = nil;
			response(NO, error);
		}
		
		//do log back in callbacks
		NSArray<void(^)(BOOL, NSError*)>* logBackInResponses = _logBackInResponses;
		[_logBackInResponses removeAllObjects];
		for(void(^response)(BOOL,NSError*) in logBackInResponses)
		{
			response(YES, nil);
		}
	}
}

-(void)audioStreamingDidLogout:(SPTAudioStreamingController*)audioStreaming
{
	NSError* error = [RCTSpotify errorWithCode:RCTSpotifyErrorCodeNotLoggedIn description:@"Spotify was logged out"];
	
	if(_startResponse != nil)
	{
		//do login callback
		void(^response)(BOOL,NSError*) = _startResponse;
		_startResponse = nil;
		response(NO, error);
	}
	
	//do log back in callbacks
	NSArray<void(^)(BOOL, NSError*)>* logBackInResponses = _logBackInResponses;
	[_logBackInResponses removeAllObjects];
	for(void(^response)(BOOL,NSError*) in logBackInResponses)
	{
		response(YES, error);
	}
}

@end

