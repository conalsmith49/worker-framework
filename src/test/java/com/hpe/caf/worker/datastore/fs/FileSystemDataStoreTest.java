package com.hpe.caf.worker.datastore.fs;


import com.hpe.caf.api.ConfigurationException;
import com.hpe.caf.api.worker.DataStore;
import com.hpe.caf.api.worker.DataStoreException;
import com.hpe.caf.api.worker.ReferenceNotFoundException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;


public class FileSystemDataStoreTest
{
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    private File temp;
    private final String testData = "test123";


    @Before
    public void setUp()
        throws IOException
    {
        temp = tempDir.newFolder();
    }


    @Test
    public void testDataStore()
        throws ConfigurationException, DataStoreException, IOException
    {
        FileSystemDataStoreConfiguration conf = new FileSystemDataStoreConfiguration();
        conf.setDataDir(temp.getAbsolutePath());
        DataStore store = new FileSystemDataStore(conf);
        final byte[] data = testData.getBytes(StandardCharsets.UTF_8);
        String storeRef = store.store(new ByteArrayInputStream(data));
        InputStream inStr = store.retrieve(storeRef);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int nRead;
        while ((nRead = inStr.read(data, 0, data.length)) != -1) {
            bos.write(data, 0, nRead);
        }
        bos.flush();
        Assert.assertArrayEquals(data, bos.toByteArray());
        Assert.assertEquals(testData.length(), store.getDataSize(storeRef));
    }


    @Test(expected = DataStoreException.class)
    public void testInvalidReference()
        throws DataStoreException, IOException
    {
        FileSystemDataStoreConfiguration conf = new FileSystemDataStoreConfiguration();
        conf.setDataDir(temp.getAbsolutePath());
        DataStore store = new FileSystemDataStore(conf);
        Path p = Paths.get(temp.getAbsolutePath());
        for ( int i = 0; i < 5; i++ ) {
            p = p.resolve("..");
        }
        store.retrieve(p.toString());
    }


    @Test(expected = ReferenceNotFoundException.class)
    public void testMissingRef()
        throws DataStoreException
    {
        FileSystemDataStoreConfiguration conf = new FileSystemDataStoreConfiguration();
        conf.setDataDir(temp.getAbsolutePath());
        DataStore store = new FileSystemDataStore(conf);
        store.retrieve(UUID.randomUUID().toString());
    }
}
