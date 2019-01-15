
#import "RNSpotify.h"
#import <AVFoundation/AVFoundation.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>
#import "RNSpotifyAuthController.h"
#import "RNSpotifyProgressView.h"
#import "RNSpotifyConvert.h"
#import "RNSpotifyCompletion.h"
#import "HelperMacros.h"

#define SPOTIFY_API_BASE_URL @"https://api.spotify.com/"
#define SPOTIFY_API_URL(endpoint) [NSURL URLWithString:NSString_concat(SPOTIFY_API_BASE_URL, endpoint)]

@interface RNSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate> {
	BOOL _initialized;
	BOOL _loggedIn;
	BOOL _loggingIn;
	BOOL _loggingInPlayer;
	BOOL _loggingOutPlayer;
	
	SPTAuth* _auth;
	NSTimer* _authRenewalTimer;
	SPTAudioStreamingController* _player;
	
	NSDictionary* _options;
	NSNumber* _cacheSize;
	
	NSMutableArray<RNSpotifyCompletion*>* _loginPlayerResponses;
	NSMutableArray<RNSpotifyCompletion*>* _logoutPlayerResponses;
	
	BOOL _renewingSession;
	NSMutableArray<RNSpotifyCompletion*>* _renewCallbacks;
	
	NSString* _audioSessionCategory;
}
+(NSMutableDictionary*)mutableDictFromDict:(NSDictionary*)dict;
-(BOOL)hasPlayerScope;

