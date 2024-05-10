# NextTraceroute 

NextTraceroute is a root-free Android route tracing application that defaults to using the NextTrace API.

NextTraceroute，一款默认使用NextTrace API的免root安卓版路由跟踪应用。

# Usage

[Releases](https://github.com/nxtrace/NextTraceroute/releases)

Enter target address (IPv4, IPv6, Hostname and URL), press run, and enjoy!

支持IPv4、IPv6、域名输入和URL提取域名，点击运行。

# Launching on Google Play



我们需要您帮助测试以上架Google Play，[Google的新政策](https://support.google.com/googleplay/android-developer/answer/14151465)要求20个人进行14天的测试才能上架，以下是教程：

1. 在以下问卷输入您的Google Play账号绑定的主邮箱： (https://docs.google.com/forms/d/e/1FAIpQLSfg41wXSyPbYKj1X3KkVIdutVd9roV1Z4C42CrLX7fnnkMSSA/viewform?usp=sf_link)
2. 在我手动添加您的邮箱进入测试名单后，就可以在浏览器打开此链接进入测试： (https://play.google.com/apps/testing/com.surfaceocean.nexttraceroute)
3. 删除之前安装的该应用，并在上一条链接进入测试后，就可以进入该Google Play商店页面安装测试应用： (https://play.google.com/store/apps/details?id=com.surfaceocean.nexttraceroute)
4. 在安装以后，Google要求您保持测试和安装状态14天以上以达到测试标准,并在14天内“经常”打开应用。
5. 在Google Play商店页面给出评分和简短的评价、意见和建议。

We need your help to test before launching on Google Play. [Google's new policy](https://support.google.com/googleplay/android-developer/answer/14151465) requires 20 people to test for 14 days before going live. Below is the tutorial:

1. Enter the main email associated with your Google Play account in the following questionnaire: (https://docs.google.com/forms/d/e/1FAIpQLSfg41wXSyPbYKj1X3KkVIdutVd9roV1Z4C42CrLX7fnnkMSSA/viewform?usp=sf_link)
2. After I manually add your email to the test list, you can open this link in your web browser to join the test: (https://play.google.com/apps/testing/com.surfaceocean.nexttraceroute)
3. Uninstall the previously installed app, and after joining the test in the previous link, you can go to this Google Play Store page to install the test app: (https://play.google.com/store/apps/details?id=com.surfaceocean.nexttraceroute)
4. After installation, Google requires you to maintain the test and installation status for more than 14 days to meet the testing standards, and to "frequently" open the app during these 14 days.
5. Leave a rating and a brief review, opinion, and suggestion on the Google Play store page.

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

# LICENSE
```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program [here](./LICENSE).  If not, see <https://www.gnu.org/licenses/>.

Disclaimer: The NextTrace API (hosted at nxtrace.org) used by default in this program is not managed by the program's developer.
We do not guarantee the performance, accuracy, or any other aspect of the NextTrace API,
nor do we endorse, approve, or guarantee the results returned by the NextTrace API.
Users may customize the API server address themselves.

This project uses the libraries listed below. Detailed information can be found in the LICENSE file of this project.

The "dnsjava" library is licensed under the BSD 3-Clause License.

The "seancfoley/IPAddress" library is licensed under the Apache 2.0 License.

The "square/okhttp" library is licensed under the Apache 2.0 License.

The "gson" library is licensed under the Apache 2.0 License.

The "slf4j-android" library is licensed under the MIT License.

The "androidx" library is licensed under the Apache 2.0 License.

```

[Privacy Policy](./PrivacyPolicy.md)
