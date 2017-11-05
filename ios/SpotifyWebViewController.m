//
//  SpotifyWebViewController.m
//  RCTSpotify
//
//  Created by Luis Finke on 11/5/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "SpotifyWebViewController.h"

@interface SpotifyWebViewController() <UIWebViewDelegate>
{
	NSURL* _initialURL;
	UINavigationBar* _navigationBar;
}
-(void)handleCloseButton;
@end

@implementation SpotifyWebViewController

@synthesize delegate = _delegate;
@synthesize webView = _webView;

-(id)initWithURL:(NSURL*)url
{
	if(self = [super init])
	{
		_initialURL = url;
		
		_webView = [[UIWebView alloc] init];
		_webView.delegate = self;
		
		_navigationBar = [[UINavigationBar alloc] init];
		UINavigationItem* item = [[UINavigationItem alloc] initWithTitle:@""];
		item.leftBarButtonItem = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemCancel target:self action:@selector(handleCloseButton)];
		[_navigationBar setItems:@[item]];
		_navigationBar.barTintColor = [UIColor blackColor];
		_navigationBar.tintColor = [UIColor whiteColor];
		_navigationBar.titleTextAttributes = @{NSForegroundColorAttributeName : [UIColor whiteColor]};
		
		self.modalPresentationStyle = UIModalPresentationPageSheet;
	}
	return self;
}

-(void)viewDidLoad
{
	[super viewDidLoad];
	
	[self.view addSubview:_webView];
	[self.view addSubview:_navigationBar];
	
	self.view.backgroundColor = [UIColor blackColor];
	
	NSURLRequest* initialRequest = [NSURLRequest requestWithURL:_initialURL];
	[_webView loadRequest:initialRequest];
}

-(void)viewWillLayoutSubviews
{
	[super viewWillLayoutSubviews];
	CGSize size = self.view.bounds.size;
	
	CGFloat statusBarHeight = [UIApplication sharedApplication].statusBarFrame.size.height;
	
	CGFloat navOffset = 0;
	_navigationBar.frame = CGRectMake(0, statusBarHeight, size.width, 44);
	if(!_navigationBar.hidden)
	{
		navOffset = statusBarHeight+44;
	}
	_webView.frame = CGRectMake(0, navOffset, size.width, size.height-navOffset);
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

-(BOOL)navigationBarVisible
{
	return !_navigationBar.hidden;
}

-(void)setNavigationBarVisible:(BOOL)navigationBarVisible
{
	_navigationBar.hidden = !navigationBarVisible;
	[self.view setNeedsLayout];
}

-(void)handleCloseButton
{
	if(_delegate!=nil && [_delegate respondsToSelector:@selector(spotifyWebControllerDidCancel:)])
	{
		[_delegate spotifyWebControllerDidCancel:self];
	}
}

-(void)setTitle:(NSString*)title
{
	_navigationBar.topItem.title = title;
}

-(NSString*)title
{
	return _navigationBar.topItem.title;
}



#pragma mark - UIWebViewDelegate

-(void)webViewDidStartLoad:(UIWebView*)webView
{
	NSLog(@"didStartLoad: %@", webView.request.URL);
}

-(void)webViewDidFinishLoad:(UIWebView*)webView
{
	NSLog(@"webViewDidFinishLoad: %@", webView.request.URL);
}

-(void)webView:(UIWebView*)webView didFailLoadWithError:(NSError*)error
{
	NSLog(@"webView:didFailLoadWithError: %@", webView.request.URL);
}

@end
