-keep @com.parse.ParseClassName class com.parse.*
-keepnames class com.parse.** { *; }
-keepclassmembers public class * extends com.parse.** {
   public <init>(...);
}

# Required for Parse
-keepattributes *Annotation*
-keepattributes Signature

# Retracing stacktraces
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile