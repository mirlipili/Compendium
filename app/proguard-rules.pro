# Required for Gson TypeToken generic type resolution
-keepattributes Signature
-keepattributes *Annotation*

# Gson model classes — field names must match JSON keys, so they cannot be renamed
-keep class vet.derichs.compendium.data.model.Medication { *; }
-keep class vet.derichs.compendium.data.network.VersionInfo { *; }
-keep class vet.derichs.compendium.data.network.LanguageData { *; }

# WorkManager — worker class name is looked up by string at runtime
-keep class vet.derichs.compendium.worker.UpdateWorker { *; }

# Keep stack traces readable in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
