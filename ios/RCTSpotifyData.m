//
//  RCTSpotify.m
//  RCTSpotify
//
//  Created by Luis Finke on 11/4/17.
//  Copyright © 2017 Facebook. All rights reserved.
//

#import "RCTSpotifyData.h"

@implementation RCTSpotifyData

@synthesize auth = _auth;

-(id)initWithAuth:(SPTAuth*)auth
{
	if(self = [super init])
	{
		_auth = auth;
	}
	return self;
}

@end
