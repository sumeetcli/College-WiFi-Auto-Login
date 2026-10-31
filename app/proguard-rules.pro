# Keep basic cryptographic providers and security
-keepclassmembers class * extends java.security.Provider { *; }
-dontwarn javax.annotation.**
-keepattributes *Annotation*
-keepattributes Signature
