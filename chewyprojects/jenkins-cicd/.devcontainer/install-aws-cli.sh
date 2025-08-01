#!/bin/bash

# Abort script if any part fails.
set -e

if [[ $(dpkg --print-architecture) == "arm64" ]]; then
    echo "Installing aws-cli for ARM architecture"
    curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
else # x86_64
    echo "Installing aws-cli for AMD architecture"
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
fi
unzip awscliv2.zip
sudo ./aws/install
rm -r aws && rm awscliv2.zip

