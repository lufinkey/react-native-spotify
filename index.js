
import { NativeModules } from 'react-native';

var RCTSpotifyManager = NativeModules.RCTSpotifyManager || NativeModules.RCTSpotifyManager;

export default class Spotify
{
	constructor(client_id)
	{
		RCTSpotifyManager.testSpotify();
	}
}
