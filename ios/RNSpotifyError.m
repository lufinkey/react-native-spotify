//
//  RNSpotifyError.m
//  RNSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RNSpotifyError.h"
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>


@interface RNSpotifyErrorCode()
-(id)initWithName:(NSString*)name message:(NSString*)message;
+(instancetype)codeWithName:(NSString*)name message:(NSString*)message;
@end

@implementation RNSpotifyErrorCode

#define DEFINE_SPOTIFY_ERROR_CODE(errorName, messageStr) \
	static RNSpotifyErrorCode* _RNSpotifyErrorCode##errorName = nil; \
	+(RNSpotifyErrorCode*)errorName { \
		if(_RNSpotifyErrorCode##errorName == nil) { \
			_RNSpotifyErrorCode##errorName = [RNSpotifyErrorCode codeWithName:@#errorName message:messageStr]; } \
		return _RNSpotifyErrorCode##errorName; } \

DEFINE_SPOTIFY_ERROR_CODE(AlreadyInitialized, @"Spotify has already been initialized")
DEFINE_SPOTIFY_ERROR_CODE(NotInitialized, @"Spotify has not been initialized")
DEFINE_SPOTIFY_ERROR_CODE(NotImplemented, @"This feature has not been implemented")
DEFINE_SPOTIFY_ERROR_CODE(NotLoggedIn, @"You are not logged in")
DEFINE_SPOTIFY_ERROR_CODE(MissingOption, @"Missing required option")
DEFINE_SPOTIFY_ERROR_CODE(BadParameter, @"Invalid parameter")
DEFINE_SPOTIFY_ERROR_CODE(NullParameter, @"Null parameter")
DEFINE_SPOTIFY_ERROR_CODE(ConflictingCallbacks, @"You cannot call this function while it is already executing")
DEFINE_SPOTIFY_ERROR_CODE(BadResponse, @"Invalid response format")
DEFINE_SPOTIFY_ERROR_CODE(PlayerNotReady, @"Player is not ready")
DEFINE_SPOTIFY_ERROR_CODE(SessionExpired, @"Your login session has expired")

#undef DEFINE_SPOTIFY_ERROR_CODE

@synthesize name = _name;
@synthesize message = _message;

-(id)initWithName:(NSString*)name message:(NSString*)message {
	if(self = [super init]) {
		_name = [NSString stringWithString:name];
		_message = [NSString stringWithString:message];
	}
	return self;
}

+(instancetype)codeWithName:(NSString*)name message:(NSString*)message {
	return [[self alloc] initWithName:name message:message];
}

-(NSString*)code {
	return [NSString stringWithFormat:@"RNS%@", _name];
}

-(NSDictionary*)reactObject {
	return @{ @"code":self.code, @"message":self.message };
}

-(void)reject:(void(^)(NSString*,NSString*,NSError*))promiseRejector {
	promiseRejector(self.code, self.message, nil);
}

@end



@interface RNSpotifyError() {
	NSError* _error;
}
+(NSString*)getSDKErrorCode:(SpErrorCode)enumVal;
@end

@implementation RNSpotifyError

@synthesize code = _code;
@synthesize message = _message;

-(id)initWithCode:(NSString*)code message:(NSString*)message {
	if(self = [super init]) {
		_error = nil;
		_code = [NSString stringWithString:code];
		_message = [NSString stringWithString:message];
	}
	return self;
}

-(id)initWithCode:(NSString*)code error:(NSError*)error {
	if(code == nil || code.length == 0) {
		return [self initWithNSError:error];
	}
	if(self = [super init]) {
		if(error == nil) {
			@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"Cannot provide a nil error to RNSpotifyError" userInfo:nil];
		}
		_error = error;
		_code = [NSString stringWithString:code];
		_message = _error.localizedDescription;
	}
	return self;
}

-(id)initWithCodeObj:(RNSpotifyErrorCode*)code {
	return [self initWithCodeObj:code message:code.message];
}

-(id)initWithCodeObj:(RNSpotifyErrorCode*)code message:(NSString*)message {
	if(self = [super init]) {
		_error = nil;
		_code = [NSString stringWithString:code.code];
		_message = [NSString stringWithString:message];
	}
	return self;
}

-(id)initWithNSError:(NSError*)error {
	if(self = [super init]) {
		if(error == nil) {
			@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"Cannot provide a nil error to RNSpotifyError" userInfo:nil];
		}
		_error = error;
		if([_error.domain isEqualToString:@"com.spotify.ios-sdk.playback"]) {
			_code = [self.class getSDKErrorCode:_error.code];
			_message = _error.localizedDescription;
		}
		else {
			_code = [NSString stringWithFormat:@"%@:%ld", _error.domain, _error.code];
			_message = _error.localizedDescription;
		}
	}
	return self;
}

