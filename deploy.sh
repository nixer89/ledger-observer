#!/bin/bash

lein build-prod
scp -r resources root@alivingledger.com:~/ledger-observer/
