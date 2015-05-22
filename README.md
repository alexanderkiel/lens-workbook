__This software is ALPHA, lacks documentation and has to be deployed in conjunction with other Lens modules.__

# Lens Workbook Service

[![Build Status](https://travis-ci.org/alexanderkiel/lens-workbook.svg?branch=master)](https://travis-ci.org/alexanderkiel/lens-workbook)

A workbook storage service for Lens.

## Usage with Leiningen

To start the service with leiningen, run the following command

    lein with-profile production,datomic-free trampoline run -h

This prints a help of all command line options. You need to specify at least a
database URI and a token introspection URI. The database URI has to point to a
Datomic Free Edition Database. If you like to use the Pro Edition, you have
to use the `datomic-pro` leiningen profile instead of the `datomic-free`
profile. An example database URI is:

    datomic:free://localhost:4334/lens-workbook
    
The token introspection URI has to point to the introspection endpoint of the
[lens-auth][1] service.

## Usage on Heroku Compatible PaaS

This application uses the following environment vars:

* `PORT` - the port to listen on
* `DB_URI` - the Datomic database URI
* `TOKEN_INTROSPECTION_URI` -  the OAuth2 token inspection URI to use
* `CONTEXT_PATH` - an optional context path under which the workbook service runs
* `DATOMIC_EDITION` - one of `free` or `pro` with a default of `free`

If you have [foreman][2] installed you can create an `.env` file listing the
environment vars specified above and just type `foreman start`.

## Develop

Running a REPL will load the user namespace. Use `(startup)` to start the server
and `(reset)` to reload after code changes.

If you use [Intellij IDEA][5] with [Cursive][6], you can add a Datomic stub JAR
to your project dependencies as described [here][7]. The stub will provide
signatures and documentation for the Datomic API functions. I can't add the
stub dependency to the project.clj file because it is currently not available on
Clojars. I opened an issue [here][8].

## The Concept behind Workbooks
 
Workbooks are collections of queries. Queries are the heart of Lens. Because
many projects will need more than one query to answer there particular question,
workbooks offer the right granularity to be the key thing to manage.

Workbooks are versioned. Versions form a history which is annotated by
automatically created comments. Each action on a workbook performed by the user
leads to a new version. There is no such thing as a save button. Each user
action is pushed to the server immediately forming version after version.
Workbook versions are immutable. It is always possible to go back to an
arbitrary version.

It is possible to create a duplicate of a workbook. A duplicate shares the
version history with its original workbook. One can still go back to older
versions on a duplicate. The difference is that the original one stays on the
version which was current as the duplicate was created. After creating a
duplicate one has effectively two strings of versions available which are
independent from each other. 

Workbooks have names. Names are not versioned. If one creates a workbook, one
gives it a name. If one duplicates a workbook one should give the duplicate a
different name to help to separate both the original one and the duplicate. If
one creates a duplicate of a workbook and gives it another name, the name will
not revert to the name of the original workbook if one goes back to older
versions. Apart from that, one can still change the name of a workbook.

Workbooks can have snapshots. A snapshot points to a specific version of a
workbook and is itself immutable. Snapshots are like named versions of a
workbook. One can revert a workbook to a specific snapshot. One can also create
a new workbook based on a snapshot of an existing one. Besides there name,
snapshots have a message were one can describe the status of or the reason why
the snapshot was created.

Every user has a collection of private workbooks. Public workbooks on the other
hand are organized in groups. Groups have members. Each member can view, create
and edit workbooks. Workbooks can be moved from private to groups and between
groups.

Workbooks itself have also members. 

## Prepare Datomic Transactor

The Lens Workbook Service uses [shortid][3] to generate workbook ids. Because
the ids are generated inside a Datomic transaction, the transactor needs the
shortid jar file in its classpath. You can download the jar file from the
[shortid releases][4] on GitHub and place them in the lib directory of your
Datomic installation.

## License

Copyright © 2015 Alexander Kiel

Distributed under the Eclipse Public License, the same as Clojure.

[1]: https://github.com/alexanderkiel/lens-auth
[2]: https://github.com/ddollar/foreman
[3]: https://github.com/alexanderkiel/shortid
[4]: https://github.com/alexanderkiel/shortid/releases
[5]: https://www.jetbrains.com/idea/
[6]: https://cursiveclojure.com
[7]: https://cursiveclojure.com/userguide/support.html
[8]: https://github.com/cursiveclojure/cursive/issues/896
