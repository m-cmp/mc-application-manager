#!/bin/bash

# env variable
if [ -z "$(env | grep PROJECT_ROOT)" ]; then
  echo "SET ENV"
  cat $(pwd)/.env | sudo tee -a $HOME/.bashrc
fi

source $HOME/.bashrc