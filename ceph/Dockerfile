FROM gators/dind16
MAINTAINER gator1@github.com

ENV JEPSEN_GIT_URL https://github.com/gator1/jepsen
ENV LEIN_ROOT true

#
# Jepsen dependencies
#
RUN apt-get -y -q update && \
    apt-get -y -q install software-properties-common && \
    add-apt-repository ppa:openjdk-r/ppa && \
    apt-get -y -q update && \
    apt-get install -qqy \
        openjdk-8-jdk \
        libjna-java \
        git \
        gnuplot \
        ack-grep \
        vim \
        tmux \
        wget


RUN cd / && wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein && mv /lein /usr/bin 
RUN chmod +x /usr/bin/lein

RUN git clone $JEPSEN_GIT_URL /jepsen
RUN cd /jepsen/jepsen && lein install


ADD ./build-dockerized-ceph.sh /usr/local/bin/build-dockerized-ceph.sh
RUN chmod +x /usr/local/bin/build-dockerized-ceph.sh

ADD ./bashrc /root/.bashrc

