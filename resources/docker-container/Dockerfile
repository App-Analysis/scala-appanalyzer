# syntax=docker/dockerfile:1.3
# Basis-Image
FROM ubuntu:22.04

# Set Env Vars to not be asked after"interactive mode"
ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Update system and install apt pakets
RUN apt-get update && apt-get install -y \
    sudo \
    libslirp0 \
    libsqlite3-dev \
    libssl-dev \
    linux-generic-hwe-22.04 \
    login \
    m17n-db \
    mitmproxy \
    ncurses-base \
    ncurses-bin \
    neovim \
    net-tools \
    nfs-common \
    openjdk-17-jdk \
    openssh-server \
    plocate \
    postgresql \
    postgresql-contrib \
    python3.11-venv \
    python3-pip \
    qemu-user \
    qemu-user-static \
    rpcbind \
    scala \
    shim-signed \
    slirp4netns \
    tk-dev \
    tmux \
    lib32stdc++6 \
    lib32z1 \
    qemu-kvm \
    libvirt-clients \
    libvirt-daemon-system \
    x11-apps \
    ubuntu-desktop-minimal \
    ubuntu-minimal \
    ubuntu-restricted-addons \
    ubuntu-standard \
    wbritish \
    zlib1g-dev \
    curl \
    git \
    wget \
    unzip \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install sbt
RUN apt-get update && apt-get install -y apt-transport-https curl gnupg && \
    echo "deb [signed-by=/usr/share/keyrings/sbt-release-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb [signed-by=/usr/share/keyrings/sbt-release-keyring.gpg] https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    wget -qO - "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x99E82A75642AC823" | gpg --dearmor -o /usr/share/keyrings/sbt-release-keyring.gpg && \
    apt-get update && \
    apt-get install -y sbt

RUN apt-get update && apt-get install -y \
    python3.11-venv \
    python3-venv
    
# Create user and grant sudo permissions
RUN useradd -m -d /home/ias -s /bin/bash -G sudo ias && \
    echo "ias ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Set Android SDK-Env vars
ENV ANDROID_SDK_ROOT="/home/ias/Android/Sdk"
ENV ANDROID_HOME="/home/ias/Android/Sdk"
ENV PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

RUN apt-get update && apt-get install -y \
    libpq-dev

# Install Node.js (Version 16) and npm (Version 8)
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash - && \
    apt-get install -y nodejs && \
    npm install -g npm@8
    
# Install Frida and Appium
RUN npm install -g frida

# Install `atob` (Base64 Decoder/Encoder)
RUN npm install -g atob

# Install Appium and uiautomator2
RUN npm install -g appium

# Copy requirements.txt into Image (so it's available every time)
COPY requirements.txt /home/ias/requirements.txt

# Install all python requirements
RUN python3 -m venv /venv && \
    /venv/bin/pip install --upgrade pip && \
    /venv/bin/pip install --no-cache-dir -r /home/ias/requirements.txt

# Set virt env as standard
ENV PATH="/venv/bin:$PATH"

# Install libimobiledevice (for iOS-Communication)
RUN apt-get update && apt-get install -y libimobiledevice-utils && apt-get clean

# Clone scala-appanalyzer repository
RUN mkdir -p /root/.ssh && ssh-keyscan github.com >> /root/.ssh/known_hosts
RUN --mount=type=ssh git clone git@github.com:App-Analysis/scala-appanalyzer.git /home/ias/scala-appanalyzer

# Change user
USER ias
WORKDIR /home/ias

# Install Appium and uiautomator2
RUN appium driver install uiautomator2

# Define the Standard Command for the container
CMD ["bash"]
