# Docker Container for the scala-appanalyzer

This container provides the working environment for the Appanalyzer in a Docker container.

## Preparation
On the host PC must also be set up according to the provided documentation in the scala-appanalyzer:
- Postgre database
- App dataset
- Android Emulator with Android sdk and tool


## Steps

First of all, the preparation steps must be completed

### SSH Agent
Start the ssg agent:
```bash
eval "$(ssh-agent -s)"
```

And add the ssh key to it:
```bash
ssh-add ~/.ssh/id_rsa
```

### Build Container

```bash
DOCKER_BUILDKIT=1 docker build --ssh default -t [env_name] . || docker image prune -a
```

And delete directly if an error occurs.

### Start

Show Emulator on host:
```bash
xhost +local:root
```

Start Container:
```bash
sudo docker run --privileged -it --rm \
    -p [mitmproxy_port]:[mitmproxy_port] \
    # For displaying the emulator to the host
    -v /dev/kvm:/dev/kvm \
    -e DISPLAY=$DISPLAY \
    -v /tmp/.X11-unix:/tmp/.X11-unix \
    # The exact same path is important for the next otherwise
    # the intern linking isn't working
    -v /path/to/.android:/path/to/.android \
    -v /path/to/Android/Sdk:/path/to/Android/Sdk \
    -v /path/to/app/dataset:/path/to/app/dataset/on/container \
    -e DATABASE_URL="[user]://[host_gateway_docker_container]:[db_port]/[db_name]" \
    [env_name]:latest
```



### Useful Information

Here I am writing down some information that was important to me.

#### Database

In file postgresql.conf listen for all addresses: listen_addresses = '*'.
In file pg_hba.conf add entry: host all all 172.17.0.0/16 trust.

#### Emulator

Get the emulator path with:
```bash
avdmanager list avd
```
Test if the emulator is startable:
```bash
emulator -avd Pixel_6a_API_34_2 -no-snapshot-load
```

#### Appanalyzer

Run functionality check:
```bash
./aa.sh run android_emulator_root '[path/app.apk]' functionalityCheck plugin TrafficCollection -p "time-ms=60000"
```

#### Normal run
Run emulator:
```bash
./aa.sh run android_emulator_root '[path/to/appdataset/]' plugin TrafficCollection -p "time-ms=60000"
```

#### Docker

Stop container:
```bash
docker stop [container]
```

See all containers:
```bash
docker ps -a
```

Delete container:
```bash
sudo docker rm name
```

Delete all failed builds:
```bash
docker image prune -a
```

See docker images:
```bash
docker images
```

Delete docker images:
```bash
docker rmi [env]
```
