# StartBlynkServer.cmd

This batch file allows you to run local server in simpler way. Placing it alongside the server file allows you to:

- Display Server IP address
- Display application and hardware ports
- Running the server without the burden of writing the full path of it.

## Note
Modify the server version to match the version you used


`java -jar server-<version>.jar -dataFolder /path`

## Example:
`java -jar server-0.17.0.jar -dataFolder /path`


## Update 08/09/2016
The script is now able to detect the latest version on your local server folder and run it.

![Scipt in action](https://s16.postimg.org/awmfis8it/2016_09_08_22_47_01.png)

![placement of script in folder with multiple server versions](https://s9.postimg.org/tjo9ysnzj/2016_09_08_22_52_31.png)

