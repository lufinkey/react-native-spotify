//
//  RCTSpotifyCompletion.m
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RCTSpotifyCompletion.h"

@interface RCTSpotifyCompletion()
{
	BOOL _responded;
	void(^_resolver)(id);
	void(^_rejector)(RCTSpotifyError*);
	void(^_completion)(id, RCTSpotifyError*);
}
@end

@implementation RCTSpotifyCompletion

-(id)initWithOnResolve:(void(^)(id))resolver onReject:(void(^)(RCTSpotifyError*))rejector
{
	if(self = [super init])
	{
		_responded = NO;
		_resolver = resolver;
		_rejector = rejector;
		_completion = nil;
	}
	return self;
}

-(id)initWithOnComplete:(void(^)(id,RCTSpotifyError*))completion
{
	if(self = [super init])
	{
		_responded = NO;
		_resolver = nil;
		_rejector = nil;
		_completion = completion;
	}
	return self;
}

-(void)resolve:(id)result
{
	if(_responded)
	{
		@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"cannot call resolve or reject multiple times on a Completion object" userInfo:nil];
	}
	_responded = YES;
	if(_resolver != nil)
	{
		_resolver(result);
	}
	if(_completion != nil)
	{
		_completion(result, nil);
	}
}

-(void)reject:(RCTSpotifyError*)error
{
	if(_responded)
	{
		@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"cannot call resolve or reject multiple times on a Completion object" userInfo:nil];
	}
	_responded = YES;
	if(_rejector != nil)
	{
		_rejector(error);
	}
	if(_completion != nil)
	{
		_completion(nil, error);
	}
}

@end