-(void)logBackInIfNeeded:(RNSpotifyCompletion<NSNumber*>*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse;
-(void)initializePlayerIfNeeded:(RNSpotifyCompletion*)completion;
-(void)loginPlayer:(RNSpotifyCompletion*)completion;
-(void)logoutPlayer:(RNSpotifyCompletion*)completion;
-(void)startAuthRenewalTimer;
-(NSTimeInterval)tokenRefreshEarliness;
-(void)scheduleAuthRenewalTimer;
-(void)authRenewalTimerDidFire;
-(void)stopAuthRenewalTimer;
-(BOOL)clearSession;
-(void)prepareForPlayer:(RNSpotifyCompletion*)completion;
-(void)prepareForRequest:(RNSpotifyCompletion*)completion;
-(void)doAPIRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params jsonBody:(BOOL)jsonBody completion:(RNSpotifyCompletion*)completion;
@end

@implementation RNSpotify

@synthesize bridge = _bridge;

-(id)init {
	if(self = [super init]) {
		_initialized = NO;
		_loggedIn = NO;
		_loggingIn = NO;
		_loggingInPlayer = NO;
		_loggingOutPlayer = NO;
		
		_auth = nil;
		_authRenewalTimer = nil;
		_player = nil;
		
		_options = nil;
		_cacheSize = nil;
		
		_loginPlayerResponses = [NSMutableArray array];
		_logoutPlayerResponses = [NSMutableArray array];
		
		_renewingSession = NO;
		_renewCallbacks = [NSMutableArray array];
		
		_audioSessionCategory = nil;
	}
	return self;
}

-(void)invalidate {
	[self stopAuthRenewalTimer];
	_player.delegate = nil;
	_player.playbackDelegate = nil;
	if(_player.initialized) {
		[_player logout];
		NSError* error = nil;
		[_player stopWithError:&error];
		if(error != nil) {
			printErrLog(@"error stopping player: %@", error);
		}
	}
}

+(BOOL)requiresMainQueueSetup {
	return NO;
}

RCT_EXPORT_METHOD(__registerAsJSEventEmitter:(int)moduleId) {
	[RNEventEmitter registerEventEmitterModule:self withID:moduleId bridge:_bridge];
}

-(void)sendEvent:(NSString*)event args:(NSArray*)args {
	[RNEventEmitter emitEvent:event withParams:args module:self bridge:_bridge];
}

+(id)reactSafeArg:(id)arg {
	if(arg==nil) {
		return [NSNull null];
	}
	return arg;
}

+(NSMutableDictionary*)mutableDictFromDict:(NSDictionary*)dict {
	if(dict==nil) {
		return [NSMutableDictionary dictionary];
	}
	return dict.mutableCopy;
}

-(BOOL)hasPlayerScope {
	if(_options==nil) {
		return NO;
	}
	id scopes = _options[@"scopes"];
	if(scopes==nil || ![scopes isKindOfClass:[NSArray class]]) {
		return NO;
	}
	return [scopes containsObject:@"streaming"];
}

-(void)activateAudioSession {
	AVAudioSession* audioSession = [AVAudioSession sharedInstance];
	NSError* error = nil;
	if(![_audioSessionCategory isEqualToString:audioSession.category]) {
		[audioSession setCategory:_audioSessionCategory error:&error];
		if(error != nil) {
			printErrLog(@"Error setting spotify audio session category: %@", error);
		}
	}
	error = nil;
	[audioSession setActive:YES error:&error];
	if(error != nil) {
		printErrLog(@"Error setting spotify audio session active: %@", error);
	}
}

-(void)deactivateAudioSession {
	AVAudioSession* audioSession = [AVAudioSession sharedInstance];
	NSError* error = nil;
	[audioSession setActive:NO error:&error];
	if(error != nil) {
		printErrLog(@"Error setting spotify audio session inactive: %@", error);
	}
}



#pragma mark - React Native functions

RCT_EXPORT_MODULE()

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(test) {
	printOutLog(@"ayy lmao");
	return [NSNull null];
}

RCT_EXPORT_METHOD(initialize:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	if(_initialized) {
		[RNSpotifyErrorCode.AlreadyInitialized reject:reject];
		return;
	}
	
	printOutLog(@"initializing Spotify");
	
	// ensure options is not null or missing fields
	if(options == nil) {
		[[RNSpotifyError nullParameterErrorForName:@"options"] reject:reject];
		return;
	}
	else if(options[@"clientID"] == nil) {
		[[RNSpotifyError missingOptionErrorForName:@"clientID"] reject:reject];
		return;
	}
	
	// load default options
	_options = options;
	_auth = [SPTAuth defaultInstance];
	_player = [SPTAudioStreamingController sharedInstance];
	_cacheSize = @(1024 * 1024 * 64);
	
	// load auth options
	_auth.clientID = options[@"clientID"];
	_auth.redirectURL = [NSURL URLWithString:options[@"redirectURL"]];
	_auth.sessionUserDefaultsKey = options[@"sessionUserDefaultsKey"];
	_auth.requestedScopes = options[@"scopes"];
	_auth.tokenSwapURL = [NSURL URLWithString:options[@"tokenSwapURL"]];
	_auth.tokenRefreshURL = [NSURL URLWithString:options[@"tokenRefreshURL"]];
	NSNumber* cacheSize = options[@"cacheSize"];
	if(cacheSize!=nil) {
		_cacheSize = cacheSize;
	}
	
	// load iOS-specific options
	NSDictionary* iosOptions = options[@"ios"];
	if(iosOptions == nil) {
		iosOptions = @{};
	}
	_audioSessionCategory = iosOptions[@"audioSessionCategory"];
	if(_audioSessionCategory == nil) {
		_audioSessionCategory = AVAudioSessionCategoryPlayback;
	}
	
	// done initializing
	_initialized = YES;
	
	// call callback
	BOOL authLoggedIn = _auth.session != nil;
	if(authLoggedIn) {
		_loggedIn = true;
	}
	resolve(@(_loggedIn));
	if(_loggedIn) {
		[self sendEvent:@"login" args:@[]];
	}
	
	[self logBackInIfNeeded:[RNSpotifyCompletion<NSNumber*> onComplete:^(NSNumber* loggedIn, RNSpotifyError* error) {
		if(loggedIn != nil && loggedIn.boolValue && [[self isLoggedIn] boolValue]) {
			[self startAuthRenewalTimer];
		}
	}] waitForDefinitiveResponse:YES];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isInitialized) {
	if(_auth==nil) {
		return @NO;
	}
	return [NSNumber numberWithBool:_initialized];
}

RCT_EXPORT_METHOD(isInitializedAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([self isInitialized]);
}



#pragma mark - React Native functions - Session Handling

-(void)logBackInIfNeeded:(RNSpotifyCompletion<NSNumber*>*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse {
	// ensure auth is actually logged in
	if(_auth.session == nil) {
		[completion resolve:@NO];
		return;
	}
	// attempt to renew auth session
	[self renewSessionIfNeeded:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		// session renewal failed (we should log out)
		if([[self isLoggedIn] boolValue]) {
			// session renewal returned a failure, but we're still logged in
			// log out player
			[self logoutPlayer:[RNSpotifyCompletion onComplete:^(id result, RNSpotifyError* error) {
				// clear session
				BOOL wasLoggedIn = [[self isLoggedIn] boolValue];
				if(wasLoggedIn) {
					[self clearSession];
				}
				// call completion
				[completion resolve:@NO];
				// send logout event
				if(wasLoggedIn) {
					[self sendEvent:@"logout" args:@[]];
				}
			}]];
		}
		else {
			// auth wasn't logged in during the renewal failure, so just fail
			if(waitForDefinitiveResponse) {
				[completion resolve:@NO];
			}
			else {
				[completion reject:error];
			}
		}
	} onResolve:^(id unused) {
		// success
		[completion resolve:@YES];
	}] waitForDefinitiveResponse:waitForDefinitiveResponse];
}

