# AI Edge Gallery (Android)

AI Edge Gallery is a modular and extensible Android application designed to showcase and interact with various AI models directly on-device. It provides a platform for experiencing cutting-edge edge AI capabilities across multiple domains, including language, vision, and audio.

## Key Features

*   **On-Device AI Showcase:** Explore a wide range of AI tasks running entirely on your Android device.
*   **Modular Architecture:** Easily extend the app with new AI functionalities via the `CustomTask` system.
*   **Dynamic Model Management:** Automatically download and manage the lifecycle of AI models from a remote allowlist.
*   **Agent-Based Skills:** Extend AI agent capabilities through a flexible "Skills" system using `SKILL.md` and JavaScript.
*   **Modern Android Stack:** Built with Kotlin, Jetpack Compose, Hilt, and WorkManager.

## Stock Analyzer App Flow

The Stock Analyzer is an experimental feature that provides on-device AI analysis of stock portfolios.

1.  **Setup:** Navigate to **Settings** -> **Experimental** -> **Stock Analyzer Settings**. Enable the **Timer Worker** and **Debug Mode** (if needed to bypass market hours).
2.  **Credentials:** From the **Home** screen, enter **Stock Analyzer**. Manage your Alpaca API credentials here.
3.  **Account Detail:** Select an account to view its real-time equity, buying power, and portfolio status.
4.  **Watchlist:** Use the **FAB (List Icon)** on the account detail screen to manage a per-account watchlist of stock symbols.
5.  **AI Analysis:** The `TimerWorker` runs in the background (every 15 minutes) to fetch account data and watchlist status, using on-device models (e.g., Gemma 4) to generate summaries and insights saved to the **Log Entries** screen.

## Technologies Used

*   **AI Runtimes:**
    *   **LiteRT (formerly TensorFlow Lite):** For high-performance on-device machine learning.
    *   **AICore:** Leveraging system-level AI capabilities on supported devices.
    *   **ML Kit GenAI:** For advanced generative AI tasks.
*   **UI Framework:** Jetpack Compose with Material 3 design.
*   **Dependency Injection:** Hilt.
*   **Data Management:** Jetpack DataStore and Protocol Buffers (Protobuf).
*   **Background Tasks:** WorkManager for reliable model downloads and updates.

## Getting Started

### Prerequisites

*   Android Studio Ladybug or newer.
*   Android SDK (Target SDK 37, Min SDK 31).
*   Gradle 9.2+.

### Building the Project

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Build and run the app on a compatible Android device or emulator.

Alternatively, use the command line:
```bash
./gradlew assembleDebug
```

## Extending the Gallery

AI Edge Gallery is designed to be easily extensible. You can add new AI functionalities by implementing a `CustomTask`.

### Adding a Custom Task

1.  **Implement `CustomTask`:** Create a new class that implements the `CustomTask` interface.
2.  **Define Metadata:** Specify task details (label, description, models) in the `task` property.
3.  **Handle Model Lifecycle:** Implement `initializeModelFn` and `cleanUpModelFn`.
4.  **Create UI:** Implement the `MainScreen` composable using Jetpack Compose.
5.  **Register via Hilt:** Use `@IntoSet` in a Hilt module to make your task discoverable.

For a detailed walkthrough, refer to the `ExampleCustomTask` implementation in `app/src/main/java/com/google/ai/edge/gallery/customtasks/examplecustomtask/`.

## Contributing

Contributions are welcome! Please follow the established coding standards and ensure all tests pass before submitting a pull request.

## License

Copyright 2025 Google LLC. Licensed under the Apache License, Version 2.0. See the `LICENSE` file (if available) or individual source files for details.
