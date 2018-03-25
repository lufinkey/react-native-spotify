//
//  RNSpotifyAuthController.h
//  RNSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>
#import "RNSpotifyCompletion.h"

typedef void(^RNSpotifyAuthCallback)(BOOL authenticated, NSError* error);

@interface RNSpotifyAuthController : UINavigationController

-(id)initWithAuth:(SPTAuth*)auth;

-(void)clearCookies:(void(^)())completion;

+(UIViewController*)topViewController;

@property (strong) RNSpotifyCompletion<NSNumber*>* completion;

@end
