#!/bin/zsh

# The following command will use a Docker image containing Open JDK 8, based on the Alpine Linux image to run a full Maven build
# This container will re-use the local host machine's Maven (.m2) repository so any dependencies already downloaded are available as-is, speeding up builds
# This container is being set up to persist the 'target' directory of each module inside this Maven project so that reports arising from various stages of this build can be reviewed after container is removed

# Build this project and install its build artifacts to your local Maven cache
docker -D run -it --rm \
	-v `pwd`/..:`pwd`/.. -w `pwd`/.. \
	-v "$HOME/.m2":/root/.m2 \
	maven:3.6.3-jdk-8 \
	mvn clean install \
	-Dmaven.test.skip=false \
	-s build/maven-settings.xml \
	-f SystemT/pom.xml \
