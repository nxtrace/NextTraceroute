# NextTraceroute 

NextTraceroute is a root-free Android route tracing application that defaults to using the NextTrace API.

# Usage

[Releases](https://github.com/nxtrace/NextTraceroute/releases)

Enter target address (IPv4, IPv6, Hostname and URL), press run, and enjoy!

# Example Screenshot
![example1](./pic/1.png)
![example2](./pic/2.png)
![examplesettings](./pic/settings.png)

# FAQ

* Q: Why does the app return "IPv4 and IPv6 native ping failed! Using linux api instead. (Unstable)"?
  
  A: This alert means that your Android device does not support the system's native ping and ping6 programs, and therefore, this application cannot currently be used. A root-free route tracing based on the Linux API is still under development.
