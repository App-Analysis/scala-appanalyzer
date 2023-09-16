# How to Setup Traffic Interception

There are two ways of setting up traffic interception. You can do it the easy way by `Configuring a Proxy` or the hard 
but transparent way by `Using OpenWRT Router and iptables`. If you are working with the android emulator only the 
proxy option is available. Both options are available for the iPhone and for Android.

## 1. Installing Dependencies

For the basic traffic interception on the measurement machines side you require `mitmproxy` which can be installed
using `pip mitmproxy`. Furthermore, we require the `dotenv` package that can be installed via `pip install python-dotenv`
or by using the local package manager.

As we want to store any intercepted traffic in a postgres database we also require the corresponding python package 
`psycopg2` which can be installed via `pip install psycopg2` or also using the local package manager.


## 2.A Configuring a Proxy

Either iOS and Android provide advanced configuration options for any connected WLAN. Simply enter the ip of your 
machine and the mitmproxy port as the proxy configuration for the WLAN connection. To make sure that any traffic is routed
via the WLAN and not via mobile traffic we recommend to deactivate mobile data usage entirely.

## 2.B Using OpenWRT Router and iptables

This method assumes that the smartphone and the measurement machine are connected to the same [OpenWRT](https://reedmideke.github.io/networking/2021/01/04/mitmproxy-openwrt.html) router network.
It is recommended to set a static IP for the proxy machine as well as the phone. The following commands have to be 
executed on the used OpenWRT WLAN router to configure a transparent traffic rerouting to the mitmproxy on the measurement
machine.

```
# create a routing rule entry with ID 101 (must be unique)
echo 101 mitmproxy >> /etc/iproute2/rt_tables

# Configuration below is ephemeral in case of reboot always check router before collection
# <ip of phone> is the ip of the phone you want to intercept the traffic of
# <ip of proxy> is the ip of the machine you want to use as a proxy (i.e., running this tool)
# <router wlan device> the name of the device being the wlan, in our case it was br-lan (use `ip a` to find your wlan device)
# add rule for phones to be monitored
ip rule add from <ip of phone> lookup mitmproxy
# add rule for forwarding traffic to the proxy host 
ip route add default via <proxy host> dev <router wlan device> table mitmproxy
# set mangling
iptables -t mangle -N mitmproxy
# add rule for port 80
iptables -A mitmproxy -i <router wlan device> -t mangle -p tcp -s <phone ip> --dport 80 -j MARK --set-mark 101
# add rule for port 443
iptables -A mitmproxy -i <router wlan device> -t mangle -p tcp -s <phone ip> --dport 443 -j MARK --set-mark 101
# activating the rules
iptables -t mangle -I PREROUTING -j mitmproxy
# deactivating the rules (after experiment)
iptables -t mangle -D PREROUTING 1
```
The below commands have to be set on the proxy host. This stuff should be ephemeral and not persist after a reboot.
```
# deactivate redirects
sysctl -w net.ipv4.conf.all.send_redirects=0
# allowing ip forwarding
sysctl -w net.ipv4.ip_forward=1
# redirect to the proxy
iptables -t nat -I PREROUTING -i <wlan device> -p tcp --dport 80 -j REDIRECT --to-port 8080
iptables -t nat -I PREROUTING -i <wlan device> -p tcp --dport 443 -j REDIRECT --to-port 8080
```
**DISCLAIMER**: Always test those configurations prior to running your experiments.
Running mitmproxy and checking if simply using the phone browser shows any requests suffices.
