# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "sas.upgrade.imagestoragetest.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Architecture

This is a multi-module Android application demonstrating encrypted image storage using a ContentProvider pattern.

### Module Structure

```
:app              → Main application with Jetpack Compose UI
:imageprovider    → ContentProvider for encrypted image storage (depends on :core)
:core             → Cryptography utilities using Android Keystore
```

### App Architecture - Clean Architecture Pattern

The `:app` module follows Clean Architecture with three distinct layers:

#### 1. UI Layer (Presentation)
- **Responsibility**: Display data and handle user interactions
- **Components**: Composable functions, UI state
- **Rules**:
  - No business logic or data access
  - Depends on domain layer (use cases)
  - Can be easily replaced (e.g., Compose → XML views)

#### 2. Domain Layer (Business Logic)
- **Responsibility**: Business rules, use cases, and orchestration
- **Components**: Use case interfaces and implementations
- **Rules**:
  - Platform-independent (pure Kotlin)
  - Contains business logic (e.g., formatting rules, data composition)
  - Orchestrates repository operations
  - No Android framework dependencies (except when absolutely necessary)

#### 3. Data Layer (Repository)
- **Responsibility**: Data access and basic transformations
- **Components**: Repository interfaces and implementations
- **Rules**:
  - Handles ContentProvider queries, inserts, deletes
  - Basic data formatting
  - No business logic
  - Abstracts data sources from domain layer

**Dependency Flow**: UI → Domain → Data (unidirectional)

#### Core Module

**core/CryptoManager** - AES-256-GCM encryption using Android Keystore. Handles both file-based and byte-array encryption/decryption. IV is prepended to encrypted data.

#### ImageProvider Module

**imageprovider/EncryptedImageProvider** - Custom ContentProvider that:
- Stores image metadata in SQLite (`images.db`)
- Encrypts image files at rest in app's private storage
- Uses temp files for encryption/decryption during read/write operations
- Small files (≤256KB) use memory-based decryption, larger files use file streams

**imageprovider/ImageContract** - Contract class defining the ContentProvider's authority (`sas.upgrade.imageprovider`), URI paths, and column names.

#### App Module Components

**UI Layer** (`app/ui/screen/`):
- **MainScreen** - Compose UI that displays images, handles user interactions
- Injects and delegates to domain layer (use case)
- Pure presentation, no business logic

**Domain Layer** (`app/ui/screen/`):
- **MainScreenUseCase** - Interface defining business operations
- **MainScreenUseCaseImpl** - Business logic implementation:
  - Orchestrates repository operations
  - Formats image display information (size + date composition)
  - Applies business rules

**Data Layer** (`app/ui/screen/`):
- **MainScreenRepository** - Interface for data operations
- **MainScreenRepositoryImpl** - Data access implementation:
  - Queries images from ContentProvider
  - Saves/deletes images via ContentResolver
  - Basic data formatting (size, date)

### Data Flow

#### Image Storage Flow
1. User picks image → UI calls `useCase.saveImage()`
2. Use case delegates to `repository.saveImage()`
3. Repository calls `contentResolver.insert()` with metadata → Provider stores in SQLite
4. Repository calls `contentResolver.openOutputStream()` → Provider creates temp file, encrypts on close

#### Image Display Flow
1. UI calls `useCase.loadImages()` → Use case delegates to repository
2. Repository queries Provider → returns list of `ImageItem`
3. UI calls `useCase.formatImageDisplayInfo(image)` → Use case composes display string
4. User selects image → Provider decrypts to temp file → returns ParcelFileDescriptor for reading
5. Coil loads image from ContentProvider URI

#### Architecture Benefits
- **Testability**: Each layer can be mocked independently (UI can test with fake use case)
- **Separation of Concerns**: UI, business logic, and data access are isolated
- **Maintainability**: Changes in one layer don't affect others (e.g., change display format in use case only)
- **Flexibility**: Easy to swap implementations (e.g., switch from ContentProvider to Room database)

### Tech Stack

- Kotlin 2.0, Jetpack Compose, Material 3
- Coil for image loading
- Android Keystore for key management
- minSdk 29, targetSdk 35
