
# `XRPL Observer`

ledger.observer is a real-time visualization of the XRP Ledger (XRPL). 
Watch it live on http://observer.xrpldata.com

It is written in clojurescript using reacl (pure and truly composable clojurescript react library), three.js & ngraph. 

## Getting started

To start the application in development mode, clone this repository, change to its folder and type the following:

``` 
npm install 
npx webpack
lein build-dev
```


To compile the application for producation, type the following:

``` 
npm install 
npx webpack
lein build-prod
```
