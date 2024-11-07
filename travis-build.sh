#!/bin/bash

set -euo pipefail

maven_goal=install

if [ "${TRAVIS_BRANCH}" = "main" ]; then
    if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
        # build of main, run test and push artifacts
        maven_goal=deploy
        skip_test=false
    else
        # build of pull request into main, run test
        maven_goal=install
        skip_test=false
    fi
elif [[ "${TRAVIS_BRANCH}" =~ "release" && "${TRAVIS_COMMIT_MESSAGE}" = "TRIGGER_RELEASE" ]]; then
    # set up git repository for release build
    git checkout ${TRAVIS_BRANCH}
    git branch -u origin/${TRAVIS_BRANCH}
    git config branch.${TRAVIS_BRANCH}.remote origin
    git config branch.${TRAVIS_BRANCH}.merge refs/heads/${TRAVIS_BRANCH}

    # trigger maven-release-plugin to release automatically
    maven_goal='release:prepare release:perform'
    skip_test=false
else
    # build of a branch except main, build only
    maven_goal=install
    skip_test=true
fi

echo "maven_goal: ${maven_goal}"
echo "skip_test: ${skip_test}"

mvn clean ${maven_goal} -B -f SystemT/pom.xml -s build/maven-settings.xml -Dmaven.test.skip=${skip_test} -Dossrh.username=${OSSRH_USERNAME} -Dossrh.password=${OSSRH_PASSWORD} -Dgpg.passphrase=${GPG_PASSPHRASE}
