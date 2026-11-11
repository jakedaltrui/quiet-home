#!/bin/sh
# Local development build; never use this key for production releases.
set -eu
cd "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to your Android SDK directory}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 directory}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.1}"
PLATFORM_VERSION="${PLATFORM_VERSION:-android-35}"
TOOLS="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/$PLATFORM_VERSION/android.jar"
for tool in aapt2 aapt d8 zipalign apksigner; do
  [ -x "$TOOLS/$tool" ] || { echo "Missing SDK tool: $TOOLS/$tool" >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "Missing SDK platform: $ANDROID_JAR" >&2; exit 1; }
[ -x "$JAVA_HOME/bin/javac" ] || { echo "JAVA_HOME must point to a JDK" >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build
mkdir -p build/gen build/classes build/dex build/apk dist .local
"$TOOLS/aapt2" compile --dir res -o build/resources.zip
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
  --java build/gen --min-sdk-version 28 --target-sdk-version 30 \
  --version-code 1 --version-name 1.0.0 -o build/apk/unsigned.apk build/resources.zip
"$JAVA_HOME/bin/javac" -source 8 -target 8 -classpath "$ANDROID_JAR" \
  -d build/classes build/gen/com/jakedaltrui/quiethome/R.java \
  src/com/jakedaltrui/quiethome/*.java
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output build/dex build/classes.jar
cp build/apk/unsigned.apk build/apk/with-dex.apk
(cd build/dex && "$TOOLS/aapt" add ../apk/with-dex.apk classes.dex)
# Conventional debug credentials, not a production signing identity.
if [ ! -f .local/debug.keystore ]; then
  (umask 077; "$JAVA_HOME/bin/keytool" -genkeypair \
    -keystore .local/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US")
fi
"$TOOLS/zipalign" -f 4 build/apk/with-dex.apk build/apk/aligned.apk
"$TOOLS/apksigner" sign --ks .local/debug.keystore --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out dist/quiet-home.apk build/apk/aligned.apk
"$TOOLS/apksigner" verify --verbose dist/quiet-home.apk
echo "Built dist/quiet-home.apk (local development signature)"
