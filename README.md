# Russvy
Integration components for using [Rossvyaz Open Data](http://www.rossvyaz.ru/opendata "Rossvyaz Open Data (rus)") in Android application.
Minimum supported Android SDK version is 9 (Android 2.3+). Right now Russvy is simple enough and does not have any external dependencies.

## Repository contents
- 'assets': minified data ready to be plugged into Android application
- 'data': raw CSV data from Rossvyaz site
- 'demo': sample Android application that uses Russvy
- 'library': source code wrapped in Android Studio module
- 'project': Gradle project

## Adding Russvy to Android Studio project
1. Checkout the repository
2. Copy files from 'assets' to Android Studio project's assets
3. Do module import from 'library' directory

For other build environments, read the 'Repository contents' section of this file.

## Generating library assets
1. In terminal, go to data/converter
2. Run 'gradle update'

Or import Gradle project from 'project' to Android Studio and execute the 'update' task in 'russvy' group.