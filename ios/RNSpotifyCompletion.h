//
//  RNSpotifyCompletion.h
//  RNSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RNSpotifyError.h"

@interface RNSpotifyCompletion<__covariant ObjectType> : NSObject

-(id)initWithOnResolve:(void(^)(ObjectType result))resolver onReject:(void(^)(RNSpotifyError* error))rejector;
-(id)initWithOnComplete:(void(^)(ObjectType result, RNSpotifyError* error))completion;

-(void)resolve:(ObjectType)result;
-(void)reject:(RNSpotifyError*)error;

+(RNSpotifyCompletion*)onResolve:(void(^)(ObjectType result))onResolve onReject:(void(^)(RNSpotifyError* error))onReject;
+(RNSpotifyCompletion*)onReject:(void(^)(RNSpotifyError* error))onReject onResolve:(void(^)(ObjectType result))onResolve;
+(RNSpotifyCompletion*)onComplete:(void(^)(ObjectType result, RNSpotifyError* error))onComplete;

@end
