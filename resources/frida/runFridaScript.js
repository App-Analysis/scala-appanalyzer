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
const atob = requireGlobal('atob')
const pid = parseInt(process.argv[2])
const script = atob(process.argv[3])

//console.log(script)

const get_obj_from_frida_script = async (pid, script) => {
    try {
        if (!pid) throw new Error('Must provide pid.');
        const frida_device = await frida.getUsbDevice();
        const frida_session = await frida_device.attach(pid);
        const frida_script = await frida_session.createScript(script);
        const result_promise = new Promise((res, rej) => {
            frida_script.message.connect((message) => {
                if (message.type === 'send' && message.payload?.name === 'get_obj_from_frida_script')
                    res(message.payload?.payload);
                else rej(message);
            });
        });
        await frida_script.load();
        await frida_session.detach();
        const res = await result_promise; // We want this to be caught here if it fails, thus the `await`.
        console.log(
            JSON.stringify(
                {"result" : JSON.stringify(res) }
            )
        )
    } catch (err) {
        console.log(JSON.stringify({ "error" : err.toString() } ))
    }
}

get_obj_from_frida_script(pid,script)