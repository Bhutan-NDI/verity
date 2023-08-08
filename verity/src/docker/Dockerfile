FROM ubuntu:18.04

WORKDIR /root

RUN apt-get update \
    && apt-get install -y \
       apt-transport-https \
       curl \
       default-jdk \
       software-properties-common \
       libzmq3-dev \
       libsodium23 \
       libssl1.1 \
    && rm -rf /var/lib/apt/lists/

ARG LIBVDRTOOLS_VERSION
ENV LIBVDRTOOLS_VERSION=${LIBVDRTOOLS_VERSION:-0.8.4}
RUN curl https://gitlab.com/evernym/verity/vdr-tools/-/package_files/27311920/download --output libvdrtools_${LIBVDRTOOLS_VERSION}-bionic_amd64.deb \
    && dpkg -i libvdrtools_0.8.4-bionic_amd64.deb \
    && rm -f libvdrtools_0.8.4-bionic_amd64.deb


# TODO
# copy verity-application-assembly.jar and kanela-agent.jar from jars folder into the container
# COPY jars/*.jar /usr/lib/verity-application/ FIXME

# copy static Verity configuration files
COPY configuration/* /etc/verity/verity-application/

# Add scripts
COPY scripts/json_to_config.py /usr/local/bin/json_to_config.py
COPY scripts/entrypoint.sh /usr/local/bin/entrypoint.sh

# Set JAVA_HOME (use value for defualt-jdk in Ubuntu 18)
ENV JAVA_HOME /usr/lib/jvm/java-1.11.0-openjdk-amd64/

CMD [ "/usr/local/bin/entrypoint.sh" ]

EXPOSE 9000
