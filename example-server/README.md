# Example Token Refresh Server

An example server capable of swapping and refreshing tokens provided by Spotify API.

## Usage

1. Install dependencies using: `npm install`
2. Set the following environment variables:

* `SPOTIFY_CLIENT_ID` - your spotify app id
* `SPOTIFY_CLIENT_SECRET` - your spotify app secret
* `SPOTIFY_CLIENT_CALLBACK` - your spotify app redirect URL
* `ENCRYPTION_SECRET` - any arbitrary string to use to encrypt/decrypt refresh tokens
* `PORT` - (*optional*) The port to run the server on. Default is 3000

3. Run server using: `npm start`
4. In your react-native app set `tokenSwapURL` to `http://<SERVER_URL>:<PORT>/swap` and `tokenRefreshURL` to `http://<SERVER_URL>:<PORT>/refresh`, replacing `<SERVER_URL>` and `<PORT>` with your server URL and port.
