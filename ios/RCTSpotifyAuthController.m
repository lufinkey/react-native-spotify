//
//  RCTSpotifyAuthController.m
//  RCTSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "RCTSpotifyAuthController.h"
#import "RCTSpotifyWebViewController.h"
#import "RCTSpotifyProgressView.h"

@interface RCTSpotifyAuthController() <UIWebViewDelegate>
{
	SPTAuth* _auth;
	RCTSpotifyWebViewController* _webController;
	RCTSpotifyProgressView* _progressView;
}
-(void)didSelectCancelButton;
@end

@implementation RCTSpotifyAuthController

+(UIViewController*)topViewController
{
	UIViewController* topController = [UIApplication sharedApplication].keyWindow.rootViewController;
	while(topController.presentedViewController != nil)
	{
		topController = topController.presentedViewController;
	}
	return topController;
}

-(id)initWithAuth:(SPTAuth*)auth
{
	RCTSpotifyWebViewController* rootController = [[RCTSpotifyWebViewController alloc] init];
	if(self = [super initWithRootViewController:rootController])
	{
		_auth = auth;
		_webController = rootController;
		_progressView = [[RCTSpotifyProgressView alloc] init];
		
		self.navigationBar.barTintColor = [UIColor blackColor];
		self.navigationBar.tintColor = [UIColor whiteColor];
		self.navigationBar.titleTextAttributes = @{NSForegroundColorAttributeName : [UIColor whiteColor]};
		self.view.backgroundColor = [UIColor whiteColor];
		self.modalPresentationStyle = UIModalPresentationPageSheet;
		
		_webController.webView.delegate = self;
		//_webController.title = @"Log into Spotify";
		_webController.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemCancel target:self action:@selector(didSelectCancelButton)];
		
		NSURLRequest* request = [NSURLRequest requestWithURL:_auth.spotifyWebAuthenticationURL];
		[_webController.webView loadRequest:request];
	}
	return self;
}

-(void)clearCookies:(void(^)())completion
{
	dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
		NSHTTPCookieStorage *storage = [NSHTTPCookieStorage sharedHTTPCookieStorage];
		for (NSHTTPCookie *cookie in [storage cookies]) {
			[storage deleteCookie:cookie];
		}
		[[NSUserDefaults standardUserDefaults] synchronize];
		dispatch_async(dispatch_get_main_queue(), ^{
			if(completion != nil)
			{
				completion();
			}
		});
	});
}

-(void)didSelectCancelButton
{
	if(_completion != nil)
	{
		_completion(NO, nil);
	}
}


#pragma mark - UIWebViewDelegate

-(BOOL)webView:(UIWebView*)webView shouldStartLoadWithRequest:(NSURLRequest*)request navigationType:(UIWebViewNavigationType)navigationType
{
	NSLog(@"attempting to load URL: %@", request.URL);
	if([_auth canHandleURL:request.URL])
	{
		NSLog(@"no");
		[_progressView showInView:self.view animated:YES completion:nil];
		[_auth handleAuthCallbackWithTriggeredAuthURL:request.URL callback:^(NSError* error, SPTSession* session){
			if(session!=nil)
			{
				_auth.session = session;
			}
			
			if(error != nil)
			{
				if(_completion != nil)
				{
					_completion(NO, error);
				}
			}
			else
			{
				if(_completion != nil)
				{
					_completion(YES, nil);
				}
			}
		}];
		return NO;
	}
	return YES;
}

@end
