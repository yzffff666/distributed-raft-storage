#!/bin/bash

# Navigate to the project root directory
CD_TO_PROJECT_ROOT="cd /root/DistributeSystem0610"
${CD_TO_PROJECT_ROOT}

# Compile the entire project using Maven
MAVEN_COMPILE_COMMAND="mvn clean package"
${MAVEN_COMPILE_COMMAND}

# Check if the compilation was successful
if [ $? -eq 0 ]; then
    echo "Project compiled successfully."
else
    echo "Failed to compile the project."
fi