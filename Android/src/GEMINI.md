# AI Edge Gallery

AI Edge Gallery is a modular Android application designed to showcase and interact with various AI models directly on-device. It leverages modern Google technologies like LiteRT (formerly TensorFlow Lite), AICore, and ML Kit GenAI to provide a seamless edge AI experience.

## Project Overview

*   **Purpose:** A showcase app for on-device AI capabilities, supporting various tasks like LLM Chat, Image/Audio inquiry, and interactive AI demos.
*   **Key Technologies:**
    *   **Language:** Kotlin
    *   **UI Framework:** Jetpack Compose with Material 3
    *   **Dependency Injection:** Hilt
    *   **AI Runtimes:** LiteRT (TFLite), AICore, ML Kit GenAI
    *   **Data Management:** Jetpack DataStore, Protobuf
    *   **Background Tasks:** WorkManager for model downloads

## Architecture

The project follows a modular and extensible architecture:

*   **Custom Tasks:** New AI functionalities are implemented by creating a `CustomTask`. Each task defines its own metadata, model initialization logic, and UI.
    *   **Registration:** Tasks are automatically discovered via Hilt's `@IntoSet` multibinding. You must create a Hilt module and use `@Provides` and `@IntoSet` to register your `CustomTask` implementation.
    *   **Implementation:** See `ExampleCustomTask` for a reference implementation. Tasks should handle their own model lifecycle (`initializeModelFn`, `cleanUpModelFn`) and provide a `MainScreen` composable.
    *   **Existing tasks:** `agentchat`, `mobileactions`, `tinygarden`.
*   **Model Management:** The `ModelManagerViewModel` centralizes the lifecycle of AI models, including downloading from an online allowlist (hosted on GitHub), initialization for specific backends (CPU, GPU, NPU), and cleanup. Models are associated with tasks via the `Task` and `Model` data classes.
*   **Skills System:** An agent-based extensibility mechanism where "skills" are defined using `SKILL.md` files in `assets/skills/`.
    *   **Definition:** Each skill has a `SKILL.md` file containing metadata (name, description), example queries, and instructions for the agent.
    *   **Logic:** Skills can execute custom logic, often through JavaScript running in a WebView (see `assets/skills/*/scripts/index.html`).
    *   **Discovery:** The `SkillsRepository` and `SkillsViewModel` manage the discovery and selection of skills.
*   **Navigation:** Managed by `GalleryNavGraph` using Jetpack Compose Navigation. The `MainActivity` handles deep links and FCM intents to navigate to specific tasks or screens.

## Building and Running

### Prerequisites
*   Android SDK (Target SDK 37, Min SDK 31)
*   Gradle 9.2+

### Key Commands
*   **Build Debug APK:** `./gradlew assembleDebug`
*   **Run Unit Tests:** `./gradlew test`
*   **Run Instrumentation Tests:** `./gradlew connectedAndroidTest`
*   **Lint:** `./gradlew lint`
*   **Clean:** `./gradlew clean`

## Development Conventions

*   **DI:** Always use Hilt for dependency injection. Define new `CustomTask` implementations in a Hilt module using `@IntoSet`.
*   **UI:** Use Jetpack Compose for all new UI components. Follow the established `GalleryTheme`.
*   **Models:** Models are dynamically loaded. For local testing, you can place a `model_allowlist_test.json` in `/data/local/tmp/`.
*   **Protobuf:** Use Protobuf for structured data that needs to be persisted or sent over the wire. Proto files are located in `app/src/main/proto/`.
*   **Formatting:** Follow standard Kotlin coding styles. The project uses KSP and Kapt for code generation.
