### Context
https://nocodefunctions.com is a web app offering data analytics functions for non coders.

Under the hood, the app relies on a couple of services that desserved to be run separately:

- for the convenience of technical users who might benefit from an API access to key functions
- to trim the main app, which was growing into a big monolith taking more and more time to compile

This repo hosts these services.

## Goal
These services are very simple to re-use. It is open sourced in the hope it will help users who want to audit how https://nocodefunctions.com runs, and to users who would like to deploy these APIs on their own systems for their own use. If you do, please drop me a note (analysis@exploreyourdata.com).

### License: Apache v2
