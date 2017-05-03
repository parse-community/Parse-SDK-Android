#!/bin/bash
# Publishes SNAPSHOTs

REPO_SLUG=parse-community/Parse-SDK-Android
BRANCH=master

set -e

if [ "$TRAVIS_REPO_SLUG" != "$REPO_SLUG" ]; then
  echo "Skipping publishing SNAPSHOT: wrong repository. Expected '$REPO_SLUG' but was '$TRAVIS_REPO_SLUG'"
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Skipping publishing SNAPSHOT: was PR"
elif [ "$TRAVIS_BRANCH" != "$BRANCH" ]; then
  echo "Skipping publishing SNAPSHOT: wrong branch. Expected '$BRANCH' but was '$TRAVIS_BRANCH'"
else
  echo "Publishing SNAPSHOT..."
  ./gradlew artifactoryPublish
  echo "SNAPSHOT published!"
fi
