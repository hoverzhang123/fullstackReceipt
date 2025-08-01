#!/bin/bash

# These take place right before the container is run but after the volume mounts.
# Anything in this script can leverage the fact that the SSH and docker socket are not mounted.

# Abort script if any part fails.
set -e

# Moving to /tmp/home to an empty directory so any changes don't effect the host.
mkdir -p /tmp/home-mutation
cp -af /tmp/home/. /tmp/home-mutation


# When volume mounting a path that doesn't exist on the host, it creates a directory instead of a file
# which is unexpected in some cases, thus if certain files are directories then we need to handle them specially.
if [ -d "/tmp/home-mutation/.zshrc" ]; then
    rm -r /tmp/home-mutation/.zshrc
    cat /home/vscode/.zshrc > /tmp/home-mutation/.zshrc
fi
if [ -d "/tmp/home-mutation/.bashrc" ]; then
    rm -r /tmp/home-mutation/.bashrc
    cat /home/vscode/.bashrc > /tmp/home-mutation/.bashrc
fi

# =================================================================================================
# Either the host machines files can be used, or the container defaults so copy stuff from the host
# The copy is to prevent the container from overwriting host files since they are volume mounted.
cp -af /tmp/home-mutation/. /home/vscode

# =================================================================================================
# Create a temporary rc files so we can prepend our stuff instead of appending
touch /home/vscode/.zshrc-tmp
touch /home/vscode/.bashrc-tmp


# =================================================================================================
# Add `code` to the PATH so it can be targeted in the shell
vscodePath=''
if [ -e /home/vscode/.vscode-server/bin ]; then
    echo "Looking in /home/vscode/.vscode-server/bin for 'code' executable..."
    vscodeServer=/home/vscode/.vscode-server/bin
    vscodeSha=$(ls -t $vscodeServer | head -1)
    # `code` can exist in different spots
    if [ -e $vscodeServer/$vscodeSha/bin/remote-cli/code  ]; then
        vscodePath=$vscodeServer/$vscodeSha/bin/remote-cli
    elif [ -e $vscodeServer/$vscodeSha/bin/code ]; then
        vscodePath=$vscodeServer/$vscodeSha/bin
    fi
else
    vscodeType=$([[ $(dpkg --print-architecture) == "arm64" ]] && echo "linux-arm64" || echo "linux-x64")
    vscodeServer=/vscode/vscode-server/bin
    vscodeSha=$(ls -t $vscodeServer/$vscodeType | head -1)
    vscodePath=$vscodeServer/$vscodeType/$vscodeSha/bin/remote-cli
fi

if [ $vscodePath ]; then
    echo export PATH=\"$vscodePath:$PATH\" >> /home/vscode/.zshrc-tmp
    echo export PATH=\"$vscodePath:$PATH\" >> /home/vscode/.bashrc-tmp
else
    echo "Could not found 'code' executable..."
fi

# =================================================================================================
# Add useful aliases
echo "Adding useful aliases to for the shells"

ALIASES=$(cat <<-END
## Add aliases to make life easier here.
END

)

type="$(stat --printf=%F "/home/vscode/.zshrc")"

# For ZSH
echo $ALIASES >> /home/vscode/.zshrc-tmp
cat /home/vscode/.zshrc >> /home/vscode/.zshrc-tmp
sed -i "s|/Users/\w\+|${HOME}|g" /home/vscode/.zshrc-tmp
mv /home/vscode/.zshrc-tmp /home/vscode/.zshrc

# For bash
echo $ALIASES >> /home/vscode/.bashrc-tmp
cat /home/vscode/.bashrc >> /home/vscode/.bashrc-tmp
sed -i "s|/Users/\w\+|${HOME}|g" /home/vscode/.bashrc-tmp
mv /home/vscode/.bashrc-tmp /home/vscode/.bashrc

# Setting the artifactory credentials for gradle inside the users gradle.properties
mkdir -p /home/vscode/.gradle
echo "systemProp.gradle.wrapperUser=${ARTIFACTORY_USER}" > /home/vscode/.gradle/gradle.properties
echo "systemProp.gradle.wrapperPassword=${ARTIFACTORY_PASSWORD}" >> /home/vscode/.gradle/gradle.properties