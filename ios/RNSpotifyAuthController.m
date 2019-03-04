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
#import "RNSpotifyAuth.h"
#import "HelperMacros.h"

@interface RNSpotifyAuthController() <UIWebViewDelegate> {
	RNSpotifyLoginOptions* _options;
	NSString* _xssState;
	
	RNSpotifyWebViewController* _webController;
	RNSpotifyProgressView* _progressView;
}
-(void)didSelectCancelButton;
@end

@implementation RNSpotifyAuthController

+(UIViewController*)topViewController {
	UIViewController* topController = [UIApplication sharedApplication].keyWindow.rootViewController;
	while(topController.presentedViewController != nil) {
		topController = topController.presentedViewController;
	}
	return topController;
}

-(id)initWithOptions:(RNSpotifyLoginOptions*)options {
	RNSpotifyWebViewController* rootController = [[RNSpotifyWebViewController alloc] init];
	if(self = [super initWithRootViewController:rootController]) {
		_webController = rootController;
		_progressView = [[RNSpotifyProgressView alloc] init];
		
		
		_options = options;
		_xssState = [NSUUID UUID].UUIDString;
		
		self.navigationBar.barTintColor = [UIColor blackColor];
		self.navigationBar.tintColor = [UIColor whiteColor];
		self.navigationBar.titleTextAttributes = @{NSForegroundColorAttributeName : [UIColor whiteColor]};
		self.view.backgroundColor = [UIColor whiteColor];
		self.modalPresentationStyle = UIModalPresentationFormSheet;
		
		_webController.webView.delegate = self;
		//_webController.title = @"Log into Spotify";
		_webController.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemCancel target:self action:@selector(didSelectCancelButton)];
		
		NSURL* url = [_options spotifyWebAuthenticationURLWithState:_xssState];
		NSURLRequest* request = [NSURLRequest requestWithURL:url];
		[_webController.webView loadRequest:request];
	}
	return self;
}

-(UIStatusBarStyle)preferredStatusBarStyle {
	return UIStatusBarStyleLightContent;
}

-(void)clearCookies:(void(^)())completion {
	dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
		NSHTTPCookieStorage *storage = [NSHTTPCookieStorage sharedHTTPCookieStorage];
		for (NSHTTPCookie *cookie in [storage cookies]) {
			[storage deleteCookie:cookie];
		}
		[[NSUserDefaults standardUserDefaults] synchronize];
		dispatch_async(dispatch_get_main_queue(), ^{
			if(completion != nil) {
				completion();
			}
		});
	});
}

-(void)didSelectCancelButton {
	if(_completion != nil) {
		[_completion resolve:nil];
	}
}


#pragma mark - auth methods

+(NSDictionary*)decodeQueryString:(NSString*)queryString {
	NSArray<NSString*>* parts = [queryString componentsSeparatedByString:@"&"];
	NSMutableDictionary* params = [NSMutableDictionary dictionary];
	for(NSString* part in parts) {
		NSString* escapedPart = [part stringByReplacingOccurrencesOfString:@"+" withString:@"%20"];
		NSArray<NSString*>* expressionParts = [escapedPart componentsSeparatedByString:@"="];
		if(expressionParts.count != 2) {
			continue;
		}
		NSString* key = [expressionParts[0] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
		NSString* value = [expressionParts[1] stringByReplacingPercentEscapesUsingEncoding:NSUTF8StringEncoding];
		params[key] = value;
	}
	return params;
}

+(NSDictionary*)parseOAuthQueryParams:(NSURL*)url {
	if(url == nil) {
		return [NSDictionary dictionary];
	}
	NSDictionary* queryParams = [self decodeQueryString:url.query];
	if(queryParams != nil && queryParams.count > 0) {
		return queryParams;
	}
	NSDictionary* fragmentParams = [self decodeQueryString:url.fragment];
	if(fragmentParams != nil && fragmentParams.count > 0) {
		return fragmentParams;
	}
	return [NSDictionary dictionary];
}

-(BOOL)canHandleRedirectURL:(NSURL*)url {
	printOutLog(@"checking for redirect url in %@", url);
	if(_options.redirectURL == nil) {
		return NO;
	}
	if(![url.absoluteString hasPrefix:_options.redirectURL.absoluteString]) {
		return NO;
	}
	NSString* path = _options.redirectURL.path;
	if(path == nil || [path isEqualToString:@"/"]) {
		path = @"";
	}
	NSString* cmpPath = url.path;
	if(cmpPath == nil || [cmpPath isEqualToString:@"/"]) {
		cmpPath = @"";
	}
	if(![path isEqualToString:cmpPath]) {
		return NO;
	}
	return YES;
}

-(void)handleRedirectURL:(NSURL*)url {
	NSDictionary* params = [RNSpotifyAuthController parseOAuthQueryParams:url];
	NSString* state = params[@"state"];
	NSString* error = params[@"error"];
	if(error != nil) {
		// error
		if([error isEqualToString:@"access_denied"]) {
			[_completion resolve:nil];
		}
		else {
			[_completion reject:[RNSpotifyError errorWithCode:error message:error]];
		}
	}
	else if(_xssState != nil && (state == nil || ![_xssState isEqualToString:state])) {
		// state mismatch
		[_completion reject:[RNSpotifyError errorWithCode:@"state_mismatch" message:@"state mismatch"]];
	}
	else if(params[@"access_token"] != nil) {
		// access token
		RNSpotifySessionData* sessionData = [[RNSpotifySessionData alloc] init];
		sessionData.accessToken = params[@"access_token"];
		NSString* expiresIn = params[@"expires_in"];
		NSInteger expireSeconds = [expiresIn integerValue];
		if(expireSeconds == 0) {
			[_completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadResponse message:@"Access token expire time was 0"]];
			return;
		}
		NSString* scope = params[@"scope"];
		NSArray<NSString*>* scopes = nil;
		if(scope != nil) {
			scopes = [scope componentsSeparatedByString:@" "];
		}
		else if(_options.scopes != nil) {
			scopes = [NSArray arrayWithArray:_options.scopes];
		}
		sessionData.expireDate = [RNSpotifySessionData expireDateFromSeconds:expireSeconds];
		sessionData.refreshToken = params[@"refresh_token"];
		sessionData.scopes = scopes;
		[_completion resolve:sessionData];
	}
	else if(params[@"code"] != nil) {
		// authentication code
		if(_options.tokenSwapURL == nil) {
			[_completion reject:[RNSpotifyError missingOptionErrorForName:@"tokenSwapURL"]];
			return;
		}
		[RNSpotifyAuth swapCodeForToken:params[@"code"] url:_options.tokenSwapURL completion:[RNSpotifyCompletion onReject:^(RNSpotifyError *error) {
			[_completion reject:error];
		} onResolve:^(RNSpotifySessionData* sessionData) {
			[_completion resolve:sessionData];
		}]];
	}
	else {
		[_completion reject:[RNSpotifyError errorWithCodeObj:RNSpotifyErrorCode.BadResponse message:@"Missing expected parameters in redirect URL"]];
	}
}


#pragma mark - UIWebViewDelegate

-(BOOL)webView:(UIWebView*)webView shouldStartLoadWithRequest:(NSURLRequest*)request navigationType:(UIWebViewNavigationType)navigationType {
	if([self canHandleRedirectURL:request.URL]) {
		[_progressView showInView:self.view animated:YES completion:nil];
		[self handleRedirectURL:request.URL];
		return NO;
	}
	return YES;
}

@end
