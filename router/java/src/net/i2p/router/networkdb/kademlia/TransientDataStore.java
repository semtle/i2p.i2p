package net.i2p.router.networkdb.kademlia;
/*
 * free (adj.): unencumbered; not under the control of others
 * Written by jrandom in 2003 and released into the public domain
 * with no warranty of any kind, either expressed or implied.
 * It probably won't make your computer catch on fire, or eat
 * your children, but it might.  Use at your own risk.
 *
 */

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.i2p.data.DataHelper;
import net.i2p.data.DataStructure;
import net.i2p.data.Hash;
import net.i2p.data.LeaseSet;
import net.i2p.data.RouterInfo;
import net.i2p.router.ProfileManager;
import net.i2p.util.Clock;
import net.i2p.util.Log;
import net.i2p.router.RouterContext;

class TransientDataStore implements DataStore {
    private Log _log;
    private Map _data; // hash --> DataStructure
    protected RouterContext _context;
    
    public TransientDataStore(RouterContext ctx) {
        _context = ctx;
        _log = ctx.logManager().getLog(TransientDataStore.class);
        _data = new HashMap(1024);
        if (_log.shouldLog(Log.INFO))
            _log.info("Data Store initialized");
    }
    
    public Set getKeys() {
        synchronized (_data) {
            return new HashSet(_data.keySet());
        }
    }
    
    public DataStructure get(Hash key) {
        synchronized (_data) {
            return (DataStructure)_data.get(key);
        }
    }
    
    public boolean isKnown(Hash key) {
        synchronized (_data) {
            return _data.containsKey(key);
        }
    }
    
    /** nothing published more than 5 minutes in the future */
    private final static long MAX_FUTURE_PUBLISH_DATE = 5*60*1000;
    /** don't accept tunnels set to expire more than 3 hours in the future, which is insane */
    private final static long MAX_FUTURE_EXPIRATION_DATE = 3*60*60*1000;
    
    public void put(Hash key, DataStructure data) {
        if (data == null) return;
        _log.debug("Storing key " + key);
        Object old = null;
        synchronized (_data) {
            old = _data.put(key, data);
        }
        if (data instanceof RouterInfo) {
            _context.profileManager().heardAbout(key);
            RouterInfo ri = (RouterInfo)data;
            if (old != null) {
                RouterInfo ori = (RouterInfo)old;
                if (ri.getPublished() < ori.getPublished()) {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Almost clobbered an old router! " + key + ": [old published on " + new Date(ori.getPublished()) + " new on " + new Date(ri.getPublished()) + "]");
                    if (_log.shouldLog(Log.DEBUG))
                        _log.debug("Number of router options for " + key + ": " + ri.getOptions().size() + " (old one had: " + ori.getOptions().size() + ")", new Exception("Updated routerInfo"));
                    synchronized (_data) {
                        _data.put(key, old);
                    }
                } else if (ri.getPublished() > _context.clock().now() + MAX_FUTURE_PUBLISH_DATE) {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Hmm, someone tried to give us something with the publication date really far in the future (" + new Date(ri.getPublished()) + "), dropping it");
                    if (_log.shouldLog(Log.DEBUG))
                        _log.debug("Number of router options for " + key + ": " + ri.getOptions().size() + " (old one had: " + ori.getOptions().size() + ")", new Exception("Updated routerInfo"));
                    synchronized (_data) {
                        _data.put(key, old);
                    }
                } else {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Updated the old router for " + key + ": [old published on " + new Date(ori.getPublished()) + " new on " + new Date(ri.getPublished()) + "]");
                    if (_log.shouldLog(Log.DEBUG))
                        _log.debug("Number of router options for " + key + ": " + ri.getOptions().size() + " (old one had: " + ori.getOptions().size() + ")", new Exception("Updated routerInfo"));
                }
            } else {
                if (_log.shouldLog(Log.INFO))
                    _log.info("Brand new router for " + key + ": published on " + new Date(ri.getPublished()));
                if (_log.shouldLog(Log.DEBUG))
                    _log.debug("Number of router options for " + key + ": " + ri.getOptions().size(), new Exception("Updated routerInfo"));
            }
        } else if (data instanceof LeaseSet) {
            LeaseSet ls = (LeaseSet)data;
            if (old != null) {
                LeaseSet ols = (LeaseSet)old;
                if (ls.getEarliestLeaseDate() < ols.getEarliestLeaseDate()) {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Almost clobbered an old leaseSet! " + key + ": [old published on " + new Date(ols.getEarliestLeaseDate()) + " new on " + new Date(ls.getEarliestLeaseDate()) + "]");
                    synchronized (_data) {
                        _data.put(key, old);
                    }
                } else if (ls.getEarliestLeaseDate() > _context.clock().now() + MAX_FUTURE_EXPIRATION_DATE) {
                    if (_log.shouldLog(Log.INFO))
                        _log.info("Hmm, someone tried to give us something with the expiration date really far in the future (" + new Date(ls.getEarliestLeaseDate()) + "), dropping it");
                    synchronized (_data) {
                        _data.put(key, old);
                    }
                }
            }
        }
    }
    
    public int hashCode() {
        return DataHelper.hashCode(_data);
    }
    public boolean equals(Object obj) {
        if ( (obj == null) || (obj.getClass() != getClass()) ) return false;
        TransientDataStore ds = (TransientDataStore)obj;
        return DataHelper.eq(ds._data, _data);
    }
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Transient DataStore: ").append(_data.size()).append("\nKeys: ");
        Map data = new HashMap();
        synchronized (_data) {
            data.putAll(_data);
        }
        for (Iterator iter = data.keySet().iterator(); iter.hasNext();) {
            Hash key = (Hash)iter.next();
            DataStructure dp = (DataStructure)data.get(key);
            buf.append("\n\t*Key:   ").append(key.toString()).append("\n\tContent: ").append(dp.toString());
        }
        buf.append("\n");
        return buf.toString();
    }
    
    public DataStructure remove(Hash key) {
        synchronized (_data) {
            if (_log.shouldLog(Log.DEBUG))
                _log.debug("Removing key " + key.toBase64());
            return (DataStructure)_data.remove(key);
        }
    }
}
