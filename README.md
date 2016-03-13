![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)

# Android Client for Para
[ ![Download](https://api.bintray.com/packages/erudika/releases/para-client-android/images/download.svg) ](https://bintray.com/erudika/releases/para-client-android/_latestVersion)
[![Join the chat at https://gitter.im/Erudika/para](https://badges.gitter.im/Erudika/para.svg)](https://gitter.im/Erudika/para?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## What is this?

**Para** was designed as a simple and modular backend framework for object persistence and retrieval.
It helps you build applications faster by taking care of the backend. It works on three levels -
objects are stored in a NoSQL data store or any old relational database, then automatically indexed
by a search engine and finally, cached.

This is the Android client for Para.

### Quick start

1. Use Maven or Gradle and include `para-client-android` as a 
dependency to your project.

```
dependencies {
    compile 'com.erudika:para-client-android:+'
}
```

2. Initialize the client with your access and secret API keys.
```java
// avoid using the secret key on mobile devices
ParaClient client = new ParaClient('ACCESS_KEY', null, applicationContext);
client.signIn("facebook", "fb_access_token", new Response.Listener<ParaObject>() {
    public void onResponse(ParaObject user) {
    	if (user != null) {
    		// success! user is authenticated, JWT token is saved
    	}
    }
});
```

The Android client uses Volley for HTTP requests so all requests are asynchronous, unless you use
the methods that end in `Sync()`. These methods are executed synchronously and will block.
Each method takes a callback (Listener) in which the result is returned.

To trust a self-signed TLS certificate use the method `pc.trustHostnameCertificates(hostname)`:
```java
ParaClient pc = new ParaClient(myAppId, null, applicationContext);
pc.trustHostnameCertificates("myhost.com");
```

## Documentation

###[Read the Docs](http://paraio.org/docs)

## Contributing

1. Fork this repository and clone the fork to your machine
2. Create a branch (`git checkout -b my-new-feature`)
3. Implement a new feature or fix a bug and add some tests
4. Commit your changes (`git commit -am 'Added a new feature'`)
5. Push the branch to **your fork** on GitHub (`git push origin my-new-feature`)
6. Create new Pull Request from your fork

For more information see [CONTRIBUTING.md](https://github.com/Erudika/para/blob/master/CONTRIBUTING.md)

## License
[Apache 2.0](LICENSE)
