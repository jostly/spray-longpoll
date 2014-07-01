## Spray Long-Polling test

When load testing, do not forget to increase open file limit:

    sudo sh -c "ulimit -n 65535 && exec su $LOGNAME"

Package with:

    universal:packageZipTarball
