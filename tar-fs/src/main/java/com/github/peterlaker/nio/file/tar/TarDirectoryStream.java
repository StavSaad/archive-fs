package com.github.peterlaker.nio.file.tar;

import java.io.IOException;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class TarDirectoryStream implements DirectoryStream<Path> {

    private final AbstractTarFileSystem tarfs;
    private final byte[] path;
    private final DirectoryStream.Filter<? super Path> filter;
    private volatile boolean isClosed;
    private volatile Iterator<Path> itr;

    TarDirectoryStream(TarPath tarPath,
                       DirectoryStream.Filter<? super java.nio.file.Path> filter)
        throws IOException
    {
        this.tarfs = tarPath.getFileSystem();
        this.path = tarPath.getResolvedPath();
        this.filter = filter;
        if (!tarfs.isDirectory(path))
            throw new NotDirectoryException(tarPath.toString());
    }

    @Override
    public synchronized Iterator<Path> iterator() {
        if (isClosed)
            throw new ClosedDirectoryStreamException();
        if (itr != null)
            throw new IllegalStateException("Iterator has already been returned");

        try {
            itr = tarfs.iteratorOf(path, filter);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new Iterator<Path>() {
            @SuppressWarnings("unused")
			private Path next;
            @Override
            public boolean hasNext() {
                if (isClosed)
                    return false;
                return itr.hasNext();
            }

            @Override
            public synchronized Path next() {
                if (isClosed)
                    throw new NoSuchElementException();
                return itr.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public synchronized void close() throws IOException {
        isClosed = true;
    }


}
