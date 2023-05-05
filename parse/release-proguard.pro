-keepnames class com.parse.** { *; }
-keep @com.parse.ParseClassName class com.parse.*

# Required for Parse
-keepattributes *Annotation*
-keepattributes Signature

# https://github.com/square/okio#proguard
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
