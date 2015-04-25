# LIFE Lens Workbook Service

A workbook storage service for Lens.

## Usage

* lein with-profile production-run trampoline run

## Usage on Heroku Compatible PaaS

This application uses the following environment vars:

* `PORT` - the port to listen on
* `DB_URI` - the main Datomic database URI

## Build

Currently a complete compilation works using:

    lein with-profile production compile :all

## Run

Just use the command from the Procfiles web task which currently is

    lein with-profile production-run trampoline run

Trampoline lets the Leiningen process behind. So this is a production ready run
command.

If you have foreman installed you can create an `.env` file listing the
environment vars specified above and just type `foreman start`.

## Develop

Running a REPL will load the user namespace. Use `(startup)` to start the server
and `(reset)` to reload after code changes.

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
