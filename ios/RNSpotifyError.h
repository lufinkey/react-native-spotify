//
//  RNSpotifyError.h
//  RNSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface RNSpotifyErrorCode : NSObject

@property (readonly) NSString* name;
@property (readonly) NSString* code;
@property (readonly) NSString* message;
@property (readonly) NSDictionary* reactObject;

#define DECLARE_SPOTIFY_ERROR_CODE(errorName) \
	@property (class, readonly) RNSpotifyErrorCode* errorName;

DECLARE_SPOTIFY_ERROR_CODE(AlreadyInitialized)
DECLARE_SPOTIFY_ERROR_CODE(NotInitialized)
DECLARE_SPOTIFY_ERROR_CODE(NotImplemented)
DECLARE_SPOTIFY_ERROR_CODE(NotLoggedIn)
DECLARE_SPOTIFY_ERROR_CODE(MissingOption)
DECLARE_SPOTIFY_ERROR_CODE(BadParameter)
DECLARE_SPOTIFY_ERROR_CODE(NullParameter)
DECLARE_SPOTIFY_ERROR_CODE(ConflictingCallbacks)
DECLARE_SPOTIFY_ERROR_CODE(BadResponse)
DECLARE_SPOTIFY_ERROR_CODE(PlayerNotReady)
DECLARE_SPOTIFY_ERROR_CODE(SessionExpired)

#undef DECLARE_SPOTIFY_ERROR_CODE

-(void)reject:(void(^)(NSString*,NSString*,NSError*))promiseRejector;

@end



@interface RNSpotifyError : NSObject

-(id)initWithCode:(NSString*)code message:(NSString*)message;
-(id)initWithCode:(NSString*)code error:(NSError*)error;
-(id)initWithCodeObj:(RNSpotifyErrorCode*)code;
-(id)initWithCodeObj:(RNSpotifyErrorCode*)code message:(NSString*)message;
-(id)initWithNSError:(NSError*)error;

+(instancetype)errorWithCode:(NSString*)code message:(NSString*)message;
+(instancetype)errorWithCode:(NSString*)code error:(NSError*)error;
+(instancetype)errorWithCodeObj:(RNSpotifyErrorCode*)code;
+(instancetype)errorWithCodeObj:(RNSpotifyErrorCode*)code message:(NSString*)message;
+(instancetype)errorWithNSError:(NSError*)error;

-(void)reject:(void(^)(NSString*,NSString*,NSError*))promiseRejector;

@property (readonly) NSString* code;
@property (readonly) NSString* message;
@property (readonly) NSDictionary* reactObject;

+(RNSpotifyError*)nullParameterErrorForName:(NSString*)paramName;
+(RNSpotifyError*)missingOptionErrorForName:(NSString*)optionName;
+(RNSpotifyError*)httpErrorForStatusCode:(NSInteger)statusCode;
+(RNSpotifyError*)httpErrorForStatusCode:(NSInteger)statusCode message:(NSString*)message;

@end
