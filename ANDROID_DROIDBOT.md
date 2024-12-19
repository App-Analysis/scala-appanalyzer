# How to setup droidbot app traversal
There is a dedicated fork from the droidbot repository that we use as it has a database integration.
The repository can be found [here](https://github.com/jannesschuett/droidbot).
Further this device configuration is designed for the appanalyzer-RecipeActor plugin.

## 1. Installing dependencies
It is recommended to use a virtual environment with the python version 3.11.2.
Otherwise, it can happen that droidbot is recognized as a command after running ```pip install -e .``` 
inside of the droidbot directory.
Droidbot can be installed alongside the other requirements required from the Appanalyzer.

## 2. Parameters
Droidbot can be started with the following parameters:

| parameter               | description                                                                                                    |
|-------------------------|----------------------------------------------------------------------------------------------------------------|
| droidbot                | The path to the droidbot executable. Defaults to droidbot which is fine if installed correctly.                |
| droidbot_count          | The number of interactions droidbot should make. Defaults to 15.                                               |
| droidbot_interval       | The interval in seconds between the interactions. Defaults to 3.                                               |
| droidbot_script         | The path to the script that should be used for the interactions. Defaults to None.                             |
| is_emulator             | If the device is an emulator. Defaults to true.                                                                |
| flags                   | The flags that should be passed to droidbot. Defaults to empty list. Example: -grant_perm,-random              |
| output_directory        | The directory where the output of droidbot should be stored. Defaults to None.                                 |
| mitm_intercept_filepath | Path to external parameters used by mitmproxy. Defaults to None. Used for i.e. a vendor list to ignore.        |
| postgres_host           | The host of the postgres database. Defaults to localhost.                                                      |
| postgres_port           | The port of the postgres database. Defaults to 5432.                                                           |
| postgres_db             | The name of the postgres database. Defaults to deepapp.                                                        |
| postgres_user           | The user of the postgres database. Defaults to deepapp.                                                        |
| postgres_pwd            | The password of the postgres database. Defaults to None.                                                       |

These can either be set via ```--param <key>=<value>``` or via a file with ```<key>=<value>``` per line.
