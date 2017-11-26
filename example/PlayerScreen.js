
import React, { Component } from 'react';
import {
	StyleSheet,
	Text,
	View
} from 'react-native';

export class PlayerScreen extends Component
{
	static navigationOptions = {
		title: 'Player',
	};

	constructor()
	{
		super();
	}

	render()
	{
		return (
			<View style={styles.container}>
				<Text style={styles.greeting}>
					This is the player screen
				</Text>
			</View>
		);
	}
}

const styles = StyleSheet.create({
	container: {
		flex: 1,
		justifyContent: 'center',
		alignItems: 'center',
		backgroundColor: '#F5FCFF',
	},
	greeting: {
		fontSize: 20,
		textAlign: 'center',
		margin: 10,
	},
});
