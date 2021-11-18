# D-arby
slack bot for planning poker

## Local development

### Prepare:
Rename `application-prod.yml.example` to `application-prod.yml`

### Build image:
```shell
./gradlew jibDockerBuild
```

### Push image
```shell
docker push registry.pyn.ru/darby:latest
```

### Pull image
```shell
docker pull registry.pyn.ru/darby:latest
```

### Run
```shell
docker-compose up
```
