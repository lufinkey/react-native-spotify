//
//  RCTSpotifyCompletion.h
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RCTSpotifyError.h"

@interface RCTSpotifyCompletion<__covariant ObjectType> : NSObject

-(id)initWithOnResolve:(void(^)(ObjectType result))resolver onReject:(void(^)(RCTSpotifyError* error))rejector;
-(id)initWithOnComplete:(void(^)(ObjectType result, RCTSpotifyError* error))completion;

-(void)resolve:(ObjectType)result;
-(void)reject:(RCTSpotifyError*)error;

+(RCTSpotifyCompletion*)onResolve:(void(^)(ObjectType result))onResolve onReject:(void(^)(RCTSpotifyError* error))onReject;
+(RCTSpotifyCompletion*)onReject:(void(^)(RCTSpotifyError*))onReject onResolve:(void(^)(ObjectType result))onResolve;
+(RCTSpotifyCompletion*)onComplete:(void(^)(ObjectType result, RCTSpotifyError* error))onComplete;

@end
