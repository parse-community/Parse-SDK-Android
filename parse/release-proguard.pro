-keepnames class com.parse.** { *; }

# Required for Parse
-keepattributes *Annotation*
-keepattributes Signature
# https://github.com/square/okio#proguard
-dontwarn okio.**
