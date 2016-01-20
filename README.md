![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

# Android Client for Para


## What is this?

**Para** was designed as a simple and modular back-end framework for object persistence and retrieval.
It enables your application to store objects directly to a data store (NoSQL) or any relational database (RDBMS)
and it also automatically indexes those objects and makes them searchable.

This is the Android client for Para.

### Quick start

1. Use Maven or Gradle and include `para-client-android` as a 
dependency to your project.

```
dependencies {
    compile 'com.erudika:para-client-android:1.18.0'
}
```

2. Initialize the client with your access and secret API keys.
```java
ParaClient client = new ParaClient('ACCESS_KEY', 'SECRET_KEY');
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