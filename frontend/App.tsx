/**
 * What To Eat Mobile Application
 * React Native with Spring Boot Backend
 */

import React, { useEffect } from 'react';
import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import { AuthProvider } from './src/app/providers/AuthContext';
import AppNavigator from './src/app/navigation/AppNavigator';
import ErrorBoundary from './src/components/ErrorBoundary';

function App(): React.JSX.Element {
  useEffect(() => {
    // Load Ionicons font
    Icon.loadFont()
      .then(() => undefined)
      .catch(error => console.error('Error loading Ionicons font:', error));
  }, []);

  return (
    <SafeAreaProvider>
      <AuthProvider>
        <ErrorBoundary>
          <StatusBar barStyle="dark-content" backgroundColor="#FFF8F1" />
          <AppNavigator />
        </ErrorBoundary>
      </AuthProvider>
    </SafeAreaProvider>
  );
}

export default App;
