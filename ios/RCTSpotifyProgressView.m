//
//  RCTSpotifyProgressView.m
//  RCTSpotify
//
//  Created by Luis Finke on 1/16/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RCTSpotifyProgressView.h"
#import <QuartzCore/QuartzCore.h>

@interface RCTSpotifyProgressView()
{
	UIView* _hudView;
}
@end

@implementation RCTSpotifyProgressView

-(id)initWithFrame:(CGRect)frame
{
	if(self = [super initWithFrame:frame])
	{
		self.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
		
		CGSize hudSize = CGSizeMake(100, 100);
		
		_hudView = [[UIView alloc] initWithFrame:CGRectMake(0,0,hudSize.width,hudSize.height)];
		_hudView.backgroundColor = [UIColor colorWithWhite:0 alpha:0.4];
		_hudView.layer.cornerRadius = 10;
		
		_activityIndicator = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
		_activityIndicator.center = CGPointMake(hudSize.width/2, hudSize.height/2);
		
		[_hudView addSubview:_activityIndicator];
		[self addSubview:_hudView];
	}
	return self;
}

-(void)layoutSubviews
{
	[super layoutSubviews];
	CGSize size = self.bounds.size;
	
	_hudView.center = CGPointMake(size.width/2, size.height/2);
}

-(void)willMoveToSuperview:(UIView*)newSuperview
{
	[super willMoveToSuperview:newSuperview];
	if(newSuperview != nil)
	{
		[_activityIndicator startAnimating];
	}
	else
	{
		[_activityIndicator stopAnimating];
	}
}

-(void)showInView:(UIView*)view animated:(BOOL)animated completion:(void(^)())completion
{
	CGSize viewSize = view.bounds.size;
	self.frame = CGRectMake(0, 0, viewSize.width, viewSize.height);
	[self setNeedsLayout];
	
	if(animated)
	{
		_hudView.alpha = 0;
		_hudView.transform = CGAffineTransformMakeScale(1.4, 1.4);
		[view addSubview:self];
		[UIView animateWithDuration:0.25 animations:^{
			_hudView.alpha = 1;
			_hudView.transform = CGAffineTransformIdentity;
		} completion:^(BOOL finished) {
			if(completion != nil)
			{
				completion();
			}
		}];
	}
	else
	{
		[view addSubview:self];
		if(completion != nil)
		{
			completion();
		}
	}
}

-(void)dismissAnimated:(BOOL)animated completion:(void(^)())completion
{
	if(animated)
	{
		[UIView animateWithDuration:0.25 animations:^{
			self.alpha = 0;
		} completion:^(BOOL finished) {
			[self removeFromSuperview];
			self.alpha = 1;
			if(completion!=nil)
			{
				completion();
			}
		}];
	}
	else
	{
		[self removeFromSuperview];
		if(completion!=nil)
		{
			completion();
		}
	}
}

@end
