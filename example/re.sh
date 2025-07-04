#!/bin/bash
docker compose down && ../gradlew clean build && ./build.sh && docker compose up -d