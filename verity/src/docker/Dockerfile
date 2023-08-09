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
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME (use value for defualt-jdk in Ubuntu 18)
ENV JAVA_HOME /usr/lib/jvm/java-1.11.0-openjdk-amd64/

ARG VERITY_VERSION
ENV VERITY_VERSION=${VERITY_VERSION:-2.19.0.0}

ARG VDRTOOLS_VERSION
ENV VDRTOOLS_VERSION=${VDRTOOLS_VERSION:-0.8.6}

# Install the vdr-tools debian package from the github releases
RUN curl -L https://github.com/Bhutan-NDI/verity/releases/download/v${VERITY_VERSION}/libvdrtools_${VDRTOOLS_VERSION}-bionic_amd64.deb --output libvdrtools.deb \
    && dpkg -i libvdrtools.deb \
    && rm libvdrtools.deb

# copy verity-application-assembly.jar and kanela-agent.jar from jars folder into the container
COPY jars/*.jar /usr/lib/verity-application/ FIXME

# copy static Verity configuration files
COPY configuration/* /etc/verity/verity-application/

# Add scripts
COPY scripts/json_to_config.py /usr/local/bin/json_to_config.py
COPY scripts/entrypoint.sh /usr/local/bin/entrypoint.sh

CMD [ "/usr/local/bin/entrypoint.sh" ]

EXPOSE 9000
