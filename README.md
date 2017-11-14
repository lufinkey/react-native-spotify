
# react-native-spotify

This module is still unfinished, so there may be many things that don't work yet

## Install

To add react-native-spotify to your project
```bash
npm install --save https://github.com/lufinkey/react-native-spotify
react-native link react-native-spotify
```

Then do the manual setup for each platform:

#### iOS
Manually add the Frameworks from `node_modules/react-native-spotify/ios/external/SpotifySDK` to Embedded Binaries in your project settings

Ensure you have enabled deep linking for react native in your Objective-C code. You can follow the instructions for that [here](https://facebook.github.io/react-native/docs/linking.html)

#### Android

Edit `android/build.grandle` and add `flatDir`

```gradle
...
allprojects {
	repositories {
		mavenLocal()
		jcenter()
		maven {
			// All of React Native (JS, Obj-C sources, Android binaries) is installed from npm
			url "$rootDir/../node_modules/react-native/android"
		}
		flatDir {
			dirs project(':react-native-spotify').file('libs'), 'libs'
		}
	}
}
...
```

Edit `android/app/build.grandle` and add `packagingOptions`

```
...
buildTypes {
    release {
        minifyEnabled enableProguardInReleaseBuilds
        proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
    }
}
packagingOptions {
    pickFirst 'lib/armeabi-v7a/libgnustl_shared.so'
    pickFirst 'lib/x86/libgnustl_shared.so'
}
...
```



## Usage

```javascript
import Spotify from 'react-native-spotify';
```

### Initialization/Authorization Methods

* **initialize**( *options*, ( *loggedIn*, *error*? ) => {} )

	Initializes the Spotify modules and resumes a logged in session if there is one
	
	* *parameters*
		* **options** - an object with options to pass to the Spotify Module
			* **clientID** (*required*) - Your spotify application's ClientID that you registered with spotify [here](https://developer.spotify.com/my-applications)
			* **redirectURL** (*required*) - The redirect URL to use when you've finished logging in. You need to set this up for your application [here](https://developer.spotify.com/my-applications)
			* **sessionUserDefaultsKey** - The preference key to use to store session data for this module
			* **scopes** - An array of scopes to use in the application. A list of scopes can be found [here](https://developer.spotify.com/web-api/using-scopes/)
			* **tokenSwapURL** - The URL to use to swap an authentication code for an access token
			* **tokenRefreshURL** - The URL to use to get a new access token from a refresh token
  		
		* **loggedIn** - A boolean indicating whether or not a session was automatically logged back in
  	
		* **error** - An error that occurred during initialization, or *null* if no error occurred




* **login**( ( *loggedIn*, *error*? ) => {} )

	Opens a UI to log into spotify
	
	* *parameters*
	
		* **loggedIn** - A boolean indicating whether or not the client was logged in
	
		* **error** - An error that occurred during login, or *null* if no error occurred




* **isLoggedIn**()

	* *returns*
		
		* *true* if the client is logged in
		* *false* if the client is not logged in




* **handleAuthURL**( *url* )

	Handles an authentication URL sent to the app through deep linking. You are *required* to use this function in order for login to work correctly.
	
	* *parameters*
		
		* **url** - A URL that was sent to the app
		
	* *returns*
		
		* *true* if the url passed to it was successfully handled as an authentication URL
		* *false* if the url was not an authentication URL

	* *example implementation*
		```javascript
		App.handleOpenURL = (event) => {
			if(Spotify.handleAuthURL(event.url))
			{
				return true;
			}
			return false;
		}
		Linking.addEventListener('url', App.handleOpenURL);
		```




### Player Methods

* **playURI**( *spotifyURI*, *startIndex*, *startPosition*, ( *error*? ) => {} )

	Play a Spotify URI.
	
	* *parameters*
		
		* **spotifyURI** - The Spotify URI to play
		
		* **startIndex** - The index of an item that should be played first, e.g. 0 - for the very first track in the playlist or a single track
		
		* **startPosition** - starting position for playback in seconds
		
		* **error** - An error object if an error occurred, or null if no error occurred




* **queueURI**( *spotifyURI*, ( *error*? ) => {} )

	Queue a Spotify URI.
	
	* *parameters*
	
		* **spotifyURI** - The Spotify URI to queue
		
		* **error** - An error object if an error occurred, or null if no error occurred




* **setPlaying**( *playing*, ( *error*? ) => {} )

	Set the “playing” status of the player.
	
	* *parameters*
	
		* **playing** - pass *true* to resume playback, or *false* to pause it
		
		* **error** - An error object if an error occurred, or null if no error occurred

* **getPlaybackState**()

	Gives the player's current state.
	
	* *returns*
	
		An object with the current player state. The state properties are:
		
		* **playing** - boolean indicating whether the player is playing
		* **repeating** - boolean indicating whether the player is repeating
		* **shuffling** - boolean indicating whether the player is shuffling
		* **activeDevice** - boolean indicating whether the current device is the one playing
		* **position** - the position of the player in the current track, in seconds




* **skipToNext**( ( *error*? ) => {} )

	Skips to the next track.
	
	* *parameters*
	
		* **error** - An error object if an error occurred, or null if no error occurred




* **skipToPrevious**( ( *error*? ) => {} )

	Skips to the previous track.
	
	* *parameters*
	
		* **error** - An error object if an error occurred, or null if no error occurred




* **setShuffling**( *shuffling*, ( *error*? ) => {] )

	Enables or disables shuffling on the player.
	
	* *parameters*
	
		* **shuffling** - *true* to enable shuffle, *false* to disable it
		
		* **error** - An error object if an error occurred, or null if no error occurred




* **setRepeating**( *repeating*, ( *error*? ) => {} )

	Enables or disables repeating on the player.
	
	* *parameters*
	
		* **repeating** - *true* to enable repeat, *false* to disable it
		
		* **error** - An error object if an error occurred, or null if no error occurred
