# Pair programming with Eclipse Cloud Development top projects
This is the prototype of the Pair programming plugin for Eclipse Che.

## Resources

Video of the demos can be found

  - [Jug Summer Camp 2015](https://www.youtube.com/watch?v=-FXmt4cfpRo)
  - [Eclipse Con Europe 2015](https://www.youtube.com/watch?v=lX-4ftWzK3s)

Last slides done at Eclipse Con NA 2016: [Slides](https://docs.google.com/presentation/d/1Emhsy9erAd5PVkAwFk7S36aC9M_Yn4pIS0z5GDYFb_8/pub?start=false&loop=false&delayms=3000&slide=id.g10d592b969_1_123)

## How to run the demo:

### Requirements

Docker 1.10 + Java JDK 1.8


### User

Should be with uid 1000 and be in the docker group. `docker ps` should work.

### Build

    git clone https://github.com/sunix/che-plugin-flux-live-edit.git && \
    cd che-plugin-flux-live-edit && \
    mvn clean install -Dmaven.test.skip -Dfindbugs.skip && \
    git checkout assembly4flux && \
    mvn clean install -Dmaven.test.skip -Dfindbugs.skip -f assembly/pom.xml && \
    cp -rf assembly/assembly-main/target/eclipse-che-*/eclipse-che-* .
 
### Run

    ./eclipse-che-4.0.0-RC14-SNAPSHOT/bin/che.sh -r:ip run

Access to Che through with your browser http://ip:8080


### Demo

To make the demo up and running:

1. Create a workspace from a custom stack. Provide the following Dockerfile to your stack:

        FROM sunix/chefluxworkspace

2. Start the workspace, Open in IDE
3. Import the helloworld  project from Github  (can actually be any project):

        https://github.com/sunix/helloworld

4. Create a custom command

  - name: `flux`
  - commandline: `sudo service rabbitmq-server start && cd /home/user/flux-master/node.server && npm start`

5. Start the flux command
6. Open a file to edit
7. Open a new browser windows to the same workspace, open the same file to edit
8. And it should work. If not, try to refresh both browser windows.


