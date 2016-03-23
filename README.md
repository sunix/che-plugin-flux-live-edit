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

TODO


