# D-arby
slack bot for planning poker

## Installation
1. Rename `application-prod.yml.example` to `application-prod.yml` 
2. Change vars in `application-prod.yml` with your Slack bot credentials.

## Local development

Test build and run:

```
./gradlew -x test clean build run
```

Connect to the mongodb (assuming you are running mongo in Docker container):

```
docker exec -ti mongodb mongosh
```

```
./gradlew jibDockerBuild
docker image inspect darby:0.0.1-SNAPSHOT
```
