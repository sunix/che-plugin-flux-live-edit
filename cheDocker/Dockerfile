#FROM ubuntu:15.10
FROM ubuntu
RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive apt-get -y dist-upgrade
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install python-software-properties \
                                                      software-properties-common \
                                                      git tig \
                                                      screen ack-grep bash-completion bindfs dnsutils vim \
                                                      apache2 build-essential \
                                                      curl sudo procps wget && \
    echo "%sudo ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers && \
    useradd -u 1000 -G users,sudo -d /home/user --shell /bin/bash -m user && \
    echo "secret\nsecret" | passwd user && \
    curl -sSL https://get.docker.com/ | sh && \
    usermod -aG docker user && sudo apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*



# Install Java.
RUN \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install Maven
ENV MAVEN_VERSION 3.3.9

RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xzf - -C /usr/share \
  && mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven

USER user
WORKDIR /home/user
RUN mkdir /home/user/.che && \
    git clone https://github.com/sunix/che-plugin-flux-live-edit.git && \
    cd che-plugin-flux-live-edit && \
    mvn clean install -Dmaven.test.skip=true && \
    git checkout 3.x && \
    mvn clean install -Dmaven.test.skip=true

ADD che.properties /home/user/che-plugin-flux-live-edit/assembly-sdk/target/assembly-sdk-3.14.1-SNAPSHOT/assembly-sdk-3.14.1-SNAPSHOT/conf/che.properties
EXPOSE 8080 49152-49162
#ENV CODENVY_LOCAL_CONF_DIR /home/user/.che/

CMD  sudo service docker start && \
    # cd /home/user/che-plugin-flux-live-edit/assembly-sdk/target/assembly-sdk-3.14.1-SNAPSHOT/assembly-sdk-3.14.1-SNAPSHOT/bin/ && \
     cd /home/user/che-plugin-flux-live-edit && \
     ./che.sh run