-(void)renewSessionIfNeeded:(RNSpotifyCompletion<NSNumber*>*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse {
	if(_auth.session == nil || _auth.session.isValid) {
		// not logged in or session does not need renewal
		[completion resolve:@NO];
	}
	else if(_auth.session.encryptedRefreshToken == nil) {
		// no refresh token to renew session with, so the session has expired
		[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.SessionExpired]];
	}
	else {
		[self renewSession:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
			[completion reject:error];
		} onResolve:^(id result) {
			[completion resolve:result];
		}] waitForDefinitiveResponse:waitForDefinitiveResponse];
	}
}

-(void)renewSPTAuthSession:(RNSpotifyCompletion<NSNumber*>*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse {
	dispatch_async(dispatch_get_main_queue(), ^{
		if(_auth.session == nil) {
			[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotLoggedIn]];
			return;
		}
		else if(!_auth.hasTokenRefreshService) {
			[completion resolve:@NO];
			return;
		}
		else if(_auth.session.encryptedRefreshToken == nil) {
			printOutLog(@"No refresh token available. Not renewing session");
			[completion resolve:@NO];
			return;
		}
		
		// add completion to be called when the renewal finishes
		if(completion != nil) {
			[_renewCallbacks addObject:completion];
		}
		
		// if we're already in the process of renewing the session, don't continue
		if(_renewingSession) {
			return;
		}
		_renewingSession = YES;
		
		// renew session
		if(_auth.session.accessToken == nil || _auth.session.accessToken.length == 0) {
			printOutLog(@"we currently have no auth token");
		}
		printOutLog(@"calling SPTAuth.renewSession");
		[_auth renewSession:_auth.session callback:^(NSError* error, SPTSession* session){
			dispatch_async(dispatch_get_main_queue(), ^{
				if(error != nil) {
					printErrLog(@"failed to renew Spotify session: %@", error);
				}
				_renewingSession = NO;
				
				id renewed = @NO;
				if(session != nil) {
					_auth.session = session;
					renewed = @YES;
				}
				
				//TODO figure out what SPTAuth.renewSession does if the internet is not connected (probably throws an error)
				
				NSArray<RNSpotifyCompletion*>* renewCallbacks = [NSArray arrayWithArray:_renewCallbacks];
				[_renewCallbacks removeAllObjects];
				for(RNSpotifyCompletion* completion in renewCallbacks) {
					if(error != nil) {
						[completion reject:[RNSpotifyError errorWithNSError:error]];
					}
					else {
						[completion resolve:renewed];
					}
				}
			});
		}];
	});
}

-(void)renewSession:(RNSpotifyCompletion<NSNumber*>*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse {
	printOutLog(@"renewing Spotify session");
	[self renewSPTAuthSession:[RNSpotifyCompletion onResolve:^(NSNumber* renewed) {
		printOutLog(@"session renewed?: %@", renewed);
		if(renewed.boolValue) {
			if(_player == nil || !_player.loggedIn) {
				[self initializePlayerIfNeeded:[RNSpotifyCompletion onResolve:^(id unused) {
					[completion resolve:renewed];
				} onReject:^(RNSpotifyError* error) {
					[completion reject:error];
				}]];
				return;
			}
			else {
				NSString* accessToken = nil;
				if(_auth.session != nil) {
					accessToken = _auth.session.accessToken;
				}
				[_player loginWithAccessToken:accessToken];
			}
		}
		[completion resolve:renewed];
	} onReject:^(RNSpotifyError* error) {
		printErrLog(@"Couldn't renew session: %@", error);
		[completion reject:error];
	}] waitForDefinitiveResponse:waitForDefinitiveResponse];
}

RCT_EXPORT_METHOD(renewSession:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self renewSession:[RNSpotifyCompletion onResolve:^(NSNumber* renewed) {
		// ensure we're logged in
		if(_loggedIn && renewed.boolValue) {
			// reschedule the timer
			[self scheduleAuthRenewalTimer];
		}
		resolve(renewed);
	} onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	}] waitForDefinitiveResponse:NO];
}



