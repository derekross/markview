# Navigation type-safe routes are serialized with kotlinx.serialization.
-keepclassmembers @kotlinx.serialization.Serializable class com.derekross.markview.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# commonmark-java loads nothing reflectively, but keep extension node classes used via instanceof.
-keep class org.commonmark.ext.** { *; }
