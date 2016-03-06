FROM ubuntu
RUN apt-get update && apt-get -y install curl sudo procps wget && \
    echo "%sudo ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers && \
    useradd -u 1000 -G users,sudo -d /home/user --shell /bin/bash -m user && \
    echo "secret\nsecret" | passwd user && \
    curl -sSL https://get.docker.com/ | sh && \
    usermod -aG docker user && sudo apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Install Maven
ENV MAVEN_VERSION 3.3.9

RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar xzf - -C /usr/share \
  && mv /usr/share/apache-maven-$MAVEN_VERSION /usr/share/maven \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
    
USER user
ENV JAVA_VERSION=8u65 \
    JAVA_VERSION_PREFIX=1.8.0_65 \
    CHE_LOCAL_CONF_DIR=/home/user/.che
RUN mkdir /home/user/.che && \
    wget \
   --no-cookies \
   --no-check-certificate \
   --header "Cookie: oraclelicense=accept-securebackup-cookie" \
   -qO- \
   "http://download.oracle.com/otn-pub/java/jdk/$JAVA_VERSION-b14/jdk-$JAVA_VERSION-linux-x64.tar.gz" | sudo tar -zx -C /opt/
ENV JAVA_HOME /opt/jdk$JAVA_VERSION_PREFIX
ENV PATH $JAVA_HOME/bin:$PATH


EXPOSE 8080

WORKDIR /home/user
RUN git clone https://github.com/sunix/che-plugin-flux-live-edit.git && \
    cd che-plugin-flux-live-edit && \
    mvn clean install -Dmaven.test.skip -Dfindbugs.skip && \
    git checkout assembly4flux && \
    mvn clean install -Dmaven.test.skip -Dfindbugs.skip -f plugins/plugin-orion/che-plugin-orion-editor/pom.xml  && \
    mvn clean install -Dmaven.test.skip -Dfindbugs.skip -f assembly/pom.xml  && \
    rm -rf /home/user/.m2/ && \
    cp -rf assembly/assembly-main/target/eclipse-che-*/eclipse-che-* /home/user/che

CMD sudo chown -R user:user /home/user && \
    rm -rf /home/user/che/lib-copy/* && \
    mkdir -p /home/user/che/lib-copy/ && \
    cp -rf /home/user/che/lib/* /home/user/che/lib-copy && \
    /home/user/che/bin/che.sh run
