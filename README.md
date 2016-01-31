# indoqa-http-proxy

```java
HttpProxy httpProxy = new HttpProxyBuilder("/proxy/mount/path", "http://targer.url/mount/path").build();

HttpServletRequest request = ..
HttpServletResponse response = ..

httpProxy.proxy(containerRequest, containerResponse);
```

```
GET http://servlet.host/proxy/mount/path/api/v1/testresource 
 -> http://targer.url/mount/path/api/v1/testresource
```


