#!/bin/zsh

# The following command will use a Docker image containing Open JDK 8, based on the Alpine Linux image to run a full Maven build
# This container will re-use the local host machine's Maven (.m2) repository so any dependencies already downloaded are available as-is, speeding up builds
# This container is being set up to persist the 'target' directory of each module inside this Maven project so that reports arising from various stages of this build can be reviewed after container is removed

# Build this project and install its build artifacts to your local Maven cache
docker -D run -it --rm -eARTIFACTORY_USERNAME=$ARTIFACTORY_USERNAME -eARTIFACTORY_API_KEY=$ARTIFACTORY_API_KEY \
	-v `pwd`/../..:`pwd`/../.. \
	-v `pwd`/../../..:`pwd`/../../.. \
	-v "$HOME/.m2":/root/.m2 \
	-w `pwd` \
	maven:3.6.3-jdk-8 \
	mvn clean install \
	-Dtest=UIMATests#scriptSentimentTest \
	-s ../../../build/maven-settings.xml \
	-f ../pom.xml \
	-Dartifactory.username=${ARTIFACTORY_USERNAME} \
	-Dartifactory.password=${ARTIFACTORY_API_KEY}
