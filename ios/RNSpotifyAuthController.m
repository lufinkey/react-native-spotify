//
//  RNSpotifyAuthController.m
//  RNSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "RNSpotifyAuthController.h"
#import "RNSpotifyWebViewController.h"
#import "RNSpotifyProgressView.h"

@interface RNSpotifyAuthController() <UIWebViewDelegate>
{
	SPTAuth* _auth;
	RNSpotifyWebViewController* _webController;
	RNSpotifyProgressView* _progressView;
}
-(void)didSelectCancelButton;
@end

@implementation RNSpotifyAuthController

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
	RNSpotifyWebViewController* rootController = [[RNSpotifyWebViewController alloc] init];
	if(self = [super initWithRootViewController:rootController])
	{
		_auth = auth;
		_webController = rootController;
		_progressView = [[RNSpotifyProgressView alloc] init];
		
		self.navigationBar.barTintColor = [UIColor blackColor];
		self.navigationBar.tintColor = [UIColor whiteColor];
		self.navigationBar.titleTextAttributes = @{NSForegroundColorAttributeName : [UIColor whiteColor]};
		self.view.backgroundColor = [UIColor whiteColor];
		self.modalPresentationStyle = UIModalPresentationFormSheet;
		
		_webController.webView.delegate = self;
		//_webController.title = @"Log into Spotify";
		_webController.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemCancel target:self action:@selector(didSelectCancelButton)];
		
		NSURLRequest* request = [NSURLRequest requestWithURL:_auth.spotifyWebAuthenticationURL];
		[_webController.webView loadRequest:request];
	}
	return self;
}

-(UIStatusBarStyle)preferredStatusBarStyle
{
	return UIStatusBarStyleLightContent;
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
		[_completion resolve:@NO];
	}
}

+(NSDictionary*)decodeQueryString:(NSString*)queryString
{
	NSArray<NSString*>* parts = [queryString componentsSeparatedByString:@"&"];
	NSMutableDictionary* params = [NSMutableDictionary dictionary];
	for (NSString* part in parts)
	{
		NSString* escapedPart = [part stringByReplacingOccurrencesOfString:@"+" withString:@"%20"];
		NSArray<NSString*>* expressionParts = [escapedPart componentsSeparatedByString:@"="];
		if(expressionParts.count != 2)
		{
			continue;
		}
		NSString* key = [expressionParts[0] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
		NSString* value = [expressionParts[1] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
		params[key] = value;
	}
	return params;
}

+(NSDictionary*)parseOAuthQueryParams:(NSURL*)url
{
	if(url == nil)
	{
		return [NSDictionary dictionary];
	}
	NSDictionary* queryParams = [self decodeQueryString:url.query];
	if(queryParams != nil && queryParams.count > 0)
	{
		return queryParams;
	}
	NSDictionary* fragmentParams = [self decodeQueryString:url.fragment];
	if(fragmentParams != nil && fragmentParams.count > 0)
	{
		return fragmentParams;
	}
	return [NSDictionary dictionary];
}


#pragma mark - UIWebViewDelegate

-(BOOL)webView:(UIWebView*)webView shouldStartLoadWithRequest:(NSURLRequest*)request navigationType:(UIWebViewNavigationType)navigationType
{
	if([_auth canHandleURL:request.URL])
	{
		[_progressView showInView:self.view animated:YES completion:nil];
		[_auth handleAuthCallbackWithTriggeredAuthURL:request.URL callback:^(NSError* error, SPTSession* session){
			if(session!=nil)
			{
				_auth.session = session;
			}
			
			if(error == nil)
			{
				// success
				if(_completion != nil)
				{
					[_completion resolve:@YES];
				}
			}
			else
			{
				// error
				// get actual oauth error if possible
				NSDictionary* urlParams = [self.class parseOAuthQueryParams:request.URL];
				if(_completion != nil)
				{
					[_completion reject:[RNSpotifyError errorWithCode:urlParams[@"error"] error:error]];
				}
			}
		}];
		return NO;
	}
	return YES;
}

@end