+(instancetype)errorWithCode:(NSString*)code message:(NSString*)message {
	return [[self alloc] initWithCode:code message:message];
}

+(instancetype)errorWithCode:(NSString *)code error:(NSError *)error {
	return [[self alloc] initWithCode:code error:error];
}

+(instancetype)errorWithCodeObj:(RNSpotifyErrorCode*)code {
	return [[self alloc] initWithCodeObj:code];
}

+(instancetype)errorWithCodeObj:(RNSpotifyErrorCode*)code message:(NSString*)message {
	return [[self alloc] initWithCodeObj:code message:message];
}

+(instancetype)errorWithNSError:(NSError*)error {
	return [[self alloc] initWithNSError:error];
}

-(void)reject:(void(^)(NSString*,NSString*,NSError*))promiseRejector {
	promiseRejector(_code, _message, _error);
}

-(NSDictionary*)reactObject {
	return @{ @"code":_code, @"message":_message };
}



+(RNSpotifyError*)nullParameterErrorForName:(NSString*)paramName {
	return [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.NullParameter
									 message:[NSString stringWithFormat:@"%@ cannot be null", paramName]];
}

+(RNSpotifyError*)missingOptionErrorForName:(NSString*)optionName {
	return [RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.MissingOption
									 message:[NSString stringWithFormat:@"Missing required option %@", optionName]];
}

+(RNSpotifyError*)httpErrorForStatusCode:(NSInteger)statusCode {
	if(statusCode <= 0) {
		return [RNSpotifyError errorWithCode:@"HTTPRequestFailed" message:@"Unable to send request"];
	}
	return [RNSpotifyError errorWithCode:[NSString stringWithFormat:@"HTTP%ld", statusCode]
								  message:[NSHTTPURLResponse localizedStringForStatusCode:statusCode]];
}

+(RNSpotifyError*)httpErrorForStatusCode:(NSInteger)statusCode message:(NSString*)message {
	NSString* code = [NSString stringWithFormat:@"HTTP%ld", statusCode];
	if(statusCode <= 0) {
		code = @"HTTPRequestFailed";
	}
	return [RNSpotifyError errorWithCode:code message:message];
}



#define SDK_ERROR_CASE(error) case error: return @#error;

+(NSString*)getSDKErrorCode:(SpErrorCode)enumVal {
	switch(enumVal) {
		SDK_ERROR_CASE(SPErrorOk)
		SDK_ERROR_CASE(SPErrorFailed)
		SDK_ERROR_CASE(SPErrorInitFailed)
		SDK_ERROR_CASE(SPErrorWrongAPIVersion)
		SDK_ERROR_CASE(SPErrorNullArgument)
		SDK_ERROR_CASE(SPErrorInvalidArgument)
		SDK_ERROR_CASE(SPErrorUninitialized)
		SDK_ERROR_CASE(SPErrorAlreadyInitialized)
		SDK_ERROR_CASE(SPErrorLoginBadCredentials)
		SDK_ERROR_CASE(SPErrorNeedsPremium)
		SDK_ERROR_CASE(SPErrorTravelRestriction)
		SDK_ERROR_CASE(SPErrorApplicationBanned)
		SDK_ERROR_CASE(SPErrorGeneralLoginError)
		SDK_ERROR_CASE(SPErrorUnsupported)
		SDK_ERROR_CASE(SPErrorNotActiveDevice)
		SDK_ERROR_CASE(SPErrorAPIRateLimited)
		SDK_ERROR_CASE(SPErrorPlaybackErrorStart)
		SDK_ERROR_CASE(SPErrorGeneralPlaybackError)
		SDK_ERROR_CASE(SPErrorPlaybackRateLimited)
		SDK_ERROR_CASE(SPErrorPlaybackCappingLimitReached)
		SDK_ERROR_CASE(SPErrorAdIsPlaying)
		SDK_ERROR_CASE(SPErrorCorruptTrack)
		SDK_ERROR_CASE(SPErrorContextFailed)
		SDK_ERROR_CASE(SPErrorPrefetchItemUnavailable)
		SDK_ERROR_CASE(SPAlreadyPrefetching)
		SDK_ERROR_CASE(SPStorageWriteError)
		SDK_ERROR_CASE(SPPrefetchDownloadFailed)
	}
	return [NSString stringWithFormat:@"SPError:%ld", (NSInteger)enumVal];
}

@end
