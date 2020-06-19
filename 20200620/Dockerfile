FROM 172.16.1.99/transwarp/tdh-baseimage:transwarp-6.2
MAINTAINER datasecurity@transwarp.io

WORKDIR /root
VOLUME /var/guardian

ENV GUARDIAN_HOME /usr/lib/guardian
ENV GUARDIAN_DIR /var/lib/guardian
ENV GUARDIAN_CONF_INTERNAL_DIR /etc/guardian/conf-internal
ENV GUARDIAN_CERT_DIR /etc/guardian/cert
ENV GUARDIAN_SCRIPTS /usr/lib/guardian/scripts/

COPY rootfs/ /

RUN mkdir -p /var/lib/guardian \
    mkdir -p $GUARDIAN_CONF_INTERNAL_DIR \
    mkdir -p $GUARDIAN_CERT_DIR

ENV PATH $PATH:$GUARDIAN_SCRIPTS
