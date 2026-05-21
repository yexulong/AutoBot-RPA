#!/bin/bash

# AutoBot RPA Build Script
# Requirements:
# - Java 17 or 21
# - Android SDK with API 34
# - Network access to Google Maven & Maven Central

echo "AutoBot RPA Build Script"
echo "========================="

# Check for Java
if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install Java 17 or 21."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java version: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Warning: Java version < 17. This project requires Java 17 or higher."
fi

# Set Android SDK path
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/opt/android-sdk" ]; then
        export ANDROID_HOME="/opt/android-sdk"
    else
        echo "Warning: ANDROID_HOME not set. Please set it to your Android SDK path."
    fi
fi

if [ -n "$ANDROID_HOME" ]; then
    echo "Android SDK: $ANDROID_HOME"
fi

# Set Java home if needed
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/lib/jvm/java-17" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17"
    elif [ -d "/usr/lib/jvm/java-21" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21"
    fi
fi

if [ -n "$JAVA_HOME" ]; then
    echo "JAVA_HOME: $JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Clean and build
echo ""
echo "Building debug APK..."
echo ""

./gradlew clean assembleDebug --no-daemon

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    
    # Show APK info
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo ""
        echo "APK Info:"
        ls -lh app/build/outputs/apk/debug/app-debug.apk
    fi
else
    echo ""
    echo "Build failed. Please check the error messages above."
    echo ""
    echo "Common issues:"
    echo "1. Network connectivity to Maven repositories"
    echo "2. Java version compatibility"
    echo "3. Android SDK installation"
    echo ""
    echo "Try running with --info flag for more details:"
    echo "./gradlew assembleDebug --info"
fi