-(void)initializePlayerIfNeeded:(RNSpotifyCompletion*)completion {
	if(![self hasPlayerScope]) {
		[completion resolve:nil];
		return;
	}
	
	// ensure only one thread is invoking the initialization at a time
	BOOL initializedPlayer = NO;
	NSError* error = nil;
	BOOL allowCaching = (_cacheSize.unsignedIntegerValue > 0);
	@synchronized(_player) {
		// check if player is already initialized
		if(_player.initialized) {
			initializedPlayer = YES;
		}
		else {
			initializedPlayer = [_player startWithClientId:_auth.clientID audioController:nil allowCaching:allowCaching error:&error];
		}
	}
	
	// handle initialization failure
	if(!initializedPlayer) {
		[completion reject:[RNSpotifyError errorWithNSError:error]];
		return;
	}
	
	// setup player
	_player.delegate = self;
	_player.playbackDelegate = self;
	if(allowCaching) {
		_player.diskCache = [[SPTDiskCache alloc] initWithCapacity:_cacheSize.unsignedIntegerValue];
	}
	
	// attempt to log in the player
	[self loginPlayer:completion];
}

-(void)loginPlayer:(RNSpotifyCompletion*)completion {
	BOOL playerLoggedIn = NO;
	
	// add completion to a list to be called when the login succeeds or fails
	@synchronized(_loginPlayerResponses) {
		// ensure we're not already logged in
		if(_player.loggedIn) {
			playerLoggedIn = true;
		}
		else {
			//wait for audioStreamingDidLogin:
			// or audioStreaming:didReceiveError:
			// or audioStreamingDidLogout:
			[_loginPlayerResponses addObject:completion];
		}
	}
	
	if(playerLoggedIn) {
		// we're already logged in, so finish
		[completion resolve:nil];
	}
	else if(!_loggingInPlayer) {
		// only the first thread to call loginPlayer should actually attempt to log the player in
		_loggingInPlayer = YES;
		[_player loginWithAccessToken:_auth.session.accessToken];
	}
}

-(void)logoutPlayer:(RNSpotifyCompletion*)completion {
	BOOL loggedOut = NO;
	
	@synchronized(_logoutPlayerResponses) {
		if(!_player.loggedIn) {
			loggedOut = YES;
		}
		else {
			// wait for RNSpotifyModule.onLoggedOut
			[_logoutPlayerResponses addObject:completion];
		}
	}
	
	if(loggedOut) {
		[completion resolve:nil];
	}
	else if(!_loggingOutPlayer) {
		_loggingOutPlayer = true;
		[_player logout];
	}
}

