-keep class com.iris.assistant.data.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**
