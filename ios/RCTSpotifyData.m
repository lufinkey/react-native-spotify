//
//  RCTSpotify.m
//  RCTSpotify
//
//  Created by Luis Finke on 11/4/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "RCTSpotifyData.h"

@implementation RCTSpotifyData

@synthesize instanceID = _instanceID;
@synthesize auth = _auth;
@synthesize player = _player;

-(id)initWithAuth:(SPTAuth*)auth
{
	if(self = [super init])
	{
		_instanceID = [NSString stringWithFormat:@"%p", self];
		_auth = auth;
		_player = nil;
	}
	return self;
}

@end
