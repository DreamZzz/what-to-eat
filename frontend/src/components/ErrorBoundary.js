import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true };
  }

  componentDidCatch(error, errorInfo) {
    this.setState({
      error: error,
      errorInfo: errorInfo
    });
    
    // Log error to console for debugging
    console.error('ErrorBoundary caught an error:', error, errorInfo);
    
    // In a production app, you would log to an error reporting service
    // logErrorToService(error, errorInfo);
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null
    });
    
    // You might want to trigger a navigation reset here
    // if the error requires resetting the app state
    if (this.props.onReset) {
      this.props.onReset();
    }
  };

  render() {
    if (this.state.hasError) {
      // Error UI
      if (__DEV__) {
        // Development mode: show detailed error
        return (
          <View style={styles.container}>
            <Text style={styles.title}>应用遇到错误</Text>
            <Text style={styles.errorText}>{this.state.error?.toString()}</Text>
            <Text style={styles.infoText}>
              组件堆栈: {this.state.errorInfo?.componentStack}
            </Text>
            <TouchableOpacity style={styles.button} onPress={this.handleReset}>
              <Text style={styles.buttonText}>重试</Text>
            </TouchableOpacity>
            <TouchableOpacity 
              style={[styles.button, styles.secondaryButton]} 
              onPress={() => console.log(this.state.errorInfo)}
            >
              <Text style={[styles.buttonText, styles.secondaryButtonText]}>查看详情</Text>
            </TouchableOpacity>
          </View>
        );
      } else {
        // Production mode: user-friendly error screen
        return (
          <View style={styles.container}>
            <Text style={styles.title}>抱歉，应用遇到问题</Text>
            <Text style={styles.message}>
              我们正在修复这个问题。请稍后重试。
            </Text>
            <TouchableOpacity style={styles.button} onPress={this.handleReset}>
              <Text style={styles.buttonText}>重试</Text>
            </TouchableOpacity>
          </View>
        );
      }
    }

    // Normal render
    return this.props.children;
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#F8F9FA',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212529',
    marginBottom: 16,
    textAlign: 'center',
  },
  errorText: {
    fontSize: 14,
    color: '#DC3545',
    marginBottom: 12,
    textAlign: 'center',
    fontFamily: 'monospace',
  },
  infoText: {
    fontSize: 12,
    color: '#6C757D',
    marginBottom: 20,
    textAlign: 'center',
    fontFamily: 'monospace',
  },
  message: {
    fontSize: 16,
    color: '#495057',
    marginBottom: 24,
    textAlign: 'center',
    lineHeight: 22,
  },
  button: {
    backgroundColor: '#6C8EBF',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    marginBottom: 12,
    minWidth: 120,
    alignItems: 'center',
  },
  secondaryButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#6C8EBF',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButtonText: {
    color: '#6C8EBF',
  },
});

export default ErrorBoundary;