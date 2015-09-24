package com.hpe.caf.api.worker;


import com.hpe.caf.api.ConfigurationSource;


/**
 * Simple boilerplate to return a DataStore implementation.
 * @since 5.0
 */
public interface DataStoreProvider
{
    DataStore getDataStore(final ConfigurationSource configurationSource)
        throws DataStoreException;
}
