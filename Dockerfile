FROM ubuntu:22.04

RUN apt-get update && export DEBIAN_FRONTEND=noninteractive && \
    apt-get -y install --no-install-recommends \
      build-essential \
      default-jdk \
      locales \
      python3 \
      python3-yaml \
      ruby \
      ruby-dev

RUN gem install fastlane

RUN locale-gen en_US.UTF-8
ENV LC_ALL=en_US.UTF-8
ENV LANG=en_US.UTF-8


ARG USER_UID=1111
ARG USER_GID=$USER_UID

RUN groupadd --gid $USER_GID fast
RUN useradd -s /bin/bash --uid $USER_UID --gid $USER_GID -m fast

USER fast

ARG ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT
ENV ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT


WORKDIR /dotcube/app/DroneController

CMD bash -c "fastlane android assemble && find app/build/outputs/apk -name '*.apk' | xargs -I% mv % /dotcube/app/output"