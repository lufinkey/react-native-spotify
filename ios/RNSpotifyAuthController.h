//
//  RNSpotifyAuthController.h
//  RNSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "RNSpotifySessionData.h"
#import "RNSpotifyLoginOptions.h"
#import "RNSpotifyCompletion.h"


@interface RNSpotifyAuthController: UINavigationController <UIAdaptivePresentationControllerDelegate>

-(id)initWithOptions:(RNSpotifyLoginOptions*)options;

+(UIViewController*)topViewController;

@property (nonatomic) RNSpotifyCompletion<RNSpotifySessionData*>* completion;

@end
