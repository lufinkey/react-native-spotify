//
//  SpotifyWebViewController.h
//  RCTSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <SpotifyAuthentication/SpotifyAuthentication.h>

@class SpotifyWebViewController;

@protocol SpotifyWebViewDelegate <NSObject>
@optional
-(void)spotifyWebControllerDidCancel:(SpotifyWebViewController*)webController;
@end


@interface SpotifyWebViewController : UIViewController

-(id)initWithURL:(NSURL*)url;

-(void)clearCookies:(void(^)())completion;

@property (nonatomic, weak) id<SpotifyWebViewDelegate> delegate;
@property (nonatomic, readonly) UIWebView* webView;
@property (nonatomic) BOOL navigationBarVisible;

@end
