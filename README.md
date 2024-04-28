# NextTraceroute 

NextTraceroute is a root-free Android route tracing application that defaults to using the NextTrace API.

NextTraceroute，一款默认使用NextTrace API的免root安卓版路由跟踪应用。

# Usage

[Releases](https://github.com/nxtrace/NextTraceroute/releases)

Enter target address (IPv4, IPv6, Hostname and URL), press run, and enjoy!

支持IPv4、IPv6、域名输入和URL提取域名，点击运行。

# Contact

The following are all the social media and contact methods of the main maintainer of this project：

以下为本项目主要维护者的所有社交媒体及联系方式：

[r2qb8uc5@protonmail.com](mailto:r2qb8uc5@protonmail.com)

[Telegram channel](https://t.me/nexttraceroute)

# Example Screenshot
![example1](./pic/1.png)
![example2](./pic/2.png)
![examplesettings](./pic/settings.png)

# FAQ

* Q: Why does the app return "IPv4 and IPv6 native ping failed! Using linux api instead. (Unstable)"?

  问：为什么程序返回“IPv4 and IPv6 native ping failed! Using linux api instead. (Unstable)”
  
  A: By default, user space Android programs are not allowed to use raw sockets. This program currently uses the ping and ping6 programs in the Linux system. This alert means that your Android device does not support the system's native ping and ping6 programs; therefore, this application cannot be used at this time. A root-free route tracing feature based on the Linux API is still under development.

  答：默认情况下，用户空间的安卓程序不允许使用原始套接字。本程序目前使用Linux系统自带的ping和ping6程序，这个警告表明您的安卓设备不支持系统原生的ping和ping6程序，因此目前无法使用该程序。基于Linux API的免root路由追踪功能仍在开发中。
