-keep @com.parse.ParseClassName class com.parse.*
-keepnames class com.parse.** { *; }
-keepclassmembers public class * extends com.parse.** {
   public <init>(...);
}

# Required for Parse
-keepattributes *Annotation*
-keepattributes Signature

# https://github.com/square/okio#proguard
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