RCT_EXPORT_METHOD(login:(NSDictionary*)options resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	// ensure we're not already logging in
	if(_loggingIn) {
		[[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.ConflictingCallbacks message:@"Cannot call login multiple times before completing"] reject:reject];
		return;
	}
	else if([[self isLoggedIn] boolValue]) {
		resolve(@YES);
		return;
	}
	NSMutableDictionary* loginParams = [NSMutableDictionary dictionary];
	NSNumber* showDialog = options[@"showDialog"];
	if(showDialog != nil) {
		loginParams[@"show_dialog"] = showDialog.boolValue ? @"true" : @"false";
	}
	NSArray* scopes = options[@"scopes"];
	if(scopes != nil) {
		loginParams[@"scope"] = [scopes componentsJoinedByString:@" "];
	}
	_loggingIn = YES;
	
	// do UI logic on main thread
	dispatch_async(dispatch_get_main_queue(), ^{
		RNSpotifyAuthController* authController = [[RNSpotifyAuthController alloc] initWithAuth:_auth params:loginParams];
		
		__weak RNSpotifyAuthController* weakAuthController = authController;
		authController.completion = [RNSpotifyCompletion<NSNumber*> onReject:^(RNSpotifyError* error) {
			// login failed
			RNSpotifyAuthController* authController = weakAuthController;
			[authController.presentingViewController dismissViewControllerAnimated:YES completion:^{
				_loggingIn = NO;
				[error reject:reject];
			}];
		} onResolve:^(NSNumber* authenticated) {
			RNSpotifyAuthController* authController = weakAuthController;
			if(!authenticated.boolValue) {
				// login cancelled
				[authController.presentingViewController dismissViewControllerAnimated:YES completion:^{
					_loggingIn = NO;
					resolve(@NO);
				}];
			}
			else {
				// login successful
				[self initializePlayerIfNeeded:[RNSpotifyCompletion onComplete:^(id unused, RNSpotifyError* error) {
					// do UI logic on main thread
					dispatch_async(dispatch_get_main_queue(), ^{
						[authController.presentingViewController dismissViewControllerAnimated:YES completion:^{
							if (error != nil) {
								_auth.session = nil;
								[error reject:reject];
							}
							else {
								BOOL authLoggedIn = _auth.session != nil;
								if(authLoggedIn) {
									_loggedIn = YES;
								}
								_loggingIn = NO;
								resolve(@(_loggedIn));
								if(_loggedIn) {
									[self sendEvent:@"login" args:@[]];
								}
								[self startAuthRenewalTimer];
							}
						}];
					});
				}]];
			}
		}];
		
		// present auth view controller
		UIViewController* topViewController = [RNSpotifyAuthController topViewController];
		[topViewController presentViewController:authController animated:YES completion:nil];
	});
}

RCT_EXPORT_METHOD(logout:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	if(![[self isLoggedIn] boolValue]) {
		resolve(nil);
		return;
	}
	[self logoutPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		BOOL wasLoggedIn = [self clearSession];
		resolve(nil);
		if(wasLoggedIn) {
			[self sendEvent:@"logout" args:@[]];
		}
	}]];
}



-(void)startAuthRenewalTimer {
	if(_authRenewalTimer != nil) {
		// auth renewal timer has already been started, don't bother starting again
	}
	[self scheduleAuthRenewalTimer];
}

-(NSTimeInterval)tokenRefreshEarliness {
	id tokenRefreshEarliness = _options[@"tokenRefreshEarliness"];
	if(tokenRefreshEarliness == nil || ![tokenRefreshEarliness isKindOfClass:[NSNumber class]]) {
		return 300.0;
	}
	return (NSTimeInterval)[tokenRefreshEarliness doubleValue];
}

-(void)scheduleAuthRenewalTimer {
	if(_auth.tokenRefreshURL == nil || _auth.session == nil || _auth.session.encryptedRefreshToken == nil) {
		// we can't perform token refresh, so don't bother scheduling the timer
		return;
	}
	NSTimeInterval now = [NSDate date].timeIntervalSince1970;
	NSTimeInterval expirationTime = _auth.session.expirationDate.timeIntervalSince1970;
	NSTimeInterval timeDiff = expirationTime - now;
	NSTimeInterval tokenRefreshEarliness = [self tokenRefreshEarliness];
	NSTimeInterval renewalTimeDiff = (expirationTime - tokenRefreshEarliness) - now;
	if(timeDiff <= 30.0 || timeDiff <= (tokenRefreshEarliness + 30.0) || renewalTimeDiff <= 0.0) {
		[self authRenewalTimerDidFire];
	}
	else {
		if(_authRenewalTimer != nil) {
			[_authRenewalTimer invalidate];
		}
		NSTimer* timer = [NSTimer timerWithTimeInterval:renewalTimeDiff target:self selector:@selector(authRenewalTimerDidFire) userInfo:nil repeats:NO];
		[[NSRunLoop mainRunLoop] addTimer:timer forMode:NSRunLoopCommonModes];
		_authRenewalTimer = timer;
	}
}

-(void)authRenewalTimerDidFire {
	[self renewSession:[RNSpotifyCompletion onComplete:^(id result, RNSpotifyError* error) {
		// ensure we're logged in
		if(_loggedIn) {
			// reschedule the timer
			[self scheduleAuthRenewalTimer];
		}
	}] waitForDefinitiveResponse:YES];
}

-(void)stopAuthRenewalTimer {
	if(_authRenewalTimer != nil) {
		[_authRenewalTimer invalidate];
		_authRenewalTimer = nil;
	}
}

