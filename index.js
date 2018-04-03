import {
  DeviceEventEmitter,
  Platform,
  NativeEventEmitter,
  NativeModules
} from 'react-native';

const Spotify = NativeModules.Spotify;

const eventEmitter = Platform.select({
  ios: new NativeEventEmitter(Spotify),
  android: DeviceEventEmitter
});

Spotify.events = eventEmitter;

export default Spotify;
