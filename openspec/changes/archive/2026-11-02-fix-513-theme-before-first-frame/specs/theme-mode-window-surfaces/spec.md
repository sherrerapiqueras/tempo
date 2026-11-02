# Spec Delta

## Purpose
Defines what the system draws around the app's own content, the splash screen and the window background, so that it follows the theme chosen in the app rather than the system's.

## ADDED Requirements

### Requirement: The splash follows the app's theme
On Android 12 and up, the splash screen SHALL use the theme mode chosen in the app, from the launch after that mode was first applied. With the mode set to follow the system, it SHALL follow the system.

#### Scenario: Dark app on a light system
- **WHEN** the app's theme is Dark, the system theme is light, and the user starts the app from cold on Android 12 or later
- **THEN** the splash has the dark background, and no white frame appears before the app

#### Scenario: Back to following the system
- **WHEN** the user sets the theme to System on a light system and starts the app from cold
- **THEN** the splash has the light background

### Requirement: The window background follows the app's theme
The window background SHALL match the current theme's surface at every API level, so that area not yet drawn by the app never shows the other theme's colour.

#### Scenario: Growing a desktop window
- **WHEN** the app's theme is Dark, the system theme is light, and the user makes a desktop window bigger
- **THEN** no white frame appears in the newly exposed area

### Requirement: Changing the theme doesn't restart the screen
Changing the theme in Settings, or the system theme, SHALL be applied in place, without recreating the activity.

#### Scenario: Change in Settings
- **WHEN** the user changes the theme in Settings
- **THEN** the colours change and Settings stays open where it was
