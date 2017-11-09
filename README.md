
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

* **initialize**( *options*, ( *loggedIn*, *error*? ) => {} )

	Initializes the Spotify modules and resumes a logged in session if there is one
	
	* *parameters*
		* **options** - an object with options to pass to the Spotify Module
			* **clientID** (*required*) - Your spotify application's ClientID that you registered with spotify [here](https://developer.spotify.com/my-applications)
			* **redirectURL** (*required*) - The redirect URL to use when you've finished logging in. You need to set this up for your application [here](https://developer.spotify.com/my-applications)
			* **sessionUserDefaultsKey** - The preference key to use to store session data for this module
  		
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

Example implementation:	
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
