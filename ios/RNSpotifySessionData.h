//
//  RNSpotifySessionData.h
//  RNSpotify
//
//  Created by Luis Finke on 3/4/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RNSpotifyError.h"

@interface RNSpotifySessionData : NSObject

@property (nonatomic) NSString* accessToken;
@property (nonatomic) NSDate* expireDate;
@property (nonatomic) NSString* refreshToken;
@property (nonatomic) NSArray<NSString*>* scopes;

@property (nonatomic, readonly) BOOL isValid;

-(BOOL)hasScope:(NSString*)scope;

-(void)saveToUserDefaults:(NSUserDefaults*)userDefaults key:(NSString*)key;
+(RNSpotifySessionData*)sessionFromUserDefaults:(NSUserDefaults*)userDefaults key:(NSString*)key;
+(RNSpotifySessionData*)sessionFromDictionary:(NSDictionary*)dict error:(RNSpotifyError**)error;

+(NSDate*)expireDateFromSeconds:(NSInteger)seconds;

@end
