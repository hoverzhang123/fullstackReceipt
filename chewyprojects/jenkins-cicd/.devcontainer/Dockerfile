FROM mcr.microsoft.com/devcontainers/java:21 AS base
# Docs ^: https://hub.docker.com/r/microsoft/devcontainers-java

# The Chewy secops team has SSL interception so we need to trust
# certs from umbrella.
COPY ./.devcontainer/certs/. /usr/local/share/ca-certificates/
RUN update-ca-certificates

ENV DOCKER_BUILDKIT=1
# =================================================================================================
# Install all the needed stuff with apt-get
RUN apt-get update -y && \
    apt-get install -y --no-install-recommends \
    # Needed for aws sso login (to open a browser on host)
    xdg-utils \
    # Handy to have available
    curl \
    # Utilities (Ex: `ping`)
    iputils-ping \
    # CA certificates
    ca-certificates \
    # Add Docker for Docker in Docker Compatibility
    docker.io \
    # Git and SSH
    git \
    git-man \
    man \
    openssh-client \
    less

# =================================================================================================
# Download public key for github.com
RUN mkdir -p -m 0600 ~/.ssh && ssh-keyscan github.com >> ~/.ssh/known_hosts

# =================================================================================================
# Create directories for volume mounts and let vscode own them
RUN mkdir -p /home/vscode/.aws \
    && chown vscode:vscode /home/vscode/.aws \
    && mkdir -p /home/vscode/.gradle \
    && chown vscode:vscode /home/vscode/.gradle \
    && mkdir -p /workspace/build \
    && chown vscode:vscode /workspace/build \
    && mkdir -p /workspace/.gradle \
    && chown vscode:vscode /workspace/.gradle

# =================================================================================================
# Install the CLIs for the container
RUN mkdir -p /tmp-installs
WORKDIR /tmp-installs

COPY ./.devcontainer/install-aws-cli.sh /tmp-installs/install-aws-cli.sh

RUN apkArch="$(dpkg --print-architecture)"; \
    echo $apkArch; \
    case "$apkArch" in \
        armhf) export BUILDARCH='arm64' ;; \
        arm64) export BUILDARCH='arm64' ;; \
        amd64) export BUILDARCH='amd64' ;; \
        x86) export BUILDARCH='amd64' ;; \
    esac; \
    # Install AWS CLI
    chmod +x /tmp-installs/install-aws-cli.sh && /tmp-installs/install-aws-cli.sh

# AWS CLI should look for profiles locally
# ENV AWS_CONFIG_FILE=/workspace/sso-profiles

# =================================================================================================
# Cleanup
WORKDIR /
USER root
RUN rm -r /var/cache/* /var/lib/apt/lists/* /tmp-installs/*
