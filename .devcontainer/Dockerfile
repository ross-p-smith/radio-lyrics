# Base: Microsoft devcontainers Java 21 on Debian Bookworm
FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

ARG ANDROID_HOME=/opt/android-sdk
ARG ANDROID_API=35
ARG ANDROID_BUILD_TOOLS=35.0.0
# Pinned cmdline-tools build; bump explicitly when Google rotates the URL.
ARG CMDLINE_TOOLS_VERSION=11076708

ENV ANDROID_HOME=${ANDROID_HOME} \
    ANDROID_SDK_ROOT=${ANDROID_HOME} \
    PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}

USER root

# System packages: adb deps, unzip, libs commonly needed by Android tooling.
# The base image ships a stale Yarn apt source whose GPG key has expired; drop it
# before `apt-get update` so the install isn't blocked by the unsigned-repo error.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        wget \
        curl \
        libglu1-mesa \
        libpulse0 \
        libxcomposite1 \
        libxcursor1 \
        libxi6 \
        libxtst6 \
        usbutils \
        android-tools-adb \
    && rm -rf /var/lib/apt/lists/*

# Install Android command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && cd /tmp \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip -O cmdline-tools.zip \
    && unzip -q cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm cmdline-tools.zip


# Accept licenses + install platform/build-tools/platform-tools + required NDK
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-${ANDROID_API}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
        "ndk;27.0.12077973"

# Permissions for the non-root vscode user
RUN chown -R vscode:vscode ${ANDROID_HOME}

# Prepare mounted volume directories and set permissions
RUN mkdir -p /home/vscode/.gradle/wrapper /home/vscode/.android/cache \
    && chown -R vscode:vscode /home/vscode/.gradle /home/vscode/.android

USER vscode

# Install Gradle via SDKMAN (provided by Java feature) — optional; gradlew is preferred
RUN bash -lc "sdk install gradle 8.10.2 && sdk default gradle 8.10.2" || true
