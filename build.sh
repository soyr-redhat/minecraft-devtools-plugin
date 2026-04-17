#!/bin/bash

# Build script for Minecraft Dev Commands Plugin

echo "Building Dev Commands Plugin..."

# Clean and build
mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo "Plugin JAR: target/dev-commands-plugin-1.0.0.jar"
    echo ""
    echo "To install:"
    echo "  cp target/dev-commands-plugin-1.0.0.jar /path/to/server/plugins/"
    echo ""
else
    echo "Build failed!"
    exit 1
fi
