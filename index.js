
import { NativeModules } from 'react-native';

const RNSpotify = NativeModules.RNSpotify || NativeModules.RNSpotify;

export default class Spotify
{
	test()
	{
		console.log("test");
	}
}

