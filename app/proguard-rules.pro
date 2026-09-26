# PDFBox-Android and its crypto stack resolve fonts, COS objects and security
# providers reflectively; keep them whole until a device pass justifies
# tighter rules. App code is still shrunk and optimized — CI builds
# assembleRelease on every PR, but only a sideloaded run proves the rules
# suffice.
-keep class com.tom_roush.** { *; }
-keep class org.spongycastle.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.spongycastle.**
-dontwarn org.bouncycastle.**