-(BOOL)clearSession {
	BOOL wasLoggedIn = [[self isLoggedIn] boolValue];
	[self stopAuthRenewalTimer];
	_auth.session = nil;
	_loggedIn = NO;
	return wasLoggedIn;
}



RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isLoggedIn) {
	if(_initialized && _loggedIn && _auth.session != nil) {
		return @YES;
	}
	return @NO;
}

RCT_EXPORT_METHOD(isLoggedInAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([self isLoggedIn]);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getAuth) {
	return [RNSpotifyConvert SPTAuth:_auth];
}

RCT_EXPORT_METHOD(getAuthAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([RNSpotifyConvert ID:[self getAuth]]);
}





#pragma mark - React Native functions - Playback

-(void)prepareForPlayer:(RNSpotifyCompletion*)completion {
	if(!_initialized) {
		[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotInitialized]];
		return;
	}
	[self logBackInIfNeeded:[RNSpotifyCompletion<NSNumber*> onReject:^(RNSpotifyError* error) {
		if(!_player.loggedIn && [self hasPlayerScope]) {
			[completion reject:error];
		}
		else {
			[completion resolve:nil];
		}
	} onResolve:^(NSNumber* unused) {
		if([[self isLoggedIn] boolValue]) {
			[self initializePlayerIfNeeded:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
				if(!_player.loggedIn && [self hasPlayerScope]) {
					[completion reject:error];
				}
				else {
					[completion resolve:nil];
				}
			} onResolve:^(id result) {
				if(!_player.loggedIn && [self hasPlayerScope]) {
					[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.PlayerNotReady]];
				}
				else {
					[completion resolve:nil];
				}
			}]];
		}
		else {
			[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotLoggedIn]];
		}
	}] waitForDefinitiveResponse:NO];
}

