# indoqa-http-proxy

A simple http proxy that can be used inside Java Servlet environments like Wicket, JavaSpark etc. or inside a ServletFilter. Inspired by the simplicity of [node-http-proxy](https://www.npmjs.com/package/http-proxy) it delegates all requests after a *proxyMountPath* to another server definded by *targetBaseUrl*.

```java
HttpProxy httpProxy = new HttpProxyBuilder("/proxy/mount/path", "http://targer.url/mount/path").build();

HttpServletRequest request = ..
HttpServletResponse response = ..

httpProxy.proxy(request, response);
```

```
GET http://servlet.host/proxy/mount/path/api/v1/testresource 
 -> http://targer.url/mount/path/api/v1/testresource
```


