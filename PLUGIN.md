# Managing Appanalyzer Plugins

The app analyzer uses a plugin structure, so you can easily implement missing functionality yourself.
This chapter is about compiling and installing your own plugins.

## Build Your own Plugin

If you are missing functionality you can implement your own plugin to fill this gap. Just follow the instructions below 
to get started.

### Publish the scala-appanlyzer locally

We try to keep the newest release of the `scala-appanlyzer` available on maven central, but it never hurts to build
the newest code or if you have trouble finding the scala-appanalyzer via sbt.
Simply clone the `scala-appanlyzer` repository and publish it locally via running ```sbt publishLocal```. 
Make sure published version line up with the one your plugin eventually wants to use.

### Building a Plugin

To build a plugin you need to create a `.jar` file. To achieve this you have to add
`addSbtPlugin("com.github.sbt"      % "sbt-native-packager"   % "1.9.16")` to the `plugin.sbt` of your project.
Furthermore, you have to import the `scala-appanalyzer` by adding `de.halcony %% scala-appanalyzer % <version>` to
your dependencies.

You can then start programming your plugin. The entrypoint for the `scala-appanalyzer` is defined via the class that
implements the `ActorPlugin` trait no main is required.

You can build the plugin using ```sbt package``` and the corresponding jar should be created at 
```./target/scala-[version]/```. Move the jar into the `plugins` folder of the `scala-appanlyzer` and you should be
able to use the plugin immediately.

## Installing a Plugin

If you already have your eyes fixed on a plugin you would like to use. You can either install it manually or if it is
published via the `scala-appanalyzer` itself.

### Install a Locally Built Plugin

If you want to build a plugin from source please refer to [Building a plugin](##Build Your own Plugin). The finally created
jar can then be moved into the `plugins` folder and is immediately available. The required naming convention for the
`scala-appanlyzer` to recognize the plugin is `plugin-[name]-[major.minor.revision].jar`.

### Install a Published Plugin

Some plugins are published so that the `scala-appanlyzer` can be used to manage them. The corresponding plugins have to
be listed in the `plugin` section of the `config.json` within the `available` section:

```
"available" : {
  "TrafficCollection": {
    "owner": "[owner]",
    "repo": "[repository-name]"
  }
}
```

To install such a plugin simply run `./aa.sh plugin install <name>`. To see the available plugins run 
`./aa.sh plugin list available`. To see all plugins that are installed run `./aa.sh plugin list installed`.