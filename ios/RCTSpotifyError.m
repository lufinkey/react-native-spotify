//
//  RCTSpotifyError.m
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RCTSpotifyError.h"
#import <SpotifyAudioPlayback/SpotifyAudioPlayback.h>

@interface RCTSpotifyError()
{
	NSError* _error;
}
+(NSString*)getSDKErrorCode:(SpErrorCode)enumVal;
@end

@implementation RCTSpotifyError

@synthesize code = _code;
@synthesize message = _message;

-(id)initWithCode:(NSString*)code message:(NSString*)message
{
	if(self = [super init])
	{
		_code = [NSString stringWithString:code];
		_message = [NSString stringWithString:message];
	}
	return self;
}

-(id)initWithCode:(NSString*)code error:(NSError*)error
{
	if(code == nil || code.length == 0)
	{
		return [self initWithError:error];
	}
	if(self = [super init])
	{
		if(error == nil)
		{
			@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"Cannot provide a nil error to RCTSpotifyError" userInfo:nil];
		}
		_error = error;
		_code = [NSString stringWithString:code];
		_message = _error.localizedDescription;
	}
	return self;
}

-(id)initWithError:(NSError*)error
{
	if(self = [super init])
	{
		if(error == nil)
		{
			@throw [NSException exceptionWithName:NSInvalidArgumentException reason:@"Cannot provide a nil error to RCTSpotifyError" userInfo:nil];
		}
		_error = error;
		if([_error.domain isEqualToString:@"com.spotify.ios-sdk.playback"])
		{
			_code = [self.class getSDKErrorCode:_error.code];
			_message = _error.localizedDescription;
		}
		else
		{
			_code = [NSString stringWithFormat:@"%@:%ld", _error.domain, _error.code];
			_message = _error.localizedDescription;
		}
	}
	return self;
}

+(instancetype)errorWithCode:(NSString*)code message:(NSString*)message
{
	return [[self alloc] initWithCode:code message:message];
}

+(instancetype)errorWithCode:(NSString *)code error:(NSError *)error
{
	return [[self alloc] initWithCode:code error:error];
}

+(instancetype)errorWithError:(NSError*)error
{
	return [[self alloc] initWithError:error];
}


#define SDK_ERROR_CASE(error) case error: return @#error;

+(NSString*)getSDKErrorCode:(SpErrorCode)enumVal
{
	switch(enumVal)
	{
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
