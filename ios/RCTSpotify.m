
#import "RCTSpotify.h"
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import <SpotifyMetadata/SpotifyMetadata.h>
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotify() <SPTAudioStreamingDelegate, SPTAudioStreamingPlaybackDelegate, SPTAuthViewDelegate>
{
	SPTAuth* _auth;
	SPTAudioStreamingController* _player;
	
	NSNumber* _cacheSize;
	
	void(^_authControllerResponse)(BOOL loggedIn, NSError* error);
	void(^_logBackInResponse)(BOOL loggedIn, NSError* error);
}
+(id)dictFromError:(NSError*)error;

-(void)logBackInIfNeeded:(void(^)(BOOL loggedIn, NSError* error))completion;
-(void)start:(void(^)(NSError*))completion;
@end

@implementation RCTSpotify

+(id)dictFromError:(NSError*)error
{
	if(error==nil)
	{
		return [NSNull null];
	}
	return @{
		@"domain":error.domain,
		@"code":@(error.code),
		@"description":error.localizedDescription
	};
}

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
			completion(@[ [NSNull null] ]);
		}
		else
		{
			completion(@[ [RCTSpotify dictFromError:error] ]);
		}
	}];
}

-(void)logBackInIfNeeded:(void(^)(BOOL, NSError*))completion
{
	if(_auth.session == nil)
	{
		completion(NO, nil);
	}
	else if([_auth.session isValid])
	{
		completion(YES, nil);
	}
	else if(_auth.hasTokenRefreshService)
	{
		[_auth renewSession:_auth.session callback:^(NSError* error, SPTSession* session){
			if(error!=nil)
			{
				completion(NO, error);
			}
			else
			{
				_auth.session = session;
				//[_player loginWithAccessToken:_auth.session.accessToken];
				completion(YES, nil);
			}
		}];
	}
}

RCT_EXPORT_METHOD(login:(RCTResponseSenderBlock)completion)
{
	//do UI logic on main thread
	dispatch_async(dispatch_get_main_queue(), ^{
		SPTAuthViewController* authController = [SPTAuthViewController authenticationViewControllerWithAuth:_auth];
		authController.delegate = self;
		UIViewController* rootController = [UIApplication sharedApplication].keyWindow.rootViewController;
		if(rootController == nil)
		{
			//no root view controller to present on
			completion(@[ @NO, @{@"description":@"can't login when not in foreground"} ]);
		}
		else
		{
			if(_authControllerResponse != nil)
			{
				completion(@[ @NO, @{@"description":@"Cannot call login while login is already being called"} ]);
				return;
			}
			
			//wait for authenticationViewController:didFailToLogin:
			// or authenticationViewController:didLoginWithSession:
			// or authenticationViewControllerDidCancelLogin
			_authControllerResponse = ^(BOOL loggedIn, NSError* error){
				completion(@[ [NSNumber numberWithBool:loggedIn], [RCTSpotify dictFromError:error] ]);
			};
			[rootController presentViewController:authController animated:YES completion:nil];
		}
	});
}

-(void)start:(void(^)(NSError*))completion
{
	BOOL allowCaching = (_cacheSize.unsignedIntegerValue > 0);
	NSError* error = nil;
	if([_player startWithClientId:_auth.clientID audioController:nil allowCaching:allowCaching error:&error])
	{
		_player.delegate = self;
		_player.playbackDelegate = self;
		if(allowCaching)
		{
			_player.diskCache = [[SPTDiskCache alloc] initWithCapacity:_cacheSize.unsignedIntegerValue];
		}
		completion(nil);
	}
	else
	{
		completion(error);
	}
}



#pragma mark - SPTAuthViewDelegate

-(void)authenticationViewControllerDidCancelLogin:(SPTAuthViewController*)authenticationViewController
{
	if(_authControllerResponse != nil)
	{
		void(^response)(BOOL, NSError*) = _authControllerResponse;
		_authControllerResponse = nil;
		response(NO, nil);
	}
}

-(void)authenticationViewController:(SPTAuthViewController*)authenticationViewController didFailToLogin:(NSError*)error
{
	if(_authControllerResponse != nil)
	{
		void(^response)(BOOL, NSError*) = _authControllerResponse;
		_authControllerResponse = nil;
		response(NO, error);
	}
}

-(void)authenticationViewController:(SPTAuthViewController*)authenticationViewController didLoginWithSession:(SPTSession*)session
{
	_auth.session = session;
	if(_authControllerResponse != nil)
	{
		void(^response)(BOOL, NSError*) = _authControllerResponse;
		_authControllerResponse = nil;
		response(YES, nil);
	}
}



#pragma mark - SPTAudioStreamingDelegate

-(void)audioStreamingDidLogin:(SPTAudioStreamingController*)audioStreaming
{
	NSLog(@"audioStreamingDidLogin");
}

-(void)audioStreaming:(SPTAudioStreamingController*)audioStreaming didReceiveError:(NSError*)error
{
	NSLog(@"audioStreaming:didReceiveError: %@", error);
	
	/*NSMutableArray<void(^)(NSError*)>* _unfulfilledBlocks = [NSMutableArray array];
	
	if(_loginResponse != nil)
	{
		if(error.code==SPErrorGeneralLoginError || error.code==SPErrorLoginBadCredentials)
		{
			//do login callback
			RCTResponseSenderBlock response = _loginResponse;
			_loginResponse = nil;
			response(@[@{
				@"domain":error.domain,
				@"code":@(error.code),
				@"description":error.localizedDescription
			}]);
		}
	}*/
}

@end
  
