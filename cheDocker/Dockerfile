FROM ubuntu:15.10
RUN apt-get update
RUN DEBIAN_FRONTEND=noninteractive apt-get -y dist-upgrade
RUN DEBIAN_FRONTEND=noninteractive apt-get -y install python-software-properties \
                                                      software-properties-common \
                                                      subversion git git-svn tig \
                                                      screen ack-grep pass bash-completion tofrodos bindfs dnsutils vim \
                                                      apache2 php5 libapache2-mod-php5 php5-pgsql build-essential \
                                                      libxml2-dev curl g++ flex bison gperf ruby perl libsqlite3-dev libfontconfig1-dev libicu-dev \
                                                      libfreetype6 libssl-dev libpng-dev libjpeg-dev fontconfig mysql-client \
                                                      nodejs nodejs-dbg nodejs-dev nodejs-legacy npm \
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
  apt-get install -y oracle-java8-installer maven jmeter && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install Maven

USER user
WORKDIR /home/user
RUN git clone https://github.com/sunix/che-plugin-flux-live-edit.git && \
    cd che-plugin-flux-live-edit && \
    mvn clean install -Dmaven.test.skip=true && \
    git checkout 3.x && \
    mvn clean install -Dmaven.test.skip=true

EXPOSE 8080 32768-65535


CMD  sudo service docker start && \
     cd /home/user/che-plugin-flux-live-edit && \
     ./che.sh run
