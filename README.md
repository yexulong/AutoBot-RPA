# AutoBot RPA - Android Automation Application

## Project Summary

AutoBot RPA is a complete Android automation application similar to "AnJian JingLing" (按键精灵), built with modern Android development practices.

## Project Structure

```
AutoBotRPA/
├── app/
│   ├── src/main/
│   │   ├── java/com/autobot/rpa/
│   │   │   ├── AutoBotApplication.kt          # Hilt Application class
│   │   │   ├── MainActivity.kt                # Main activity
│   │   │   ├── data/
│   │   │   │   ├── database/
│   │   │   │   │   ├── AutoBotDatabase.kt    # Room database
│   │   │   │   │   └── ScriptDao.kt         # Script DAO
│   │   │   │   ├── model/
│   │   │   │   │   └── Script.kt             # Data models for scripts & actions
│   │   │   │   └── repository/
│   │   │   │       └── ScriptRepository.kt   # Repository pattern
│   │   │   ├── di/
│   │   │   │   └── DatabaseModule.kt         # Hilt DI module
│   │   │   ├── service/
│   │   │   │   ├── AutoBotAccessibilityService.kt  # Accessibility service for gestures
│   │   │   │   ├── AutoBotForegroundService.kt      # Foreground service
│   │   │   │   └── AutomationEngine.kt               # Core automation engine
│   │   │   └── ui/
│   │   │       ├── AutoBotApp.kt             # Main app navigation
│   │   │       ├── theme/                    # Material 3 theming
│   │   │       │   ├── Color.kt
│   │   │       │   ├── Theme.kt
│   │   │       │   └── Type.kt
│   │   │       ├── screens/
│   │   │       │   ├── ScriptListScreen.kt   # Script management
│   │   │       │   ├── ScriptEditorScreen.kt  # Script editing
│   │   │       │   ├── ScriptExecutionScreen.kt # Script execution & logs
│   │   │       │   ├── SettingsScreen.kt      # App settings
│   │   │       │   └── ViewModels            # MVVM ViewModels
│   │   │       └── components/
│   │   │           └── ActionItemCard.kt     # Reusable action cards
│   │   ├── res/
│   │   │   ├── values/                       # Resources (strings, colors, themes)
│   │   │   ├── xml/
│   │   │   │   └── accessibility_service_config.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle                              # Root build configuration
├── settings.gradle                           # Project settings
├── gradle.properties                         # Gradle properties
└── SPEC.md                                   # Project specification

## Key Features Implemented

### 1. Core RPA Actions
- **Touch Operations**: Tap, Swipe, Long Press
- **Text Input**: Input text into fields
- **Key Press**: Simulate physical buttons
- **Delay**: Wait for specified duration

### 2. Image Recognition (Stubs)
- **Screenshot**: Capture current screen
- **Find Image**: Template matching (basic implementation)

### 3. Flow Control
- **Loop**: Repeat actions (N times or infinite)
- **Condition**: Branch based on image/color presence
- **Comment**: Add notes to scripts

### 4. Script Management
- Create, edit, delete scripts
- Organize actions in scripts
- Reorder actions via drag-and-drop
- Save/load scripts with Room database

### 5. Execution Engine
- Real-time execution control (Run/Pause/Stop)
- Live logging with timestamps
- Execution statistics
- Foreground service for background operation

### 6. Accessibility Service
- Perform gestures using Android AccessibilityService
- Tap at coordinates
- Swipe gestures
- Long press support

## Technology Stack

- **Language**: Kotlin 2.0.0
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM with Clean Architecture
- **DI**: Hilt 2.51
- **Database**: Room 2.6.1
- **Navigation**: Jetpack Navigation Compose
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build Tool**: Gradle 8.5 with Android Gradle Plugin 8.5.0

## Building the Project

### Prerequisites
1. Install Java 17 or 21
2. Install Android SDK (API 34)
3. Ensure network access to Google Maven and Maven Central

### Build Commands

```bash
# Set environment variables
export ANDROID_HOME=/path/to/android/sdk
export JAVA_HOME=/path/to/java17-or-21

# Build debug APK
./gradlew assembleDebug

# Or using system Gradle
gradle assembleDebug

# Clean and rebuild
./gradlew clean assembleDebug
```

### Build Output
The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Application Permissions

The app requires the following permissions:
- `BIND_ACCESSIBILITY_SERVICE` - For automating touches and gestures
- `FOREGROUND_SERVICE` - For running automation in background
- `POST_NOTIFICATIONS` - For notification when automation is running
- `SYSTEM_ALERT_WINDOW` - For overlay features

## Usage Instructions

### 1. First Launch
1. Install the APK on your Android device
2. Grant Accessibility Service permission when prompted
3. Grant other required permissions in Settings

### 2. Creating a Script
1. Tap the "+" button on the Scripts screen
2. Enter a name for your script
3. Tap "Add Action" to add actions
4. Choose action type and configure parameters
5. Save the script

### 3. Running a Script
1. Go to the Execute tab
2. Select your script
3. Tap "Run" to start execution
4. Use "Pause" to pause, "Stop" to stop
5. View logs in the log panel

### 4. Example Script Flow
```
1. Delay: 2000ms (Wait for app to load)
2. Tap: (500, 500) (Tap button)
3. Delay: 1000ms (Wait for response)
4. Find Image: button.png (Wait for image)
5. If found:
   - Tap: (500, 500)
6. Loop: 10 times
   - Tap: (300, 800)
   - Delay: 500ms
7. End Loop
```

## Architecture Details

### MVVM Pattern
- **Model**: Script data class with actions list
- **View**: Jetpack Compose screens
- **ViewModel**: State management with StateFlow

### Clean Architecture Layers
1. **UI Layer**: Compose screens and components
2. **Domain Layer**: Business logic (AutomationEngine)
3. **Data Layer**: Room database and repositories

### State Management
- ViewModels with `StateFlow` for reactive UI
- Sealed classes for execution states
- Immutable data models

## Known Limitations

1. **Image Recognition**: Basic template matching implemented, may need OpenCV for advanced features
2. **Accessibility Service**: Requires explicit user permission
3. **Background Execution**: Requires foreground service notification

## Future Enhancements

1. Advanced image recognition with OpenCV
2. OCR text recognition
3. Variable system for storing data
4. Import/export scripts
5. Cloud script sharing
6. Recording mode (record user actions)
7. Loop nesting and complex conditions

## Troubleshooting

### Accessibility Service Not Working
1. Go to Settings > Accessibility
2. Find AutoBot RPA
3. Enable the service
4. Grant all permissions

### Script Not Executing
1. Check if accessibility service is enabled
2. Ensure foreground service notification is shown
3. Check logs for errors
4. Verify all actions have valid parameters

### Build Issues
1. Ensure Java 17/21 is installed
2. Verify ANDROID_HOME is set
3. Check network connectivity to Google Maven
4. Clean Gradle cache: `./gradlew clean`

## License

This project is for educational and personal use. Use responsibly and in accordance with app store policies.

## Contact & Support

For issues or feature requests, please refer to the project repository.
