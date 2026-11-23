# R8 rules for the release build.
#
# Shrink and optimize, but do not rename.  Names stay as they are in the
# source, so that:
#  - stack traces in bug reports can be read without a mapping file, and
#    nobody needs one to compare the APK with the source;
#  - Orchid's loggers, which are named after their classes
#    (Class#getSimpleName), keep their names;
#  - the class-relative resources that Orchid and Rakkyo read
#    (getResourceAsStream: the geoip file, the version properties, the
#    certificate pins) are found where they are packaged.
-dontobfuscate

# Line numbers in stack traces.
-keepattributes SourceFile,LineNumberTable

# Neither Orchid nor Rakkyo uses reflection, and the Android components are
# kept by the rules that aapt writes from the manifest and the layouts, so
# no other rule is needed.  If a release build misbehaves where the debug
# build does not, look here first.
