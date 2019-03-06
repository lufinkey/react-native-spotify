//
//  RNSpotifyProgressView.h
//  RNSpotify
//
//  Created by Luis Finke on 1/16/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface RNSpotifyProgressView : UIView

-(void)showInView:(UIView*)view animated:(BOOL)animated completion:(void(^)(void))completion;
-(void)dismissAnimated:(BOOL)animated completion:(void(^)(void))completion;

@property (readonly) UIActivityIndicatorView* activityIndicator;

@end
