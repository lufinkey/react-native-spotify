//
//  RNSpotifyAuth.h
//  RNSpotify
//
//  Created by Luis Finke on 3/3/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RNSpotifyAuthController.h"
#import "RNSpotifyLoginOptions.h"
#import "RNSpotifyCompletion.h"
#import "RNSpotifySessionData.h"

@interface RNSpotifyAuth : NSObject

@property (nonatomic) NSString* sessionUserDefaultsKey;

@property (nonatomic, readonly) NSString* clientID;
@property (nonatomic, readonly) NSURL* tokenRefreshURL;

@property (nonatomic, readonly) RNSpotifySessionData* session;

@property (nonatomic, readonly) BOOL isLoggedIn;
@property (nonatomic, readonly) BOOL isSessionValid;
@property (nonatomic, readonly) BOOL hasStreamingScope;
@property (nonatomic, readonly) BOOL canRefreshSession;

-(void)loadWithOptions:(RNSpotifyLoginOptions*)options;
-(void)save;

-(void)startSession:(RNSpotifySessionData*)session options:(RNSpotifyLoginOptions*)options;
-(void)clearSession;

-(void)renewSessionIfNeeded:(RNSpotifyCompletion*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse;
-(void)renewSession:(RNSpotifyCompletion*)completion waitForDefinitiveResponse:(BOOL)waitForDefinitiveResponse;

+(void)performTokenURLRequestTo:(NSURL*)url params:(NSDictionary*)params completion:(RNSpotifyCompletion<NSDictionary*>*)completion;
+(void)swapCodeForToken:(NSString*)code url:(NSURL*)url completion:(RNSpotifyCompletion<RNSpotifySessionData*>*)completion;

@end
