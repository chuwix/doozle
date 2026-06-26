{
  description = "Doozle - Photo organization by comparison";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" "34" ];
          buildToolsVersions = [ "35.0.0" "34.0.0" ];
          includeNDK = false;
          includeEmulator = false;
        };

        androidSdk = androidComposition.androidsdk;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            androidSdk
            android-tools
            gradle
            jdk17
            kotlin
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          JAVA_HOME = "${pkgs.jdk17}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";

          shellHook = ''
            echo "Doozle dev environment"
            echo "  Android SDK: $ANDROID_HOME"
            echo "  Java: $(java --version 2>&1 | head -1)"
            echo "  Gradle: $(gradle --version 2>&1 | grep '^Gradle' || echo 'available')"
          '';
        };
      });
}
