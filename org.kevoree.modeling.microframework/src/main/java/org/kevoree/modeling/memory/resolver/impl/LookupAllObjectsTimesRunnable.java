package org.kevoree.modeling.memory.resolver.impl;

import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.memory.manager.impl.DataManager;

public class LookupAllObjectsTimesRunnable implements Runnable {

    private long _universe;
    private long[] _times;
    private long[] _keys;
    private KCallback<KObject[]> _callback;
    private DataManager _dataManager;

    public LookupAllObjectsTimesRunnable(long p_universe, long[] p_times, long[] p_keys, KCallback<KObject[]> p_callback, DataManager p_dataManager) {
        this._universe = p_universe;
        this._times = p_times;
        this._keys = p_keys;
        this._callback = p_callback;
        this._dataManager = p_dataManager;
    }

    @Override
    public void run() {
        /*
        final KContentKey[] tempKeys = new KContentKey[_keys.length];
        for (int i = 0; i < _keys.length; i++) {
            if (_keys[i] != KConfig.NULL_LONG) {
                tempKeys[i] = KContentKey.createUniverseTree(_keys[i]);
            }
        }
        _dataManager.bumpKeysToCache(tempKeys, new KCallback<KObjectChunk[]>() {
            @Override
            public void on(KObjectChunk[] universeIndexes) {
                final KContentKey[] tempKeys2 = new KContentKey[_keys.length * _times.length];
                for (int i = 0; i < _keys.length; i++) {
                    for (int j = 0; j < _times.length; j++) {
                        if (universeIndexes[i] != null) {
                            //TODO check optimize for duplicate
                            KUniverseOrderMap globalUniverseTree = (KUniverseOrderMap) _dataManager.cache().get(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG);
                            tempKeys2[(i * _times.length) + j] = KContentKey.createTimeTree(ResolutionHelper.resolve_universe(globalUniverseTree, (KUniverseOrderMap) universeIndexes[i], _times[j], _universe), _keys[i]);
                        }
                    }
                }
                //todo check duplicate
                _dataManager.bumpKeysToCache(tempKeys2, new KCallback<KObjectChunk[]>() {
                    @Override
                    public void on(KObjectChunk[] timeIndexes) {
                        for (int i = 0; i < _keys.length; i++) {
                            for (int j = 0; j < _times.length; j++) {
                                KContentKey resolvedContentKey = null;
                                if (timeIndexes[i] != null) {
                                    KLongTree cachedIndexTree = (KLongTree) timeIndexes[i];
                                    long resolvedNode = cachedIndexTree.previousOrEqual(_times[j]);
                                    if (resolvedNode != KConfig.NULL_LONG) {
                                        resolvedContentKey = KContentKey.createObject(tempKeys2[i].universe, resolvedNode, _keys[i]);
                                    }
                                }
                                tempKeys2[(i * _times.length) + j] = resolvedContentKey;
                            }
                        }
                        _dataManager.bumpKeysToCache(tempKeys2, new KCallback<KObjectChunk[]>() {
                            @Override
                            public void on(KObjectChunk[] cachedObjects) {
                                KObject[] proxies = new KObject[_keys.length * _times.length];
                                for (int i = 0; i < _keys.length; i++) {
                                    for (int j = 0; j < _times.length; j++) {
                                        if (cachedObjects[(i * _times.length) + j] != null) {
                                            proxies[(i * _times.length) + j] = ((AbstractKModel) _dataManager.model()).createProxy(_universe, _times[j], _keys[i], _dataManager.model().metaModel().metaClasses()[((KObjectChunk) cachedObjects[i]).metaClassIndex()]);
                                            if (proxies[(i * _times.length) + j] != null) {
                                                KLongTree cachedIndexTree = (KLongTree) timeIndexes[(i * _times.length) + j];
                                                cachedIndexTree.inc();
                                                KUniverseOrderMap universeTree = (KUniverseOrderMap) universeIndexes[i];
                                                universeTree.inc();

                                                cachedObjects[(i * _times.length) + j].inc();
                                            }
                                        }
                                    }
                                }
                                _callback.on(proxies);
                            }
                        });
                    }
                });
            }
        });
        */
    }
}
