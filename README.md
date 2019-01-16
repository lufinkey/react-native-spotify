

# Spotify for React Native

A react native module for the Spotify SDK forked from [lufinkey/react-native-spotify](https://github.com/lufinkey/react-native-spotify)

I added some methods and activate MediaContoller for IOS [Play/Pause] 

<img src="https://s3-eu-west-1.amazonaws.com/sharelist2me/screen-media-player.PNG" width="275">  

### Playback Methods

- **setMediaPlayerInfo**( *songName*, *artist*) [Works only IOS, for android coming soon...]
	- *Parameters*
		
		- **SongName** - to set title title of media player
		- **artist** - to set artist of media player


To activate MediaPlayer controllers on IOS, add this into `AppDelegate.h`
```
...
- (BOOL) canBecomeFirstResponder
{
	return  YES;
}
...
```

then add this into `- (BOOL)application:(UIApplication *)application`  before return  YES;
```
...
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions

{
	NSURL *jsCodeLocation;
	jsCodeLocation = [[RCTBundleURLProvider  sharedSettings] jsBundleURLForBundleRoot:@"index"  fallbackResource:nil];

	[[UIApplication  sharedApplication] beginReceivingRemoteControlEvents]; //add this
	[self  becomeFirstResponder]; //and this

	return  YES;
}
...
```







