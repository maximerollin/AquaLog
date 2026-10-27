# AquaLog release ProGuard/R8 rules.
#
# Room, RevenueCat, PostHog, Firebase Crashlytics, WorkManager and the Play
# In-App Review client all ship their own consumer rules, so this file only
# covers what is specific to this app.

# Keep source file and line numbers so release crash stack traces stay
# readable once de-obfuscated with the mapping.txt uploaded alongside the
# bundle on each Play Store release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---------------------------------------------------
# The portable import/export models in :shared are serialized through their
# generated $$serializer. kotlinx.serialization ships consumer rules, but we
# keep the serializers of our own package explicitly as a safety net.
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.maximerollin.aqualog.shared.**$$serializer { *; }
-keepclassmembers class com.maximerollin.aqualog.shared.** {
    *** Companion;
}
-keepclasseswithmembers class com.maximerollin.aqualog.shared.** {
    kotlinx.serialization.KSerializer serializer(...);
}