RCT_EXPORT_METHOD(playURI:(NSString*)uri startIndex:(NSUInteger)startIndex startPosition:(NSTimeInterval)startPosition resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		[_player playSpotifyURI:uri startingWithIndex:startIndex startingWithPosition:(startPosition*1000) callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(queueURI:(NSString*)uri resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id result) {
		[_player queueSpotifyURI:uri callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(setVolume:(double)volume resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	if(!_initialized) {
		[[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotInitialized] reject:reject];
		return;
	}
	[_player setVolume:(SPTVolume)volume callback:^(NSError* error){
		if(error != nil) {
			[[RNSpotifyError errorWithNSError:error] reject:reject];
		}
		else {
			resolve(nil);
		}
	}];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getVolume) {
	if(_player==nil) {
		return nil;
	}
	return @(_player.volume);
}

RCT_EXPORT_METHOD(getVolumeAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([RNSpotifyConvert ID:[self getVolume]]);
}

RCT_EXPORT_METHOD(setPlaying:(BOOL)playing resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		[_player setIsPlaying:playing callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(setShuffling:(BOOL)shuffling resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		[_player setShuffle:shuffling callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(setRepeating:(BOOL)repeating resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		SPTRepeatMode repeatMode = SPTRepeatOff;
		if(repeating) {
			repeatMode = SPTRepeatContext;
		}
		[_player setRepeat:repeatMode callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPlaybackState) {
	return [RNSpotifyConvert SPTPlaybackState:_player.playbackState];
}

RCT_EXPORT_METHOD(getPlaybackStateAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([RNSpotifyConvert ID:[self getPlaybackState]]);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPlaybackMetadata) {
	return [RNSpotifyConvert SPTPlaybackMetadata:_player.metadata];
}

RCT_EXPORT_METHOD(getPlaybackMetadataAsync:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	resolve([RNSpotifyConvert ID:[self getPlaybackMetadata]]);
}

RCT_EXPORT_METHOD(skipToNext:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		[_player skipNext:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(skipToPrevious:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id unused) {
		[_player skipPrevious:^(NSError *error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}

RCT_EXPORT_METHOD(seek:(double)position resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self prepareForPlayer:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id result) {
		[_player seekTo:(NSTimeInterval)position callback:^(NSError* error) {
			if(error != nil) {
				[[RNSpotifyError errorWithNSError:error] reject:reject];
			}
			else {
				resolve(nil);
			}
		}];
	}]];
}



#pragma mark - React Native functions - Request Sending

-(void)prepareForRequest:(RNSpotifyCompletion*)completion {
	if(!_initialized) {
		[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotInitialized]];
		return;
	}
	[self logBackInIfNeeded:[RNSpotifyCompletion onComplete:^(id unused, RNSpotifyError* unusedError) {
		[completion resolve:nil];
	}] waitForDefinitiveResponse:NO];
}

-(void)doAPIRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params jsonBody:(BOOL)jsonBody completion:(RNSpotifyCompletion*)completion {
	[self prepareForRequest:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[completion reject:error];
	} onResolve:^(id unused) {
		// build request
		NSError* error = nil;
		NSURLRequest* request = [SPTRequest createRequestForURL:SPOTIFY_API_URL(endpoint)
												withAccessToken:_auth.session.accessToken
													 httpMethod:method
														 values:params
												valueBodyIsJSON:jsonBody
										  sendDataAsQueryString:!jsonBody
														  error:&error];
		// handle request params error
		if(error != nil) {
			[completion reject:[RNSpotifyError errorWithNSError:error]];
			return;
		}
		
		// send request
		[[SPTRequest sharedHandler] performRequest:request callback:^(NSError* error, NSURLResponse* response, NSData* data) {
			if(error != nil) {
				[completion reject:[RNSpotifyError errorWithNSError:error]];
				return;
			}
			
			// check if content is json
			BOOL isJSON = NO;
			if([response isKindOfClass:[NSHTTPURLResponse class]]) {
				NSHTTPURLResponse* httpResponse = (NSHTTPURLResponse*)response;
				NSString* contentType = httpResponse.allHeaderFields[@"Content-Type"];
				if(contentType!=nil) {
					contentType = [contentType componentsSeparatedByString:@";"][0];
				}
				if([contentType caseInsensitiveCompare:@"application/json"] == NSOrderedSame) {
					isJSON = YES;
				}
			}
			
			id result = nil;
			if(isJSON) {
				result = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
				if(error != nil) {
					[completion reject:[RNSpotifyError errorWithNSError:error]];
					return;
				}
				
				id errorObj = result[@"error"];
				if(errorObj != nil) {
					id errorDescription = result[@"error_description"];
					if(errorDescription != nil) {
						if(![errorObj isKindOfClass:[NSString class]] || ![errorDescription isKindOfClass:[NSString class]]) {
							[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadResponse]];
							return;
						}
						[completion reject:[RNSpotifyError errorWithCode:errorObj message:errorDescription]];
					}
					else {
						if(![errorObj isKindOfClass:[NSDictionary class]]) {
							[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadResponse]];
							return;
						}
						id statusCode = errorObj[@"status"];
						id message = errorObj[@"message"];
						if(statusCode == nil || message == nil || ![statusCode isKindOfClass:[NSNumber class]] || ![message isKindOfClass:[NSString class]]) {
							[completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadResponse]];
							return;
						}
						[completion reject:[RNSpotifyError httpErrorForStatusCode:[statusCode integerValue] message:message]];
					}
					return;
				}
			}
			else {
				if(data.length > 0) {
					NSStringEncoding encoding = NSUTF8StringEncoding;
					if(response.textEncodingName != nil) {
						encoding = CFStringConvertEncodingToNSStringEncoding(CFStringConvertIANACharSetNameToEncoding((__bridge CFStringRef)response.textEncodingName));
					}
					result = [[NSString alloc] initWithData:data encoding:encoding];
				}
			}
			[completion resolve:result];
		}];
	}]];
}

RCT_EXPORT_METHOD(sendRequest:(NSString*)endpoint method:(NSString*)method params:(NSDictionary*)params isJSONBody:(BOOL)jsonBody resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
	[self doAPIRequest:endpoint method:method params:params jsonBody:jsonBody completion:[RNSpotifyCompletion onReject:^(RNSpotifyError* error) {
		[error reject:reject];
	} onResolve:^(id result) {
		resolve(result);
	}]];
}



#pragma mark - SPTAudioStreamingDelegate

-(void)audioStreamingDidLogin:(SPTAudioStreamingController*)audioStreaming {
	_loggingInPlayer = NO;
	
	// handle loginPlayer callbacks
	NSArray<RNSpotifyCompletion*>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
	[_loginPlayerResponses removeAllObjects];
	for(RNSpotifyCompletion* response in loginPlayerResponses) {
		[response resolve:nil];
	}
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveError:(NSError*)error {
	if(_loggingInPlayer) {
		_loggingInPlayer = NO;
		// if the error is one that requires logging out, log out
		BOOL sendLogoutEvent = NO;
		if([[self isLoggedIn] boolValue]) {
			// clear session and stop player
			[self clearSession];
			[_player stopWithError:nil];
			sendLogoutEvent = YES;
		}
		
		// handle loginPlayer callbacks
		NSArray<RNSpotifyCompletion*>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
		[_loginPlayerResponses removeAllObjects];
		for(RNSpotifyCompletion* response in loginPlayerResponses) {
			[response reject:[RNSpotifyError errorWithNSError:error]];
		}
		
		if(sendLogoutEvent) {
			[self sendEvent:@"logout" args:@[]];
		}
	}
}

-(void)audioStreamingDidLogout:(SPTAudioStreamingController*)audioStreaming {
	_loggingInPlayer = NO;
	
	BOOL wasLoggingOutPlayer = _loggingOutPlayer;
	_loggingOutPlayer = NO;
	
	// handle loginPlayer callbacks
	NSArray<RNSpotifyCompletion*>* loginPlayerResponses = [NSArray arrayWithArray:_loginPlayerResponses];
	[_loginPlayerResponses removeAllObjects];
	for(RNSpotifyCompletion* response in loginPlayerResponses) {
		[response reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NotLoggedIn message:@"You have been logged out"]];
	}
	
	// if we didn't explicitly log out, try to renew the session
	if(!wasLoggingOutPlayer && _auth.hasTokenRefreshService && _auth.session != nil && _auth.session.encryptedRefreshToken != nil) {
		printOutLog(@"player logged out, so session needs renewal");
		[self renewSession:[RNSpotifyCompletion onComplete:^(NSNumber* renewed, RNSpotifyError* error) {
			if(error != nil || !renewed.boolValue) {
				if([[self isLoggedIn] boolValue]) {
					// clear session and stop player
					[self clearSession];
					[_player stopWithError:nil];
					[self sendEvent:@"logout" args:@[]];
				}
			}
			else {
				// we renewed the auth token, so we're good here
			}
		}] waitForDefinitiveResponse:YES];
	}
	else {
		// clear session and stop player
		[self clearSession];
		[_player stopWithError:nil];
		
		// handle logoutPlayer callbacks
		NSArray<RNSpotifyCompletion*>* logoutResponses = [NSArray arrayWithArray:_logoutPlayerResponses];
		[_logoutPlayerResponses removeAllObjects];
		for(RNSpotifyCompletion* response in logoutResponses) {
			[response resolve:nil];
		}
		
		// send logout event
		[self sendEvent:@"logout" args:@[]];
	}
}

-(void)audioStreamingDidDisconnect:(SPTAudioStreamingController*)audioStreaming {
	[self sendEvent:@"disconnect" args:@[]];
}

-(void)audioStreamingDidReconnect:(SPTAudioStreamingController*)audioStreaming {
	[self sendEvent:@"reconnect" args:@[]];
}

-(void)audioStreamingDidEncounterTemporaryConnectionError:(SPTAudioStreamingController*)audioStreaming {
	[self sendEvent:@"temporaryPlayerError" args:@[]];
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveMessage:(NSString*)message {
	[self sendEvent:@"playerMessage" args:@[message]];
}



#pragma mark - SPTAudioStreamingPlaybackDelegate

-(NSMutableDictionary*)createPlaybackEvent {
	NSMutableDictionary* event = [NSMutableDictionary dictionary];
	event[@"state"] = [self getPlaybackState];
	event[@"metadata"] = [self getPlaybackMetadata];
	event[@"error"] = [NSNull null];
	return event;
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceivePlaybackEvent:(SpPlaybackEvent)event {
	switch(event) {
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

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didChangePlaybackStatus:(BOOL)isPlaying {
	if(isPlaying) {
		[self activateAudioSession];
	}
	else {
		[self deactivateAudioSession];
	}
}

-(void)audioStreaming:(SPTAudioStreamingController *)audioStreaming didChangeShuffleStatus:(BOOL)enabled {
	[self sendEvent:@"shuffleStatusChange" args:@[[self createPlaybackEvent]]];
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didChangeRepeatStatus:(SPTRepeatMode)repeateMode {
	[self sendEvent:@"repeatStatusChange" args:@[[self createPlaybackEvent]]];
}

@end

