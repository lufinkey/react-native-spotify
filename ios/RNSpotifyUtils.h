//
//  RNSpotifyUtils.h
//  RNSpotify
//
//  Created by Luis Finke on 3/3/19.
//  Copyright © 2019 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface RNSpotifyUtils : NSObject

+(NSString*)makeQueryString:(NSDictionary*)params;

+(BOOL)isNull:(id)obj;
+(id)getOption:(NSString*)option from:(NSDictionary*)options fallback:(NSDictionary*)fallback;
+(void)setOrRemoveObject:(id)object forKey:(NSString*)key in:(NSMutableDictionary*)dict;
+(id)getObjectForKey:(NSString*)key in:(NSDictionary*)dict;

@end
