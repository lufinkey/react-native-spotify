//
//  RNSpotifyCompletion.m
//  RNSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RNSpotifyCompletion.h"

@interface RNSpotifyCompletion() {
	BOOL _responded;
	void(^_resolver)(id);
	void(^_rejector)(RNSpotifyError*);
	void(^_completion)(id, RNSpotifyError*);
}
@end

@implementation RNSpotifyCompletion

-(id)initWithOnResolve:(void(^)(id))resolver onReject:(void(^)(RNSpotifyError*))rejector {
	if(self = [super init]) {
		_responded = NO;
		_resolver = resolver;
		_rejector = rejector;
		_completion = nil;
	}
	return self;
}

-(id)initWithOnComplete:(void(^)(id,RNSpotifyError*))completion {
	if(self = [super init]) {
		_responded = NO;
		_resolver = nil;
		_rejector = nil;
		_completion = completion;
	}
	return self;
}

-(void)resolve:(id)result {
	if(_responded) {
		@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"cannot call resolve or reject multiple times on a Completion object" userInfo:nil];
	}
	_responded = YES;
	if(_resolver != nil) {
		_resolver(result);
	}
	if(_completion != nil) {
		_completion(result, nil);
	}
}

-(void)reject:(RNSpotifyError*)error {
	if(_responded) {
		@throw [NSException exceptionWithName:NSInternalInconsistencyException reason:@"cannot call resolve or reject multiple times on a Completion object" userInfo:nil];
	}
	_responded = YES;
	if(_rejector != nil) {
		_rejector(error);
	}
	if(_completion != nil)
	{
		_completion(nil, error);
	}
}

+(RNSpotifyCompletion*)onResolve:(void(^)(id))onResolve onReject:(void(^)(RNSpotifyError*))onReject {
	return [[self alloc] initWithOnResolve:onResolve onReject:onReject];
}

+(RNSpotifyCompletion*)onReject:(void(^)(RNSpotifyError*))onReject onResolve:(void(^)(id))onResolve {
	return [[self alloc] initWithOnResolve:onResolve onReject:onReject];
}

+(RNSpotifyCompletion*)onComplete:(void(^)(id,RNSpotifyError*))onComplete {
	return [[self alloc] initWithOnComplete:onComplete];
}

@end
