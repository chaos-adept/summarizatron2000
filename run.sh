#!/bin/bash

source .env

docker stop summaryzatron2000
docker rm summaryzatron2000

docker run -d --restart unless-stopped --name summaryzatron2000 \
  -v ./workspace-docker/database:/workspace/database \
  -v ./workspace-docker/tdlight-session:/workspace/tdlight-session \
  -v ./workspace-docker/run-results:/workspace/run-results \
  -v ./workspace-docker/logs:/workspace/logs \
  -e SPRING_PROFILES_ACTIVE=GPT,scheduled \
  -e LANG=en_US.UTF-8 \
  -e LC_ALL=en_US.UTF-8 \
  -e JAVA_OPTS="-Dfile.encoding=UTF-8" \
  -e REINIT_FIRST_RUN=true \
  --env-file .env \
   summaryzatron2000:3.0.0.0-SNAPSHOT

docker logs -f summaryzatron2000