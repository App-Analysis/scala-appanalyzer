function requireGlobal(packageName) {
    var childProcess = require('child_process');
    var path = require('path');
    var fs = require('fs');

    var globalNodeModules = childProcess.execSync('npm root -g').toString().trim();
    var packageDir = path.join(globalNodeModules, packageName);
    if (!fs.existsSync(packageDir))
        packageDir = path.join(globalNodeModules, 'npm/node_modules', packageName); //find package required by old npm

    if (!fs.existsSync(packageDir))
        throw new Error('Cannot find global module \'' + packageName + '\'');

    var packageMeta = JSON.parse(fs.readFileSync(path.join(packageDir, 'package.json')).toString());
    var main = path.join(packageDir, packageMeta.main);

    return require(main);
}

const frida = requireGlobal('frida')
const app_id = process.argv[2]

const async_is_really_dumb = async () => {
    const session = await frida.getUsbDevice().then((f) => f.attach('Settings'));
    console.log(app_id)
    const script = await session.createScript(`ObjC.classes.CLLocationManager.setAuthorizationStatusByType_forBundleIdentifier_(4, "${app_id}");`);
    const ret = await script.load()
    console.log(ret)
    await session.detach()
}

async_is_really_dumb()