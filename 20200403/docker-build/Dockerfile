FROM 172.16.1.99/transwarp/tdh-baseimage:transwarp-6.2
MAINTAINER datasecurity@transwarp.io

WORKDIR /root

ENV APACHEDS_HOME /usr/lib/guardian-apacheds
ENV APACHEDS_SCRIPTS /usr/lib/guardian-apacheds/scripts/

COPY rootfs/ /

RUN chmod 700 /usr/bin/apacheds-util

RUN mkdir -p /var/lib/guardian-apacheds

ENV PATH $PATH:$APACHEDS_SCRIPTS
