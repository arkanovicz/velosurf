# Velosurf Library

```diff
- The Velosurf project is deprecated.
- Please use the <a href="https://github.com/arkanovicz/modality">Modality</a> [Modality](https://github.com/arkanovicz/modality) project.
```

## WHAT IS IT ? 

Velosurf is a database access layer for the [Apache Velocity template engine](http://velocity.apache.org). It provides an automatic database mapping
of tables and relationships without any code generation.

In the context of a Webapp, Velosurf also provides handy tools for
authentication, localization and forms validation.

## WHY VELOSURF ?

Mainly, to avoid rewriting each time specific database mapping layers in each
project involving velocity and database entities that template writers have to
deal with.

Velosurf comes from a design architecture paradigm called the Pull Model, or
the Toolbox Model. It inherits the standard MVC (Model-View-Controller)
architecture paradigm, and its main idea is that the view layer should rely on
a series of dedicated tools that designers use to create views.

The VelocityTools subproject offers the ability to configure context tools
from a standard xml config for Velocity.

Velosurf appears as such a tool.

## HOW TO USE ?

Velosurf can be used either from a standard java application, or from a
VelocityTools webapp.

Please refer to docs/download.html and docs/installation.html.

## HOW TO BUILD ?

First, you'll need to have Apache Ant installed.
Then, go to the ./build/ directory and run 'ant jar'.

## HOW TO TEST ?

With ant installed, you can test Velosurf with the 'ant test' command.

Any question or problem ? Feel free to contact us on the mailing list!
