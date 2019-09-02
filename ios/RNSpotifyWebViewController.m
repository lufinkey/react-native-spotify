//
//  RNSpotifyWebViewController.m
//  RNSpotify
//
//  Created by Luis Finke on 1/16/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RNSpotifyWebViewController.h"

@implementation RNSpotifyWebViewController

-(id)init {
	if(self = [super init]) {
		_webView = [[WKWebView alloc] init];
	}
	return self;
}

-(void)viewDidLoad {
	[super viewDidLoad];
	
	[self.view addSubview:_webView];
}

-(void)viewWillLayoutSubviews {
	[super viewWillLayoutSubviews];
	CGSize size = self.view.bounds.size;
	
	_webView.frame = CGRectMake(0,0,size.width, size.height);
}

@end
