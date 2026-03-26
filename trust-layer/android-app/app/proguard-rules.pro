# Add project specific ProGuard rules here.

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.** { *; }

# Keep Nimbus JWT
-keep class com.nimbusds.** { *; }
-dontwarn com.nimbusds.**

# Keep Trust Layer models
-keep class et.trustlayer.android.di.** { *; }
