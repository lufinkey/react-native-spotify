//
//  RNSpotifyLoginOptions.h
//  RNSpotify
//
//  Created by Luis Finke on 3/4/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RNSpotifyError.h"

@interface RNSpotifyLoginOptions : NSObject
@property (nonatomic) NSString* clientID;
@property (nonatomic) NSURL* redirectURL;
@property (nonatomic) NSArray<NSString*>* scopes;
@property (nonatomic) NSURL* tokenSwapURL;
@property (nonatomic) NSURL* tokenRefreshURL;
@property (nonatomic) NSDictionary* params;

-(NSURL*)spotifyWebAuthenticationURLWithState:(NSString*)state;

+(RNSpotifyLoginOptions*)optionsFromDictionary:(NSDictionary*)dict fallback:(NSDictionary*)fallbackDict error:(RNSpotifyError**)error;
+(RNSpotifyLoginOptions*)optionsFromDictionary:(NSDictionary*)dict fallback:(NSDictionary*)fallbackDict ignore:(NSArray<NSString*>*)ignore error:(RNSpotifyError**)error;
@end
