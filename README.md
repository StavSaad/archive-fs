archive-fs
======

This is a repository containing my implementations of JDK 1.7's FileSystem to support various archiving fromats.

tar-fs
======

A pure java implementation supprting the ustar file format. contains the following URL schemes to use:

<B>"tar:"</B> - pure .tar files - My own implementation<BR>
<B>"tar.gz:"</B> - .tar.gz files - Using java.util.zip package <BR>
<B>"tar.bz2:"</B> - .tar.bz2 files - Using Apache's Common Compress 1.8<BR>
<B>"tar.xz:"</B> - .tar.xz files - Using Apaches's Common Compress 1.8
