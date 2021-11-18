# D-arby
slack bot for planning poker

## Local development

### Prepare:
Rename `application-prod.yml.example` to `application-prod.yml`

### Build image:
```shell
./gradlew jibDockerBuild
```

### Run
```shell
docker-compose up
```

### Push image
```shell
docker push registry.pyn.ru/darby:latest
```
